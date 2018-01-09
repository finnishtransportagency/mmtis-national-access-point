 (ns ote.views.passenger-transportation
  "Required datas for passenger transportation provider"
   (:require [reagent.core :as reagent]
             [cljs-react-material-ui.reagent :as ui]
             [cljs-react-material-ui.icons :as ic]
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
             [ote.ui.validation :as validation])
  (:require-macros [reagent.core :refer [with-let]]))

(defn transportation-form-options [e! schemas]
  {:name->label (tr-key [:field-labels :passenger-transportation] [:field-labels :transport-service-common] [:field-labels :transport-service])
   :update!     #(e! (ts/->EditTransportService %))
   :name        #(tr [:olennaiset-tiedot :otsikot %])
   :footer-fn   (fn [data]
                  [ts-common/footer e! data schemas])})



(defn luggage-restrictions-group []
  (form/group
    {:label (tr [:passenger-transportation-page :header-restrictions])
    :columns 3
    :layout :row}

    {:name      ::t-service/luggage-restrictions
     :type      :localized-text
     :is-empty? validation/empty-localized-text?
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
    :is-empty? validation/empty-localized-text?
    :rows 1
    :full-width? true
    :container-class "col-xs-12 col-sm-6 col-md-6"}

   {:name ::t-service/limited-accessibility-description
    :type :localized-text
    :is-empty? validation/empty-localized-text?
    :rows 1
    :container-class "col-xs-12 col-sm-6 col-md-6"
    :full-width? true}

   {:name ::t-service/accessibility-info-url
    :type :string
    :container-class "col-xs-12 col-sm-6 col-md-6"
    :full-width? true}

   {:name        ::t-service/additional-services
    :type        :multiselect-selection
    :show-option (tr-key [:enums ::t-service/additional-services])
    :options     t-service/additional-services
    :container-class "col-xs-12 col-sm-6 col-md-6"
    :full-width? true}
   ))

(defn pricing-group [sub-type]
  (let [price-class-name-label (cond
                                 (= :taxi sub-type) (tr [:field-labels :passenger-transportation ::t-service/price-class-name-taxi])
                                 (= :other sub-type) (tr [:field-labels :passenger-transportation ::t-service/price-class-name-other])
                                 (= :request sub-type) (tr [:field-labels :passenger-transportation ::t-service/price-class-name-request])
                                 (= :schedule sub-type) (tr [:field-labels :passenger-transportation ::t-service/price-class-name-schedule])
                                 :else (tr [:field-labels :passenger-transportation ::t-service/price-class-name-other]))
        price-description-label (cond
                                 (= :taxi sub-type) (tr [:field-labels :passenger-transportation ::t-service/pricing-description-taxi])
                                 (= :other sub-type) (tr [:field-labels :passenger-transportation ::t-service/pricing-description])
                                 (= :request sub-type) (tr [:field-labels :passenger-transportation ::t-service/pricing-description])
                                 (= :schedule sub-type) (tr [:field-labels :passenger-transportation ::t-service/pricing-description])
                                 :else (tr [:field-labels :passenger-transportation ::t-service/pricing-description]))]
  (form/group
   {:label (tr [:passenger-transportation-page :header-price-information])
    :columns 3
    :layout :row}

   (form/info
     [:div
      [:p (tr [:form-help :pricing-info])]])

   {:container-class "col-xs-12"
    :name         ::t-service/price-classes
    :type         :table
    :prepare-for-save values/without-empty-rows
    :table-fields [{:name ::t-service/name :type :string :label price-class-name-label}
                   {:name ::t-service/price-per-unit :type :number :currency? true :style {:width "100px"}
                    :input-style {:text-align "right" :padding-right "5px"}}
                   {:name ::t-service/unit :type :string :style {:width "100px"}}]
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
    :is-empty? validation/empty-localized-text?
    :rows 6
    :full-width? true
    }

   {:container-class "col-xs-12 col-sm-6 col-md-6"
    :name ::t-service/pricing-description
    :label price-description-label
    :type :localized-text
    :is-empty? validation/empty-localized-text?
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



(defn passenger-transportation-info [e! {form-data ::t-service/passenger-transportation :as service}]
  (with-let [form-groups
             [(ts-common/name-group (tr [:passenger-transportation-page :header-service-info]))
              (ts-common/contact-info-group)
              (ts-common/companies-group)
              (ts-common/place-search-group e! ::t-service/passenger-transportation)
              (ts-common/external-interfaces (= :schedule (get service ::t-service/sub-type)))
              (luggage-restrictions-group)
              (ts-common/service-url
               (tr [:field-labels :passenger-transportation ::t-service/real-time-information])
               ::t-service/real-time-information)
              (ts-common/service-url
               (tr [:field-labels :transport-service-common ::t-service/booking-service])
               ::t-service/booking-service)
              (accessibility-group)
              (pricing-group (get service ::t-service/sub-type))
              (ts-common/service-hours-group)]
             form-options (transportation-form-options e! form-groups)]
    [:div.row
     [form/form form-options form-groups form-data]]))
