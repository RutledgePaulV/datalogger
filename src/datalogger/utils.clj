(ns datalogger.utils
  (:require [clojure.string :as strings]
            [clojure.walk :as walk])
  (:import (org.slf4j.event Level)
           (java.util.function Supplier)))

(defn deep-merge [& maps]
  (letfn [(combine [x y]
            (if (and (map? x) (map? y))
              (deep-merge x y)
              y))]
    (apply merge-with combine maps)))

(defn filter-vals [pred form]
  (walk/postwalk
    (fn [form]
      (if (map? form)
        (into {} (filter (comp pred val) form))
        form))
    form))

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

(defn frame->data [^StackTraceElement frame]
  {:class  (.getClassName frame)
   :method (.getMethodName frame)
   :line   (.getLineNumber frame)})

(defn not-log-frame? [^StackTraceElement frame]
  (not= "datalogger.core.DataLogger" (.getClassName frame)))

(defn not-reflection-frame? [^StackTraceElement frame]
  (let [clazz (.getClassName frame)]
    (or (strings/starts-with? clazz "jdk.internal.")
        (strings/starts-with? clazz "java.lang.")
        (strings/starts-with? clazz "clojure.lang.Reflector"))))

(defn get-calling-frame []
  (->> (Thread/currentThread)
       (.getStackTrace)
       (drop-while (some-fn not-log-frame? not-reflection-frame?))
       (second)))

(defn callsite-info []
  (some-> (get-calling-frame) (frame->data)))

(defn touch [arg]
  (cond (delay? arg) (force arg) (instance? Supplier arg) (.get arg) :else arg))

(defn realize [form]
  (walk/postwalk touch form))