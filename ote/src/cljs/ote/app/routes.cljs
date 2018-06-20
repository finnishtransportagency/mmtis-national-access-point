(ns ote.app.routes
  "Routes for the frontend app."
  (:require [bide.core :as r]
            [ote.app.state :as state]
            [tuck.core :as tuck]
            [ote.util.fn :refer [flip]]))

(def ote-router
  (r/router
   [["/" :front-page]
    ["/own-services" :own-services]
    ["/transport-operator/:id" :transport-operator]
    ["/passenger-transportation" :passenger-transportation]
    ["/terminal" :terminal]
    ["/rentals" :rentals]
    ["/parking" :parking]
    ["/new-service/:operator-id/:sub-type" :new-service]
    ["/new-service/:operator-id" :transport-service]
    ["/edit-service/:id" :edit-service]
    ["/services" :services]
    ["/services/:operator" :services]
    ["/operators" :operators]

    ;; Route based traffic
    ["/routes" :routes]
    ["/route/new" :new-route]
    ["/edit-route/:id" :edit-route]

    ["/routes/view-gtfs" :view-gtfs]
    ["/transit-visualization/:operator-id" :transit-visualization]
    ["/transit-changes" :transit-changes]

    ;; 60 day pre-notice
    ["/pre-notices" :pre-notices]
    ["/pre-notice/new" :new-notice]
    ["/pre-notice/edit/:id" :edit-pre-notice]
    ["/authority-pre-notices" :authority-pre-notices]
    ["/authority-pre-notices/:id" :authority-pre-notices]

    ["/admin" :admin]
    ["/admin/:admin-page" :admin]]))

;; Add pages that needs authenticating to this list
(def auth-required #{:own-services :transport-service :transport-operator :edit-service :new-service :admin :routes
                     :new-route :edit-route :new-notice :edit-pre-notice :pre-notices :authority-pre-notices
                     :transit-visualization :transit-changes})

(defmulti on-navigate-event
  "Determine event(s) to be run when user navigates to a given route.
  Returns a single Tuck event or a vector of Tuck events to be applied
  to the app state. Takes a map containing the navigation data as parameter.
  Route parameters are under the :params key."
  :page)

(defmethod on-navigate-event :default [_] nil)

(defmulti on-leave-event
          "Determine event(s) to be run when user navigates away from the give route.
          Return values identical to on-navigate-event."
          :page)

(defmethod on-leave-event :default [_] nil)


(defn requires-authentication? [app]
  (and
       (nil? (get-in app [:user :username]))
       (contains? auth-required (:page app))))

(defn- send-startup-events [event]
  (let [e! (fn e! [event]
             (binding [tuck/*current-send-function* e!]
               (swap! state/app (flip tuck/process-event) event)))]
    (if (vector? event)
      ;; Received multiple events, apply them all
      (doseq [event event
              :when event]
        (e! event))
      ;; Apply single event
      (e! event))))

(declare navigate!)

(defn- on-navigate [go-to-url-event name params query]
  (swap! state/app
         (fn [{:keys [before-unload-message navigation-prompt-open? url] :as app}]
           (if (and before-unload-message (not navigation-prompt-open?))
             (let [new-url js/window.location.href]
               ;; push previous URL to the history (the one we want to stay on)
               (.pushState js/window.history #js {} js/document.title
                           url)
               ;; Open confirmation dialog and only go to new page
               ;; if the user confirms navigation.
               (assoc app
                 :navigation-prompt-open? true
                 :navigation-confirm (go-to-url-event new-url)))

             (if (not= (:url app) js/window.location.href)
               (let [navigation-data {:page name
                                      :params params
                                      :query query
                                      :url js/window.location.href}
                     event-leave (on-leave-event {:page (:page app)})
                     event-leave (if (vector? event-leave) event-leave [event-leave])
                     event-to (on-navigate-event navigation-data)
                     event-to (if (vector? event-to) event-to [event-to])
                     orig-app app
                     app (merge app navigation-data)]

                 (if (requires-authentication? app)
                   (do (navigate! :front-page)
                       (assoc orig-app
                         :login {:show? true
                                 :navigate-to navigation-data}))
                   (do
                     ;; Send startup events (if any) immediately after returning from this swap
                     (when (or event-leave event-to)
                       (.setTimeout js/window #(send-startup-events (vec (concat event-leave event-to))) 0))
                     app)))
               app)))))

(defn start! [go-to-url-event]
  (r/start! ote-router {:default :front-page
                        :on-navigate (partial on-navigate go-to-url-event)}))

(defn navigate!
  "Navigate to given page with optional route and query parameters.
  The navigation is done by setting a timeout and can be called from
  tuck process-event."
  ([page] (navigate! page nil nil))
  ([page params] (navigate! page params nil))
  ([page params query]
   (.setTimeout js/window
                #(r/navigate! ote-router page params query) 0)))
