(ns datalogger.core
  (:require [jsonista.core :as jsonista]
            [clojure.string :as strings]
            [datalogger.utils :as utils]
            [datalogger.config :as config]
            [datalogger.context :as context])
  (:import (com.fasterxml.jackson.databind SerializationFeature ObjectMapper)
           (clojure.lang Agent ISeq IFn)
           (java.util.concurrent Executor)))

(set! *warn-on-reflection* true)

(defmacro with-context [context & body]
  `(let [old# context/*context*]
     (binding [context/*context* (utils/deep-merge old# ~context)]
       ~@body)))

(defmacro capture [& body]
  `(let [prom#   (promise)
         mapper# (config/get-object-mapper)
         splliter# (if (.isEnabled ^ObjectMapper mapper# SerializationFeature/INDENT_OUTPUT)
                     (fn [s#] (strings/split s# #"^\{\n$"))
                     strings/split-lines)
         lines#  (->> (with-out-str
                        (deliver prom# (do ~@body))
                        (await context/logging-agent))
                      (splliter#)
                      (remove strings/blank?)
                      (mapv #(jsonista/read-value %1 mapper#)))]
     [lines# (deref prom#)]))

(defmacro log [& args]
  (let [calling-ns (name (.getName *ns*))]
    `(let [callsite# ~(assoc (meta &form) :ns calling-ns)]
       (let [context# context/*context*
             mdc# (context/get-mdc)
             extra# (context/execution-context)]
         (.dispatch ^Agent context/logging-agent ^IFn
                    (fn [_#]
             (let [categorized#  ~(utils/categorize-arguments args)
                   full-context# (utils/deep-merge mdc# extra# context# callsite# (:data categorized# {}))]
               (context/write!
                 (cond-> full-context#
                   (contains? categorized# :message)
                   (assoc :message (utils/template (:message categorized#) full-context#))
                   (contains? categorized# :logger)
                   (assoc :logger (:logger categorized#))
                   (not (contains? categorized# :logger))
                   (assoc :logger (:ns callsite#))
                   (contains? categorized# :level)
                   (assoc :level (:level categorized#))
                   (some? (:exception categorized#))
                   (assoc :exception (:exception categorized#))))))
                    ^ISeq
                    (seq [])
                    ^Executor
                    Agent/soloExecutor))
       nil)))



(log :error "Stuff")