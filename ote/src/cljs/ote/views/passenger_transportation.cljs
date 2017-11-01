(ns ote.views.passenger-transportation
  "Required datas for passenger transportation provider"
  (:require [reagent.core :as reagent]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]
            [ote.ui.form :as form]
            [ote.ui.form-groups :as form-groups]
            [ote.ui.napit :as napit]
            [ote.app.controller.transport-service :as ts]
            [ote.app.controller.passenger-transportation :as pt]
            [ote.db.transport-service :as t-service]
            [ote.db.common :as common]
            [ote.localization :refer [tr tr-key]]
            [ote.views.place-search :as place-search]
            [tuck.core :as tuck]
            [stylefy.core :as stylefy]
            [ote.style.base :as style-base])
  (:require-macros [reagent.core :refer [with-let]]))

(defn footer [e! {published? ::t-service/published? :as data}]
  [:div.row
   (if published?
     [napit/tallenna {:on-click #(e! (pt/->SavePassengerTransportationToDb true))
                      :disabled (not (form/can-save? data))}
      (tr [:buttons :save-updated])]
     [:span
      [napit/tallenna {:on-click #(e! (pt/->SavePassengerTransportationToDb true))
                       :disabled (not (form/can-save? data))}
       (tr [:buttons :save-and-publish])]
      [napit/tallenna {:on-click #(e! (pt/->SavePassengerTransportationToDb false))
                        :disabled (not (form/can-save? data))}
       (tr [:buttons :save-as-draft])]])
   [napit/cancel {:on-click #(e! (pt/->CancelPassengerTransportationForm))}
    (tr [:buttons :discard])]])

(defn transportation-form-options [e!]
  {:name->label (tr-key [:field-labels :passenger-transportation] [:field-labels :transport-service-common] [:field-labels :transport-service])
   :update!     #(e! (pt/->EditPassengerTransportationState %))
   :name        #(tr [:olennaiset-tiedot :otsikot %])
   :footer-fn   (fn [data]
                  [footer e! data])})

(defn name-and-type-group [e!]
  (form/group
   {:label (tr [:passenger-transportation-page :header-service-info])
    :columns 3
    :layout :row}

   {:name ::t-service/name
    :type :string}

   {:style style-base/long-drowpdown ;; Pass only style from stylefy base
    :name ::t-service/sub-type
    :type        :selection
    :show-option (tr-key [:enums :ote.db.transport-service/sub-type])
    :options     t-service/passenger-transportation-sub-types}))

(defn place-search-group [e!]
  (place-search/place-search-form-group
   (tuck/wrap-path e! :transport-service ::t-service/passenger-transportation ::t-service/operation-area)
   (tr [:field-labels :passenger-transportation ::t-service/operation-area])
   ::t-service/operation-area))

(defn luggage-restrictions-group []
  (form/group
   {:label (tr [:passenger-transportation-page :header-restrictions-payments])
    :columns 3
    :layout :row}

   {:name ::t-service/luggage-restrictions
    :type :localized-text
    :rows 1 :max-rows 5}

   {:name        ::t-service/payment-methods
    :type        :multiselect-selection
    :show-option (tr-key [:enums ::t-service/payment-methods])
    :options     t-service/payment-methods}))

(defn contact-info-group []
  (form/group
   {:label  (tr [:passenger-transportation-page :header-contact-details])
    :columns 3
    :layout :row}
   {:name        ::common/street
    :type        :string
    :read (comp ::common/street ::t-service/contact-address)
    :write (fn [data street]
             (assoc-in data [::t-service/contact-address ::common/street] street))
    :label (tr [:field-labels ::common/street])}

   {:name        ::common/postal_code
    :type        :string
    :read (comp ::common/postal_code ::t-service/contact-address)
    :write (fn [data postal-code]
             (assoc-in data [::t-service/contact-address ::common/postal_code] postal-code))
    :label (tr [:field-labels ::common/postal_code])}

   {:name        ::common/post_office
    :type        :string
    :read (comp ::common/post_office ::t-service/contact-address)
    :write (fn [data post-office]
             (assoc-in data [::t-service/contact-address ::common/post_office] post-office))
    :label (tr [:field-labels ::common/post_office])}

   {:name        ::t-service/contact-phone
    :type        :string}

   {:name        ::t-service/contact-email
    :type        :string}

   {:name        ::t-service/homepage
    :type        :string}))

(defn accessibility-group []
  (form/group
   {:label (tr [:passenger-transportation-page :header-other-services-and-accessibility])
    :columns 3
    :layout :row}

   {:name        ::t-service/additional-services
    :type        :multiselect-selection
    :show-option (tr-key [:enums ::t-service/additional-services])
    :options     t-service/additional-services}

   {:name        ::t-service/accessibility-tool
    :type        :multiselect-selection
    :show-option (tr-key [:enums ::t-service/accessibility-tool])
    :options     t-service/accessibility-tool}

   {:name ::t-service/accessibility-description
    :type :localized-text
    :rows 1 :max-rows 5}))

(defn pricing-group [e!]
  (form/group
    {:label (tr [:passenger-transportation-page :header-price-information])
     :columns 3
     :actions [napit/tallenna
               {:style (stylefy/use-style style-base/base-button)
                :label-style {:color "#FFFFFF" :font-weight "bold" :font-size "12px"}
                :label "Lisää hintarivi"
                :on-click #(e! (ts/->AddPriceClassRow))}]}

    {:name         ::t-service/price-classes
    :type         :table
    :table-fields [{:name ::t-service/name :type :string
                    :label (tr [:field-labels :passenger-transportation ::t-service/price-class-name])}
                   {:name ::t-service/price-per-unit :type :number}
                   {:name ::t-service/unit :type :string}
                   {:name ::t-service/currency :type :string :width "100px"}
                   ]
    :delete?      true}))

(defn service-hours-group [e!]
  (form/group
   {:label (tr [:passenger-transportation-page :header-service-hours])
    :columns 3
    :actions [napit/tallenna
              {:style (stylefy/use-style style-base/base-button)
               :label-style {:color "#FFFFFF" :font-weight "bold" :font-size "12px"}
               :label (tr [:buttons :add-add-new-row])
               :on-click #(e! (ts/->AddServiceHourRow))}]}

   {:name         ::t-service/service-hours
    :type         :table
    :table-fields [{:name ::t-service/week-days
                    :type :multiselect-selection
                    :options t-service/days
                    :show-option (tr-key [:enums ::t-service/day :full])
                    :show-option-short (tr-key [:enums ::t-service/day :short])}
                   {:name ::t-service/from :type :time}
                   {:name ::t-service/to :type :time}]
    :delete?      true}))

(defn passenger-transportation-info [e! {form-data ::t-service/passenger-transportation}]
  (with-let [form-options (transportation-form-options e!)
             form-groups [(name-and-type-group e!)
                          (contact-info-group)
                          (place-search-group e!)
                          (luggage-restrictions-group)
                          (form-groups/service-url
                           (tr [:field-labels :passenger-transportation ::t-service/real-time-information])
                           ::t-service/real-time-information)
                          (form-groups/service-url
                           (tr [:field-labels :passenger-transportation ::t-service/booking-service])
                           ::t-service/booking-service)
                          (accessibility-group)
                          (pricing-group e!)
                          (service-hours-group e!)]]
    [:div.row
     [:div {:class "col-lg-12"}
      [:div
       [:h3 (tr [:passenger-transportation-page :header-passenger-transportation-service])(tr [:passenger-transportation-page :header-passenger-transportation-service]) ]]
      [form/form form-options form-groups form-data]]]))
