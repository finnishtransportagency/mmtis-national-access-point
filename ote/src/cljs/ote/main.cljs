(ns ote.main
  "OTE app startup"
  (:require [reagent.core :as r]
            [cljsjs.material-ui]
            [ote.mui-wrapper.core :refer [get-mui-theme color]]
            [ote.mui-wrapper.reagent :as ui]
            [ote.mui-wrapper.icons :as ic]
            [cljsjs.react-leaflet]
            [cljsjs.leaflet-draw]
            [cljsjs.nprogress]
            [tuck.core :as tuck]
            [ote.app.state :as state]
            [ote.views.main :as main]
            [ote.views.ckan-service-viewer :as ckan-service-viewer]
            [ote.views.ckan-org-viewer :as ckan-org-viewer]
            [ote.localization :as localization]
            [ote.app.routes :as routes]
            [ote.app.controller.front-page :as fp-controller]
            [stylefy.core :as stylefy]
            [ote.communication :as comm]
            [goog.net.Cookies]
            [ote.app.controller.login :as login]
            [ote.transit :as transit]))

(defn language []
  (.get (goog.net.Cookies. js/document)
        "finap_lang" "fi"))

(defn init-app [session-data]
  (if (nil? session-data)
    (swap! state/app login/unauthenticated)
    (swap! state/app login/update-transport-operator-data session-data))
  (stylefy/init)
  (routes/start! fp-controller/->GoToUrl)
  (state/windowresize-handler nil) ;; Calculate window width
  (r/render-component [tuck/tuck state/app main/ote-application]
                      (.getElementById js/document "oteapp")))

(defn- load-embedded-user-info []
  (let [elt (.getElementById js/document "ote-user-info")
        user-info (transit/transit->clj (.-innerText elt))]
    (.removeChild (.-parentNode elt) elt)
    user-info))

(defn ^:export main []
  (localization/load-embedded-translations!)
  (init-app (load-embedded-user-info)))

(defn ^:export reload-hook []
  (r/force-update-all))

(defn ^:export geojson_view []
  (comm/set-base-url! "/ote/")
  (localization/load-language!
   (language)
   (fn [lang _]
     (reset! localization/selected-language lang)
     (stylefy/init)
     (r/render-component [tuck/tuck state/viewer ckan-service-viewer/viewer]
                         (.getElementById js/document "nap_viewer")))))

(defn ^:export ckan_org_view []
  (comm/set-base-url! "/ote/")
  (localization/load-language!
    (language)
    (fn [lang _]
      (reset! localization/selected-language lang)
      (stylefy/init)
      (r/render-component [tuck/tuck state/app ckan-org-viewer/viewer]
                          (.getElementById js/document "nap_viewer")))))
