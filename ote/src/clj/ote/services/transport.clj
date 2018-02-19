(ns ote.services.transport
  "Services for getting transport data from database"
  (:require [com.stuartsierra.component :as component]
            [ote.components.http :as http]
            [specql.core :refer [fetch update! insert! upsert! delete!] :as specql]
            [clj-time.core :as time]
            [specql.op :as op]
            [ote.db.transport-operator :as t-operator]
            [ote.db.transport-service :as t-service]
            [ote.db.common :as common]
            [compojure.core :refer [routes GET POST DELETE]]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [specql.impl.composite :as specql-composite]
            [ote.services.places :as places]
            [ote.services.external :as external]
            [ote.authorization :as authorization]
            [jeesql.core :refer [defqueries]]
            [cheshire.core :as cheshire]
            [ote.db.tx :as tx]
            [ote.nap.publish :as publish]
            [ote.db.modification :as modification]
            [ote.access-rights :as access]
            [clojure.string :as str]
            [ote.nap.ckan :as ckan])
  (:import (java.time LocalTime)
           (java.sql Timestamp)))

(defqueries "ote/services/places.sql")
(defqueries "ote/services/transport.sql")
(defqueries "ote/services/operators.sql")

(def transport-operator-columns
  #{::t-operator/id ::t-operator/business-id ::t-operator/email
    ::t-operator/name})

(defn get-transport-operator [db where-parameter]
  (let [where  (merge  {::t-operator/deleted? false} where-parameter)
        operator (first (fetch db ::t-operator/transport-operator
                               (specql/columns ::t-operator/transport-operator)
                               where {::specql/limit 1}))]
    (when operator
      (assoc operator ::t-operator/ckan-description (or (fetch-transport-operator-ckan-description
                                                         db {:id (::t-operator/ckan-group-id operator)})
                                                        "")))))

(def transport-services-columns
  #{::t-service/id
    ::t-service/transport-operator-id
    ::t-service/name
    ::t-service/type
    ::t-service/sub-type
    ::t-service/published?
    ::modification/created
    ::modification/modified})

(defn get-transport-services [db where]
  "Return Vector of transport-services"
  (fetch db ::t-service/transport-service
                transport-services-columns
                where
                {::specql/order-by ::t-service/type ::specql/order-direction :desc}))

(defn get-transport-service
  "Get single transport service by id"
  [db id]
  (let [ts (first (fetch db ::t-service/transport-service
                         (conj (specql/columns ::t-service/transport-service)
                               ;; join external interfaces
                               [::t-service/external-interfaces
                                (specql/columns ::t-service/external-interface-description)])
                         {::t-service/id id}))]
    (if ts
      (assoc ts ::t-service/operation-area
             (places/fetch-transport-service-operation-area db id))
      nil)))

(defn delete-transport-service!
  "Delete single transport service by id"
  [nap-config db user id]

  (let [{::t-service/keys [transport-operator-id published?]}
        (first (specql/fetch db ::t-service/transport-service
                             #{::t-service/transport-operator-id
                               ::t-service/published?}
                             {::t-service/id id}))]
    (authorization/with-transport-operator-check
      db user transport-operator-id
      #(do

         (if published?
           ;; For published services, call CKAN API to delete dataset
           ;; this cascades to all OTE information
           (do
             (try
               (publish/delete-published-service! nap-config db user id)
               ;; If for some reason the service is not found
               ;; from ckan then delete service from OTE anyway
               (catch Exception e
                 (delete! db ::t-service/transport-service {::t-service/id id}))))
           ;; Otherwise delete from transport-service table
           (delete! db ::t-service/transport-service {::t-service/id id}))
         id))))

