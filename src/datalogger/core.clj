(ns datalogger.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [jsonista.core :as jsonista])
  (:import (org.slf4j ILoggerFactory Logger LoggerFactory)
           (clojure.lang Atom)
           (org.slf4j.helpers BasicMarkerFactory BasicMDCAdapter)))

(def DEFAULTS
  {})

(def CONFIG
  (delay (if-some [overrides (io/resource "datalogger.edn")]
           (merge DEFAULTS (edn/read-string (slurp overrides)))
           DEFAULTS)))

(def thread (agent nil :error-mode :continue))

(def mapper
  (jsonista/object-mapper
    {:encode-key-fn true :decode-key-fn true}))


(defn mask-sensitive-data [x]
  x)

(defn ensure-serializable [x]
  x)

(defn log-level-enabled? [logger level]
  )

(defn serialize [m]
  (let [clean (into (sorted-map) (-> m ensure-serializable mask-sensitive-data))]
    (jsonista/write-value *out* clean mapper)))

(defn write! [m]
  (send-off thread (fn [_] (println (serialize m)))))

(deftype DataLogger [logger-name] :load-ns true
  Logger
  (isTraceEnabled [this]
    (log-level-enabled? this :trace))
  (isDebugEnabled [this]
    (log-level-enabled? this :debug))
  (isInfoEnabled [this]
    (log-level-enabled? this :info))
  (isWarnEnabled [this]
    (log-level-enabled? this :warn))
  (isErrorEnabled [this]
    (log-level-enabled? this :error))
  (^void trace [this ^String message]
    (write! {:logger logger-name :level :trace :message message}))
  (^void debug [this ^String message]
    (write! {:logger logger-name :level :debug :message message}))
  (^void info [this ^String message]
    (write! {:logger logger-name :level :info :message message}))
  (^void warn [this ^String message]
    (write! {:logger logger-name :level :warn :message message}))
  (^void error [this ^String message]
    (write! {:logger logger-name :level :error :message message})))

(deftype DataLoggerFactory [^Atom loggers] :load-ns true
  ILoggerFactory
  (getLogger [this name]
    (letfn [(swapper [old]
              (if (and old (= (hash (class old)) (hash DataLogger)))
                old
                (DataLogger. name)))]
      (get (swap! loggers update name swapper) name))))

(defonce logger-factory (delay (DataLoggerFactory. (atom {}))))
(defonce marker-factory (delay (BasicMarkerFactory.)))
(defonce mdc-adapter (delay (BasicMDCAdapter.)))


(defmacro log [level data]
  (let [line (-> &form meta :line)]
    `(let [logger# (str *ns*)
           extras# {:level ~level :logger logger# :line ~line}]
       )))