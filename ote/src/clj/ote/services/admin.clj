(ns ote.services.admin
  "Backend services for admin functionality."
  (:require [ote.components.http :as http]
            [com.stuartsierra.component :as component]
            [specql.core :refer [fetch update! insert! upsert! delete!] :as specql]
            [specql.op :as op]
            [taoensso.timbre :as log]
            [compojure.core :refer [routes GET POST DELETE]]
            [jeesql.core :refer [defqueries]]
            [ote.nap.users :as nap-users]
            [specql.core :as specql]
            [ote.db.auditlog :as auditlog]
            [ote.db.transport-service :as t-service]
            [ote.db.transport-operator :as t-operator]
            [ote.db.modification :as modification]
            [ote.services.transport :as transport]
            [cheshire.core :as cheshire]
            [ote.authorization :as authorization]
            [ote.util.db :as db-util]
            [clojure.string :as str]))

(defqueries "ote/services/admin.sql")

(def service-search-result-columns
  #{::t-service/contact-email
    ::t-service/sub-type
    ::t-service/id
    ::t-service/contact-gsm
    ::t-service/contact-address
    ::t-service/name
    ::t-service/type
    ::modification/created
    ::t-service/published?
    ::t-service/transport-operator-id
    ::t-service/contact-phone

    ;; Information JOINed from other tables
    ::t-service/operator-name})

(defn- require-admin-user [route user]
  (when (not (:admin? user))
    (throw (SecurityException. "admin only"))))

(defn- admin-service [route {user :user
                             form-data :body :as req} db handler]
  (require-admin-user route (:user user))
  (http/transit-response
   (handler db user (http/transit-request form-data))))

(defn- list-users [db user query]
  (let [users (nap-users/list-users db {:email (str "%" query "%")
                                        :name (str "%" query "%")
                                        :transit-authority? nil})]
    (mapv
      (fn [{groups :groups :as user}]
        (if groups
          (assoc user :groups (cheshire/parse-string (.getValue groups) keyword))
          user))
      users)))

(defn- delete-user [db user query]
  (let [id (:id query)
        timestamp (java.util.Date.)]
    (log/info "Deleting user with id: " (pr-str id))
    (nap-users/delete-user! db {:id id :name timestamp})
    id))

