(ns ote.views.parking
  "Required data input fields for parking services"
  (:require [reagent.core :as r]
            [ote.mui-wrapper.reagent :as ui]
            [ote.mui-wrapper.icons :as ic]
            [ote.ui.form :as form]
            [ote.ui.form-groups :as form-groups]
            [ote.ui.buttons :as buttons]
            [ote.db.transport-service :as t-service]
            [ote.db.common :as common]
            [ote.localization :refer [tr tr-key]]
            [ote.views.place-search :as place-search]
            [tuck.core :as tuck]
            [stylefy.core :as stylefy]
            [ote.style.base :as style-base]
            [ote.style.form :as style-form]
            [ote.app.controller.transport-service :as ts]
            [ote.views.transport-service-common :as ts-common]
            [ote.time :as time]
            [ote.util.values :as values]
            [ote.ui.validation :as validation]))

(defn form-options [e! schemas app]
  {:name->label (tr-key [:field-labels :parking]
                        [:field-labels :transport-service-common]
                        [:field-labels :transport-service]
                        [:field-labels])
   :update!     #(e! (ts/->EditTransportService %))
   :footer-fn   (fn [data]
                  [ts-common/footer e! data schemas app])})

(defn pricing-group [e!]
  (form/group
    {:label   (tr [:parking-page :header-price-and-payment-methods])
     :columns 3
     :layout  :row}

    (form/info
     [:div (tr [:form-help :pricing-info])])

    {:name         ::t-service/price-classes
     :type         :table
     :prepare-for-save values/without-empty-rows
     :table-fields [{:name  ::t-service/name
                     :type :string
                     :label (tr [:field-labels :parking ::t-service/price-class-name])
                     :required? true
                     :max-length 200}
                    {:name ::t-service/price-per-unit
                     :type :number
                     :currency? true
                     :style {:width "100px"}
                     :input-style {:text-align "right" :padding-right "5px"}
                     :required? true}
                    {:name ::t-service/unit
                     :type :string
                     :style {:width "100px"}
                     :max-length 128}]
     :add-label    (tr [:buttons :add-new-price-class])
     :delete?      true}

    {:container-class "col-md-6"
     :name            ::t-service/pricing-description
     :type            :localized-text
     :full-width?     true
     :write           #(assoc-in %1 [::t-service/pricing ::t-service/description] %2)
     :read            (comp ::t-service/description ::t-service/pricing)}

    {:container-class "col-md-5"
     :name  ::t-service/pricing-url
     :type  :string
     :full-width? true
     :write #(assoc-in %1 [::t-service/pricing ::t-service/url] %2)
     :read  (comp ::t-service/url ::t-service/pricing)}

    {:container-class "col-md-3"
     :name            ::t-service/payment-methods
     :type            :multiselect-selection
     :show-option     (tr-key [:enums ::t-service/payment-methods])
     :options         t-service/payment-methods}

    {:container-class "col-md-8"
     :name ::t-service/payment-method-description
     :type :localized-text
     :rows 1
     :full-width? true}))

(defn service-hours-group [e!]
  (let [tr* (tr-key [:field-labels :service-exception])
        write-time (fn [key]
                (fn [{all-day? ::t-service/all-day :as data} time]
                  ;; Don't allow changing time if all-day checked
                  (if all-day?
                    data
                    (assoc data key time))))]
    (form/group
      {:label   (tr [:parking-page :header-service-hours])
       :columns 3
       :layout  :row}

      {:name      ::t-service/service-hours
       :type      :table
       :prepare-for-save values/without-empty-rows
       :table-fields
                  [{:name              ::t-service/week-days
                    :width             "40%"
                    :type              :multiselect-selection
                    :options           t-service/days
                    :show-option       (tr-key [:enums ::t-service/day :full])
                    :show-option-short (tr-key [:enums ::t-service/day :short])
                    :required? true
                    :is-empty? validation/empty-enum-dropdown?
                    }
                   {:name  ::t-service/all-day
                    :width "10%"
                    :type  :checkbox
                    :write (fn [data all-day?]
                             (merge data
                                    {::t-service/all-day all-day?}
                                    (if all-day?
                                      {::t-service/from (time/->Time 0 0 nil)
                                       ::t-service/to (time/->Time 24 0 nil)}
                                      {::t-service/from nil
                                       ::t-service/to nil})))}

                   {:name         ::t-service/from
                    :width        "25%"
                    :type         :time
                    :write        (write-time ::t-service/from)
                    :required? true
                    :is-empty? time/empty-time?}
                   {:name         ::t-service/to
                    :width        "25%"
                    :type         :time
                    :write        (write-time ::t-service/to)
                    :required? true
                    :is-empty? time/empty-time?}]
       :delete?   true
       :add-label (tr [:buttons :add-new-service-hour])}

      {:name         ::t-service/service-exceptions
       :type         :table
       :prepare-for-save values/without-empty-rows
       :table-fields [{:name  ::t-service/description
                       :label (tr* :description)
                       :type  :localized-text}
                      {:name  ::t-service/from-date
                       :type  :date-picker
                       :label (tr* :from-date)}
                      {:name  ::t-service/to-date
                       :type  :date-picker
                       :label (tr* :to-date)}]
       :delete?      true
       :add-label    (tr [:buttons :add-new-service-exception])}

      ;;

      {:name ::t-service/maximum-stay
       :type :interval
       :enabled-label (tr [:field-labels :parking :maximum-stay-limited])
       :container-style style-form/full-width})))

