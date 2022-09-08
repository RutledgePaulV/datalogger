(ns datalogger.impl.provider-test
  (:require [clojure.test :refer :all])
  (:import [datalogger.impl provider]))

(deftest getRequestedApiVersion-test
  (is (string? (.getRequestedApiVersion (new provider)))))