(defn- user-operator-members [db user query]
  (let [user-id (:id query)
        members (into [] (nap-users/search-user-operators-and-members db {:user-id user-id}))]
    (mapv (fn [x]
            (update x :members #(db-util/PgArray->vec %))) members)))

(defn- published-search-param [query]
  (case (:published-type query)
    nil? nil
    :ALL nil
    :YES {::t-service/published? true}
    :NO {::t-service/published? false}
    nil))

(defn- list-services
  "Returns list of transport-services. Query parameters aren't mandatory, but it can be used to filter results."
  [db user query]
  (let [q (when (not (nil? (:query query)))
            {::t-service/name (op/ilike (str "%" (:query query) "%"))})
        search-params (merge q (published-search-param query))]
    (fetch db ::t-service/transport-service-search-result
         service-search-result-columns
         search-params
         {:specql.core/order-by ::t-service/name})))

(defn- list-operators
  "Returns list of transport-operators. Query parameters aren't mandatory, but it can be used to filter results."
  [db user query]
  (let [q (when (not (nil? (:query query)))
            {::t-operator/deleted? false
             ::t-operator/name (op/ilike (str "%" (:query query) "%"))})]
    (fetch db ::t-operator/transport-operator
           (specql/columns ::t-operator/transport-operator)
           q
           {:specql.core/order-by ::t-operator/name})))

(defn- list-services-by-operator [db user query]
  (let [q (when (not (nil? (:query query)))
            {::t-service/operator-name (op/ilike (str "%" (:query query) "%"))})
        search-params (merge q (published-search-param query))]
    (fetch db ::t-service/transport-service-search-result
         service-search-result-columns
         search-params
         {:specql.core/order-by ::t-service/operator-name})))

(defn- interfaces-array->vec [db-interfaces]
  (mapv (fn [d] (-> d
                    (update :format #(db-util/PgArray->vec %))
                    (update :data-content #(db-util/PgArray->vec %)))) db-interfaces))

(defn- list-interfaces [db user query]
  (let [service-name (:service-name query)
        operator-name (:operator-name query)
        import-error (:import-error query)
        db-error (:db-error query)
        interface-format (:interface-format query)
        interface-url (:interface-url query)]
  (interfaces-array->vec (search-interfaces db {:service-name     (when service-name (str "%" service-name "%"))
                                                :operator-name    (when operator-name (str "%" operator-name "%"))
                                                :interface-url    (when interface-url (str "%" interface-url "%"))
                                                :import-error     (when import-error true)
                                                :db-error         (when db-error true)
                                                :interface-format (when (and interface-format (not= :ALL interface-format)) (str/lower-case (name interface-format)))}))))

(defn distinct-by [f coll]
  (let [groups (group-by f coll)]
    (map #(first (groups %)) (distinct (map f coll)))))

(defn- business-id-report [db user query]
  (let [services (when
                   (or
                     (nil? (:business-id-filter query))
                     (= :ALL (:business-id-filter query))
                     (= :services (:business-id-filter query)))
                   (fetch-service-business-ids db))
        operators (when
                   (or
                     (nil? (:business-id-filter query))
                     (= :ALL (:business-id-filter query))
                     (= :operators (:business-id-filter query)))
                    (fetch-operator-business-ids db))

        report (concat services operators)]
    (sort-by :operator (distinct-by :business-id report))))

(defn- admin-delete-transport-service!
  "Allow admin to delete single transport service by id"
  [nap-config db user {id :id}]
  (let [deleted-service (transport/get-transport-service db id)
        return (transport/delete-transport-service! nap-config db user id)
        auditlog {::auditlog/event-type :delete-service
                  ::auditlog/event-attributes
                  [{::auditlog/name "transport-service-id", ::auditlog/value (str id)},
                   {::auditlog/name "transport-service-name", ::auditlog/value (::t-service/name deleted-service)}]
                  ::auditlog/event-timestamp (java.sql.Timestamp. (System/currentTimeMillis))
                  ::auditlog/created-by (get-in user [:user :id])}]
    (upsert! db ::auditlog/auditlog auditlog)
    return))

(defn delete-transport-operator!
  "Delete single transport operator by id. Delete services and all other datas as well."
  [db user transport-operator-id]
  (let [auditlog {::auditlog/event-type :delete-operator
                  ::auditlog/event-attributes
                                        [{::auditlog/name "transport-operator-id"
                                          ::auditlog/value (str transport-operator-id)}]
                  ::auditlog/event-timestamp (java.sql.Timestamp. (System/currentTimeMillis))
                  ::auditlog/created-by (get-in user [:user :id])}]
  (authorization/with-transport-operator-check
    db user transport-operator-id
    #(do
       (delete-transport-operator db {:operator-group-name (str "transport-operator-" transport-operator-id)})
       (upsert! db ::auditlog/auditlog auditlog)
       transport-operator-id))))

(defn- admin-routes [db http nap-config]
  (routes
    (POST "/admin/users" req (admin-service "users" req db #'list-users))

    (POST "/admin/delete-user" req (admin-service "delete-user" req db #'delete-user))

    (POST "/admin/user-operator-members" req (admin-service "user-operators" req db #'user-operator-members))

    (POST "/admin/transport-services" req (admin-service "services" req db #'list-services))

    (POST "/admin/transport-operators" req (admin-service "operators" req db #'list-operators))

    (POST "/admin/transport-services-by-operator" req (admin-service "services" req db #'list-services-by-operator))

    (POST "/admin/interfaces" req (admin-service "interfaces-by-operator" req db #'list-interfaces))

    (POST "/admin/transport-service/delete" req
      (admin-service "transport-service/delete" req db
                     (partial admin-delete-transport-service! nap-config)))

    (POST "/admin/business-id-report" req (admin-service "business-id-report" req db #'business-id-report))

    (POST "/admin/transport-operator/delete" {form-data :body user :user}
      (http/transit-response
        (delete-transport-operator! db user
                                    (:id (http/transit-request form-data)))))))

(defrecord Admin [nap-config]
  component/Lifecycle
  (start [{db :db http :http :as this}]
    (assoc this ::stop
           (http/publish! http (admin-routes db http nap-config))))

  (stop [{stop ::stop :as this}]
    (stop)
    (dissoc this ::stop)))
