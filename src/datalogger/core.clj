(ns datalogger.core
  (:require [jsonista.core :as jsonista]
            [clojure.string :as strings]
            [datalogger.utils :as utils]
            [datalogger.config :as config]
            [datalogger.context :as context]))


(defmacro with-context [context & body]
  `(let [old# context/*context*]
     (binding [context/*context* (utils/deep-merge old# ~context)]
       ~@body)))

(defmacro capture [& body]
  `(let [prom#   (promise)
         mapper# (config/get-object-mapper)
         lines#  (->> (with-out-str
                        (deliver prom# (do ~@body))
                        (await context/logging-agent))
                      (strings/split-lines)
                      (remove strings/blank?)
                      (mapv #(jsonista/read-value %1 mapper#)))]
     [lines# (deref prom#)]))

(defmacro log [& args]
  (let [calling-ns (name (.getName *ns*))]
    `(let [callsite# ~(assoc (meta &form) :ns calling-ns)]
       (let [context# (context/capture-context)]
         (send-off context/logging-agent
           (fn [_#]
             (let [categorized#  ~(utils/categorize-arguments args)
                   full-context# (utils/deep-merge context# callsite# (:data categorized# {}))]
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
                   (assoc :exception (:exception categorized#))))))))
       nil)))


