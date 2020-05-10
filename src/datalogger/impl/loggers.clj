(ns datalogger.impl.loggers
  (:require [datalogger.impl.config :as config]
            [clojure.walk :as walk]
            [clojure.string :as strings]
            [datalogger.impl.context :as context]
            [datalogger.impl.utils :as utils])
  (:import (org.slf4j ILoggerFactory Marker)
           (org.slf4j.helpers LegacyAbstractLogger MessageFormatter)
           (java.util.function Supplier)
           (clojure.lang Agent ISeq IFn)
           (java.util.concurrent Executor)))

(set! *warn-on-reflection* true)

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
  (cond (delay? arg) (force arg) (instance? Supplier arg) (.get ^Supplier arg) :else arg))

(defn realize [form]
  (walk/postwalk touch form))

(defn maybe-format [msg args]
  (if (not-empty args)
    (MessageFormatter/basicArrayFormat msg (object-array (realize args)))
    msg))

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
      (let [current-context  context/*context*
            current-mdc      (context/get-mdc)
            current-extra    (context/execution-context)
            callsite-context (callsite-info)
            extras           {:exception throwable
                              :level     (str level)
                              :logger    logger-name}
            out              *out*]
        (.dispatch ^Agent context/logging-agent
                   ^IFn
                   (fn [_]
                     (context/write! out
                       (utils/deep-merge current-extra
                                         current-mdc
                                         current-context
                                         callsite-context
                                         extras
                                         {:message (maybe-format message arguments)})))
                   ^ISeq
                   (seq [])
                   ^Executor
                   Agent/soloExecutor)))))

(defn data-logger-factory []
  (let [state (atom {})]
    (reify ILoggerFactory
      (getLogger [this name]
        (get (swap! state update name #(or % (data-logger name))) name)))))