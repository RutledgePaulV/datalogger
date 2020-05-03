(ns datalogger.protos
  (:import (clojure.lang MapEntry Keyword)
           (java.util Map Set List)))


(defn stringify-key [k]
  (cond
    (qualified-keyword? k)
    (str (namespace k) "/" (name k))
    (keyword? k)
    (name k)
    :otherwise
    (str k)))

(defn get-mask-for-key [k options]
  )

(defn apply-string-mask [s options]
  s)

(defn masked-key? [k options]
  )

(defprotocol LoggableData
  (as-data [x options]))

(extend-protocol LoggableData
  Object
  (as-data [x options]
    (.getName (class x)))
  String
  (as-data [x options]
    (apply-string-mask x options))
  Keyword
  (as-data [x options]
    (apply-string-mask (stringify-key x) options))
  Number
  (as-data [x options] x)
  Boolean
  (as-data [x options] x)
  nil
  (as-data [x options] x)
  MapEntry
  (as-data [x options]
    (cond
      (or (nil? (first x)) (nil? (second x)))
      nil
      (masked-key? x options)
      [(as-data (key x) options) (as-data (get-mask-for-key x options) options)]
      :otherwise
      [(as-data (key x) options) (as-data (val x) options)]))
  Map
  (as-data [x options]
    (into (sorted-map) (keep #(as-data % options) x)))
  Set
  (as-data [x options]
    (into (sorted-set) (map #(as-data % options) x)))
  List
  (as-data [x options]
    (mapv #(as-data % options) x)))