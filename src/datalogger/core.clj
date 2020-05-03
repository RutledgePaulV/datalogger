(ns datalogger.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [jsonista.core :as jsonista]
            [clojure.string :as strings]
            [datalogger.utils :as utils])
  (:import (org.slf4j ILoggerFactory)
           (org.slf4j.helpers BasicMarkerFactory BasicMDCAdapter LegacyAbstractLogger)
           (java.time Instant)
           (java.net InetAddress)))

(def DEFAULTS
  {:levels {"*" :warn}})

(defn normalize-config [config]
  (let [conf (utils/deep-merge DEFAULTS config)]
    (assoc conf :filter (utils/compile-filter (:levels conf)))))

(def CONFIG (atom (normalize-config {})))

(defn set-configuration! [config]
  (reset! CONFIG (normalize-config config)))

(defn load-configuration! [resource-name]
  (when-some [resource (io/resource resource-name)]
    (set-configuration! (edn/read-string (slurp resource)))))

(def mapper
  (let [options {:encode-key-fn true :decode-key-fn true}]
    (jsonista/object-mapper options)))

(defn mask-sensitive-data [x]
  x)

(defn ensure-serializable [x]
  (utils/filter-vals some? x))

(defn log-level-enabled? [logger level]
  ((:filter @CONFIG) logger level))

(defonce mdc-adapter (delay (BasicMDCAdapter.)))

(def ^:dynamic *context* {})

(defmacro with-context [context & body]
  `(let [old# *context*]
     (binding [*context* (utils/deep-merge old# ~context)]
       ~@body)))

(defn include-mdc [m]
  (utils/deep-merge (.getCopyOfContextMap (force mdc-adapter)) m))

(defn include-context [m]
  (utils/deep-merge *context* m))

(def hostname
  (delay (.getHostName (InetAddress/getLocalHost))))

(defn additional-context []
  {"@timestamp" (str (Instant/now))
   :hostname    (force hostname)
   :thread      (.getName (Thread/currentThread))})

(defn write! [m]
  (let [clean (-> m
                  include-mdc
                  include-context
                  ensure-serializable
                  mask-sensitive-data
                  utils/stringify-keys
                  utils/consistent-order)]
    (jsonista/write-value *out* clean mapper)
    (newline)))

(defn agent-error-handler [agent error]
  (send-off agent (fn [_] (write! {:message "Error writing to log." :throwable error}))))

(def logging-agent (agent nil :error-mode :continue :error-handler agent-error-handler))

(defn data-logger [logger-name]
  (proxy [LegacyAbstractLogger] []
    (getName []
      logger-name)
    (isTraceEnabled []
      (log-level-enabled? logger-name :trace))
    (isDebugEnabled []
      (log-level-enabled? logger-name :debug))
    (isInfoEnabled []
      (log-level-enabled? logger-name :info))
    (isWarnEnabled []
      (log-level-enabled? logger-name :warn))
    (isErrorEnabled []
      (log-level-enabled? logger-name :error))
    (getFullyQualifiedCallerName [] nil)
    (handleNormalizedLoggingCall [level marker message arguments throwable]
      (let [current-context  (additional-context)
            callsite-context (utils/callsite-info)]
        (send-off logging-agent
                  (fn [_]
                    (let [formatted-msg (apply format message (utils/realize arguments))
                          base-data     {:throwable throwable :message formatted-msg :marker marker :level level}
                          data          (merge callsite-context current-context base-data)]
                      (write! data))))))))

(defn data-logger-factory []
  (let [state (atom {})]
    (reify ILoggerFactory
      (getLogger [this name]
        (get (swap! state update name #(or % (data-logger name))) name)))))

(defonce logger-factory (delay (data-logger-factory)))
(defonce marker-factory (delay (BasicMarkerFactory.)))

(defmacro capture [& body]
  `(let [prom#  (promise)
         lines# (->> (with-out-str
                       (deliver prom# (do ~@body))
                       (await logging-agent))
                     (strings/split-lines)
                     (remove strings/blank?)
                     (mapv #(jsonista/read-value %1 mapper)))]
     [lines# (deref prom#)]))

(defmacro log [level & args]
  (let [calling-ns (name (.getName *ns*))]
    `(let [level-arg#  ~level
           level#      (if (vector? level-arg#) (second level-arg#) level-arg#)
           logger#     (if (vector? level-arg#) (first level-arg#) ~calling-ns)
           callsite#   ~(assoc (:meta &form) :ns calling-ns)
           additional# (additional-context)]
       (send-off logging-agent (fn [_#] (write! (merge callsite# additional# {:level level# :logger logger#})))))))
