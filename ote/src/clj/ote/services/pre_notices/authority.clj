(ns ote.services.pre-notices.authority
  "Pre notice services for transport authority users"
  (:require [ote.components.http :as http]
            [compojure.core :refer [routes POST GET]]
            [specql.core :as specql]
            [ote.db.transit :as transit]
            [ote.db.modification :as modification]
            [ote.authorization :as authorization]
            [ote.db.transport-operator :as t-operator]))


(defn list-published-notices [db user]
  (def last-user user) ;;FIXME: for repl debugging
  (specql/fetch db ::transit/pre-notice
                #{::transit/id
                  ::modification/created
                  ::transit/route-description
                  ::transit/pre-notice-type
                  [::t-operator/transport-operator #{::t-operator/name}]}
                {}
                {:specql.core/order-by ::modification/created
                 :specql.core/order-direction :desc}))

(defn fetch-notice [db id]
  (first (specql/fetch db ::transit/pre-notice
                       (specql/columns ::transit/pre-notice)
                       {::transit/id id})))

(defn add-comment [db user {:keys [id comment]}]
  (specql/insert! db ::transit/pre-notice-comment
                  (modification/with-modification-fields
                    {::transit/comment comment
                     ::transit/pre-notice-id id}
                    ::transit/id user)))

(defn authority-pre-notices-routes [db]
  (routes
   (GET "/pre-notices/authority-list" {user :user :as req}
        (authorization/with-transit-authority-check
          db user
          #(http/no-cache-transit-response (list-published-notices db user))))))
