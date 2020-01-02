(ns ote.services.transport
  "Services for getting transport data from database"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [routes GET POST DELETE]]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch upsert! delete!] :as specql]
            [ote.components.http :as http]
            [ote.util.feature :as feature]
            [ote.db.common :as common]
            [ote.db.modification :as modification]
            [ote.db.transport-operator :as t-operator]
            [ote.db.transport-service :as t-service]
            [ote.util.db :as util]
            [ote.authorization :as authorization]
            [ote.netex.netex_util :as netex-util]
            [ote.services.places :as places]
            [ote.services.external :as external])
  (:import (java.util UUID)))

(defqueries "ote/services/places.sql")
(defqueries "ote/services/transport.sql")
(defqueries "ote/services/operators.sql")
(defqueries "ote/services/associations.sql")
(defqueries "ote/nap/users.sql")

(def transport-service-personal-columns
  #{::t-service/contact-phone
    ::t-service/contact-gsm
    ::t-service/contact-email})

(def transport-services-column-keys
  {:id ::t-service/id
   :transport-operator-id ::t-service/transport-operator-id
   :name ::t-service/name
   :transport-type ::t-service/transport-type
   :interface-types ::t-service/interface-types
   :type ::t-service/type
   :sub-type ::t-service/sub-type
   :parent-id ::t-service/parent-id
   :has-child? ::t-service/has-child?
   :published ::t-service/published
   :validate ::t-service/validate
   :re-edit ::t-service/re-edit
   :created ::modification/created
   :modified ::modification/modified})

(defn translate-pick-up-country [pick-up-locations country-list]
  (mapv
    (fn [p]
      (let [pick-up-country-code (get-in p [::t-service/pick-up-address ::common/country_code])
            country (some #(when (= pick-up-country-code (::common/country_code %))
                             (::common/value %))
                          country-list)]
        (if (some? country)
          (assoc-in p [::t-service/pick-up-address :country] country)
          p)))
    pick-up-locations))

(defn add-error-data
  [service]
  (let [sub-type (::t-service/sub-type service)
        interface-types (::t-service/interface-types service)]
    (assoc service :has-errors?
                   (and
                     (= sub-type :schedule)
                     (not ((set interface-types) :route-and-schedule))))))

(defn get-transport-services
  "Return Vector of transport-services"
  [db operators]
  (let [services (fetch-transport-services db {:operator-ids operators})
        ;; Add namespace for non namespaced keywords because sql query returns values without namespace
        modified-services (mapv (fn [x] (set/rename-keys x transport-services-column-keys)) services)]
    ;; For some reason type must be a keyword and query returns it as a string so make it keyword.
    (mapv (fn [service]
            (-> service
                (update ::t-service/type keyword)
                (update ::t-service/sub-type keyword)
                (update ::t-service/interface-types #(mapv keyword (util/PgArray->vec %)))
                (update ::t-service/transport-type #(mapv keyword (util/PgArray->vec %)))
                add-error-data))
          modified-services)))

(defn all-data-transport-service
  "Get single transport service by id"
  [config db id]
  (let [ts (first (fetch db ::t-service/transport-service
                         (conj (specql/columns ::t-service/transport-service)
                               ;; join external interfaces
                               [::t-service/external-interfaces
                                (specql/columns ::t-service/external-interface-description)])
                         {::t-service/id id}))]
    (if ts
      (http/no-cache-transit-response
        (-> (assoc ts ::t-service/operation-area
                      (places/fetch-transport-service-operation-area db id))
            vector
            (netex-util/append-ote-netex-urls config db ::t-service/external-interfaces)
            first)
        200)
      {:status 404})))

(defn delete-transport-service!
  "Delete single transport service by id"
  [db user id]
  (let [{::t-service/keys [transport-operator-id]}
        (first (specql/fetch db ::t-service/transport-service
                             #{::t-service/transport-operator-id
                               ::t-service/published}
                             {::t-service/id id}))]
    (authorization/with-transport-operator-check
      db user transport-operator-id
      #(do
         (delete! db ::t-service/transport-service {::t-service/id id})
         id))))

