(ns datalogger.core
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.string :as strings]
            [datalogger.impl.config :as config]
            [datalogger.impl.context :as context]
            [datalogger.impl.utils :as utils]
            [datalogger.specs :as specs])
  (:import (clojure.lang Agent IFn ISeq)
           (java.io StringWriter)
           (java.util.concurrent Executor)
           (java.util.logging Level Logger)
           (org.slf4j.bridge SLF4JBridgeHandler)))

(set! *warn-on-reflection* true)

(defn update-configuration! [f & args]
  "Update current config"
  (letfn [(swap [old-config]
            (config/normalize-config (apply f old-config args)))]
    (utils/quietly (swap! config/*config* swap))))

(defn set-configuration!
  "Set logging configuration options."
  [config]
  (update-configuration! (constantly config)))

(defmacro with-config
  "Alters the config/*config* binding to new configuration for the execution of body."
  [config & body]
  `(let [validator# (get-validator config/*config*)]
     (binding [config/*config*
               (doto (atom (config/normalize-config ~config))
                 (set-validator! validator#))]
       ~@body)))

(defmacro with-config+
  "Alters the config/*config* binding to new configuration for the execution of body."
  [config & body]
  `(with-config (utils/deep-merge (deref config/*config*) ~config) ~@body))

(defmacro with-level-context
  "Add data onto the context stack to be included in any logs at the given level or less
   severe than the given level. Data is deep merged with data already on the stack."
  [level context & body]
  `(binding [context/*context* (utils/deep-merge context/*context* {(strings/upper-case (name ~level)) ~context})]
     ~@body))

(defmacro with-context
  "Add data onto the context stack to be included in any logs.
   Data is deep merged with data already on the stack."
  [context & body]
  `(with-level-context :error ~context ~@body))

(defmacro awaiting
  "Waits for logs in the buffer to be written before returning."
  [& body]
  `(try
     ~@body
     (finally
       (await context/logging-agent))))

(defmacro capture
  "Capture the logs being written."
  [& body]
  `(let [config#
         (deref config/*config*)
         splitter#
         (if (get-in config# [:json-options :indent])
           utils/split-pretty-printed
           strings/split-lines)
         string-tee#
         (StringWriter.)
         raw-result#
         (with-open [tee# string-tee#]
           (case (:stream config#)
             :stderr (binding [*err* (utils/teed-writer *err* tee#)]
                       (awaiting ~@body))
             :stdout (binding [*out* (utils/teed-writer *out* tee#)]
                       (awaiting ~@body))))
         raw-lines#
         (str string-tee#)]
     [(->> raw-lines#
           (splitter#)
           (remove strings/blank?)
           (mapv #(json/read-str %1)))
      raw-result#]))

(defmacro log
  "Write to the log.

    required argument that should be first.

      keyword to indicate the log level or a vector of [logger-name level]

    optional arguments that may appear in any order.

      a map of additional data to write to the log entry.
      a throwable instance to include a stack trace / exception data.
      string message that can be formatted with data from the data map.
  "
  [& args]
  (let [calling-ns (name (.getName *ns*))]
    `(let [callsite#    ~(assoc (meta &form) :ns calling-ns)
           categorized# (utils/categorize-arguments (list ~@args))
           config#      (deref config/*config*)]
       (when ((config/get-log-filter config#)
              (or (:logger categorized#)
                  (:ns callsite#))
              (:level categorized#))
         (let [context# (context/get-context-for-level (:level categorized#))
               mdc#     (context/get-mdc)
               extra#   (context/execution-context)
               out#     (config/get-log-stream (:stream config#))]
           (.dispatch ^Agent context/logging-agent ^IFn
                      (fn [_#]
                        (let [full-context# (utils/deep-merge mdc# extra# context# callsite# (:data categorized# {}))]
                          (context/write! config# out#
                                          (cond-> full-context#
                                            (contains? categorized# :message)
                                            (assoc :message (utils/template (:message categorized#) full-context#))
                                            (contains? categorized# :logger)
                                            (assoc :logger (:logger categorized#))
                                            (not (contains? categorized# :logger))
                                            (assoc :logger (:ns callsite#))
                                            (contains? categorized# :level)
                                            (assoc :level (:level categorized#))
                                            (some? (:exception categorized#))
                                            (assoc :exception (:exception categorized#))))))
                      ^ISeq
                      (seq [])
                      ^Executor
                      Agent/soloExecutor)))
       nil)))


(defmacro deflogged
  "Define a function that logs its inputs and outputs (and errors). Will not
   log inputs whose symbol begins with an underscore."
  [symbol docs bindings & body]
  `(defn ~symbol ~docs ~bindings
     (let [inputs# ~(->> (utils/walk-seq bindings)
                         (filter symbol?)
                         (remove #(strings/starts-with? (name %) "_"))
                         (map (fn [s] [(keyword s) s]))
                         (into {}))
           fun#    (symbol (resolve '~symbol))]
       (try
         (let [return# (do ~@body)]
           (log :info {:datalogger/function fun# :datalogger/input inputs# :datalogger/output return#})
           return#)
         (catch Exception e#
           (log :error e# {:datalogger/function fun# :datalogger/input inputs#})
           (throw e#))))))


; runtime initialization
(defonce _init

  (when-not *compile-files*

    ; attach an error handler to the logging agent so we know if
    ; there's an error in the logging system
    (set-error-handler!
      context/logging-agent
      (fn [^Agent a ^Throwable e]
        (with-config+ {"datalogger.core" :trace}
          (log :error e "Failed to write log message."))))

    (letfn [(validator [config]
              (if-not (s/valid? ::specs/config config)
                (do (log :error "Bad datalogger configuration provided, will continue using previous value."
                         {:problems (::s/problems (s/explain-data ::specs/config config))})
                    false)
                true))]

      (SLF4JBridgeHandler/removeHandlersForRootLogger)

      (SLF4JBridgeHandler/install)

      (doto (Logger/getLogger "")
        (.setLevel Level/ALL))

      (set-validator! config/*config* validator)

      (when-some [resource (io/resource "datalogger.edn")]
        (set-configuration! (utils/parse-config (slurp resource)))
        (log :info "Merged datalogger.edn from classpath with defaults for logging configuration."))

      (when (get-in config/*config* [:exceptions :handle-uncaught])
        (let [original (Thread/getDefaultUncaughtExceptionHandler)]
          (Thread/setDefaultUncaughtExceptionHandler
            (reify Thread$UncaughtExceptionHandler
              (^void uncaughtException [this ^Thread thread ^Throwable throwable]
                (try
                  (log :error throwable {"@thread" thread})
                  (finally
                    (when (some? original)
                      (.uncaughtException original thread throwable))))))))))))

