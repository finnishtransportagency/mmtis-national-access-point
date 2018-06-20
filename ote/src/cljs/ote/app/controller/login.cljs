(ns ote.app.controller.login
  (:require [tuck.core :as tuck]
            [ote.communication :as comm]
            [ote.app.routes :as routes]
            [ote.db.transport-operator :as t-operator]
            [ote.localization :refer [tr]]))

(defrecord ShowLoginDialog [])
(defrecord UpdateLoginCredentials [credentials])
(defrecord Login [])
(defrecord LoginResponse [response])
(defrecord LoginFailed [response])
(defrecord LoginCancel [])
(defrecord Logout [])
(defrecord LogoutResponse [response])
(defrecord LogoutFailed [response])

(defn unauthenticated
  "Init session without user."
  [app]
  (assoc app
         :transport-operator-data-loaded? true
         :user nil))

(defn update-transport-operator-data
  [{:keys [page ckan-organization-id transport-operator] :as app}
   {:keys [user transport-operators] :as response}]

  (let [app (assoc app
                   :transport-operator-data-loaded? true
                   :user user)]
    (if (and (empty? transport-operators)
             (not= :services page))
      ;; If page is :transport-operator and user has no operators, start creating a new one
      (do
        (if (= (:page app) :transport-operator)
          (assoc app
                 :transport-operator {:new? true}
                 :services-changed? true)
          app))

        ;; Get services from response.
        ;; Use selected operator if possible, if not, use the first one from the response.
        ;; Selected can either be previously selected or ckan-organization-id (CKAN edit view)
        (let [selected-operator (or
                                 (some #(when (or (= (::t-operator/id transport-operator)
                                                     (get-in % [:transport-operator ::t-operator/id]))
                                                  (= ckan-organization-id
                                                     (get-in % [:transport-operator ::t-operator/ckan-group-id])))
                                          %)
                                       transport-operators)
                                 (first transport-operators))]

          (assoc app
                 :transport-operators-with-services transport-operators
                 :transport-operator (:transport-operator selected-operator)
                 :transport-service-vector (:transport-service-vector selected-operator))))))

(defn- login-navigate->page [app response]
  (let [authority? (get-in response [:session-data :user :transit-authority?])
        operators-count (count (get-in response [:session-data :transport-operators]))
        navigate-to (get-in app [:login :navigate-to])
        new-page (cond
                   (not (empty? navigate-to)) (:page navigate-to)
                   (and authority? (= 0 operators-count)) :authority-pre-notices
                   :else :own-services)]
    (do
      (routes/navigate! new-page (:params navigate-to))
      (-> app
          (dissoc :login)
          (update-transport-operator-data (:session-data response))
          (assoc :flash-message (tr [:common-texts :logged-in])
                 :page new-page)))))

(extend-protocol tuck/Event

  ShowLoginDialog
  (process-event [_ app]
    (assoc app :login {:show? true}))

  UpdateLoginCredentials
  (process-event [{credentials :credentials} app]
    (update-in app [:login :credentials] merge credentials))

  Login
  (process-event [_ app]
    (comm/post! "login"
                (select-keys (get-in app [:login :credentials]) #{:email :password})
                {:on-success (tuck/send-async! ->LoginResponse)
                 :on-failure (tuck/send-async! ->LoginFailed)})
    (update app :login
            #(-> %
                 (dissoc :failed? :error)
                 (assoc :in-progress? true))))

  LoginResponse
  (process-event [{response :response} app]
      (if (:success? response)
        (login-navigate->page app response)
        (update app :login assoc
                :failed? true
                :in-progress? false
                :error (:error response))))

  LoginFailed
  (process-event [{response :response} app]
    ;; The login request itself failed
    (assoc-in app [:login :error] response))

  LoginCancel
  (process-event [_ app]
    (dissoc app :login))

  Logout
  (process-event [_ app]
    (comm/post! "logout" nil
                {:on-success (tuck/send-async! ->LogoutResponse)
                 :on-failure (tuck/send-async! ->LogoutFailed) })
    app)

  LogoutResponse
  (process-event [_ app]
    (routes/navigate! :front-page)
    (-> app
        (dissoc :user
                :transport-operator
                :transport-operators-with-services
                :transport-service-vector
                :route-list
                :routes-vector)
        (assoc :flash-message (tr [:login :logged-out]))))

  LogoutFailed
  (process-event [_ app]
    (assoc app :flash-message (tr [:common-texts :server-error]))))
