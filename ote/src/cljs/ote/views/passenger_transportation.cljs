 (ns ote.views.passenger-transportation
  "Required datas for passenger transportation provider"
   (:require [reagent.core :as reagent]
             [ote.mui-wrapper.reagent :as ui]
             [ote.mui-wrapper.icons :as ic]
             [ote.ui.form :as form]
             [ote.ui.form-groups :as form-groups]
             [ote.ui.buttons :as buttons]
             [ote.app.controller.transport-service :as ts]
             [ote.db.transport-service :as t-service]
             [ote.db.transport-operator :as t-operator]
             [ote.db.common :as common]
             [ote.localization :refer [tr tr-key]]
             [ote.views.place-search :as place-search]
             [tuck.core :as tuck]
             [stylefy.core :as stylefy]
             [ote.style.base :as style-base]
             [ote.views.transport-service-common :as ts-common]
             [ote.time :as time]
             [ote.style.form :as style-form]
             [ote.util.values :as values]
             [ote.ui.validation :as validation]
             [reagent.core :as r])
  (:require-macros [reagent.core :refer [with-let]]))

(defn transportation-form-options [e! schemas app]
  {:name->label (tr-key [:field-labels :passenger-transportation] [:field-labels :transport-service-common] [:field-labels :transport-service])
   :update!     #(e! (ts/->EditTransportService %))
   :footer-fn   (fn [data]
                  [ts-common/footer e! data schemas app])})

(defn luggage-restrictions-group []
  (form/group
    {:label (tr [:passenger-transportation-page :header-restrictions])
    :columns 3
    :layout :row}

    {:name      ::t-service/luggage-restrictions
     :type      :localized-text
     :rows      1
     :full-width? true
     :container-class "col-xs-12"}))



(defn accessibility-group []
  (form/group
   {:label (tr [:passenger-transportation-page :header-other-services-and-accessibility])
    :columns 3
    :layout :row}

   {:name        ::t-service/guaranteed-vehicle-accessibility
    :help (tr [:form-help :guaranteed-vehicle-accessibility])
    :type        :checkbox-group
    :show-option (tr-key [:enums ::t-service/vehicle-accessibility])
    :options     t-service/vehicle-accessibility
    :full-width? true
    :container-class "col-xs-12 col-sm-6 col-md-6"}

   {:name        ::t-service/limited-vehicle-accessibility
    :help (tr [:form-help :limited-vehicle-accessibility])
    :type        :checkbox-group
    :show-option (tr-key [:enums ::t-service/vehicle-accessibility])
    :options     t-service/vehicle-accessibility
    :full-width? true
    :container-class "col-xs-12 col-sm-6 col-md-6"}

   {:name ::t-service/guaranteed-info-service-accessibility
    :type :checkbox-group
    :show-option (tr-key [:enums ::t-service/information-service-accessibility])
    :options t-service/information-service-accessibility
    :full-width? true
    :container-class "col-xs-12 col-sm-6 col-md-6"}

   {:name ::t-service/limited-info-service-accessibility
    :type :checkbox-group
    :show-option (tr-key [:enums ::t-service/information-service-accessibility])
    :options t-service/information-service-accessibility
    :full-width? true
    :container-class "col-xs-12 col-sm-6 col-md-6"}

   {:name ::t-service/guaranteed-transportable-aid
    :type :checkbox-group
    :show-option (tr-key [:enums ::t-service/transportable-aid])
    :options t-service/transportable-aid
    :full-width? true
    :container-class "col-xs-12 col-sm-6 col-md-6"}

   {:name ::t-service/limited-transportable-aid
    :type :checkbox-group
    :show-option (tr-key [:enums ::t-service/transportable-aid])
    :options t-service/transportable-aid
    :full-width? true
    :container-class "col-xs-12 col-sm-6 col-md-6"}

   {:name ::t-service/guaranteed-accessibility-description
    :type :localized-text
    :rows 1
    :full-width? true
    :container-class "col-xs-12 col-sm-6 col-md-6"}

   {:name ::t-service/limited-accessibility-description
    :type :localized-text
    :rows 1
    :container-class "col-xs-12 col-sm-6 col-md-6"
    :full-width? true}

   {:name ::t-service/accessibility-info-url
    :type :string
    :container-class "col-xs-12 col-sm-6 col-md-6"
    :full-width? true
    :max-length 200}

   {:name        ::t-service/additional-services
    :type        :multiselect-selection
    :show-option (tr-key [:enums ::t-service/additional-services])
    :options     t-service/additional-services
    :container-class "col-xs-12 col-sm-6 col-md-6"
    :full-width? true}
   ))

