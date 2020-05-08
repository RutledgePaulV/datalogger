(ns datalogger.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [datalogger.utils :as utils]
            [jsonista.core :as jsonista]))

(set! *warn-on-reflection* true)

(def DEFAULTS
  {:levels {"*" :warn}
   :mapper {:encode-key-fn true :decode-key-fn true :pretty false}})

(defn mapper-from-config [config]
  (cond
    (map? (:mapper config))
    (jsonista/object-mapper (:mapper config))
    (symbol? (:mapper config))
    (if-some [v (some-> (requiring-resolve (:mapper config)) deref)]
      (cond (fn? v) (v) (delay? v) (force v) :otherwise (throw (ex-info "Couldn't resolve object mapper." {}))))))

(defn normalize-config [config]
  (let [conf       (utils/deep-merge DEFAULTS config)
        mapper     (mapper-from-config conf)
        log-filter (utils/compile-filter (:levels conf))]
    (assoc conf :filter log-filter :object-mapper mapper)))

(def CONFIG (atom (normalize-config {})))

(defn set-configuration! [config]
  (reset! CONFIG (normalize-config config)))

(defn load-configuration-file! [resource-name]
  (when-some [resource (io/resource resource-name)]
    (set-configuration! (edn/read-string (slurp resource)))))

(defn get-object-mapper []
  (:object-mapper (deref CONFIG)))

(defn get-log-filter []
  (:filter (deref CONFIG)))