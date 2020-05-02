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

(defn output [data]
  (let [clean (into (sorted-map) (-> data ensure-serializable mask-sensitive-data))]
    (jsonista/write-value *out* clean mapper)))


(defn log-level-enabled? [logger level]
  )

(defn dispatch-log [level msg arguments]
  )

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
  (^void warn [this ^String message]
    (send thread #(println message))))

(deftype DataLoggerFactory [^Atom loggers] :load-ns true
  ILoggerFactory
  (getLogger [this name]
    (get (swap! loggers update name #(or % (DataLogger. name))) name)))

(def logger-factory (delay (DataLoggerFactory. (atom {}))))
(def marker-factory (delay (BasicMarkerFactory.)))
(def mdc-adapter (delay (BasicMDCAdapter.)))



(defn demo [msg]
  (let [logger (LoggerFactory/getLogger "demo")]
    (.warn logger "demo")))

(defmacro log [level data]
  (let [line (-> &form meta :line)]
    `(let [logger# (str *ns*)
           extras# {:level ~level :logger logger# :line ~line}]
       )))