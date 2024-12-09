(ns datalogger.impl.loggers
  (:require [clojure.string :as strings]
            [datalogger.impl.config :as config]
            [clojure.walk :as walk]
            [datalogger.impl.context :as context]
            [datalogger.impl.utils :as utils])
  (:import (org.slf4j ILoggerFactory Marker)
           (org.slf4j.event KeyValuePair LoggingEvent)
           (org.slf4j.helpers LegacyAbstractLogger MessageFormatter)
           (org.slf4j.spi LoggingEventAware)
           (java.util.function Supplier)
           (clojure.lang Agent ISeq IFn)
           (java.time Instant)
           (java.util.concurrent Executor)))

(set! *warn-on-reflection* true)

(defn callsite-info []
  {})

(defn touch [arg]
  (cond (delay? arg) (force arg) (instance? Supplier arg) (.get ^Supplier arg) :else arg))

(defn realize [form]
  (walk/postwalk touch form))

(defn maybe-format [msg args]
  (if (not-empty args)
    (MessageFormatter/basicArrayFormat msg (object-array (realize args)))
    msg))

(defn data-logger [logger-name]
  (proxy [LegacyAbstractLogger LoggingEventAware] []
    (getName []
      logger-name)
    ;; org.slf4j.helpers.LegacyAbstractLogger
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
    ;; org.slf4j.helpers.AbstractLogger
    (getFullyQualifiedCallerName [] nil)
    (handleNormalizedLoggingCall [level marker message arguments throwable]
      (let [current-context  (context/get-context-for-level (str level))
            current-mdc      (context/get-mdc)
            current-extra    (context/execution-context)
            callsite-context (callsite-info)
            extras           (cond->
                               {:level     (str level)
                                :logger    logger-name}
                               throwable (assoc :exception throwable))
            config           (deref config/*config*)
            out              (config/get-log-stream (:stream config))]
        (.dispatch ^Agent context/logging-agent
                   ^IFn
                   (fn [_]
                     (context/write! config out
                       (utils/deep-merge current-extra
                                         current-mdc
                                         current-context
                                         callsite-context
                                         extras
                                         {:message (maybe-format message arguments)})))
                   ^ISeq
                   (seq [])
                   ^Executor
                   Agent/soloExecutor)))
    ;; org.slf4j.spi.LoggingEventAware
    (log [^LoggingEvent event]
      (let [level            (str (.getLevel event))
            current-context  (context/get-context-for-level level)
            current-mdc      (context/get-mdc)
            current-extra    (merge (context/execution-context)
                                    (when-let [thread (.getThreadName event)]
                                      {"@thread" thread})
                                    (let [timestamp (.getTimeStamp event)]
                                      ;; `LoggingEvent`'s timestamp is a primitive, so it cannot be null. Check for its
                                      ;; default value. This does preclude the possibility of logging events that happen
                                      ;; at the moment of the epoch. Given that it has been a few years since the epoch,
                                      ;; that is hopefully not necessary.
                                      (when-not (zero? timestamp)
                                        ;; <https://www.slf4j.org/apidocs/org/slf4j/event/LoggingEvent.html#getTimeStamp()>
                                        ;; does not describe the format of this timestamp, but usage
                                        ;; (e.g. <https://github.com/qos-ch/slf4j/blob/v_2.0.16/slf4j-jdk14/src/main/java/org/slf4j/jul/JDK14LoggerAdapter.java#L290>)
                                        ;; implies that is epoch milliseconds.
                                        {"@timestamp" (Instant/ofEpochMilli timestamp)})))
            callsite-context (callsite-info)
            extras           (merge {:level  level
                                     :logger (.getLoggerName event)}
                                    (when-let [throwable (.getThrowable event)]
                                      {:exception throwable}))
            config           (deref config/*config*)
            out              (config/get-log-stream (:stream config))]
        ;; NOTE `event`'s markers are ignored. According to <https://www.slf4j.org/apidocs/org/slf4j/Marker.html>, that
        ;; is acceptable: "Many conforming logging systems ignore marker data entirely."
        (.dispatch ^Agent context/logging-agent
                   ^IFn
                   (fn [_]
                     (context/write! config out
                       (utils/deep-merge current-extra
                                         current-mdc
                                         current-context
                                         callsite-context
                                         extras
                                         (into {}
                                               (map (fn [^KeyValuePair kvp] [(.-key kvp) (.-value kvp)]))
                                               (.getKeyValuePairs event))
                                         {:message (-> event
                                                       .getMessage
                                                       (maybe-format (.getArguments event)))})))
                   ^ISeq
                   (seq [])
                   ^Executor
                   Agent/soloExecutor)))))

(defn data-logger-factory []
  (let [state (atom {})]
    (reify ILoggerFactory
      (getLogger [this name]
        (get (swap! state update name #(or % (data-logger name))) name)))))
