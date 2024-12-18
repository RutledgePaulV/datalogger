(ns datalogger.protos
  (:require [clojure.data.json :as json]
            [datalogger.impl.utils :as utils]
            [clojure.stacktrace :as stack])
  (:import (clojure.lang MapEntry Keyword Delay Atom ISeq Volatile Namespace Symbol)
           (java.util Map Set List)
           (java.time Instant)
           (java.util.function Supplier)))

(set! *warn-on-reflection* true)

(defprotocol LoggableData
  :extend-via-metadata true
  (as-data [x options]
    "Convert x into serializable (as json) data.
     Also handle any pruning of sensitive values (defined by options).
     Also produce a deterministic ordering of any unordered collections."))

(extend-protocol LoggableData
  Object
  (as-data [x {:keys [json-options]}]
    (try
      (apply json/write-str x (mapcat identity (or json-options {})))
      (catch Exception e
        (.getName (class x)))))
  StackTraceElement
  (as-data [x options]
    (as-data
      {:class    (.getClassName x)
       :method   (.getMethodName x)
       :filename (.getFileName x)
       :line     (.getLineNumber x)}
      options))
  Thread
  (as-data [x options]
    (.getName x))
  Instant
  (as-data [x options]
    (str x))
  Symbol
  (as-data [x options]
    (utils/stringify-key x))
  Namespace
  (as-data [x options]
    (as-data (.getName x) options))
  Throwable
  (as-data [x options]
    (as-data
      (if (:root-only options)
        (let [root ^Throwable (stack/root-cause x)]
          (cond-> {:class (.getName (class root))
                   :message (ex-message root)
                   :trace (vec (.getStackTrace root))}
            (ex-data root) (assoc :data (ex-data root))))
        (cond-> {:class (.getName (class x))
                 :message (ex-message x)
                 :trace (vec (.getStackTrace x))}
          (ex-data x) (assoc :data (ex-data x))
          (.getCause x) (assoc :cause (.getCause x))))
      options))
  String
  (as-data [x {:keys [mask-val? masker]}]
    (if (mask-val? x)
      (masker x)
      x))
  Keyword
  (as-data [x options]
    (utils/stringify-key x))
  Number
  (as-data [x options] x)
  Boolean
  (as-data [x options] x)
  Atom
  (as-data [x options]
    (as-data (deref x) options))
  Volatile
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
  (as-data [x {:keys [mask-key? masker elide?] :as options}]
    (cond
      (or (nil? (key x)) (elide? (key x)))
      nil
      (mask-key? (key x))
      [(as-data (key x) options) (masker (val x))]
      :otherwise
      [(as-data (key x) options) (as-data (val x) options)]))
  Map
  (as-data [x options]
    (let [entries (keep #(as-data % options) x)]
      (try
        (into (sorted-map) entries)
        (catch Exception e
          ; in case it includes things that aren't comparable
          (into {} entries)))))
  Set
  (as-data [x options]
    (let [entries (map #(as-data % options) x)]
      (try
        (into (sorted-set) entries)
        (catch Exception e
          ; in case it includes things that aren't comparable
          (into #{} entries)))))
  ISeq
  (as-data [x options]
    (mapv #(as-data % options) x))
  List
  (as-data [x options]
    (mapv #(as-data % options) x)))
