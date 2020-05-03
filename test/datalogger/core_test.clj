(ns datalogger.core-test
  (:require [clojure.test :refer :all]
            [datalogger.core :refer :all]))

(deftest logger->hierarchy-test
  (is (= ["*"] (logger->hierarchy "*")))
  (is (= ["core" "*"] (logger->hierarchy "core")))
  (is (= ["core.*" "*"] (logger->hierarchy "core.*")))
  (is (= ["a.b.c" "a.b.*" "a.*" "*"] (logger->hierarchy "a.b.c"))))


(deftest logging-filter-test
  )