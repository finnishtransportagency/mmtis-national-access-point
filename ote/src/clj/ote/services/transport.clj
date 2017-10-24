(ns ote.services.transport
  "Services for getting transport data from database"
  (:require [com.stuartsierra.component :as component]
            [ote.components.http :as http]
            [specql.core :refer [fetch update! insert! upsert! delete!] :as specql]
            [clj-time.core :as time]
            [specql.op :as op]
            [ote.db.transport-operator :as transport-operator]
            [ote.db.transport-service :as t-service]
            [ote.db.common :as common]
            [compojure.core :refer [routes GET POST DELETE]]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [specql.impl.composite :as specql-composite]
            [ote.services.places :as places]
            [ote.authorization :as authorization]
            [jeesql.core :refer [defqueries]]
            [cheshire.core :as cheshire]
            [ote.authorization :as authorization]
            [ote.db.tx :as tx])
  (:import (java.time LocalTime)
           (java.sql Timestamp)))

(defqueries "ote/services/places.sql")

;; FIXME: monkey patch specql composite reading (For now)
(defmethod specql-composite/parse-value "bpchar" [_ string] string)

(def transport-operator-columns
  #{::transport-operator/id ::transport-operator/business-id ::transport-operator/email
    ::transport-operator/name})

(defn get-transport-operator [db where]
  (first (fetch db ::transport-operator/transport-operator
                (specql/columns ::transport-operator/transport-operator)
                where {::specql/limit 1})))

(def transport-services-columns
  #{::t-service/id ::t-service/type
    :ote.db.transport-service/passenger-transportation
    :ote.db.transport-service/terminal
    :ote.db.transport-service/rental
    :ote.db.transport-service/brokerage
    :ote.db.transport-service/parking
    :ote.db.transport-service/created
    :ote.db.transport-service/modified
    ::t-service/published?
    ::t-service/name})

(defn get-transport-services [db where]
  "Return Vector of transport-services"
  (fetch db ::t-service/transport-service
                transport-services-columns
                where
                {::specql/order-by ::t-service/type
                 ::specql/order-direction :desc
                 }
         ))

(defn- get-transport-service
  "Get single transport service by id"
  [db id]
  (let [service (first (fetch db
                ::t-service/transport-service
                (specql/columns ::t-service/transport-service)
                {::t-service/id id}
                {::specql/limit 1}))
        operation-area (first (fetch-operation-area-geojson db {:transport-service-id (get service ::t-service/id)}))
        op-area (-> operation-area
                  (assoc :coordinates (get (cheshire/parse-string (get operation-area :st_asgeojson)) "coordinates"))
                    (dissoc :st_asgeojson))
        service-key :ote.db.transport-service/terminal]

    (-> service
        (assoc-in [service-key  ::t-service/operation-area] op-area))))

(defn- delete-transport-service
  "Delete single transport service by id"
  [db id]
  ;; Delete operation area first
  (delete! db ::t-service/operation_area {::t-service/transport-service-id id})
  ;; Delete service
  (delete! db ::t-service/transport-service {::t-service/id id})
  )


(defn- ensure-transport-operator-for-group [db {:keys [title id] :as ckan-group}]
  (tx/with-transaction db
    (let [operator (get-transport-operator db {::transport-operator/ckan-group-id id})]
      (or operator
          ;; FIXME: what if name changed in CKAN, we should update?
          (insert! db ::transport-operator/transport-operator
                   {::transport-operator/name title
                    ::transport-operator/ckan-group-id id})))))


(defn- get-transport-operator-data [db {:keys [title id] :as ckan-group} user]
  (let [
        transport-operator (ensure-transport-operator-for-group db ckan-group)
        transport-services-vector (get-transport-services db {::t-service/transport-operator-id (::transport-operator/id transport-operator)})
        ]
    (println " transport-services-vector " transport-services-vector)
    {:transport-operator transport-operator
     :transport-service-vector transport-services-vector
     :user user}))


(defn- save-transport-operator [db data]
  (upsert! db ::transport-operator/transport-operator data))