(defn ensure-bigdec [value]
  (when-not (nil? value) (bigdec value)))

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
               (mapv #(fix-price-classes % [::t-service/price-classes])
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

(defn mark-package-as-deleted
  "When external interface is deleted (when it is deleted or service is deleted) we don't want to
  remove all gtfs data that we have aquired. So we only mark gtfs_packages.deleted = TRUE for those packages and
  remove the interface url."
  [db external-interface-description-id]

  ;; set all found packages as deleted
  (specql/update! db :gtfs/package
                  {:gtfs/deleted? true}
                  {:gtfs/external-interface-description-id external-interface-description-id}))

(defn- save-external-interfaces
  "Save external interfaces for a transport service"
  [db transport-service-id external-interfaces removed-resources]
  (let [external-interfaces (mapv #(dissoc % :url-ote-netex) ; Remove because not part of db data model
                                  external-interfaces)]

    ;; Delete removed services from OTE db
    (doseq [{id ::t-service/id} removed-resources]
      ;; Mark possible gtfs_packages to removed and then remove interface
      (mark-package-as-deleted db id)
      ;; Delete from external-interface-download-status
      (specql/delete! db ::t-service/external-interface-download-status
                      {::t-service/external-interface-description-id id})
      ;; Delete from external-interface-description
      (specql/delete! db ::t-service/external-interface-description
                      {::t-service/id id}))

    (doseq [{id ::t-service/id :as ext-if} external-interfaces]
      (if id
        (specql/update! db ::t-service/external-interface-description
                        ext-if
                        {::t-service/transport-service-id transport-service-id
                         ::t-service/id id})
        (specql/insert! db ::t-service/external-interface-description
                        (assoc ext-if ::t-service/transport-service-id transport-service-id))))))

(defn- removable-resources
  [from-db from-client]
  (let [from-ui (into #{} (map ::t-service/id) from-client)
        to-delete (map #(select-keys % #{::t-service/id})
                       (filter (comp (complement from-ui) ::t-service/id) from-db))]
    to-delete))

(defn- delete-external-companies
  "User might remove url from service, so we delete all service-companies from db"
  [db transport-service]
  (specql/delete! db ::t-service/service-company {::t-service/transport-service-id (::t-service/id transport-service)}))

(defn save-external-companies
  "Service can contain an url that contains company names and business-id. Sevice can also contain an imported csv file
  with company names and business-ids."
  [db transport-service]
  (let [current-data (first (fetch db ::t-service/service-company (specql/columns ::t-service/service-company)
                                   {::t-service/transport-service-id (::t-service/id transport-service)}))
        companies (vec (:companies (external/check-csv {:url (::t-service/companies-csv-url transport-service)})))
        new-data (if (empty? current-data)
                   {::t-service/companies companies
                    ::t-service/transport-service-id (::t-service/id transport-service)
                    ::t-service/source "URL"}
                   (assoc current-data ::t-service/companies companies))]

    (external/save-companies db new-data)))

(defn- maybe-clear-companies
  "Companies can be added from url, csv or by hand in form. Clean up url if some other option is selected"
  [transport-service]
  (let [source (get transport-service ::t-service/company-source)]
    (cond
      (= :none source) (assoc transport-service ::t-service/companies-csv-url nil
                                                ::t-service/companies {})
      (= :form source) (assoc transport-service ::t-service/companies-csv-url nil)
      (= :csv-file source) (assoc transport-service ::t-service/companies-csv-url nil)
      (= :csv-url source) (assoc transport-service ::t-service/companies {})
      :else transport-service)))

(defn- fetch-transport-service-external-interfaces [db id]
  (when id
    (fetch db ::t-service/external-interface-description
           #{::t-service/external-interface ::t-service/data-content ::t-service/format
             ::t-service/license ::t-service/id}
           {::t-service/transport-service-id id})))

(defn- set-publish-time
  [data db]
  (let [publish? (::t-service/published? data)
        service-id (::t-service/id data)
        existing-publish-date (when service-id
                                (::t-service/published (first (specql/fetch db ::t-service/transport-service
                                                                            #{::t-service/published}
                                                                            {::t-service/id service-id}))))
        publish-date (or existing-publish-date (java.util.Date.))
        data (cond-> data
                     publish? (assoc ::t-service/published publish-date)
                     (not publish?) (assoc ::t-service/published nil)
                     true (dissoc ::t-service/published?)
                     ;; Ensure that state is publish in every case after saving
                     true (assoc ::t-service/validate nil)
                     true (assoc ::t-service/re-edit nil))]
    data))

(defn set-validate-time
  "Add validate datetime when it is moved to validation. If not, remove validate? key."
  [data]
  (cond
    ;; Saving and moving service to validation
    (::t-service/validate? data)
    (let [;; If service already published, remove id, set old id as parent-id and let the save duplicate service data
          service-id (::t-service/id data)
          data (if (::t-service/published data)
                 (-> data
                     (dissoc ::t-service/id)                ;; When creating a copy id must be removed
                     (dissoc ::t-service/published)         ;; When creating a copy the copy is not published
                     (assoc ::t-service/parent-id service-id))
                 data)]
      ;; Move to validation
      (-> data
          (dissoc ::t-service/validate?)
          (assoc ::t-service/validate (java.util.Date.)
                 ::t-service/re-edit nil)))

    ;; Save as draft
    :else
    (-> data
        (dissoc ::t-service/validate?)
        (assoc ::t-service/published nil)
        (assoc ::t-service/re-edit nil))))

(defn service-has-child?
  "When child service has parent-id pointing to given service it has child.
  Ensure that service has id before calling this fn."
  [db transport-service]
  (or
    (first (specql/fetch db ::t-service/transport-service
                         #{::t-service/id}
                         {::t-service/parent-id (::t-service/id transport-service)}))
    nil))

(defn- save-transport-service
  "UPSERT! given data to database. And convert possible float point values to bigdecimal"
  [config db user {places ::t-service/operation-area
                   external-interfaces ::t-service/external-interfaces
                   :as data}]
  ;; When validation flag is enabled we cannot let users to update service data if it has child service in validation.
  ;; So we need to check if validation flag is enabled and if parent-id is set for some other child service
  (let [has-child? (if (and
                         (feature/feature-enabled? config :service-validation)
                         (::t-service/id data))
                     (service-has-child? db data)
                     false)]

    (if has-child?
      data
      (let [original-service-id (::t-service/id data)
            ;; Validation is enabled via flag. Publish services if validation is not enabled
            data (if (feature/feature-enabled? config :service-validation)
                   (set-validate-time data)
                   (set-publish-time data db))
            service-info (-> data
                             (modification/with-modification-fields ::t-service/id user)
                             (dissoc ::t-service/operation-area)
                             floats-to-bigdec
                             (dissoc ::t-service/external-interfaces
                                     ::t-service/service-company)
                             (maybe-clear-companies))
            resources-from-db (fetch-transport-service-external-interfaces db original-service-id)
            removed-resources (removable-resources resources-from-db external-interfaces)
            ;; Store to OTE database
            transport-service
            (jdbc/with-db-transaction
              [db db]
              (let [transport-service (upsert! db ::t-service/transport-service service-info)
                    transport-service-id (::t-service/id transport-service)]

                ;; Save possible external interfaces
                (if (not= original-service-id transport-service-id)
                  ;; Copy existing interfaces to new service
                  (save-external-interfaces
                    db transport-service-id (map
                                              (fn [interface]
                                                (-> interface
                                                    (assoc ::t-service/transport-service-id transport-service-id)
                                                    (dissoc ::t-service/id)))
                                              external-interfaces)
                    nil)
                  (save-external-interfaces db transport-service-id external-interfaces removed-resources))

                ;; Save operation areas
                (if (not= original-service-id transport-service-id)
                  ;; We need to duplicate those original places and add new ones if user has desided to do changes to them
                  (do
                    (places/save-transport-service-operation-area! db transport-service-id places)
                    (places/duplicate-operation-area db original-service-id transport-service-id))
                  (places/save-transport-service-operation-area! db transport-service-id places))

                ;; Save companies
                (if (::t-service/companies-csv-url transport-service)
                  ;; Update companies
                  (save-external-companies db transport-service)
                  ;; If url is empty, delete remaining data
                  (delete-external-companies db transport-service))
                transport-service))]

        ;; Return the stored transport-service
        transport-service))))

(defn- re-edit-service [db user service-id]
  (let [service-operator-id (::t-service/transport-operator-id (first (specql/fetch db
                                                                                    ::t-service/transport-service
                                                                                    #{::t-service/transport-operator-id}
                                                                                    {::t-service/id service-id})))
        current-timestamp (java.sql.Timestamp. (System/currentTimeMillis))]
    (authorization/with-transport-operator-check
      db user service-operator-id
      #(do
         (specql/update! db ::t-service/transport-service
                         {::t-service/re-edit current-timestamp
                          ::t-service/validate nil}
                         {::t-service/id service-id})
         current-timestamp))))

