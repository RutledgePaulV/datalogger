(ns datalogger.protos
  (:import (clojure.lang MapEntry Keyword Delay Atom ISeq)
           (java.util Map Set List)
           (java.time Instant)
           (java.util.function Supplier)))


(defn stringify-key [k]
  (if (qualified-keyword? k)
    (str (namespace k) "/" (name k))
    (name k)))

(defn get-mask-for-key [k options]
  )

(defn apply-string-mask [s options]
  s)

(defn masked-key? [k options]
  )

(defprotocol LoggableData
  :extend-via-metadata true
  (as-data [x options]))

(extend-protocol LoggableData
  Thread
  (as-data [x options]
    (.getName x))
  Instant
  (as-data [x options]
    (str x))
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
  Atom
  (as-data [x options]
    (as-data (deref x) options))
  Delay
  (as-data [x options]
    (as-data (force x) options))
  Supplier
  (as-data [x options]
    (as-data (.get x) options))
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
  ISeq
  (as-data [x options]
    (mapv #(as-data % options) x))
  List
  (as-data [x options]
    (mapv #(as-data % options) x)))