(defn get-user-transport-operators-with-services [db groups user]
  (let [operators (keep #(get-transport-operator db {::t-operator/ckan-group-id (:id %)}) groups)
        operator-ids (into #{} (map ::t-operator/id) operators)
        operator-services (get-transport-services db {::t-service/transport-operator-id (op/in operator-ids)})]
    {:user (dissoc user :apikey :email :id)
     :transport-operators
     (map (fn [{id ::t-operator/id :as operator}]
            {:transport-operator operator
             :transport-service-vector (into []
                                             (filter #(= id (::t-service/transport-operator-id %)))
                                             operator-services)})
          operators)}))

(defn get-transport-operator-data [db {:keys [title id] :as ckan-group} user]
  (let [transport-operator (get-transport-operator db {::t-operator/ckan-group-id (:id ckan-group)})
        transport-services-vector (get-transport-services db {::t-service/transport-operator-id (::t-operator/id transport-operator)})
        ;; Clean up user data
        cleaned-user (dissoc user
                             :apikey
                             :email
                             :id)]
    {:transport-operator transport-operator
     :transport-service-vector transport-services-vector
     :user cleaned-user}))

(defn- create-transport-operator [nap-config db user data]
  ;; Create new transport operator
  (tx/with-transaction db
    (let [ckan (ckan/->CKAN (:api nap-config) (get-in user [:user :apikey]))

          ;; Insert to our database
          operator  (insert! db ::t-operator/transport-operator
                             (dissoc data
                                     ::t-operator/id :new?
                                     ::t-operator/ckan-description))

          ;; Create organization in CKAN
          ckan-response (ckan/create-organization!
                         ckan
                         {:ckan/name (str "transport-operator-" (::t-operator/id operator))
                          :ckan/title (::t-operator/name operator)
                          :ckan/description (or (::t-operator/ckan-description data) "")})]
      ;; Update CKAN org id
      (update! db ::t-operator/transport-operator
               {::t-operator/ckan-group-id (get-in ckan-response [:ckan/result :ckan/id])}
               {::t-operator/id (::t-operator/id operator)})
      operator)))

(defn- update-transport-operator [nap-config db user {id ::t-operator/id :as data}]
  ;; Edit transport operator
  (authorization/with-transport-operator-check
    db user id
    #(tx/with-transaction db
       (let [ckan (ckan/->CKAN (:api nap-config) (get-in user [:user :apikey]))
             operator
             (upsert! db ::t-operator/transport-operator
                      (dissoc data
                              ::t-operator/ckan-description))

             operator-ckan-id
             (::t-operator/ckan-group-id
              (first
               (fetch db ::t-operator/transport-operator
                      #{::t-operator/ckan-group-id}
                      {::t-operator/id id})))]
         ;; We show only title in ckan side - so no need to update other values
         (ckan/update-organization!
          ckan
          (->  (ckan/get-organization ckan operator-ckan-id)
               :ckan/result
               (assoc :ckan/title (::t-operator/name operator)
                      :ckan/description (or (::t-operator/ckan-description data) ""))))
         ;; Return operator
         operator))))

(defn- save-transport-operator [nap-config db user data]
  ((if (:new? data)
     create-transport-operator
     update-transport-operator) nap-config db user data))

(defn ensure-bigdec [value]
      (when (not (nil? value )) (bigdec value)))

(defn- fix-price-classes
  "Frontend sends price classes prices as floating points. Convert them to bigdecimals before db insert."
  ([service data-path]
   (fix-price-classes service data-path [::t-service/price-per-unit]))
  ([service data-path price-per-unit-path]
   (update-in service data-path
              (fn [price-classes-float]
                (mapv #(update-in % price-per-unit-path ensure-bigdec) price-classes-float)))))

(defn- update-rental-price-classes [service]
  (update-in service [::t-service/rentals ::t-service/vehicle-classes]
             (fn [vehicles]
               (mapv #(let [before %
                            after (fix-price-classes % [::t-service/price-classes])]
                        after)
                     vehicles))))

(defn- floats-to-bigdec
  "Frontend sends some values as floating points. Convert them to bigdecimals before db insert."
  [service]
  (case (::t-service/type service)
    :passenger-transportation
    (fix-price-classes service [::t-service/passenger-transportation ::t-service/price-classes])
    :parking
    (fix-price-classes service [::t-service/parking ::t-service/price-classes])
    :rentals
    (-> service
        (fix-price-classes [::t-service/rentals ::t-service/rental-additional-services]
                           [::t-service/additional-service-price ::t-service/price-per-unit])
        update-rental-price-classes)
    service))

(defn- save-external-interfaces
  "Save external interfaces for a transport service"
  [db transport-service-id external-interfaces removed-resources]

  ;; Delete services that have not been published yet
  (specql/delete! db ::t-service/external-interface-description
                  {::t-service/transport-service-id transport-service-id
                   ::t-service/ckan-resource-id op/null?})

  ;; Delete removed services from OTE db
  (doseq [{id ::t-service/id} removed-resources]
    (specql/delete! db ::t-service/external-interface-description
                    {::t-service/id id}))

  ;; Update or insert new external interfaces
  (doseq [{ckan-resource-id ::t-service/ckan-resource-id :as ext-if}
          (filter (fn [el] (not (:deleted? el))) external-interfaces)]
    (if ckan-resource-id
      (specql/update! db ::t-service/external-interface-description
                      ext-if
                      {::t-service/transport-service-id transport-service-id
                       ::t-service/ckan-resource-id ckan-resource-id})

      (specql/insert! db ::t-service/external-interface-description
                      (assoc ext-if ::t-service/transport-service-id transport-service-id)))))

(defn- removable-resources
  [from-db from-client]
  (let [in-db (into #{} (map ::t-service/id) from-db)
        from-ui (into #{} (map ::t-service/id) from-client)
        to-delete (map #(select-keys % #{::t-service/ckan-resource-id ::t-service/id})
                       (filter (comp (complement from-ui) ::t-service/id) from-db))]
    to-delete))

(defn- delete-external-companies
  "User might remove url from service, so we delete all service-companies from db"
  [db transport-service]
    (specql/delete! db ::t-service/service-company {::t-service/transport-service-id (::t-service/id transport-service)}))

(defn- save-external-companies
  "Service can contain an url that contains company nmes and business-id. Sevice can also contain an imported csv file
  with company names and business-ids."
  [db transport-service]
  (let [current-data (first (fetch db ::t-service/service-company (specql/columns ::t-service/service-company)
                            {::t-service/transport-service-id (::t-service/id transport-service)}))
        companies (into [] (:companies (external/check-csv {:url (::t-service/companies-csv-url transport-service)})))
        new-data (if (empty? current-data)
                      {::t-service/companies            companies
                       ::t-service/transport-service-id (::t-service/id transport-service)
                       ::t-service/source               "URL"}
                      (assoc current-data ::t-service/companies companies))]

    (external/save-companies db new-data)))

(defn- save-transport-service
  "UPSERT! given data to database. And convert possible float point values to bigdecimal"
  [nap-config db user {places ::t-service/operation-area
                       external-interfaces ::t-service/external-interfaces
                       service-company ::t-service/service-company
                       :as data}]
  ;(println "DATA: " (pr-str data))
  (let [service-info (-> data
                         (modification/with-modification-fields ::t-service/id user)
                         (dissoc ::t-service/operation-area)
                         floats-to-bigdec
                         (dissoc ::t-service/external-interfaces
                                 ::t-service/service-company))

        resources-from-db (publish/fetch-transport-service-external-interfaces db (::t-service/id data))
        removed-resources (removable-resources resources-from-db external-interfaces)
        ;; Store to OTE database
        transport-service
        (jdbc/with-db-transaction [db db]
          (let [transport-service (upsert! db ::t-service/transport-service service-info)
                transport-service-id (::t-service/id transport-service)]

            ;; Save possible external interfaces
            (save-external-interfaces db transport-service-id external-interfaces removed-resources)

            ;; Save operation areas
            (places/save-transport-service-operation-area! db transport-service-id places)

            ;; Save companies
            (if (::t-service/companies-csv-url transport-service)
              ;; Update companies
              (save-external-companies db transport-service)
              ;; If url is empty, delete remaining data
              (delete-external-companies db transport-service))

            transport-service))]

    ;; If published, use CKAN API to add dataset and resource
    (when (::t-service/published? data)
      (publish/delete-resources-from-published-service! nap-config user removed-resources)
      (publish/publish-service-to-ckan! nap-config db user (::t-service/id transport-service)))

    ;; Return the stored transport-service
    transport-service))

(defn- save-transport-service-handler
  "Process transport service save POST request. Checks that the transport operator id
  in the service to be stored is in the set of allowed operators for the user.
  If authorization check succeeds, the transport service is saved to the database and optionally
  published to CKAN."
  [nap-config db user request]
    (authorization/with-transport-operator-check
      db user (::t-service/transport-operator-id request)
      #(http/transit-response
        (save-transport-service nap-config db user request))))

(defn- transport-routes-auth
  "Routes that require authentication"

  [db nap-config]
  (routes

   (GET "/transport-service/:id" [id]
        (let [ts (get-transport-service db (Long/parseLong id))]
          (if-not ts
            {:status 404}
            (http/no-cache-transit-response ts))))

   (POST "/transport-operator/group" {user :user}
     (http/transit-response
       (get-transport-operator db {::t-operator/ckan-group-id (get (-> user :groups first) :id)})))

   (POST "/transport-operator/data" {user :user}
         (http/transit-response
           (get-user-transport-operators-with-services db (:groups user) (:user user))))

   (POST "/transport-operator" {form-data :body
                                user :user}
         (http/transit-response
          (save-transport-operator nap-config db user
                                   (http/transit-request form-data))))

   (POST "/transport-service" {form-data :body
                               user :user}
         (save-transport-service-handler nap-config db user (http/transit-request form-data)))

   (POST "/transport-service/delete" {form-data :body
                                      user :user}
        (http/transit-response
         (delete-transport-service! nap-config db user
                                    (:id (http/transit-request form-data)))))))

(defn- transport-routes
  "Unauthenticated routes"
  [db nap-config]
  (routes
    (GET "/transport-operator/:ckan-group-id" [ckan-group-id]
         (http/transit-response
          (get-transport-operator db {::t-operator/ckan-group-id ckan-group-id})))))

(defrecord Transport [nap-config]
  component/Lifecycle
  (start [{:keys [db http] :as this}]
    (assoc
      this ::stop
      [(http/publish! http (transport-routes-auth db nap-config))
       (http/publish! http {:authenticated? false} (transport-routes db nap-config))]))
  (stop [{stop ::stop :as this}]
    (doseq [s stop]
      (s))
    (dissoc this ::stop)))