(defn service-operator-id [db service-id]
  (::t-service/transport-operator-id (first (specql/fetch db
                                                          ::t-service/transport-service
                                                          #{::t-service/transport-operator-id}
                                                          {::t-service/id service-id}))))

(defn back-to-validation
  "User has possibility to take service back to editing state, but not continue with it. This fn will return
  service to validation in those cases."
  [db service-id]
  (let [current-timestamp (java.sql.Timestamp. (System/currentTimeMillis))]
    (do
      (specql/update! db ::t-service/transport-service
                      {::t-service/re-edit nil
                       ::t-service/validate current-timestamp}
                      {::t-service/id service-id})
      current-timestamp)))

(defn- save-transport-service-handler
  "Process transport service save POST request. Checks that the transport operator id
  in the service to be stored is in the set of allowed operators for the user.
  If authorization check succeeds, the transport service is saved to the database and optionally
  published to CKAN."
  [config db user request]
  (authorization/with-transport-operator-check
    db user (::t-service/transport-operator-id request)
    #(http/transit-response
       (save-transport-service config db user request))))

(defn- save-associated-operator
  "Save association between transport service and transport operator"
  [db user-id service-id operator-id]
  (try
    (http/transit-response
      (specql/insert! db ::t-service/associated-service-operators
                      {::t-service/service-id service-id
                       ::t-service/operator-id operator-id
                       ::t-service/user-id user-id}))
    (catch Exception e
      (log/info (str "Association save failed with service: " service-id " and user: " user-id))
      {:status 409 :body "Forbidden"})))

