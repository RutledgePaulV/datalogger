(ns datalogger.impl.context
  (:require [clojure.data.json :as json]
            [datalogger.impl.utils :as utils]
            [datalogger.protos :as protos])
  (:import (java.net InetAddress)
           (java.time Instant)
           (org.slf4j.helpers BasicMDCAdapter)
           (clojure.lang PersistentHashMap Agent)
           (java.util Map)
           (java.io Writer)))

(set! *warn-on-reflection* true)

(def ^:dynamic *context* {})

(def levels
  {"ERROR" ["ERROR"]
   "WARN"  ["ERROR" "WARN"]
   "INFO"  ["ERROR" "WARN" "INFO"]
   "DEBUG" ["ERROR" "WARN" "INFO" "DEBUG"]
   "TRACE" ["ERROR" "WARN" "INFO" "DEBUG" "TRACE"]})

(defn get-context-for-level [level]
  (let [context *context*]
    (reduce
      (fn [agg level]
        (utils/deep-merge agg (get context level {})))
      {}
      (rseq (get levels level)))))

(def ^:private ^String system-newline (System/getProperty "line.separator"))

(defonce hostname (delay (.getHostName (InetAddress/getLocalHost))))
(defonce mdc-adapter (delay (BasicMDCAdapter.)))

(defn get-mdc []
  (if-some [context ^Map (.getCopyOfContextMap ^BasicMDCAdapter (force mdc-adapter))]
    (PersistentHashMap/create context)
    {}))

(defn execution-context []
  {"@timestamp" (Instant/now)
   "@hostname"  (force hostname)
   "@thread"    (Thread/currentThread)})

(defonce logging-agent ^Agent (agent nil :error-mode :continue))

(defn write! [conf ^Writer out m]
  (let [root-only (get-in conf [:exceptions :root-only])
        clean     (protos/as-data m (assoc (meta conf) :root-only root-only))]
    (.write out ^String (str (apply json/write-str clean (mapcat identity (get conf :json-options {}))) system-newline))
    (.flush out)))