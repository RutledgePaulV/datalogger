(ns datalogger.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [jsonista.core :as jsonista]
            [clojure.string :as strings]
            [clojure.walk :as walk])
  (:import (org.slf4j ILoggerFactory)
           (org.slf4j.helpers BasicMarkerFactory BasicMDCAdapter LegacyAbstractLogger)
           (java.util.function Supplier)
           (org.slf4j.event Level)
           (com.fasterxml.jackson.databind MapperFeature)))

(defn deep-merge [& maps]
  (letfn [(combine [x y]
            (if (and (map? x) (map? y))
              (deep-merge x y)
              y))]
    (apply merge-with combine maps)))

(defn filter-vals [pred form]
  (walk/postwalk
    (fn [form]
      (if (map? form)
        (into {} (filter (comp pred val) form))
        form))
    form))

(defn logger->hierarchy [logger]
  (if (= "*" logger)
    ["*"]
    (let [re #"\.[^*.]+$"]
      (loop [iter logger pieces [logger]]
        (if (re-find re iter)
          (recur (strings/replace iter re "")
                 (conj pieces (strings/replace iter re ".*")))
          (conj pieces "*"))))))

(defn level->int [level]
  (.toInt (Level/valueOf (strings/upper-case (name level)))))

(defn compile-filter [levels]
  (memoize
    (fn [logger level]
      (if-some [match (some levels (logger->hierarchy logger))]
        (<= (level->int match) (level->int level))
        false))))

(def DEFAULTS
  {:levels {"*" :warn}
   :masks  {:keys {} :values {}}})

(defn get-configuration []
  (let [config (if-some [overrides (io/resource "datalogger.edn")]
                 (deep-merge DEFAULTS (edn/read-string (slurp overrides)))
                 DEFAULTS)
        filter (compile-filter (:levels config))]
    {:config config :filter filter}))


(def CONFIG
  (delay (get-configuration)))

(def thread (agent nil
                   :error-mode :continue
                   :error-handler (fn [agent ex] (.printStackTrace ex))))

(def mapper
  (let [options {:encode-key-fn true :decode-key-fn true}]
    (doto (jsonista/object-mapper options)
      (.configure MapperFeature/SORT_PROPERTIES_ALPHABETICALLY true))))

(defn mask-sensitive-data [x]
  x)

(defn ensure-serializable [x]
  (filter-vals some? x))

(defn log-level-enabled? [logger level]
  (let [filter (:filter @CONFIG)]
    (filter logger level)))

(defn write! [m]
  (let [clean (-> m ensure-serializable mask-sensitive-data)]
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