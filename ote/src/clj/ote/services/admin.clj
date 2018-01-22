(ns ote.services.admin
  "Backend services for admin functionality."
  (:require [ote.components.http :as http]
            [com.stuartsierra.component :as component]
            [specql.core :refer [fetch update! insert! upsert! delete!] :as specql]
            [taoensso.timbre :as log]
            [compojure.core :refer [routes GET POST DELETE]]
            [ote.nap.users :as nap-users]
            [specql.core :as specql]
            [ote.db.auditlog :as auditlog]
            [ote.services.transport :as transport]))

(defn- require-admin-user [route user]
  (when (not (:admin? user))
    (throw (SecurityException. "admin only"))))

(defn- admin-service [route {user :user
                             form-data :body :as req} db handler]
  (require-admin-user route (:user user))
  (http/transit-response
   (handler db user (http/transit-request form-data))))

(defn- list-users [db user query]
  (nap-users/list-users db {:email (str "%" query "%")
                            :name (str "%" query "%")}))

(defn- admin-delete-transport-service!
  "Allow admin delete single transport service by id"
  [nap-config db user id]
  (let [str-id (str id)
        return nil ;(transport/delete-transport-service! nap-config db user id)
        my-map {::auditlog/event-type :delete-service
                ::auditlog/event-attributes
                                      [{::auditlog/name "transport-service-id", ::auditlog/value str-id}]
                ::auditlog/event-timestamp (java.sql.Timestamp. (System/currentTimeMillis))
                ::auditlog/created-by (get-in user [:user :id])}
        ]
    (println "my-map " my-map)
    (upsert! db ::auditlog/auditlog my-map)
    return))



(defn- admin-routes [db http nap-config]
  (routes
   (POST "/admin/users" req (admin-service "users" req db #'list-users))
   (GET "/admin/transport-service/delete/:id" {{id :id} :params
                                         user :user}
        (admin-delete-transport-service! nap-config db user (Long/parseLong id)))
   ))

(defrecord Admin [nap-config]
  component/Lifecycle
  (start [{db :db http :http :as this}]
    (assoc this ::stop
           (http/publish! http (admin-routes db http nap-config))))

  (stop [{stop ::stop :as this}]
    (stop)
    (dissoc this ::stop)))
