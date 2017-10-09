(ns ote.services.transport-service-services
  (:require [tuck.core :as t]
            [ote.db.transport-service :as transport-service]))

(defrecord AddPriceClassRow [])
(defrecord RemovePriceClassRow [])

(extend-protocol t/Event

  AddPriceClassRow
  (process-event [_ app]
    (update-in app [:transport-service ::transport-service/passenger-transportation ::transport-service/price-classes]
               #(conj (or % []) {::transport-service/currency "EUR"})))

  RemovePriceClassRow
  (process-event [_ app]
    (assoc-in app [:transport-service :price-class-open] false)))

