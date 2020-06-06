(ns datalogger.impl.config
  (:require [datalogger.impl.utils :as utils]
            [jsonista.core :as jsonista]))

(set! *warn-on-reflection* true)

(def DEFAULTS
  {:elide   #{}
   :levels  {"*" :warn "datalogger.core" :info}
   :masking {:mask "<redacted>" :keys #{} :values #{}}
   :mapper  {:encode-key-fn true :decode-key-fn true :pretty false}})

(defn resolve-symbol [symbol message]
  (if-some [v (some-> symbol requiring-resolve deref)]
    (cond (fn? v) (v)
          (delay? v) (force v)
          :otherwise (throw (ex-info message {})))))

(defn mapper-from-config [config]
  (cond
    (map? (:mapper config))
    (jsonista/object-mapper (:mapper config))
    (symbol? (:mapper config))
    (resolve-symbol (:mapper config) "Couldn't resolve object mapper.")))

(defn mask-from-config [{{mask :mask} :masking}]
  (if (string? mask)
    (constantly mask)
    (resolve-symbol mask "Couldn't resolve masking function.")))

(defn normalize-config [config]
  (let [conf       (utils/deep-merge DEFAULTS config)
        mapper     (mapper-from-config conf)
        log-filter (utils/compile-filter (get-in conf [:levels]))
        key-test   (utils/compile-key-pred (get-in conf [:masking :keys]))
        value-test (utils/compile-val-pred (get-in conf [:masking :values]))
        elide-test (utils/compile-key-pred (get-in conf [:elide]))
        mask       (mask-from-config conf)]
    (with-meta conf
      {:filter        log-filter
       :masker        mask
       :elide?        elide-test
       :mask-key?     key-test
       :mask-val?     value-test
       :object-mapper mapper})))

(defonce ^:dynamic *config* (atom (normalize-config {})))

(defn get-object-mapper
  ([] (get-object-mapper (deref *config*)))
  ([config] (some-> config meta :object-mapper)))

(defn get-log-filter
  ([] (get-log-filter (deref *config*)))
  ([config] (some-> config meta :filter)))

(defn serializable? [mapper x]
  (try
    (jsonista/write-value-as-string x mapper)
    true
    (catch Exception e false)))
