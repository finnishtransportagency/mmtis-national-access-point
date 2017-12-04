(ns ote.nap.users
  "Integrate into CKAN user and group information via database queries."
  (:require [jeesql.core :refer [defqueries]]
            [ote.db.utils :as db-utils]
            [taoensso.timbre :as log]))

;; FIXME:
;; CKAN tables are owned by the ckan user and access needs to be granted to them
;; for the OTE application user. Automate this in flyway.
;;
;; GRANT ALL PRIVILEGES ON "group" TO ote;
;; GRANT ALL PRIVILEGES ON "user" TO ote;
;; GRANT ALL PRIVILEGES ON "member" TO ote;

(defqueries "ote/nap/users.sql")

(defn find-user [db username]
  (let [rows (map db-utils/underscore->structure
                  (fetch-user-by-username db {:username username}))]
    (when-not (empty? rows)
      (assoc (first rows)
             :groups (map :group rows)))))



(defn wrap-user-info
  "Ring middleware to fetch user info based on :user-id (username)."
  [db handler]
  ;; Cache fetched user info for 15 minutes
  (let [cached-user-info (atom (cache/ttl-cache-factory {} :ttl (* 15 60 1000)))
        find-user (partial find-user db)]
    (fn [{username :user-id :as req}]
      (if-let [user (when username
                      (find-user username))]
        (do (log/debug "User found: " (pr-str user))
            (handler (assoc req :user user)))
        (do (log/info "No user info, return 401")
            {:status 401
             :body "Unknown user"})))))
