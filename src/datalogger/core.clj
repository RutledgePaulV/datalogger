(ns datalogger.core
	(:require [jsonista.core :as jsonista]
						[clojure.string :as strings]
						[datalogger.impl.utils :as utils]
						[datalogger.impl.config :as config]
						[datalogger.impl.context :as context])
	(:import (com.fasterxml.jackson.databind SerializationFeature ObjectMapper)
					 (clojure.lang Agent ISeq IFn)
					 (java.util.concurrent Executor)))

(set! *warn-on-reflection* true)

(defn set-configuration!
	"Set logging configuration options."
	[config]
	(reset! config/CONFIG (config/normalize-config config)))

(defmacro with-context
	"Add data onto the context stack to be included in any logs.
	 Data is deep merged with data already on the stack."
	[context & body]
	`(let [old# context/*context*]
		 (binding [context/*context* (utils/deep-merge old# ~context)]
			 ~@body)))

(defmacro capture
	"Capture the logs being written."
	[& body]
	`(let [prom#     (promise)
				 mapper#   (config/get-object-mapper)
				 splliter# (if (.isEnabled ^ObjectMapper mapper# SerializationFeature/INDENT_OUTPUT)
										 (fn [s#] (strings/split s# #"^\{\n$"))
										 strings/split-lines)
				 lines#    (->> (with-out-str
													(deliver prom# (do ~@body))
													(await context/logging-agent))
												(splliter#)
												(remove strings/blank?)
												(mapv #(jsonista/read-value %1 mapper#)))]
		 [lines# (deref prom#)]))

(defmacro log
	"Write to the log.

		required argument that should be first.

			keyword to indicate the log level or a vector of [logger-name level]

		optional arguments that may appear in any order.

			a map of additional data to write to the log entry.
			a throwable instance to include a stack trace / exception data.
			string message that can be formatted with data from the data map.
	"
	[& args]
	(let [calling-ns (name (.getName *ns*))]
		`(let [callsite#    ~(assoc (meta &form) :ns calling-ns)
					 categorized# ~(utils/categorize-arguments args)
					 config#      (deref config/CONFIG)]
			 (when ((:filter config#)
							(or (:logger categorized#)
									(:ns callsite#))
							(:level categorized#))
				 (let [context# context/*context*
							 mdc#     (context/get-mdc)
							 extra#   (context/execution-context)
							 out#     *out*]
					 (.dispatch ^Agent context/logging-agent ^IFn
											(fn [_#]
												(let [full-context# (utils/deep-merge mdc# extra# context# callsite# (:data categorized# {}))]
													(context/write! config# out#
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
											Agent/soloExecutor)))
			 nil)))

; attach an error handler to the logging agent so we know if
; there's an error in the logging system
(set-error-handler!
	context/logging-agent
	(fn [^Agent a ^Throwable e]
		(log :error e "Failed to write log message.")))