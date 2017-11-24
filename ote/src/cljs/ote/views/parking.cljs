(ns ote.views.parking
  "Required data input fields for parking services"
  (:require [reagent.core :as r]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]
            [ote.ui.form :as form]
            [ote.ui.form-groups :as form-groups]
            [ote.ui.buttons :as buttons]
            [ote.app.controller.parking :as parking]
            [ote.db.transport-service :as t-service]
            [ote.db.common :as common]
            [ote.localization :refer [tr tr-key]]
            [ote.views.place-search :as place-search]
            [tuck.core :as tuck]
            [ote.style.base :as style-base]
            [ote.app.controller.transport-service :as ts]
            [ote.views.transport-service-common :as ts-common]
            [ote.time :as time]))

(defn form-options [e!]
  {:name->label (tr-key [:field-labels :parking] [:field-labels :transport-service-common])
   :update!     #(e! (parking/->EditParkingState %))
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
    {:label (tr [:passenger-transportation-page :header-price-information])
     :columns 3
     :layout :row}

    {:name         ::t-service/price-classes
     :type         :table
     :table-fields [{:name ::t-service/name :type :string
                     :label (tr [:field-labels :passenger-transportation ::t-service/price-class-name])}
                    {:name ::t-service/price-per-unit :type :number}
                    {:name ::t-service/unit :type :string}
                    {:name ::t-service/currency :type :string :width "100px"}]
     :add-label (tr [:buttons :add-new-price-class])
     :delete?      true}

    {:name ::t-service/pricing-description
     :type :localized-text
     :write #(assoc-in %1 [::t-service/pricing ::t-service/description] %2)
     :read (comp ::t-service/description ::t-service/pricing)
     :columns 1}

    {:name ::t-service/pricing-url
     :type :string
     :write #(assoc-in %1 [::t-service/pricing ::t-service/url] %2)
     :read (comp ::t-service/url ::t-service/pricing)
     :columns 1}))

(defn service-hours-group [e!]
  (let [tr* (tr-key [:field-labels :service-exception])
        write (fn [key]
                (fn [{all-day? ::t-service/all-day :as data} time]
                  ;; Don't allow changing time if all-day checked
                  (if all-day?
                    data
                    (assoc data key time))))]
    (form/group
      {:label (tr [:passenger-transportation-page :header-service-hours])
       :columns 3}

      {:name         ::t-service/service-hours
       :type         :table
       :table-fields
                     [{:name ::t-service/week-days
                       :width "40%"
                       :type :multiselect-selection
                       :options t-service/days
                       :show-option (tr-key [:enums ::t-service/day :full])
                       :show-option-short (tr-key [:enums ::t-service/day :short])}
                      {:name ::t-service/all-day
                       :width "10%"
                       :type :checkbox
                       :write (fn [data all-day?]
                                (merge data
                                       {::t-service/all-day all-day?}
                                       (when all-day?
                                         {::t-service/from (time/->Time 0 0 nil)
                                          ::t-service/to (time/->Time 24 0 nil)})))}

                      {:name ::t-service/from
                       :width "25%"
                       :type :time
                       :cancel-label (tr [:buttons :cancel])
                       :ok-label (tr [:buttons :save])
                       :write (write ::t-service/from)
                       :default-time {:hours "08" :minutes "00"}}
                      {:name ::t-service/to
                       :width "25%"
                       :type :time
                       :cancel-label (tr [:buttons :cancel])
                       :ok-label (tr [:buttons :save])
                       :write (write ::t-service/to)
                       :default-time {:hours "19" :minutes "00"}}]
       :delete?      true
       :add-label (tr [:buttons :add-new-service-hour])}

      {:name ::t-service/service-exceptions
       :type :table
       :table-fields [{:name ::t-service/description
                       :label (tr* :description)
                       :type :localized-text}
                      {:name ::t-service/from-date
                       :type :date-picker
                       :label (tr* :from-date)}
                      {:name ::t-service/to-date
                       :type :date-picker
                       :label (tr* :to-date)}]
       :delete? true
       :add-label (tr [:buttons :add-new-service-exception])})))

(defn parking [e! {form-data ::t-service/parking}]
  (r/with-let [options (form-options e!)
               groups [(name-and-type-group e!)
                       (ts-common/contact-info-group)
                       (ts-common/external-interfaces)]]
              [:div.row
               [:div {:class "col-lg-12"}
                [:div
                 [:h3 (tr [:parking-page :header-add-new-parking])]]
                [form/form options groups form-data]]]))