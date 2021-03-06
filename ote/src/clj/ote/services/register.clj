(ns ote.services.register
  (:require [compojure.core :refer [routes GET POST DELETE]]
            [com.stuartsierra.component :as component]
            [ote.db.user :as user]
            [ote.components.http :as http]
            [specql.core :as specql]
            [ote.util.encrypt :as encrypt]
            [ote.services.users :as user-service]
            [ote.db.tx :as tx :refer [with-transaction]]
            [jeesql.core :refer [defqueries]]
            [ote.util.feature :as feature]
            [clojure.string :as str]
            [ote.services.transport-operator :as transport-operator]
            [taoensso.timbre :as log])
  (:import (java.util UUID)))

(defqueries "ote/services/user_service.sql")

(defn- valid-registration? [{:keys [name email password]}]
  (and (user/password-valid? password)
       (user/email-valid? email)
       (string? name) (not (str/blank? name))))

(defn- register-user! [db auth-tkt-config {:keys [name email password token acceped-tos?] :as form-data}]
  (if-not (valid-registration? form-data)
    ;; Check errors that should have been checked on the form
    {:success? false}
    (let [email-taken? (email-exists? db {:email email})
          group-info (when token
                       (first (fetch-operator-info db {:token token})))
          _ (println "register " (pr-str form-data))]
      (if email-taken?
        ;; email taken, return errors to form
        {:success? false
         :email-taken (when email-taken? email)}
        ;; Registration data is valid and email is not taken
        (do
          (let [user-id (str (UUID/randomUUID))
                new-user (specql/insert! db ::user/user
                                         (merge
                                           {::user/id user-id
                                            ::user/name user-id ;; Username not used anymore, use internal row id as placeholder just in case
                                            ::user/fullname name
                                            ::user/email email
                                            ::user/password (encrypt/buddy->passlib (encrypt/encrypt password))
                                            ::user/created (java.util.Date.)
                                            ::user/state "active"
                                            ::user/sysadmin false
                                            ::user/email-confirmed? false
                                            ::user/apikey (str (UUID/randomUUID))
                                            ::user/activity_streams_email_notifications false}
                                           (when (feature/feature-enabled? :terms-of-service)
                                             {::user/accepted-tos? acceped-tos?
                                              ::user/seen-tos? acceped-tos?})))]
            (when (and token group-info)                    ;; If the user doesn't have a token or group-info they can register, but aren't added to any group
              (transport-operator/create-member! db (::user/id new-user) (:ckan-group-id group-info))
              (specql/delete! db ::user/user-token
                              {::user/token token})
              (log/info "New user (" email ") registered with token from " (:name group-info))))
          {:success? true})))))

(defn- register-response [db email auth-tkt-config form-data]
  (with-transaction db
                    (feature/when-enabled :ote-register
                                          (let [result (register-user! db auth-tkt-config form-data)
                                                email-confirmation-token (str (UUID/randomUUID))
                                                user-email (:email form-data)
                                                language (:language form-data)]
                                            (if (:success? result)
                                              ;; User created, log in immediately with the user info
                                              (do (user-service/create-confirmation-token! db (:email form-data) email-confirmation-token)
                                                  (user-service/send-email-verification email user-email language email-confirmation-token)
                                                  (http/transit-response result 201))
                                              ;; registeration failed send errors
                                              (http/transit-response result 400))))))

(defn- accept-tos [db user form-data]
  (if (= (:email (:user user)) (:user-email form-data))
    (do
      (specql/update! db ::user/user
                      {::user/seen-tos? true}
                      {::user/id (:id (:user user))})
      (http/transit-response "OK" 200))
    (http/transit-response "ERROR!" 401)))

(defn- register-routes
  "Unauthenticated routes"
  [db email config]
  (let [auth-tkt-config (get-in config [:http :auth-tkt])]
    (routes

      (POST "/register" {form-data :body
                         user :user}
        (if user
          ;; Trying to register while logged in
          (http/transit-response {:success? false
                                  :message :already-logged-in} 400)
          (#'register-response db email auth-tkt-config
            (http/transit-request form-data))))

      (POST "/register/tos" {form-data :body
                             user :user}
        (accept-tos db user (http/transit-request form-data))))))

(defrecord Register [config]
  component/Lifecycle
  (start [{:keys [db email http] :as this}]
    (assoc
      this ::stop
           [(http/publish! http {:authenticated? false} (register-routes db email config))]))
  (stop [{stop ::stop :as this}]
    (doseq [s stop]
      (s))
    (dissoc this ::stop)))
