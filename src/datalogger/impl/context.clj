(ns datalogger.impl.context
  (:require [jsonista.core :as jsonista]
            [datalogger.protos :as protos]
            [datalogger.impl.config :as config])
  (:import (java.net InetAddress)
           (java.time Instant)
           (org.slf4j.helpers BasicMDCAdapter)
           (clojure.lang PersistentHashMap Agent)
           (java.util Map)
           (java.io Writer)))

(set! *warn-on-reflection* true)

(def ^:dynamic *context* {})

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

(defn write! [^Writer out m]
  (let [conf  (deref config/CONFIG)
        clean (protos/as-data m (:options conf))]
    (.write out (jsonista/write-value-as-string clean (:object-mapper conf)))
    (.append out system-newline)
    (.flush out)))