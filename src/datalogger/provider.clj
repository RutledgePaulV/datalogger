(ns datalogger.provider
  (:import (org.slf4j.helpers NOPServiceProvider BasicMarkerFactory)
           (org.slf4j.bridge SLF4JBridgeHandler))
  (:gen-class
    :implements [org.slf4j.spi.SLF4JServiceProvider]
    :constructors {[] []}))

(defonce marker-factory (delay (BasicMarkerFactory.)))
(defonce logger-factory (delay ((requiring-resolve 'datalogger.loggers/data-logger-factory))))
(defonce mdc-adapter (delay (force (deref (requiring-resolve 'datalogger.context/mdc-adapter)))))

(defn -getLoggerFactory [this]
  (force logger-factory))

(defn -getMarkerFactory [this]
  (force marker-factory))

(defn -getMDCAdapter [this]
  (force mdc-adapter))

(defn -getRequesteApiVersion [this]
  NOPServiceProvider/REQUESTED_API_VERSION)

(defn -initialize [this]
  (SLF4JBridgeHandler/removeHandlersForRootLogger)
  (SLF4JBridgeHandler/install)
  (.getLoggerFactory this)
  (.getMarkerFactory this)
  (.getMDCAdapter this))