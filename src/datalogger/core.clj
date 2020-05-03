(ns datalogger.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [jsonista.core :as jsonista]
            [clojure.string :as strings]
            [datalogger.utils :as utils]
            [datalogger.protos :as protos])
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


(defn expand-data [m]
  (cond-> m
    (some? (:throwable m))
    (-> (assoc :stack_trace (utils/serialize-exception (:throwable m)))
        (assoc :ex-data (or (ex-data (:throwable m)) {}))
        (assoc :message (ex-message (:throwable m))))
    (contains? m :data)
    (merge (:data m))
    (string? (:message m))
    (assoc :message (:message m))
    :always
    (dissoc :throwable :data)))

(defn write! [m]
  (let [clean (-> (expand-data m)
                  include-mdc
                  include-context
                  (protos/as-data (:options @CONFIG)))]
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
    `(let [callsite#      ~(assoc (:meta &form) :ns calling-ns)
           additional#    (additional-context)
           args-by-class# (utils/categorize-arguments args)]
       (send-off logging-agent (fn [_#] (write! (merge callsite# additional# args-by-class#)))))))