(defn pricing-group [sub-type]
  (let [price-class-name-label
          (cond
            (= :taxi sub-type) (tr [:field-labels :passenger-transportation ::t-service/price-class-name-taxi])
            (= :request sub-type) (tr [:field-labels :passenger-transportation ::t-service/price-class-name-request])
            (= :schedule sub-type) (tr [:field-labels :passenger-transportation ::t-service/price-class-name-schedule])
            :else (tr [:field-labels :passenger-transportation ::t-service/price-class-name-other]))
        price-description-label (if (= :taxi sub-type)
                                  (tr [:field-labels :passenger-transportation ::t-service/pricing-description-taxi])
                                  (tr [:field-labels :passenger-transportation ::t-service/pricing-description]))]
  (form/group
   {:label (tr [:passenger-transportation-page :header-price-information])
    :columns 3
    :layout :row}

   (form/info
     [:div (tr [:form-help :pricing-info])])

   {:container-class "col-xs-12"
    :name         ::t-service/price-classes
    :type         :table
    :prepare-for-save values/without-empty-rows
    :table-fields [{:name ::t-service/name
                    :type :string
                    :label price-class-name-label
                    :max-length 200}
                   {:name ::t-service/price-per-unit
                    :type :number
                    :currency? true
                    :style {:width "100px"}
                    :input-style {:text-align "right" :padding-right "5px"}}
                   {:name ::t-service/unit
                    :type :string
                    :style {:width "100px"}
                    :max-length 128}]
    :add-label (tr [:buttons :add-new-price-class])
    :delete?      true}

   {:container-class "col-xs-12 col-sm-6 col-md-6"
    :name        ::t-service/payment-methods
    :type        :checkbox-group
    :show-option (tr-key [:enums ::t-service/payment-methods])
    :options     t-service/payment-methods}

   {:container-class "col-xs-12 col-sm-6 col-md-6"
    :name ::t-service/payment-method-description
    :type :localized-text
    :rows 6
    :full-width? true
    }

   {:container-class "col-xs-12 col-sm-6 col-md-6"
    :name ::t-service/pricing-description
    :label price-description-label
    :type :localized-text
    :full-width? true
    :write #(assoc-in %1 [::t-service/pricing ::t-service/description] %2)
    :read (comp ::t-service/description ::t-service/pricing)
    }

   {:container-class "col-xs-12 col-sm-6 col-md-6"
    :name ::t-service/pricing-url
    :full-width? true
    :type :string
    :write #(assoc-in %1 [::t-service/pricing ::t-service/url] %2)
    :read (comp ::t-service/url ::t-service/pricing)
    })))

(defn passenger-transportation-info [e! {form-data ::t-service/passenger-transportation :as service} app]
  (let [form-groups
             [(ts-common/transport-type (::t-service/sub-type service))
              (ts-common/name-group (tr [:passenger-transportation-page :header-service-info]))
              (ts-common/contact-info-group)
              (ts-common/companies-group e!)
              (ts-common/brokerage-group e!)
              (ts-common/place-search-group (ts-common/place-search-dirty-event e!) ::t-service/passenger-transportation)
              (ts-common/external-interfaces e! (get service ::t-service/type) (get service ::t-service/sub-type))
              (luggage-restrictions-group)
              (ts-common/service-url
               (tr [:field-labels :passenger-transportation ::t-service/real-time-information])
               ::t-service/real-time-information
               (tr [:form-help :real-time-info]))
              (ts-common/advance-reservation-group)
              (ts-common/service-url
               (tr [:field-labels :transport-service-common ::t-service/booking-service])
               ::t-service/booking-service)
              (accessibility-group)
              (pricing-group (get service ::t-service/sub-type))
              (ts-common/service-hours-group "passenger-transportation")]
             form-options (transportation-form-options e! form-groups app)]
    [:div.row
     [form/form form-options form-groups form-data]]))
