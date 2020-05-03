(ns datalogger.core-test
  (:require [clojure.test :refer :all]
            [datalogger.core :refer :all]))

(deftest clojure-logging
  (let [[logs] (capture (log :error "Demonstration"))]
    (is (not-empty logs))
    (is (= "ERROR" (get-in logs [0 :level])))
    (is (= "Demonstration" (get-in logs [0 :message])))))

(deftest slf4j-logging
  (let [logger (org.slf4j.LoggerFactory/getLogger "myLogger")
        [logs] (capture (.error logger "Demonstration"))]
    (is (not-empty logs))
    (is (= "ERROR" (get-in logs [0 :level])))
    (is (= "Demonstration" (get-in logs [0 :message])))))

(deftest jul-logging
  (let [logger (java.util.logging.Logger/getLogger "myLogger")
        [logs] (capture (.severe logger "Demonstration"))]
    (is (not-empty logs))
    (is (= "ERROR" (get-in logs [0 :level])))
    (is (= "Demonstration" (get-in logs [0 :message])))))

(deftest log4j-logging
  (let [logger (org.apache.log4j.Logger/getLogger "myLogger")
        [logs] (capture (.error logger "Demonstration"))]
    (is (not-empty logs))
    (is (= "ERROR" (get-in logs [0 :level])))
    (is (= "Demonstration" (get-in logs [0 :message])))))