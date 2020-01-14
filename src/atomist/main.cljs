(ns atomist.main
  (:require
   [cljs-node-io.core :as io :refer [slurp spit]]
   [clojure.string :as s]
   [cljs.nodejs.shell :as sh]
   [goog.string :as gstring]
   [goog.string.format]
   [cljs.pprint :refer [pprint]]))

(enable-console-print!)

(def export-1password {"Clojars" {"CLOJARS_USERNAME" "username"
                                  "CLOJARS_PASSWORD" "password"}
                       "ApiKeySlimslenderslacksProd" {"API_KEY_SLIMSLENDERSLACKS_PROD" "password"}})

(defn ^:export handler []
  (try
    (let [args (drop 2 (. js/process -argv))]
      (doseq [[k v] (seq export-1password)]
        (let [{:keys [out err]} (sh/sh "op" "get" "item" k)]
          (cond
            (s/includes? err "401: Authentication required.")
            (throw (ex-info "Authentication required." {:error :auth
                                                        :message "Authentication required:  please run `eval $(op signin atomist)` and then retry"}))

            (s/includes? err "You are not currently signed in. Please run `op signin --help` for instructions")
            (throw (ex-info err {:error :auth
                                 :message "You are not signed in:  please run `eval $(op signin atomist)` and then retry"}))

            :else
            (let [item (js->clj (.parse js/JSON out) :keywordize-keys true)]
              (doseq [[env-name designation] (seq (get export-1password k))]
                (println (gstring/format "export %s=%s" env-name (or
                                                                  (->> item :details :fields (filter #(= designation (:designation %))) first :value)
                                                                  (if (= "password" designation) (->> item :details :password)))))))))))
    (.exit js/process 0)
    (catch :default ex
      (println "error" (ex-message ex))
      (pprint (ex-data ex))
      (.exit js/process 1))))
