(ns datalogger.impl.utils
  (:require [clojure.string :as strings]
            [clojure.stacktrace :as stack]
            [clojure.set :as sets]
            [clojure.walk :as walk])
  (:import (org.slf4j.event Level)
           (java.io Writer StringWriter)
           (java.util.regex Pattern)))

(set! *warn-on-reflection* true)

(defn teed-writer [^Writer source ^Writer fork]
  (proxy [Writer] []
    (write
      ([c]
       (cond
         (int? c)
         (do (.write source (int c))
             (.write fork (int c)))
         (string? c)
         (do (.write source ^String c)
             (.write fork ^String c))
         :otherwise
         (do (.write source ^chars c)
             (.write fork ^chars c))))
      ([c offset length]
       (cond
         (string? c)
         (do (.write source ^String c (int offset) (int length))
             (.write fork ^String c (int offset) (int length)))
         :otherwise
         (do (.write source ^chars c (int offset) (int length))
             (.write fork ^chars c (int offset) (int length))))))
    (flush []
      (.flush source)
      (.flush fork))
    (close []
      (.close source)
      (.close fork))))

(defmacro with-teed-out-str [& body]
  `(let [original# *out*
         s#        (StringWriter.)]
     (binding [*out* (teed-writer original# s#)]
       ~@body
       (str s#))))

(defn deep-merge [& maps]
  (letfn [(combine [x y]
            (if (and (map? x) (map? y))
              (deep-merge x y)
              y))]
    (apply merge-with combine maps)))

(defn stringify-key [k]
  (cond
    (qualified-ident? k)
    (str (namespace k) "/" (name k))
    (ident? k)
    (name k)
    :otherwise
    k))

(defn logger->hierarchy [logger]
  (if (= "*" logger)
    ["*"]
    (let [re #"\.[^*.]+$"]
      (loop [iter logger pieces [logger]]
        (if (re-find re iter)
          (recur (strings/replace iter re "")
                 (conj pieces (strings/replace iter re ".*")))
          (conj pieces "*"))))))

(defn level->int [level]
  (.toInt (Level/valueOf (strings/upper-case (name level)))))

(defn compile-filter [levels]
  (memoize
    (fn [logger level]
      (if-some [match (some levels (logger->hierarchy logger))]
        (<= (level->int match) (level->int level))
        false))))

(defn split-pretty-printed [s]
  (->> (strings/split s #"(?m)^\}\s*$")
       (remove strings/blank?)
       (map #(str % "}"))))

(defn ->pattern-string [x]
  (cond
    (instance? Pattern x)
    (str x)
    (string? x)
    x
    (keyword? x)
    (name x)))

(defn compile-val-pred [values]
  (if (empty? values)
    (constantly false)
    (let [joined (Pattern/compile
                   (str "(" (strings/join ")|(" (map ->pattern-string values)) ")")
                   Pattern/CASE_INSENSITIVE)]
      (fn [x] (some? (re-find joined x))))))

(defn stringify-ident [ident]
  (if (qualified-ident? ident)
    (str (namespace ident) "/" (name ident))
    (name ident)))

(defn compile-key-pred [keys]
  (let [expanded
        (sets/union
          (set (map stringify-ident keys))
          (set (map keyword keys)))]
    (fn [x] (contains? expanded x))))

(defn categorize-arguments [args]
  (reduce
    (fn [agg arg]
      (cond
        (instance? Throwable arg)
        (assoc agg :exception arg)
        (map? arg)
        (assoc agg :data arg)
        (string? arg)
        (assoc agg :message arg)
        (keyword? arg)
        (assoc agg :level (strings/upper-case (name arg)))
        (vector? arg)
        (cond-> agg
          (some? (first arg))
          (assoc :logger (name (first arg)))
          (some? (second arg))
          (assoc :level (strings/upper-case (name (second arg)))))
        :otherwise
        agg))
    {:data {}}
    args))

(defn iterable?
  "Is collection like or a single value?"
  [x]
  (and (not (string? x))
       (not (map? x))
       (seqable? x)))

(defn serialize-exception [e]
  (with-out-str (stack/print-stack-trace (stack/root-cause e))))

(defmacro quietly
  "Execute the body and return nil if there was an error"
  [& body] `(try ~@body (catch Throwable _# nil)))

(defmacro attempt
  "Returns result of first form that doesn't throw and doesn't return nil."
  ([] nil)
  ([x] `(quietly ~x))
  ([x & next] `(if-some [y# (attempt ~x)] y# (attempt ~@next))))

(defn parse-number [s]
  (attempt (Long/parseLong s) (Double/parseDouble s)))

(defn parse-boolean [s]
  ({"true" true "false" false} s))

(defn parse-best-guess [s]
  (attempt (parse-number s) (parse-boolean s) s))

(defn get*
  "Like clojure.core/get except treats strings and keywords
   as interchangeable and not-found as the result if there is
   no entry *or* if the entry value is nil."
  ([m k] (get* m k nil))
  ([m k not-found]
   (if-some [result
             (some->
               (or
                 (find m k)
                 (cond
                   (keyword? k)
                   (find m (name k))
                   (string? k)
                   (find m (keyword k))))
               (val))]
     result not-found)))

(defn get-in*
  "Like clojure.core/get-in except built atop get*."
  ([m ks]
   (reduce get* m ks))
  ([m ks not-found]
   (loop [sentinel (Object.)
          m        m
          ks       (seq ks)]
     (if ks
       (let [m (get* m (first ks) sentinel)]
         (if (identical? sentinel m)
           not-found
           (recur sentinel m (next ks))))
       m))))

(defn unqualify [m]
  (reduce-kv
    (fn [m k v]
      (if (and (qualified-keyword? k)
               (not (contains? m (keyword (name k)))))
        (assoc m (keyword (name k)) v)
        m)) m m))

(defn walk-unqualify [form]
  (walk/postwalk
    (fn [form]
      (if (map? form)
        (unqualify form)
        form))
    form))

(defn template
  "A simple string templating function that replaces {x.y.z} placeholders with values from the context."
  [text context]
  (let [expanded (delay (walk-unqualify context))]
    (letfn [(replacer [[_ group]]
              (let [val (or (get* (force expanded) group)
                            (->> (strings/split group #"\.")
                                 (map parse-best-guess)
                                 (get-in* (force expanded))))]
                (if (iterable? val) (strings/join ", " (sort val)) (str val))))]
      (strings/replace text #"\{([^\{\}]+)\}" replacer))))

