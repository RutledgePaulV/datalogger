(ns datalogger.core-test
  (:require [clojure.test :refer :all]
            [datalogger.core :refer :all]))

(def slf4j-logger (org.slf4j.LoggerFactory/getLogger "myLogger"))
(def jul-logger (java.util.logging.Logger/getLogger "myLogger"))
(def log4j-logger (org.apache.log4j.Logger/getLogger "myLogger"))
(def jcl-logger (org.apache.commons.logging.LogFactory/getLog "myLogger"))

(deftest clojure-logging
  (assert-logs [{:level "ERROR" :message "Demonstration 1."}]
    (log :error "Demonstration {value}." {:value 1})))

(deftest masking-test
  (with-config {:masking {:keys #{:ssn :ns/something} :values #{"\\d{3}-\\d{2}-\\d{4}"}}}
    (assert-logs [{:ssn "*********" :ns/ssn "badger" :something "***********" :ns/something "****"}]
      (log :error {:ssn "something" :ns/ssn "badger" :ns/something "cats" :something "111-11-1111"}))))

(deftest exceptions-test
  (let [trace-pattern (repeat {:class string? :filename string? :line number? :method string?})]
    (testing "tree of exceptions"
      (with-config {:exceptions {:root-only false}}
        (assert-logs [{:exception {:message "Outer" :trace trace-pattern :cause {:message "Inner"}}}]
          (log :error (RuntimeException. "Outer" (ex-info "Inner" {}))))))
    (testing "only the root exception"
      (with-config {:exceptions {:root-only true}}
        (assert-logs [{:exception {:message "Inner" :trace trace-pattern :cause nil?}}]
          (log :error (RuntimeException. "Outer" (ex-info "Inner" {}))))))))

(deftest clojure-logging-with-context
  (assert-logs [{:outside 1 :inside 2}]
    (with-context {:outside 1}
      (with-context {:inside 2}
        (log :error "Demonstration.")))))

(deftest slf4j-logging
  (assert-logs [{:level "ERROR" :message "Demonstration 1."}]
    (.error slf4j-logger "Demonstration {}." 1)))

(deftest slf4j-logging-mdc
  (assert-logs [{:key "value"}]
    (org.slf4j.MDC/put "key" "value")
    (.error slf4j-logger "Demonstration {}." 1)))

(deftest jcl-logging
  (assert-logs [{:level "ERROR" :message "Demonstration"}]
    (.error jcl-logger "Demonstration")))

(deftest jul-logging
  (assert-logs [{:level "ERROR" :message "Demonstration 1."}]
    (.log jul-logger java.util.logging.Level/SEVERE "Demonstration {0}." 1)))

(deftest log4j-logging
  (assert-logs [{:level "ERROR" :message "Demonstration"}]
    (.error log4j-logger "Demonstration")))

(deftest log4j-logging-mdc
  (assert-logs [{:key "log4j"}]
    (org.apache.log4j.MDC/put "key" "log4j")
    (.error log4j-logger "Demonstration")))

(deftest qualified-keywords-interpolation
  (assert-logs [{:message "Testing Interpolation" ::value "Interpolation"}]
    (log :error "Testing {value}" {::value "Interpolation"})))

(deftest assertions
  (testing "ordered"
    (assert-logs [{:level "ERROR"} {:level "WARN"}]
      (log :error "This is a test of log assertions.")
      (log :warn "This is a test of log assertions.")))
  (testing "unordered"
    (assert-logs (repeat #{{:level "WARN"} {:level "ERROR"}})
      (log :error "This is a test of log assertions.")
      (log :warn "This is a test of log assertions."))))
