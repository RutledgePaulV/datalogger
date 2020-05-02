(ns datalogger.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [jsonista.core :as jsonista]
            [clojure.string :as strings])
  (:import (org.slf4j ILoggerFactory)
           (org.slf4j.helpers BasicMarkerFactory BasicMDCAdapter AbstractLogger LegacyAbstractLogger)
           (java.util.function Supplier)))

(def DEFAULTS
  {})

(def CONFIG
  (delay (if-some [overrides (io/resource "datalogger.edn")]
           (merge DEFAULTS (edn/read-string (slurp overrides)))
           DEFAULTS)))

(def thread (agent nil
                   :error-mode :continue
                   :error-handler (fn [agent ex] (.printStackTrace ex))))

(def mapper
  (jsonista/object-mapper
    {:encode-key-fn true :decode-key-fn true}))


(defn mask-sensitive-data [x]
  x)

(defn ensure-serializable [x]
  x)

(defn log-level-enabled? [logger level]
  true)

(defn write! [m]
  (let [clean (into (sorted-map) (-> m ensure-serializable mask-sensitive-data))]
    (jsonista/write-value *out* clean mapper)
    (newline)))

(defn frame->data [^StackTraceElement frame]
  {:class  (.getClassName frame)
   :method (.getMethodName frame)
   :line   (.getLineNumber frame)})

(defn not-log-frame? [^StackTraceElement frame]
  (not= "datalogger.core.DataLogger" (.getClassName frame)))

(defn not-reflection-frame? [^StackTraceElement frame]
  (let [clazz (.getClassName frame)]
    (or (strings/starts-with? clazz "jdk.internal.")
        (strings/starts-with? clazz "java.lang.")
        (strings/starts-with? clazz "clojure.lang.Reflector"))))

(defn get-calling-frame []
  (->> (Thread/currentThread)
       (.getStackTrace)
       (drop-while (some-fn not-log-frame? not-reflection-frame?))
       (second)))

(defn callsite-info []
  (some-> (get-calling-frame) (frame->data)))

(defn realize [arg]
  (cond (delay? arg) (force arg) (instance? Supplier arg) (.get arg) :else arg))

(defn data-logger [logger-name]
  (proxy [LegacyAbstractLogger] []
    (getName [this]
      logger-name)
    (isTraceEnabled []
      (log-level-enabled? this :trace))
    (isDebugEnabled []
      (log-level-enabled? this :debug))
    (isInfoEnabled []
      (log-level-enabled? this :info))
    (isWarnEnabled []
      (log-level-enabled? this :warn))
    (isErrorEnabled []
      (log-level-enabled? this :error))
    (getFullyQualifiedCallerName [] nil)
    (handleNormalizedLoggingCall [level marker message arguments throwable]
      (send-off thread
                (fn [_]
                  (let [formatted-msg   (apply format message (map realize arguments))
                        base-data       {:throwable throwable :message formatted-msg :marker marker :level level}
                        logger-callsite (callsite-info)
                        data            (merge logger-callsite base-data)]
                    (write! data)))))))

(defn data-logger-factory []
  (let [state (atom {})]
    (reify ILoggerFactory
      (getLogger [this name]
        (get (swap! state update name #(or % (data-logger name))) name)))))

(defonce logger-factory (delay (data-logger-factory)))
(defonce marker-factory (delay (BasicMarkerFactory.)))
(defonce mdc-adapter (delay (BasicMDCAdapter.)))

(defmacro capture [& body]
  `(let [prom#  (promise)
         lines# (->> (with-out-str
                       (deliver prom# (do ~@body))
                       (await thread))
                     (strings/split-lines)
                     (mapv #(jsonista/read-value %1 mapper)))]
     [lines# (deref prom#)]))

(defmacro log [level & args]
  (let [calling-ns (name (.getName *ns*))]
    `(let [level-arg# ~level
           level#     (if (vector? level-arg#) (second level-arg#) level-arg#)
           logger#    (if (vector? level-arg#) (first level-arg#) ~calling-ns)
           callsite#  ~(assoc (:meta &form) :ns calling-ns)]
       (send-off thread (fn [_#] (write! (merge callsite# {:level level# :logger logger#})))))))


(comment
  (do (def logger (LoggerFactory/getLogger "myLogger"))))