(ns ote.services.login
  "Login related services"
  (:require [buddy.hashers :as hashers]
            [clojure.string :as str]
            [jeesql.core :refer [defqueries]]
            [com.stuartsierra.component :as component]
            [ote.components.http :as http]
            [compojure.core :refer [routes POST]]
            [ote.nap.cookie :as cookie]
            [ote.nap.users :as users]
            [ote.services.transport :as transport]
            [ote.components.service :refer [define-service-component]]
            [ote.db.tx :as tx :refer [with-transaction]]
            [specql.core :as specql]
            [ote.db.user :as user]
            [ote.time :as time]
            [ote.util.feature :as feature]
            [taoensso.timbre :as log]
            [ote.db.modification :as modification]
            [ote.localization :as localization :refer [tr]]
            [ote.email :as email]
            [ote.environment :as env])
  (:import (java.util UUID Base64 Base64$Decoder)))

(defqueries "ote/services/login.sql")

(defn base64->hex
  "Convert base64 encoded string to hex"
  [base64]
  (let [bytes (.decode (Base64/getDecoder)
                       ;; Passlib uses unconventional \. character instead of \+ in base64
                       (str/replace base64 #"\." "+"))]
    (str/join (map #(format "%02x" %) bytes))))

(defn hex->base64
  "Convert hex string to base64 encoded"
  [hex]
  (str/replace
   (->> hex
        (partition 2)
        (map #(Integer/parseInt (str/join %) 16))
        byte-array
        (.encode (Base64/getEncoder))
        (String.))
   #"\+" "."))

(defn passlib->buddy
  "Convert encrypted password from Python passlib format to buddy format."
  [hash]
  ;; Passlib format:
  ;; $pbkdf2-sha512$iterations$salt$hash
  ;;
  ;; buddy format:
  ;; pbkdf2+sha512$salt$iterations$hash
  ;;
  ;; passlib uses base64 encoding and buddy uses hex strings
  (let [[_ alg iterations salt password] (str/split hash #"\$")]
    (assert (= alg "pbkdf2-sha512"))
    (format "pbkdf2+sha512$%s$%s$%s"
            (base64->hex salt)
            iterations
            (base64->hex password))))

(defn buddy->passlib
  "Reverse of passlib->buddy."
  [hash]
  (let [[alg salt iterations password] (str/split hash #"\$")]
    (assert (= alg "pbkdf2+sha512"))
    (format "$pbkdf2-sha512$%s$%s$%s"
            iterations
            (hex->base64 salt)
            (hex->base64 password))))

(defn encrypt
  "Encrypt raw password. Returns buddy formatted password hash."
  [password]
  (hashers/derive password {:alg :pbkdf2+sha512}))

(defn with-auth-tkt [response auth-tkt-value domain]
  (update response :headers
          assoc "Set-Cookie" (if (nil? domain)
                               (str "auth_tkt=" auth-tkt-value "; Path=/; HttpOnly")
                               ;; Three cookies are required to match ckan cookie configuration
                               [(str "auth_tkt=" auth-tkt-value "; Path=/; HttpOnly; Secure")
                                (str "auth_tkt=" auth-tkt-value "; Path=/; HttpOnly"
                                     "; Domain=" domain "; Secure")
                                (str "auth_tkt=" auth-tkt-value "; Path=/; HttpOnly"
                                     "; Domain=." domain "; Secure")])))

(defn login [db auth-tkt-config
             {:keys [email password] :as credentials}]
  (let [login-info (first (fetch-login-info db {:email email}))]
    (if login-info
      (if (hashers/check password
                         (passlib->buddy (:password login-info)))
        ;; User was found and password is correct, return user info
        (with-auth-tkt
          (http/transit-response
           {:success? true
            :session-data
            (let [user (users/find-user db (:name login-info))]
              (transport/get-user-transport-operators-with-services db (:groups user) (:user user)))})
          (cookie/unparse "0.0.0.0" (:shared-secret auth-tkt-config)
                          {:digest-algorithm (:digest-algorithm auth-tkt-config)
                           :timestamp (java.util.Date.)
                           :user-id (:name login-info)
                           :user-data ""})
          (:domain auth-tkt-config))

        ;; No need to hide if the error was in the email or the password
        ;; the registration page can be used to check if an email has an account
        (http/transit-response {:error :incorrect-password}))
      (http/transit-response {:error :no-such-user}))))

(defn logout [auth-tkt-config]
  (with-auth-tkt (http/transit-response :ok) "" (:domain auth-tkt-config)))

(defn valid-registration? [{:keys [username name email password]}]
  (and (user/password-valid? password)
       (user/email-valid? email)
       (user/username-valid? username)
       (string? name) (not (str/blank? name))))

(defn valid-user-save? [{:keys [username name email]}]
  (and (user/email-valid? email)
       (user/username-valid? username)
       (string? name) (not (str/blank? name))))

(defn- register-user! [db auth-tkt-config {:keys [username name email password] :as form-data}]
  (if-not (valid-registration? form-data)
    ;; Check errors that should have been checked on the form
    {:success? false}
    (with-transaction db
      (let [username-taken? (username-exists? db {:username username})
            email-taken? (email-exists? db {:email email})]
        (if (or username-taken? email-taken?)
          ;; Username or email taken, return errors to form
          {:success? false
           :username-taken (when username-taken? username)
           :email-taken (when email-taken? email)}

          ;; Registration data is valid and username/email is not taken
          (do (specql/insert! db ::user/user
                              {::user/id (str (UUID/randomUUID))
                               ::user/name username
                               ::user/fullname name
                               ::user/email email
                               ::user/password (buddy->passlib (encrypt password))
                               ::user/created (java.util.Date.)
                               ::user/state "active"
                               ::user/sysadmin false
                               ::user/apikey (str (UUID/randomUUID))
                               ::user/activity_streams_email_notifications false})
              {:success? true}))))))

(defn register [db auth-tkt-config form-data]
  (feature/when-enabled :ote-register
    (let [result (register-user! db auth-tkt-config form-data )]
      (if (:success? result)
        ;; User created, log in immediately with the user info
        (login db auth-tkt-config form-data)

        ;; Registration failed, return errors
        (http/transit-response result)))))

(defn save-user! [db auth-tkt-config user form-data]
  (if-not (valid-user-save? form-data)
    {:success? false}
    (with-transaction db
      (let [username-taken? (and (not= (:username user) (:username form-data))
                                 (username-exists? db {:username (:username form-data)}))
            email-taken? (and (not= (:email user) (:email form-data))
                              (email-exists? db {:email (:email form-data)}))
            login-info (first (fetch-login-info db {:email (:email user)}))
            password-incorrect? (or (str/blank? (:current-password form-data))
                                    (not (hashers/check (:current-password form-data)
                                                        (passlib->buddy (:password login-info)))))]
        (if
          ;; Password incorrect, username or email taken => return errors to form
          (or username-taken? email-taken? password-incorrect?)
          {:success? false
           :username-taken (when username-taken? (:username form-data))
           :email-taken (when email-taken? (:email form-data))
           :password-incorrect? password-incorrect?}

          ;; Request is valid, do update
          (let [user (specql/update! db ::user/user
                                     (merge
                                      {::user/name (:username form-data)
                                       ::user/fullname (:name form-data)
                                       ::user/email (:email form-data)}

                                      ;; If new password provided, change it
                                      (when (not (str/blank? (:password form-data)))
                                        {::user/password (buddy->passlib (encrypt (:password form-data)))}))
                                     {::user/id (:id user)})]
            {:success? true}))))))

(defn save-user [db auth-tkt-config user form-data]

  (let [result (save-user! db auth-tkt-config user form-data)]
    (if (:success? result)
      ;; User updated, re-login immediately with updated info
      (login db auth-tkt-config
             {:email (:email form-data)
              :password (if (str/blank? (:password form-data))
                          (:current-password form-data)
                          (:password form-data))})

      ;; Failed, return errors to form
      (http/transit-response result))))

(defn request-password-reset! [db {:keys [email]}]
  (tx/with-transaction db
    (when-let [user (first (fetch-login-info db {:email email}))]
      (log/info "User " (:id user) " requested password reset.")
      {:user user
       :password-reset-request (specql/insert! db ::user/password-reset-request
                                               {::user/reset-key (java.util.UUID/randomUUID)
                                                ::modification/created-by (:id user)})})))

(defn send-password-reset-email [email {:keys [user password-reset-request]} language]
  (log/debug "Sending password reset email to " user)
  (localization/with-language language
    (email/send! email
                 {:to (:email user)
                  :subject (tr [:email-templates :password-reset :subject])
                  :body (tr [:email-templates :password-reset :body]
                            {:name (:name user)
                             :reset-link (str (env/base-url) "#reset-password?key="
                                              (::user/reset-key password-reset-request))})})))

(defn request-password-reset [db email form-data]
  (try
    (when-let [reset-request (request-password-reset! db form-data)]
      (send-password-reset-email email reset-request (:language form-data)))
    (catch Exception e
      (log/error e "Unable to send password reset email")))
  ;; Always send :ok back, no matter what
  :ok)


(define-service-component LoginService
  {:fields [auth-tkt-config]
   :dependencies {email :email}}

  ^:unauthenticated
  (POST "/login" {form-data :body}
        (#'login db auth-tkt-config
                 (http/transit-request form-data)))

  ^:unauthenticated
  (POST "/logout" []
        (logout auth-tkt-config))

  ^:unauthenticated
  (POST "/register" {form-data :body
                     user :user}
        (if user
          ;; Trying to register while logged in
          (http/transit-response {:success? false})
          (#'register db auth-tkt-config
                      (http/transit-request form-data))))

  (POST "/save-user" {form-data :body
                      user :user}
        (#'save-user db auth-tkt-config (:user user)
                     (http/transit-request form-data)))

  ^{:unauthenticated true :format :transit}
  (POST "/request-password-reset" {form-data :body}
        (request-password-reset db email (http/transit-request form-data))))
