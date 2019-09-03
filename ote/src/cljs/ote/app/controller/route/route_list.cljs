(ns ote.app.controller.route.route-list
  "Route lsit controller"
  (:require [tuck.core :as tuck]
            [ote.communication :as comm]
            [ote.db.transit :as transit]
            [ote.localization :refer [tr tr-key]]
            [ote.app.routes :as routes]
            [ote.app.controller.route.route-wizard :as route-wizard]
            [ote.db.transport-operator :as t-operator]))

;; Load users own routes
(defrecord LoadRoutes [])
(defrecord LoadRoutesResponse [response])

;; Delete
(defrecord OpenDeleteRouteModal [id])
(defrecord CancelDeleteRoute [id])
(defrecord ConfirmDeleteRoute [id])
(defrecord DeleteRouteResponse [response])
(defrecord DeleteRouteResponseFailed [response])

;; Create new route
(defrecord CreateNewRoute [])

(defn- update-route-by-id [app id update-fn & args]
  (update app :routes-vector
          (fn [services]
            (map #(if (= (::transit/route-id %) id)
                    (apply update-fn % args)
                    %)
                 services))))

(defn- get-routes-for-operator
  [app response]
  (:routes (some #(when (= (get-in app [:transport-operator ::t-operator/id])
                           (get-in % [:transport-operator ::t-operator/id])) %)
                 response)))

(extend-protocol tuck/Event
  LoadRoutes
  (process-event [_ app]
    (let [on-success (tuck/send-async! ->LoadRoutesResponse)]
      (comm/post! "routes/routes" {}
                  {:on-success on-success})
      app))

  LoadRoutesResponse
  (process-event [{response :response} app]
    (assoc app :route-list response
               :routes-vector (get-routes-for-operator app response)))

  CreateNewRoute
  (process-event [_ app]
    (routes/navigate! :new-route)
    app)

  OpenDeleteRouteModal
  (process-event [{id :id} app]
    (update-route-by-id
      app id
      assoc :show-delete-modal? true))

  CancelDeleteRoute
  (process-event [{id :id} app]
    (update-route-by-id
      app id
      dissoc :show-delete-modal?))

  ConfirmDeleteRoute
  (process-event [{id :id} app]
    (comm/post! "routes/delete" {:id id}
                {:on-success (tuck/send-async! ->DeleteRouteResponse)
                 :on-failure (tuck/send-async! ->DeleteRouteResponseFailed)})
    app)

  DeleteRouteResponse
  (process-event [{response :response} app]
    (let [filtered-map (filter #(not= (::transit/route-id %) (int response)) (get app :routes-vector))]
      (assoc app :routes-vector filtered-map
                 :flash-message (tr [:common-texts :delete-route-success])
                 :routes-changed? true)))

  DeleteRouteResponseFailed
  (process-event [{response :response} app]
    (assoc app :flash-message-error (tr [:common-texts :delete-route-error]))))
