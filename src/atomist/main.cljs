(ns atomist.main
  (:require
   [cljs-node-io.core :as io :refer [slurp spit]]
   [cljs.core.async :refer [<! >!] :refer-macros [go]]
   [clojure.string :as s]
   [cljs.nodejs.shell :as sh]
   [goog.string :as gstring]
   [goog.string.format]
   [com.rpl.specter :as specter]
   [cljs.reader]
   [cljs.pprint :refer [pprint]]))

(enable-console-print!)

(defn note-path [path]
  (let [[section title] (s/split path "/")]
    [:details :sections specter/ALL (fn [x] (= (:title x) section)) :fields specter/ALL (fn [x] (= (:t x) title)) :v]))

(def cache-file (io/file (gstring/format "%s/.1password-delete-cache-never-save" (.. js/process -env -HOME))))

(defn- op-mappings []
  (cljs.reader/read-string (slurp (gstring/format "%s/.export-1password.edn" (.. js/process -env -HOME)))))

(defn- document-handler [mapping cache-out out err]
  (let [item (js->clj (.parse js/JSON out) :keywordize-keys true)]
    (doseq [[env-name path] (seq mapping) 
            :let [value (get-in item path)
                  export (gstring/format "export %s=\"%s\"" env-name value)]]
      (.write cache-out (str export "\n"))
      (println export))))

(defn- item-handler [mapping cache-out out _]
  (let [item (js->clj (.parse js/JSON out) :keywordize-keys true)]
    (doseq [[env-name designation] (seq mapping)
            :let [value (->> item :fields (filter #(= designation (:label %))) first :value)
                  export (gstring/format "export %s=%s" env-name value)]]
      (.write cache-out (str export "\n"))
      (println export))))

(defn- specter-item-handler [mapping cache-out out _]
  (let [item (js->clj (.parse js/JSON out) :keywordize-keys true)]
    (doseq [[env-name path] (seq mapping)]
      (let [path (note-path path)
            value (first (specter/select path item))
            export (gstring/format "export %s=%s" env-name value)]
        (.write cache-out (str export "\n"))
        (println export)))))

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

(defn extract-one-item [k v cache-file-stream]
  (println "extract " k v)
  (cond
    (s/starts-with? k "document")
    (let [{:keys [out err]} (sh/sh "op" "get" "document" (s/replace k #"document/" ""))]
      (op-handler out err (partial document-handler v cache-file-stream)))

    (s/starts-with? k "note")
    (let [{:keys [out err]} (sh/sh "op" "item" "get" "--format" "json" (s/replace k #"note/" ""))]
      (op-handler out err (partial item-handler v cache-file-stream)))

    :else
    (let [{:keys [out err]} (sh/sh "op" "item" "get" "--format" "json" k)]
      (op-handler out err (partial item-handler v cache-file-stream)))))

(comment
  (extract-one-item
   "note/gcr-test AWS Account Creds"
   {"ECR_ACCESS_KEY_ID" "Creds/accessKeyId"
    "ECR_SECRET_ACCESS_KEY" "Creds/secretAccessKey"}
   (io/output-stream (io/file "tmp.txt"))
   ))

(defn extract-all []
  (go
    (try
      (let [export-1password (op-mappings)]
        (if (not (.exists cache-file))
          (let [cache-file-stream (io/output-stream cache-file)
                done-ch (cljs.core.async/chan)]
            (.on cache-file-stream "finish" (fn [& _] (go (>! done-ch :finished))))
            (doseq [[k v] (seq export-1password)]
              (extract-one-item k v cache-file-stream))
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
      (<! (extract-all))
      (.exit js/process 0))))

(comment (extract-all))
