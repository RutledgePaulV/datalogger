(ns datalogger.core-test
  (:require [clojure.test :refer :all]
            [datalogger.core :refer :all]))

(def slf4j-logger (org.slf4j.LoggerFactory/getLogger "myLogger"))
(def jul-logger (java.util.logging.Logger/getLogger "myLogger"))
(def log4j-logger (org.apache.log4j.Logger/getLogger "myLogger"))
(def jcl-logger (org.apache.commons.logging.LogFactory/getLog "myLogger"))

(deftest clojure-logging
  (let [[logs] (capture (log :error "Demonstration {value}." {:value 1}))]
    (is (not-empty logs))
    (is (= "ERROR" (get-in logs [0 :level])))
    (is (= "Demonstration 1." (get-in logs [0 :message])))))

(deftest masking-test
  (with-config {:masking {:keys #{:ssn :ns/something} :values #{"\\d{3}-\\d{2}-\\d{4}"}}}
    (let [[[one-log :as logs]]
          (capture (log :error {:ssn          "something"
                                :ns/ssn       "badger"
                                :ns/something "cats"
                                :something    "111-11-1111"}))]
      (is (not-empty logs))
      (is (= "*********" (:ssn one-log)))
      (is (= "badger" (:ns/ssn one-log)))
      (is (= "***********" (:something one-log)))
      (is (= "****" (:ns/something one-log))))))

(deftest clojure-logging-with-context
  (with-context {:outside 1}
    (with-context {:inside 2}
      (let [[logs] (capture (log :error "Demonstration."))]
        (is (not-empty logs))
        (is (= 1 (get-in logs [0 :outside])))
        (is (= 2 (get-in logs [0 :inside]))))))
  (let [[logs] (capture (log :error "Demonstration."))]
    (is (not-empty logs))
    (is (not (contains? (first logs) :outside)))
    (is (not (contains? (first logs) :inside)))))

(deftest slf4j-logging
  (let [[logs] (capture (.error slf4j-logger "Demonstration {}." 1))]
    (is (not-empty logs))
    (is (= "ERROR" (get-in logs [0 :level])))
    (is (= "Demonstration 1." (get-in logs [0 :message])))))

(deftest slf4j-logging-mdc
  (org.slf4j.MDC/put "key" "value")
  (let [[logs] (capture (.error slf4j-logger "Demonstration {}." 1))]
    (is (not-empty logs))
    (is (= "value" (get-in logs [0 :key])))))

(deftest jcl-logging
  (let [[logs] (capture (.error jcl-logger "Demonstration"))]
    (is (not-empty logs))
    (is (= "ERROR" (get-in logs [0 :level])))
    (is (= "Demonstration" (get-in logs [0 :message])))))

(deftest jul-logging
  (let [[logs] (capture (.log jul-logger java.util.logging.Level/SEVERE "Demonstration {0}." 1))]
    (is (not-empty logs))
    (is (= "ERROR" (get-in logs [0 :level])))
    (is (= "Demonstration 1." (get-in logs [0 :message])))))

(deftest log4j-logging
  (let [[logs] (capture (.error log4j-logger "Demonstration"))]
    (is (not-empty logs))
    (is (= "ERROR" (get-in logs [0 :level])))
    (is (= "Demonstration" (get-in logs [0 :message])))))

(deftest log4j-logging-mdc
  (org.apache.log4j.MDC/put "key" "log4j")
  (let [[logs] (capture (.error log4j-logger "Demonstration"))]
    (is (not-empty logs))
    (is (= "log4j" (get-in logs [0 :key])))))

(deftest qualified-keywords-interpolation
  (let [[[log :as logs]] (capture (log :error "Testing {value}" {::value "Interpolation"}))]
    (is (not-empty logs))
    (is (= "Testing Interpolation" (:message log)))))

(deftest assertions
  (testing "ordered"
    (assert-logs [{:level "ERROR"} {:level "WARN"}]
      (log :error "This is a test of log assertions.")
      (log :warn "This is a test of log assertions.")))
  (testing "unordered"
    (assert-logs (repeat #{{:level "WARN"} {:level "ERROR"}})
      (log :error "This is a test of log assertions.")
      (log :warn "This is a test of log assertions."))))