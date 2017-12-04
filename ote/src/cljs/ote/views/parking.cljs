(ns ote.views.parking
  "Required data input fields for parking services"
  (:require [reagent.core :as r]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]
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
            [ote.time :as time]))

(defn form-options [e!]
  {:name->label (tr-key [:field-labels :parking]
                        [:field-labels :transport-service-common]
                        [:field-labels :transport-service]
                        [:field-labels])
   :update!     #(e! (ts/->EditTransportService %))
   :name        #(tr [:olennaiset-tiedot :otsikot %])
   :footer-fn   (fn [data]
                  [ts-common/footer e! data])})

(defn name-and-type-group [e!]
  (form/group
    {:label   (tr [:parking-page :header-service-info])
     :columns 3
     :layout  :row}

    {:name      ::t-service/name
     :type      :string
     :required? true}))


(defn pricing-group [e!]
  (form/group
    {:label   (tr [:parking-page :header-price-and-payment-methods])
     :columns 3
     :layout  :row}

    {:name         ::t-service/price-classes
     :type         :table
     :table-fields [{:name  ::t-service/name :type :string
                     :label (tr [:field-labels :parking ::t-service/price-class-name])}
                    {:name ::t-service/price-per-unit :type :number}
                    {:name ::t-service/unit :type :string}
                    {:name ::t-service/currency :type :string :width "100px"}]
     :add-label    (tr [:buttons :add-new-price-class])
     :delete?      true}

    {:name  ::t-service/pricing-description
     :type  :localized-text
     :write #(assoc-in %1 [::t-service/pricing ::t-service/description] %2)
     :read  (comp ::t-service/description ::t-service/pricing)}

    {:name  ::t-service/pricing-url
     :type  :string
     :write #(assoc-in %1 [::t-service/pricing ::t-service/url] %2)
     :read  (comp ::t-service/url ::t-service/pricing)}

    {:name            ::t-service/payment-methods
     :type            :multiselect-selection
     :container-style style-form/full-width
     :show-option     (tr-key [:enums ::t-service/payment-methods])
     :options         t-service/payment-methods}))

(defn service-hours-group [e!]
  (let [tr* (tr-key [:field-labels :service-exception])
        write (fn [key]
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
       :table-fields
                  [{:name              ::t-service/week-days
                    :width             "40%"
                    :type              :multiselect-selection
                    :options           t-service/days
                    :show-option       (tr-key [:enums ::t-service/day :full])
                    :show-option-short (tr-key [:enums ::t-service/day :short])}
                   {:name  ::t-service/all-day
                    :width "10%"
                    :type  :checkbox
                    :write (fn [data all-day?]
                             (merge data
                                    {::t-service/all-day all-day?}
                                    (when all-day?
                                      {::t-service/from (time/->Time 0 0 nil)
                                       ::t-service/to   (time/->Time 24 0 nil)})))}

                   {:name         ::t-service/from
                    :width        "25%"
                    :type         :time
                    :cancel-label (tr [:buttons :cancel])
                    :ok-label     (tr [:buttons :save])
                    :write        (write ::t-service/from)
                    :default-time {:hours "08" :minutes "00"}}
                   {:name         ::t-service/to
                    :width        "25%"
                    :type         :time
                    :cancel-label (tr [:buttons :cancel])
                    :ok-label     (tr [:buttons :save])
                    :write        (write ::t-service/to)
                    :default-time {:hours "19" :minutes "00"}}]
       :delete?   true
       :add-label (tr [:buttons :add-new-service-hour])}

      {:name         ::t-service/service-exceptions
       :type         :table
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
       :type :interval})))

(defn capacities [e!]
  (form/group
    {:label   (tr [:parking-page :header-facilities-and-capacities])
     :columns 3
     :layout  :row}

    {:name         ::t-service/parking-capacities
     :type         :table
     :table-fields [{:name        ::t-service/parking-facility
                     :type        :selection
                     :show-option (tr-key [:enums ::t-service/parking-facility])
                     :options     t-service/parking-facilities}
                    {:name ::t-service/capacity :type :number}]
     :add-label    (tr [:buttons :add-new-parking-capacity])
     :delete?      true}))

(defn charging-points [e!]
  (form/group
    {:label   (tr [:parking-page :header-charging-points])
     :columns 3
     :layout  :row}

    {:name            ::t-service/charging-points
     :rows            5
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
     :options         t-service/accessibility
     :full-width?     true
     :container-class "col-md-6"}

    {:name            ::t-service/mobility
     :type            :checkbox-group
     :show-option     (tr-key [:enums ::t-service/mobility])
     :options         t-service/parking-mobility
     :full-width?     true
     :container-class "col-md-5"}

    {:name            ::t-service/information-service-accessibility
     :type            :checkbox-group
     :show-option     (tr-key [:enums ::t-service/information-service-accessibility])
     :options         t-service/information-service-accessibility
     :full-width?     true
     :container-class "col-md-6"}

    {:name            ::t-service/accessibility-description
     :type            :localized-text
     :rows            5
     :max-rows        5
     :container-class "col-md-5"
     :full-width?     true}

    {:name            ::t-service/accessibility-info-url
     :type            :string
     :container-class "col-md-5"
     :full-width?     true}))

(defn parking [e! {form-data ::t-service/parking}]
  (r/with-let [options (form-options e!)
               groups [(name-and-type-group e!)
                       (ts-common/contact-info-group)
                       (ts-common/place-search-group e! ::t-service/parking)
                       (ts-common/external-interfaces)
                       (ts-common/service-url
                         (tr [:field-labels :parking ::t-service/real-time-information])
                         ::t-service/real-time-information)
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
                       (service-hours-group e!)]]
              [:div.row
               [:div {:class "col-lg-12"}
                [:div
                 [:h3 (tr [:parking-page :header-add-new-parking])]]

                [form/form options groups (merge
                                            {:maximum-stay-unit :hours}
                                            form-data)]]]))