(defn capacities [e!]
  (form/group
    {:label   (tr [:parking-page :header-facilities-and-capacities])
     :columns 3
     :layout  :row}

    {:name         ::t-service/parking-capacities
     :type         :table
     :prepare-for-save values/without-empty-rows
     :table-fields [{:name        ::t-service/parking-facility
                     :type        :selection
                     :show-option (tr-key [:enums ::t-service/parking-facility])
                     :options     t-service/parking-facilities
                     :required? true}
                    {:name ::t-service/capacity :type :number
                     :required? true}]
     :add-label    (tr [:buttons :add-new-parking-capacity])
     :delete?      true}))

(defn charging-points [e!]
  (form/group
    {:label   (tr [:parking-page :header-charging-points])
     :columns 3
     :layout  :row}

    {:name            ::t-service/charging-points
     :rows            2
     :type            :localized-text
     :full-width?     true
     :container-class "col-md-6"}))

(defn accessibility-group []
  (form/group
    {:label   (tr [:parking-page :header-accessibility])
     :columns 3
     :layout  :row}

    {:name            ::t-service/accessibility
     :type            :checkbox-group
     :show-option     (tr-key [:enums ::t-service/accessibility])
     :options         t-service/parking-accessibility
     :full-width?     true
     :container-class "col-md-6"}

    {:name            ::t-service/information-service-accessibility
     :type            :checkbox-group
     :show-option     (tr-key [:enums ::t-service/information-service-accessibility])
     :options         t-service/parking-information-service-accessibility
     :full-width?     true
     :container-class "col-md-5"}

    {:name            ::t-service/accessibility-description
     :type            :localized-text
     :rows            2
     :container-class "col-md-6"
     :full-width?     true}

    {:name            ::t-service/accessibility-info-url
     :type            :string
     :container-class "col-md-5"
     :full-width?     true
     :max-length 200}))

(defn parking [e! {form-data ::t-service/parking} app]
  (r/with-let [groups [(ts-common/transport-type ::t-service/parking)
                       (ts-common/name-group (tr [:parking-page :header-service-info]))
                       (ts-common/contact-info-group)
                       (ts-common/place-search-group (ts-common/place-search-dirty-event e!) ::t-service/parking)
                       (ts-common/external-interfaces e!)
                       (ts-common/advance-reservation-group)
                       (ts-common/service-url
                        (tr [:field-labels :parking ::t-service/real-time-information])
                        ::t-service/real-time-information
                        (tr [:form-help :real-time-info]))
                       (ts-common/service-url
                         (tr [:field-labels :parking ::t-service/booking-service])
                         ::t-service/booking-service)
                       (ts-common/service-urls
                         (tr [:field-labels :parking ::t-service/additional-service-links])
                         ::t-service/additional-service-links)
                       (capacities e!)
                       (charging-points e!)
                       (pricing-group e!)
                       (accessibility-group)
                       (service-hours-group e!)]
               options (form-options e! groups app)]
              [:div.row
                [form/form options groups (merge
                                            {:maximum-stay-unit :hours}
                                            form-data)]]))
