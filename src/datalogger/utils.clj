(ns datalogger.utils
  (:require [clojure.string :as strings]
            [clojure.stacktrace :as stack]
            [clojure.walk :as walk])
  (:import (org.slf4j.event Level)
           (clojure.lang MapEntry)))

(set! *warn-on-reflection* true)

(defn deep-merge [& maps]
  (letfn [(combine [x y]
            (if (and (map? x) (map? y))
              (deep-merge x y)
              y))]
    (apply merge-with combine maps)))

(defn prefix-key [prefix k]
  (cond
    (qualified-keyword? k)
    k
    (keyword? k)
    (keyword (name prefix) (name k))
    (string? k)
    (str (name prefix) (name k))
    :otherwise
    k))

(defn prefix-keys [prefix m]
  (walk/postwalk
    (fn [form]
      (if (map-entry? form)
        (MapEntry. (prefix-key prefix (key form)) (val form))
        form))
    m))

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
   no found value *or* if the found value is nil."
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

(defn template
  "A simple string templating function that replaces {x.y.z} placeholders with values from the context."
  [text context]
  (letfn [(replacer [[_ group]]
            (let [val (or (get* context group)
                          (->> (strings/split group #"\.")
                               (map parse-best-guess)
                               (get-in* context)))]
              (if (iterable? val) (strings/join "," (sort val)) (str val))))]
    (strings/replace text #"\{([^\{\}]+)\}" replacer)))

