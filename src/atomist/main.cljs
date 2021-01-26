(ns atomist.main
  (:require
   [cljs-node-io.core :as io :refer [slurp spit]]
   [cljs.core.async :refer [<! >!] :refer-macros [go]]
   [clojure.string :as s]
   [cljs.nodejs.shell :as sh]
   [goog.string :as gstring]
   [goog.string.format]
   [cljs.reader]
   [cljs.pprint :refer [pprint]]))

(enable-console-print!)

(def cache-file (io/file (gstring/format "%s/.1password-delete-cache-never-save" (.. js/process -env -HOME))))

(defn- op-mappings []
  (cljs.reader/read-string (slurp (gstring/format "%s/.export-1password" (.. js/process -env -HOME)))))

(defn- document-handler [mapping cache-out out err]
  (let [item (js->clj (.parse js/JSON out) :keywordize-keys true)]
    (doseq [[env-name path] (seq mapping) 
            :let [value (get-in item path)
                  export (gstring/format "export %s=\"%s\"" env-name value)]]
      (.write cache-out (str export "\n"))
      (println export))))

(defn- item-handler [mapping cache-out out err]
  (let [item (js->clj (.parse js/JSON out) :keywordize-keys true)]
    (doseq [[env-name designation] (seq mapping)
            :let [value (or
                         (->> item :details :fields (filter #(= designation (:designation %))) first :value)
                         (if (= "password" designation) (->> item :details :password)))
                  export (gstring/format "export %s=%s" env-name value)]]
      (.write cache-out (str export "\n"))
      (println export))))

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

(defn extract []
  (go
    (try
      (let [export-1password (op-mappings)]
        (if (not (.exists cache-file))
          (let [cache-file-stream (io/output-stream cache-file)
                done-ch (cljs.core.async/chan)]
            (.on cache-file-stream "finish" (fn [& _] (go (>! done-ch :finished))))
            (doseq [[k v] (seq export-1password)]
              (cond

                (s/starts-with? k "document")
                (let [{:keys [out err]} (sh/sh "op" "get" "document" (s/replace k #"document/" ""))]
                  (op-handler out err (partial document-handler (get export-1password k) cache-file-stream)))

                :else
                (let [{:keys [out err]} (sh/sh "op" "get" "item" k)]
                  (op-handler out err (partial item-handler (get export-1password k) cache-file-stream)))))
            (.end cache-file-stream)
            (<! done-ch))
          (println (slurp cache-file))))
      (catch :default ex
        (println "error" (ex-message ex))
        (pprint (ex-data ex))
        (.exit js/process 1)))))

(defn ^:export handler []
  (let [args (drop 2 (. js/process -argv))]
    (go
      (<! (extract))
      (.exit js/process 0))))

(comment (extract))
