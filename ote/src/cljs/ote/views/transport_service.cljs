(ns ote.views.transport-service
  "Transport service related functionality"
  (:require [reagent.core :as reagent]
            [ote.mui-wrapper.reagent :as ui]
            [ote.mui-wrapper.icons :as ic]
            [ote.ui.form :as form]
            [ote.ui.form-groups :as form-groups]
            [ote.app.controller.transport-service :as ts]
            [ote.app.controller.transport-operator :as to]
            [ote.db.transport-service :as t-service]
            [ote.db.transport-operator :as t-operator]
            [ote.db.common :as common]
            [ote.localization :refer [tr tr-key]]
            [tuck.core :as tuck]
            [ote.communication :as comm]
            [ote.views.passenger-transportation :as pt]
            [ote.views.terminal :as terminal]
            [ote.app.routes :as routes]
            [ote.views.parking :as parking]
            [ote.views.rental :as rental]
            [ote.ui.form-fields :as form-fields]
            [ote.ui.common :as ui-common]
            [stylefy.core :as stylefy]
            [ote.style.form :as style-form]
            [reagent.core :as r]))

(def modified-transport-service-types
  ;; Create order for service type selection dropdown
  [:taxi
   :request
   :schedule
   :terminal
   :rentals
   :parking])

(defn select-service-type [e! {:keys [transport-operator transport-service] :as state}]
  (let [multiple-operators (if (second (:transport-operators-with-services state)) true false)
        disabled? (or (nil? (::t-service/sub-type transport-service))
                      (nil? (::t-operator/id transport-operator)))]
  [:div.row
   [:div {:class "col-sx-12 col-md-12"}
    [:div
     [:h1 (tr [:select-service-type-page :title-required-data])]]
    [:div.row
     [:p (tr [:select-service-type-page :transport-service-type-selection-help-text])]
     [:br]
     [:p (tr [:select-service-type-page :transport-service-type-brokerage-help-text])]]

    [:div.row {:style {:padding-top "20px"}}
     [:p {:style {:font-style "italic"}}
      (tr [:select-service-type-page :transport-service-type-selection-help-example])]]
    [:div.row {:style {:padding-top "20px"}}

        [:div
          [:div {:class "col-sx-12 col-sm-4 col-md-4"}
          [form-fields/field

           {:label (tr [:field-labels :transport-service-type-subtype])
             :name        ::t-service/sub-type
             :type        :selection
             :update!      #(e! (ts/->SelectServiceType %))
             :show-option (tr-key [:enums ::t-service/sub-type])
             :options     modified-transport-service-types
             :auto-width? true}

           (::t-service/sub-type transport-service)]]

           [:div {:class "col-sx-12 col-sm-4 col-md-4"}
             [form-fields/field
              {:label (tr [:field-labels :select-transport-operator])
               :name        :select-transport-operator
               :type        :selection
               :update!     #(e! (to/->SelectOperator %))
               :show-option ::t-operator/name
               :options     (mapv :transport-operator (:transport-operators-with-services state))
               :auto-width? true}

              transport-operator]]]]
    [:div.row
     [:div {:class "col-sx-12 col-sm-4 col-md-4"}
      [ui/raised-button {:style {:margin-top "20px"}
                         :label    (tr [:buttons :next])
                         :on-click #(e! (ts/->NavigateToNewService))
                         :primary  true
                         :disabled disabled?}]]]]]))

(defn edit-service-header-text [service-type]
  (case service-type
    :passenger-transportation (tr [:passenger-transportation-page :header-edit-passenger-transportation])
    :terminal (tr [:terminal-page :header-edit-terminal])
    :rentals (tr [:rentals-page :header-edit-rentals])
    :parking (tr [:parking-page :header-edit-parking])))

(defn new-service-header-text [service-type]
  (case service-type
    :passenger-transportation (tr [:passenger-transportation-page :header-new-passenger-transportation])
    :terminal (tr [:terminal-page :header-new-terminal])
    :rentals (tr [:rentals-page :header-new-rentals])
    :parking (tr [:parking-page :header-new-parking])))

(defn- license-info []
  [:p {:style {:padding-top "20px"
               :padding-bottom "20px"}}
   (tr [:common-texts :nap-data-license])
   [ui-common/linkify (tr [:common-texts :nap-data-license-url]) (tr [:common-texts :nap-data-license-url-label]) {:target "_blank"}]
   "."])

(defn edit-service [e! type {service :transport-service :as app}]
  [:span
   [license-info]
   (case type
     :passenger-transportation [pt/passenger-transportation-info e! (:transport-service app) app]
     :terminal [terminal/terminal e! (:transport-service app) app]
     :rentals [rental/rental e! (:transport-service app) app]
     :parking [parking/parking e! (:transport-service app) app])])

(defn edit-service-by-id [e! {loaded? :transport-service-loaded? service :transport-service :as app}]
  (if (or (nil? service) (not loaded?))
    [ui-common/loading-spinner]
    ;; Render transport service page only if given id matches with the loaded id
    ;; This will prevent page render with "wrong" or "empty" transport-service data
    (when (= (get-in app [:params :id]) (str (get-in app [:transport-service ::t-service/id])))
      [:div
       [ui-common/rotate-device-notice]

       [:h1 (edit-service-header-text (keyword (::t-service/type service)))]
       ;; Passenger transport service has sub type, and here it is shown to users
       (when (= :passenger-transportation (keyword (::t-service/type service)))
         [:p (stylefy/use-style style-form/subheader)
          (tr [:enums :ote.db.transport-service/sub-type
               (get-in app [:transport-service ::t-service/sub-type])])])
       ;; Show service owner name only for service owners
       (when (ts/is-service-owner? app)
         [:h2 (get-in app [:transport-operator ::t-operator/name])])
       ;; Render the form
       [edit-service e! (::t-service/type service) app]])))


(defn create-new-service
  "Render container and headers for empty service form"
  [e! app]
  (when (get-in app [:transport-service ::t-service/type])
    (let [service-sub-type (get-in app [:transport-service ::t-service/sub-type])
          service-type (get-in app [:transport-service ::t-service/type])
          new-header-text (new-service-header-text service-type)]

      [:div
       [ui-common/rotate-device-notice]

       [:h1 new-header-text]
       ;; Passenger transport service has sub type, and here it is shown to users
       (when (= :passenger-transportation service-type)
         [:p (stylefy/use-style style-form/subheader)
          (tr [:enums :ote.db.transport-service/sub-type
               (get-in app [:transport-service ::t-service/sub-type])])])

       [:div.row
        [:h2 (get-in app [:transport-operator ::t-operator/name])]]
       [edit-service e! service-type  app]])))
