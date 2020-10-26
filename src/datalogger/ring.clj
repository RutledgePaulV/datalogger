(ns datalogger.ring
  (:require [datalogger.core :as log])
  (:import [java.util UUID]))

(defn default-request->data [request]
  (select-keys request [:request-method :uri :query-params]))

(defn default-response->data [response]
  (select-keys response [:status]))

(defn wrap-logging
  "Defines a simple logger for ring. Logs request and response basics plus request durations.
   Places a request id onto the logging context to relate all logging calls made during the
   scope of the request."
  ([handler]
   (wrap-logging handler {}))
  ([handler {:keys [request->data response->data]
             :or   {request->data  default-request->data
                    response->data default-response->data}}]
   (fn logging-handler
     ([request]
      (let [req-id  (UUID/randomUUID)
            started (System/currentTimeMillis)]
        (log/with-context {:ring/id req-id}
          (try
            (log/log :info {:ring/request (request->data request)})
            (let [response (handler request)]
              (log/log :info {:ring/response (response->data response)
                              :ring/duration (- (System/currentTimeMillis) started)})
              response)
            (catch Exception e
              (log/log :error e {:ring/duration (- (System/currentTimeMillis) started)})
              (throw e))))))
     ([request respond raise]
      (let [req-id  (UUID/randomUUID)
            started (System/currentTimeMillis)]
        (log/with-context {:ring/id req-id}
          (log/log :info {:ring/request (request->data request)})
          (handler request
                   (fn [response]
                     (log/with-context {:ring/id req-id}
                       (try
                         (respond response)
                         (finally
                           (log/log :info {:ring/response (response->data response)
                                           :ring/duration (- (System/currentTimeMillis) started)})))))
                   (fn [exception]
                     (log/with-context {:ring/id req-id}
                       (try
                         (raise exception)
                         (finally
                           (log/log :error exception {:ring/duration (- (System/currentTimeMillis) started)}))))))))))))