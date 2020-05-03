(ns datalogger.utils-test
  (:require [clojure.test :refer :all]
            [datalogger.utils :refer :all]))


(deftest logger->hierarchy-test
  (is (= ["*"] (logger->hierarchy "*")))
  (is (= ["core" "*"] (logger->hierarchy "core")))
  (is (= ["core.*" "*"] (logger->hierarchy "core.*")))
  (is (= ["a.b.c" "a.b.*" "a.*" "*"] (logger->hierarchy "a.b.c"))))

(deftest logging-filter-test
  (let [levels   {"*" :error "a.*" :info "a.b.*" :debug "a.b.c" :trace}
        enabled? (compile-filter levels)]
    (is (enabled? "a" :error))
    (is (not (enabled? "a" :warn)))
    (is (enabled? "a.b.c" :trace))
    (is (not (enabled? "a.b.d" :trace)))
    (is (enabled? "a.b.d" :debug))
    (is (enabled? "a.b" :info))
    (is (not (enabled? "a.b" :trace)))))