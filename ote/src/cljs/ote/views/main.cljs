(ns ote.views.main
  "OTE-sovelluksen päänäkymä"
  (:require [reagent.core :as r]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.core :refer [color]]
            [cljs-react-material-ui.icons :as ic]
            [ote.ui.common :refer [linkify ckan-iframe-dialog]]
            [ote.views.transport-operator :as to]
            [ote.views.transport-operator-ytj :as to-ytj]
            [ote.views.front-page :as fp]
            [ote.app.controller.front-page :as fp-controller]
            [ote.views.transport-service :as t-service]
            [ote.views.footer :as footer]
            [ote.localization :refer [tr tr-key] :as localization]
            [ote.views.place-search :as place-search]
            [stylefy.core :as stylefy]
            [ote.style.base :as style-base]
            [ote.style.topnav :as style-topnav]
            [ote.views.theme :refer [theme]]
            [ote.views.service-search :as service-search]
            [ote.ui.form :as form]
            [ote.app.controller.flags :as flags]
            [ote.ui.common :as common]
            [ote.ui.form-fields :as form-fields]
            [ote.views.login :as login]
            [ote.views.user :as user]
            [ote.views.admin :as admin]
            [ote.views.admin.detected-changes :as admin-detected-changes]
            [ote.views.operators :as operators]
            [ote.views.email-notification-settings :as email-settings]
            [ote.views.route.route-list :as route-list]
            [ote.views.route :as route]
            [ote.views.gtfs-viewer :as gtfs-viewer]
            [ote.views.pre-notices.pre-notice :as notice]
            [ote.views.pre-notices.listing :as pre-notices-listing]
            [ote.views.pre-notices.authority-listing :as pre-notices-authority-listing]
            [ote.views.transit-visualization :as transit-visualization]
            [ote.views.transit-changes :as transit-changes]
            [ote.views.register :as register]
            [ote.ui.common :as common-ui]
            [ote.ui.main-header :refer [top-nav] :as main-header]))

(def pages {:own-services #{:own-services :transport-service :new-service
                            :edit-service :transport-operator :organizations}
            :services #{:services}
            :operators #{:operators}})

(defn document-title [page]
  (set! (.-title js/document)
        (case page
          :services (tr [:document-title :services])
          :operators (tr [:document-title :operators])
          :own-services (tr [:document-title :own-services])
          :admin (tr [:document-title :admin])

          ;; default title for other pages
          (tr [:document-title :default])))

  ;; Render as an empty span
  [:span])


(defn- scroll-to-page
  "Invisible component that scrolls to the top of the window whenever it is mounted."
  [page]
  (r/create-class
   {:component-did-mount #(.scrollTo js/window 0 0)
    :reagent-render
    (fn [page]
      [:span])}))

(def wide-pages #{:transit-visualization :transit-changes :authority-pre-notices :admin :services})

(defn ote-application
  "OTE application main view"
  [e! app]
  (let [is-scrolled? (r/atom false)]
    (fn [e! {loaded? :transport-operator-data-loaded? :as app}]
      (let [desktop? (> (:width app) style-base/mobile-width-px)
            wide? (boolean (wide-pages (:page app)))]
        [:div {:style (stylefy/use-style style-base/body)}
         [theme e! app
          (if (nil? app)
            [common/loading-spinner]
            [:div.ote-sovellus
             [top-nav e! app is-scrolled? desktop?]

             [:span
              (if (not loaded?)
                [common/loading-spinner]
                [(if wide? :div :div.wrapper)
                 (if wide?
                   {}
                   (stylefy/use-style (merge
                                       {:transition "margin-top 300ms ease"}
                                       (if (or (not desktop?) @is-scrolled?)
                                         {:margin-top "56px"}))))
                 [:div (cond
                         (= :front-page (:page app))
                         {:class "container-fluid"}

                         wide?
                         {}

                         :default
                         {:style {:padding-bottom "20px"}
                          :class "container"})
                  [document-title (:page app)]

                  ;; Ensure that a new scroll-to-page component is created when page changes
                  ^{:key (name (:page app))}
                  [scroll-to-page (:page app)]

                  (case (:page app)
                    :login [login/login e! (:login app)]
                    :reset-password [login/reset-password e! app]
                    :register [register/register e! (:register app) (:user app)]
                    :user [user/user e! (:user app)]
                    :front-page [fp/front-page e! app]
                    :own-services [fp/own-services e! app]
                    :transport-service [t-service/select-service-type e! app]
                    :transport-operator (if (flags/enabled? :open-ytj-integration) [to-ytj/operator-ytj e! app] [to/operator e! app]) ; TODO: ytj replaces old solution when ready

                    ;; Routes for the service form, one for editing an existing one by id
                    ;; and another when creating a new service
                    :edit-service [t-service/edit-service-by-id e! app]
                    :new-service [t-service/create-new-service e! app]

                    ;; service catalog page
                    :services [service-search/service-search e! app]
                    ;; show single service GeoJSON
                    :service [service-search/service-geojson e! (:service-search app)]

                    :admin [admin/admin-panel e! app]
                    :admin-detected-changes [admin-detected-changes/configure-detected-changes e! app]

                    :operators [operators/operators e! app]

                    :email-settings [email-settings/email-notification-settings e! app]

                    :routes [route-list/routes e! app]
                    :new-route [route/new-route e! app]
                    :edit-route [route/edit-route-by-id e! app]

                    ;; 60days pre notice views
                    :new-notice [notice/new-pre-notice e! app]
                    :edit-pre-notice [notice/edit-pre-notice-by-id e! app]
                    :pre-notices [pre-notices-listing/pre-notices e! app]

                    :view-gtfs [gtfs-viewer/gtfs-viewer e! app]
                    :transit-visualization [transit-visualization/transit-visualization e! (:transit-visualization app)]

                    (:transit-changes :authority-pre-notices)
                    [transit-changes/transit-changes e! app]

                    [:div (tr [:common-texts :no-such-page]) (pr-str (:page app))])]])]
             [footer/footer e!]])]]))))
