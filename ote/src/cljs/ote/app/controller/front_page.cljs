(ns ote.app.controller.front-page
  (:require [tuck.core :as t]
            [ote.communication :as comm]))


;;Change page event. Give parameter in key format e.g: :front-page, :transport-operator, :transport-service
(defrecord ChangePage [given-page])

(defrecord GetTransportOperator [])
(defrecord HandleTransportOperatorResponse [response])

(defrecord GetTransportOperatorData [])
(defrecord HandleTransportOperatorDataResponse [response])

(extend-protocol t/Event

  ChangePage
  (process-event [{given-page :given-page} app]
    (assoc app :page given-page))

  GetTransportOperator
  (process-event [_ app]
      (comm/post! "transport-operator/group" {} {:on-success (t/send-async! ->HandleTransportOperatorResponse)})
      app)

  HandleTransportOperatorResponse
  (process-event [{response :response} app]
    (assoc app :transport-operator response))

  GetTransportOperatorData
  (process-event [_ app]
    (comm/post! "transport-operator/data" {} {:on-success (t/send-async! ->HandleTransportOperatorDataResponse)})
    app)

  HandleTransportOperatorDataResponse
  (process-event [{response :response} app]
    (.log js/console " Mitäkähän dataa serveriltä tulee " (clj->js response) (clj->js (get response :transport-operator)))
    (assoc app
      :transport-operator (get response :transport-operator)
      :transport-services (get response :transport-service-vector ))))



