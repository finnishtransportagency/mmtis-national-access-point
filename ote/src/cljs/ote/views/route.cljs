(ns ote.views.route
  "Main views for route based traffic views"
  (:require [reagent.core :as r]
            [ote.mui-wrapper.reagent :as ui]
            [ote.app.controller.route.route-wizard :as rw]
            [ote.ui.buttons :as buttons]
            [ote.localization :refer [tr tr-key]]
            [ote.db.transit :as transit]

            ;; Subviews for wizard
            [ote.views.route.basic-info :as route-basic-info]
            [ote.views.route.stop-sequence :as route-stop-sequence]
            [ote.views.route.trips :as route-trips]
            [ote.style.base :as style-base]
            [ote.app.controller.front-page :as fp-controller]

            [ote.ui.common :as common]))

(defn route-save [e! {route :route :as app}]
  [ui/raised-button {:primary true
                     :on-click #(e! (rw/->SaveAsGTFS))}
   (tr [:buttons :save-as-gtfs])])

(defn- route-components [e! app]
  [:div
   [route-basic-info/basic-info e! app]
   [route-stop-sequence/stop-sequence e! app]
   [route-trips/trips e! app]])

(defn form-container [e! app]
  [:div
   [ote.ui.list-header/header app (tr [:common-texts :navigation-route])]
   [buttons/open-link "https://s3.eu-central-1.amazonaws.com/ote-assets/sea-route-user-guide.pdf" (tr [:route-list-page :link-to-help-pdf])]
   [route-components e! app]
   (when (not (rw/valid-route? (:route app)))
     [ui/card {:style {:margin "1em 0em 1em 0em"}}
      [ui/card-text {:style {:color "#be0000" :padding-bottom "0.6em"}} (tr [:route-wizard-page :publish-missing-required])]])])

(defn new-route [e! app]
  (when-not (nil? (:route app))
    [:span
     [form-container e! app]
     [:div.col-xs-12.col-sm-6.col-md-6 {:style {:padding-top "20px"}}
      [buttons/save {:disabled (not (rw/valid-route? (:route app)))
                     :on-click #(do
                                  (.preventDefault %)
                                  (e! (rw/->SaveToDb true)))}
       (tr [:buttons :save-and-publish])]
      [buttons/save {:disabled (not (rw/valid-route-name? (get-in app [:route ::transit/name])))
                     :on-click #(do
                                  (.preventDefault %)
                                  (e! (rw/->SaveToDb false)))}
       (tr [:buttons :save-as-draft])]
      [buttons/cancel {:on-click #(do
                                    (.preventDefault %)
                                    (e! (rw/->CancelRoute)))}
       (tr [:buttons :cancel])]]]))

(defn edit-route-by-id [e! {route :route :as app}]
  (if (or (nil? route) (:loading? route))
    [common/loading-spinner]
    [:span
     [form-container e! app]
     [:div.col-xs-12.col-sm-6.col-md-6 {:style {:padding-top "20px"}}
      (if (get-in app [:route ::transit/published?])
        [:span
         [buttons/save {:disabled (not (rw/valid-route? (:route app)))
                        :on-click #(do
                                     (.preventDefault %)
                                     (e! (rw/->SaveToDb true)))}
          (tr [:buttons :save])]
         [buttons/save {:disabled (not (rw/valid-route-name? (get-in app [:route ::transit/name])))
                        :on-click #(do
                                     (.preventDefault %)
                                     (e! (rw/->SaveToDb false)))}
          (tr [:buttons :back-to-draft])]]
        [:span
         [buttons/save {:disabled (not (rw/valid-route? (:route app)))
                        :on-click #(do
                                     (.preventDefault %)
                                     (e! (rw/->SaveToDb true)))}
          (tr [:buttons :save-and-publish])]
         [buttons/save {:disabled (not (rw/valid-name (:route app)))
                        :on-click #(do
                                     (.preventDefault %)
                                     (e! (rw/->SaveToDb false)))}
          (tr [:buttons :save-as-draft])]])
      [buttons/cancel {:on-click #(do
                                    (.preventDefault %)
                                    (e! (rw/->CancelRoute)))}
       (tr [:buttons :cancel])]]]))
