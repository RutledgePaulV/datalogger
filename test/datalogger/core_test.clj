(ns datalogger.core-test
  (:require [clojure.test :refer :all]
            [datalogger.core :refer :all])
  (:import (org.slf4j LoggerFactory)))



(deftest java-logging
  (let [logger (LoggerFactory/getLogger "myLogger")
        [logs] (capture (.error logger "Demonstration"))]
    (is (not-empty logs))
    (is (= "ERROR" (get-in logs [0 :level])))
    (is (= "Demonstration" (get-in logs [0 :message])))))