(ns datalogger.core-test
  (:require [clojure.test :refer :all]
            [datalogger.core :refer :all]))

(def slf4j-logger (org.slf4j.LoggerFactory/getLogger "myLogger"))
(def jul-logger (java.util.logging.Logger/getLogger "myLogger"))
(def log4j-logger (org.apache.log4j.Logger/getLogger "myLogger"))


(deftest clojure-logging
  (let [[logs] (capture (log :error "Demonstration"))]
    (is (not-empty logs))
    (is (= "ERROR" (get-in logs [0 :level])))
    (is (= "Demonstration" (get-in logs [0 :message])))))

(deftest slf4j-logging
  (let [[logs] (capture (.error slf4j-logger "Demonstration"))]
    (is (not-empty logs))
    (is (= "ERROR" (get-in logs [0 :level])))
    (is (= "Demonstration" (get-in logs [0 :message])))))

(deftest jul-logging
  (let [[logs] (capture (.severe jul-logger "Demonstration"))]
    (is (not-empty logs))
    (is (= "ERROR" (get-in logs [0 :level])))
    (is (= "Demonstration" (get-in logs [0 :message])))))

(deftest log4j-logging
  (let [[logs] (capture (.error log4j-logger "Demonstration"))]
    (is (not-empty logs))
    (is (= "ERROR" (get-in logs [0 :level])))
    (is (= "Demonstration" (get-in logs [0 :message])))))