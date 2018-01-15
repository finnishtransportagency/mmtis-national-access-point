(ns ote.app.controller.front-page
  (:require [tuck.core :as tuck]
            [ote.communication :as comm]
            [ote.db.transport-operator :as t-operator]
            [ote.app.routes :as routes]
            [ote.app.controller.login :as login]))


;;Change page event. Give parameter in key format e.g: :front-page, :transport-operator, :transport-service
(defrecord ChangePage [given-page params])
(defrecord GoToUrl [url])
(defrecord OpenNewTab [url])
(defrecord StayOnPage [])
(defrecord OpenUserMenu [])
(defrecord OpenHeader [])
(defrecord Logout [])
(defrecord SetLanguage [lang])

(defrecord GetTransportOperator [])
(defrecord TransportOperatorResponse [response])
(defrecord TransportOperatorFailed [response])
(defrecord EnsureTransportOperator [])

(defrecord GetTransportOperatorData [])
(defrecord TransportOperatorDataResponse [response])
(defrecord TransportOperatorDataFailed [error])

(defrecord ClearFlashMessage [])



(defn navigate [event {:keys [before-unload-message navigation-prompt-open?] :as app} navigate-fn]
  (if (and before-unload-message (not navigation-prompt-open?))
    (assoc app
           :navigation-prompt-open? true
           :navigation-confirm event)
    (navigate-fn (dissoc app
                         :navigation-prompt-open?
                         :before-unload-message
                         :navigation-confirm))))

(defn get-transport-operator-data [app]
  (if (:transport-operator-data-loaded? app true)
     (do
       (comm/post! "transport-operator/data" {}
                   {:on-success (tuck/send-async! ->TransportOperatorDataResponse)
                    :on-failure (tuck/send-async! ->TransportOperatorDataFailed)})
       (assoc app
              :transport-operator-data-loaded? false
              :services-changed? false))
     app))

(extend-protocol tuck/Event

  ChangePage
  (process-event [{given-page :given-page params :params :as e} app]
    (navigate e app (fn [app]
                      (do
                        (routes/navigate! given-page params)
                        (assoc app
                          :page given-page
                          :params params)))))

  GoToUrl
  (process-event [{url :url :as e} app]
    (navigate e app (fn [app]
      (.setTimeout js/window #(set! (.-location js/window) url) 0)
      app)))

  OpenNewTab
  (process-event [{url :url :as e} app]
    (let [window-open (.open js/window)]
         (set! (.-opener window-open) nil)
         (set! (.-location window-open) url)
    app))

  StayOnPage
  (process-event [_ app]
    (dissoc app :navigation-prompt-open?))

  OpenUserMenu
  (process-event [_ app]
    (assoc-in app [:ote-service-flags :user-menu-open] true) app)

  OpenHeader
  (process-event [_ app]
    (assoc-in app [:ote-service-flags :header-open]
              (if (get-in app [:ote-service-flags :header-open]) false true)))

  Logout
  (process-event [_ app]
    (assoc-in app [:ote-service-flags :user-menu-open] true)
    app)

  EnsureTransportOperator
  (process-event [_ app]
     (if (:services-changed? app)
      (get-transport-operator-data app)
      app))

  GetTransportOperator
  (process-event [_ app]
      (comm/post! "transport-operator/group" {} {:on-success (tuck/send-async! ->TransportOperatorResponse)
                                                 :on-failure (tuck/send-async! ->TransportOperatorFailed)})
      app)

  TransportOperatorResponse
  (process-event [{response :response} app]
    (assoc app :transport-operator response))

  TransportOperatorFailed
  (process-event [{response :response} app]
    ;; FIXME: figure out what the error is and add it to app state
    ;; e.g. unauhtorized should shown unauthorized page and ask user to log in.
    (.log js/console " Error: " (clj->js response))
    app)

  GetTransportOperatorData
  ;; FIXME: this should be called something else, like SessionInit (the route as well)
  (process-event [_ app]
    (get-transport-operator-data app))

  TransportOperatorDataFailed
  (process-event [{error :error} app]
    ;; 401 is ok (means user is not logged in
    (when (not= 401 (:status error))
      (.log js/console "Failed to fetch transport operator data: " (pr-str error)))
    (assoc app
           :transport-operator-data-loaded? true
           :user nil))

  TransportOperatorDataResponse
  (process-event [{response :response} app]
    (login/update-transport-operator-data app response))

  SetLanguage
  (process-event [{lang :lang} app]
    (set! (.-cookie js/document) (str "finap_lang=" lang ";path=/"))
    (.reload js/window.location))

  ClearFlashMessage
  (process-event [_ app]
    (dissoc app :flash-message :flash-message-error)))
