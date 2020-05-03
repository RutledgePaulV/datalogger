(ns datalogger.provider
  (:import (org.slf4j.helpers NOPServiceProvider)
           (org.slf4j.bridge SLF4JBridgeHandler))
  (:gen-class
    :implements [org.slf4j.spi.SLF4JServiceProvider]
    :constructors {[] []}))

(defn _obtain [symbol]
  (force (deref (requiring-resolve symbol))))

(defn -getLoggerFactory [this]
  (_obtain 'datalogger.core/logger-factory))

(defn -getMarkerFactory [this]
  (_obtain 'datalogger.core/marker-factory))

(defn -getMDCAdapter [this]
  (_obtain 'datalogger.core/mdc-adapter))

(defn -getRequesteApiVersion [this]
  NOPServiceProvider/REQUESTED_API_VERSION)

(defn -initialize [this]
  (SLF4JBridgeHandler/removeHandlersForRootLogger)
  (SLF4JBridgeHandler/install)
  (.getLoggerFactory this)
  (.getMarkerFactory this)
  (.getMDCAdapter this))