(defn- delete-associated-operator
  "remove association between trasport service and transport operator"
  [db service-id operator-id]
  (try
    (http/transit-response (specql/delete! db ::t-service/associated-service-operators
                                           {::t-service/service-id service-id
                                            ::t-service/operator-id operator-id}))
    (catch Exception e
      (log/info (str "Delete association failed with service: " service-id " and operator: " operator-id))
      {:status 404 :body "Association not found"})))

(defn public-data-transport-service
  "Get single transport service by id"
  [config db id]
  (let [ts (first (fetch db ::t-service/transport-service
                         (apply disj
                                (conj (specql/columns ::t-service/transport-service)
                                      ;; join external interfaces
                                      [::t-service/external-interfaces
                                       (specql/columns ::t-service/external-interface-description)])
                                transport-service-personal-columns)
                         {::t-service/id id}))]
    (if ts
      (http/no-cache-transit-response
        (-> (assoc ts ::t-service/operation-area
                      (places/fetch-transport-service-operation-area db id))

            vector
            (netex-util/append-ote-netex-urls config db ::t-service/external-interfaces)
            first))
      {:status 404})))

(defn ckan-group-id->group
  [db ckan-group-id]
  (first (specql/fetch db ::t-operator/group
                       (specql/columns ::t-operator/group)
                       {::t-operator/group-id ckan-group-id})))

(defn- transport-service-routes-auth
  "Routes that require authentication"
  [db config]
  (routes

    (POST "/transport-service/:service-id/associated-operators"
          {{:keys [service-id]} :params
           user :user
           data :body}
      (let [operator-id (:operator-id (http/transit-request data))
            user-id (get-in user [:user :id])
            service-id (Long/parseLong service-id)]
        (authorization/with-transport-operator-check db user operator-id
                                                     #(save-associated-operator db user-id service-id operator-id))))

    (DELETE "/transport-service/:service-id/associated-operators/:operator-id"
            {{:keys [service-id operator-id]}
             :params
             user :user}
      (let [service-id (Long/parseLong service-id)
            operator-id (Long/parseLong operator-id)]
        (authorization/with-transport-operator-check db user operator-id
                                                     #(delete-associated-operator db service-id operator-id))))

    (POST "/transport-service" {form-data :body
                                user :user}
      (save-transport-service-handler config db user (http/transit-request form-data)))

    (POST "/transport-service/delete" {form-data :body
                                       user :user}
      (http/transit-response
        (delete-transport-service! db user (:id (http/transit-request form-data)))))

    (POST "/transport-service/:service-id/re-edit-service" {{:keys [service-id]} :params
                                                            user :user}
      (let [service-id (Long/parseLong service-id)]
        (http/transit-response (re-edit-service db user service-id))))

    (POST "/transport-service/:service-id/back-to-validation" {{:keys [service-id]} :params
                                                               user :user}
      (let [service-id (Long/parseLong service-id)
            operator-id (service-operator-id db service-id)]
        (http/transit-response
          (authorization/with-transport-operator-check
            db user operator-id
            #(back-to-validation db service-id)))))))

(defn- transport-service-routes
  "Unauthenticated routes"
  [db config]
  (routes
    (GET "/transport-service/:id"
         {{:keys [id]}
          :params
          user :user
          cookies :cookies}
      (let [id (Long/parseLong id)
            operator-id (::t-service/transport-operator-id (first (specql/fetch db ::t-service/transport-service
                                                                                #{::t-service/transport-operator-id}
                                                                                {::t-service/id id})))]
        (if (or (authorization/admin? user) (authorization/is-author? db user operator-id))
          (all-data-transport-service config db id)
          (public-data-transport-service config db id))))))

(defrecord TransportService [config]
  component/Lifecycle
  (start [{:keys [db http] :as this}]
    (assoc
      this ::stop
           [(http/publish! http (transport-service-routes-auth db config))
            (http/publish! http {:authenticated? false} (transport-service-routes db config))]))
  (stop [{stop ::stop :as this}]
    (doseq [s stop]
      (s))
    (dissoc this ::stop)))
