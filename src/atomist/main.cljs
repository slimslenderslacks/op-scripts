(ns atomist.main
  (:require
   [cljs-node-io.core :as io :refer [slurp spit]]
   [clojure.string :as s]
   [cljs.nodejs.shell :as sh]
   [goog.string :as gstring]
   [goog.string.format]
   [cljs.reader]
   [cljs.pprint :refer [pprint]]))

(enable-console-print!)

(defn- op-mappings []
  (cljs.reader/read-string (slurp (gstring/format "%s/.export-1password" (.. js/process -env -HOME)))))

(defn- document-handler [mapping out err]
  (let [item (js->clj (.parse js/JSON out) :keywordize-keys true)]
    (doseq [[env-name path] (seq mapping)]
      (println (gstring/format "export %s=\"%s\"" env-name (get-in item path))))))

(defn- item-handler [mapping out err]
  (let [item (js->clj (.parse js/JSON out) :keywordize-keys true)]
    (doseq [[env-name designation] (seq mapping)]
      (println (gstring/format "export %s=%s" env-name (or
                                                            (->> item :details :fields (filter #(= designation (:designation %))) first :value)
                                                            (if (= "password" designation) (->> item :details :password))))))))

(defn- op-handler [out err callback]
  (cond
    (s/includes? err "401: Authentication required.")
    (throw (ex-info "Authentication required." {:error :auth
                                                :message "Authentication required:  please run `eval $(op signin atomist)` and then retry"}))

    (s/includes? err "You are not currently signed in. Please run `op signin --help` for instructions")
    (throw (ex-info err {:error :auth
                         :message "You are not signed in:  please run `eval $(op signin atomist)` and then retry"}))

    :else
    (callback out err)))

(defn ^:export handler []
  (try
    (let [args (drop 2 (. js/process -argv))
          export-1password (op-mappings)]
      (doseq [[k v] (seq export-1password)]
        (cond

          (s/starts-with? k "document")
          (let [{:keys [out err]} (sh/sh "op" "get" "document" (s/replace k #"document/" ""))]
            (op-handler out err (partial document-handler (get export-1password k))))

          :else
          (let [{:keys [out err]} (sh/sh "op" "get" "item" k)]
            (op-handler out err (partial item-handler (get export-1password k)))))))
    (.exit js/process 0)
    (catch :default ex
      (println "error" (ex-message ex))
      (pprint (ex-data ex))
      (.exit js/process 1))))