(defn- fix-price-classes
  "Frontend sends price classes prices as floating points. Convert them to bigdecimals before db insert."
  [price-classes-float]
  (try
    (mapv #(update % ::t-service/price-per-unit bigdec) price-classes-float)
    (catch Exception e
      (log/info "Can't fix price classes: " price-classes-float e))))

(defn- save-passenger-transportation-info
  "UPSERT! given data to database. And convert possible float point values to bigdecimal"
  [db data]
  (println "DATA: " (pr-str data))
  (let [places (get-in data [::t-service/passenger-transportation ::t-service/operation-area])
        value (-> data
                  (assoc ::t-service/modified (Timestamp. (.getMillis (time/now))))
                  (update ::t-service/passenger-transportation dissoc ::t-service/operation_area)
                  (update-in [::t-service/passenger-transportation ::t-service/price-classes] fix-price-classes))]
    (jdbc/with-db-transaction [db db]
         (let [new-value (dissoc value :transport-service)
               newer-value (if (nil? (get value ::t-service/id))
                             (assoc new-value ::t-service/created (Timestamp. (.getMillis (time/now))))
                             new-value)
               transport-service (upsert! db ::t-service/transport-service newer-value)]
        (places/link-places-to-transport-service!
         db (::t-service/id transport-service) places)
        transport-service))))

(defn- save-terminal-info
  "UPSERT! given data to database. "
  [db data]
  (println "Terminal DATA: " (pr-str data))
  (let [places (get-in data [::t-service/terminal ::t-service/operation-area])
        op-area-id (get-in data [::t-service/terminal ::t-service/operation-area :id])
        coordinates (get-in data [::t-service/terminal ::t-service/operation-area :coordinates])
        value (-> data
                  (update ::t-service/terminal dissoc ::t-service/operation_area)
                  (assoc ::t-service/modified (Timestamp. (.getMillis (time/now))))
                  )]
    (println "Terminal AREA: " (pr-str places))
    (println "Terminal coordinates: " (pr-str coordinates))
    (println "Terminal op-area-id: " op-area-id)
    (jdbc/with-db-transaction [db db]
       (let [new-value (if (nil? (get value ::t-service/id))
                           (assoc value ::t-service/created (Timestamp. (.getMillis (time/now))))
                           value)
             transport-service (upsert! db ::t-service/transport-service new-value)]
         (when (not-empty coordinates)
           (cond
             (nil? op-area-id)
             (insert-point-for-transport-service! db
                                                {:transport-service-id (get transport-service ::t-service/id)
                                                 :x (first coordinates)
                                                 :y (second coordinates)})
             :else (save-point-for-transport-service! db
                                                      {:id op-area-id
                                                       :transport-service-id (get transport-service ::t-service/id)
                                                       :x (first coordinates)
                                                       :y (second coordinates)})
             ))
      ))))

(defn- publish-transport-service [db user {:keys [transport-service-id]}]
  (let [transport-operator-ids (authorization/user-transport-operators db user)]
    (= 1
       (specql/update! db ::t-service/transport-service
                       {::t-service/published? true}

                       {::t-service/transport-operator-id (op/in transport-operator-ids)
                        ::t-service/id transport-service-id}))))


(defn- transport-routes
  [db]
  (routes

   (GET "/transport-service/:id" [id]
     (http/transit-response (get-transport-service db (Long/parseLong id))))

   (POST "/transport-operator/group" {user :user}
         (ensure-transport-operator-for-group db (-> user :groups first)))

   (POST "/transport-operator/data" {user :user}
        (http/transit-response (get-transport-operator-data db (-> user :groups first) (:user user))))

   (POST "/transport-operator" {form-data :body
                                user :user}
         (log/info "USER: " user)
         (http/transit-response (save-transport-operator db (http/transit-request form-data))))

   (POST "/passenger-transportation-info" {form-data :body}
         (http/transit-response (save-passenger-transportation-info db (http/transit-request form-data))))

   (POST "/terminal" {form-data :body}
     (http/transit-response (save-terminal-info db (http/transit-request form-data))))

   (POST "/transport-service/publish" {payload :body
                                       user :user}
         (->> payload
              http/transit-request
              (publish-transport-service db user)
              http/transit-response))

   (GET "/transport-service/delete/:id" [id]
     (http/transit-response (delete-transport-service db (Long/parseLong id))))

   ))

(defrecord Transport []
  component/Lifecycle
  (start [{:keys [db http] :as this}]
    (assoc
      this ::lopeta
           (http/publish! http (transport-routes db))))
  (stop [{lopeta ::lopeta :as this}]
    (lopeta)
    (dissoc this ::lopeta)))
