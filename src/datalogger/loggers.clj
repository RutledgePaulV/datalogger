(ns datalogger.loggers
  (:require [datalogger.config :as config]
            [clojure.walk :as walk]
            [clojure.string :as strings]
            [datalogger.context :as context]
            [datalogger.utils :as utils])
  (:import (org.slf4j ILoggerFactory Marker)
           (org.slf4j.helpers LegacyAbstractLogger)
           (java.util.function Supplier)))


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

(defn touch [arg]
  (cond (delay? arg) (force arg) (instance? Supplier arg) (.get arg) :else arg))

(defn realize [form]
  (walk/postwalk touch form))

(defn data-logger [logger-name]
  (proxy [LegacyAbstractLogger] []
    (getName []
      logger-name)
    (isTraceEnabled
      ([] ((config/get-log-filter) logger-name :trace))
      ([^Marker marker] ((config/get-log-filter) logger-name :trace)))
    (isDebugEnabled
      ([] ((config/get-log-filter) logger-name :debug))
      ([^Marker marker] ((config/get-log-filter) logger-name :debug)))
    (isInfoEnabled
      ([] ((config/get-log-filter) logger-name :info))
      ([^Marker marker] ((config/get-log-filter) logger-name :info)))
    (isWarnEnabled
      ([] ((config/get-log-filter) logger-name :warn))
      ([^Marker marker] ((config/get-log-filter) logger-name :warn)))
    (isErrorEnabled
      ([] ((config/get-log-filter) logger-name :error))
      ([^Marker marker] ((config/get-log-filter) logger-name :error)))
    (getFullyQualifiedCallerName [] nil)
    (handleNormalizedLoggingCall [level marker message arguments throwable]
      (let [current-context  (context/capture-context)
            callsite-context (callsite-info)
            extras           {:exception throwable
                              :level (str level)
                              :logger logger-name}
            callback         (fn [_]
                               (let [msg (apply format message (realize arguments))]
                                 (context/write! (utils/deep-merge current-context callsite-context extras {:message msg}))))]
        (send-off context/logging-agent callback)))))

(defn data-logger-factory []
  (let [state (atom {})]
    (reify ILoggerFactory
      (getLogger [this name]
        (get (swap! state update name #(or % (data-logger name))) name)))))