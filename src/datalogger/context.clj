(ns datalogger.context
  (:require [datalogger.utils :as utils]
            [jsonista.core :as jsonista]
            [datalogger.protos :as protos]
            [datalogger.config :as config])
  (:import (java.net InetAddress)
           (java.time Instant)
           (org.slf4j.helpers BasicMDCAdapter)
           (clojure.lang PersistentHashMap)
           (java.util Map)))


(def ^:dynamic *context* {})

(defonce hostname (delay (.getHostName (InetAddress/getLocalHost))))
(defonce mdc-adapter (delay (BasicMDCAdapter.)))

(defn get-mdc []
  (if-some [context ^Map (.getCopyOfContextMap (force mdc-adapter))]
    (PersistentHashMap/create context)
    {}))

(defn execution-context []
  {"@timestamp" (Instant/now)
   "@hostname"  (force hostname)
   "@thread"    (Thread/currentThread)})

(defn capture-context []
  (let [data {"@timestamp" (Instant/now) "@hostname" (force hostname) "@thread" (Thread/currentThread)}]
    (utils/deep-merge (get-mdc) *context* data)))

(def logging-agent (agent nil :error-mode :continue))


(defn write! [m]
  (let [conf  (deref config/CONFIG)
        clean (protos/as-data m (:options conf))]
    (jsonista/write-value *out* clean (:object-mapper conf))
    (newline)))