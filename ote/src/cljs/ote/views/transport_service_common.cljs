(ns ote.views.transport-service-common
  "View parts that are common to all transport service forms."
  (:require [tuck.core :as tuck]
            [ote.db.transport-service :as t-service]
            [ote.localization :refer [tr tr-key]]
            [ote.ui.form :as form]
            [ote.db.common :as common]
            [ote.ui.common :refer [linkify]]
            [ote.ui.buttons :as buttons]
            [ote.app.controller.transport-service :as ts]
            [ote.views.place-search :as place-search]
            [clojure.string :as str]
            [ote.time :as time]
            [ote.util.values :as values]
            [ote.style.form :as style-form]
            [cljs-react-material-ui.reagent :as ui]
            [ote.ui.validation :as validation]))

(defn service-url
  "Creates a form group for service url that creates two form elements url and localized text area"
  [label service-url-field]
  (form/group
    {:label label
    :layout :row
    :columns 3}

    {:class "set-bottom"
     :name   ::t-service/url
     :type   :string
     :read   (comp ::t-service/url service-url-field)
     :write  (fn [data url]
             (assoc-in data [service-url-field ::t-service/url] url))
     :container-class "col-md-6"
     :full-width?  true}
    {:name ::t-service/description
     :type  :localized-text
     :is-empty? validation/empty-localized-text?
     :rows  1
     :read  (comp ::t-service/description service-url-field)
     :write (fn [data desc]
             (assoc-in data [service-url-field ::t-service/description] desc))
     :container-class "col-md-6"
     :full-width?  true}))

(defn service-urls
  "Creates a table for additional service urls."
  [label service-url-field]
  (form/group
    {:label label
     :columns 3}

    {:name         service-url-field
     :type         :table
     :prepare-for-save values/without-empty-rows
     :table-fields [{:name ::t-service/url
                     :type :string}
                    {:name ::t-service/description
                     :type :localized-text
                     :is-empty? validation/empty-localized-text?}]
     :delete?      true
     :add-label    (tr [:buttons :add-new-service-link])}))

(defn external-interfaces
  "Creates a form group for external services."
  [& [rae-info?]]
  (form/group
    {:label  (tr [:field-labels :transport-service-common ::t-service/external-interfaces])
    :columns 3}

    (form/info
     [:div
      [:p (tr [:form-help :external-interfaces])]
      (when rae-info?
        [:p (tr [:form-help :external-interfaces-eg-rae])
         [linkify "https://liikennevirasto.fi/rae" (tr [:form-help :RAE-tool])
          {:target "_blank"}]])])

    {:name             ::t-service/external-interfaces
     :type             :table
     :prepare-for-save values/without-empty-rows
     :table-fields     [{:name      ::t-service/external-service-description
                         :type :localized-text
                         :width "25%"
                         :read      (comp ::t-service/description ::t-service/external-interface)
                         :write     #(assoc-in %1 [::t-service/external-interface ::t-service/description] %2)
                         :required? true
                         :is-empty? validation/empty-localized-text?}
                        {:name      ::t-service/external-service-url
                         :type :string
                         :width "18%"
                         :read      (comp ::t-service/url ::t-service/external-interface)
                         :write     #(assoc-in %1 [::t-service/external-interface ::t-service/url] %2)
                         :required? true}
                        {:name      ::t-service/format
                         :type :string
                         :width "12%"
                         :required? true}
                        {:name ::t-service/license
                         :type :string
                         :width "18%"
                         }
                        {:name ::t-service/license-url
                         :type :string
                         ;:width "21%"
                         }]
     :delete?          true
     :add-label        (tr [:buttons :add-external-interface])}

    (form/info
     [:div
      [:p (tr [:form-help :external-interfaces-end])]])

    {:name ::t-service/notice-external-interfaces?
     :type :checkbox
     :required? true
     :style style-form/padding-top
     :validate [[:checked?]]}))

(defn companies-group
  "Creates a form group for companies. A parent company can list its companies."
  []
  (form/group
   {:label (tr [:field-labels :transport-service-common ::t-service/companies])
    :columns 3}

   (form/info (tr [:form-help :companies]))

   {:name ::t-service/companies
    :type :table
    :prepare-for-save values/without-empty-rows
    :table-fields [{:name ::t-service/name :type :string
                    :label (tr [:field-labels :transport-service-common ::t-service/company-name])
                    :required? true}
                   {:name ::t-service/business-id :type :string
                    :validate [[:business-id]]
                    :required? true
                    :regex #"\d{0,7}(-\d?)?"}]
    :delete? true
    :add-label (tr [:buttons :add-new-company])}

   (form/info (tr [:form-help :brokerage?]))
   {:name ::t-service/brokerage?
    :style style-form/padding-top
    :type :checkbox}))

(defn contact-info-group []
  (form/group
   {:label  (tr [:passenger-transportation-page :header-contact-details])
    :columns 3
    :layout :row}

   (form/info (tr [:form-help :description-why-contact-info]))

   {:name        ::common/street
    :type        :string
    :container-class "col-md-4"
    :full-width?  true
    :read (comp ::common/street ::t-service/contact-address)
    :write (fn [data street]
             (assoc-in data [::t-service/contact-address ::common/street] street))
    :label (tr [:field-labels ::common/street])
    :required? true}

   {:name        ::common/postal_code
    :type        :string
    :container-class "col-md-2"
    :full-width?  true
    :regex #"\d{0,5}"
    :read (comp ::common/postal_code ::t-service/contact-address)
    :write (fn [data postal-code]
             (assoc-in data [::t-service/contact-address ::common/postal_code] postal-code))
    :label (tr [:field-labels ::common/postal_code])
    :required? true
    :validate [[:postal-code]]}

   {:name        ::common/post_office
    :type        :string
    :container-class "col-md-5"
    :full-width?  true
    :read (comp ::common/post_office ::t-service/contact-address)
    :write (fn [data post-office]
             (assoc-in data [::t-service/contact-address ::common/post_office] post-office))
    :label (tr [:field-labels ::common/post_office])
    :required? true}

   {:name        ::t-service/contact-email
    :type        :string
    :container-class "col-md-4"
    :full-width?  true}

   {:name        ::t-service/contact-phone
    :type        :string
    :container-class "col-md-2"
    :full-width?  true}

   {:name        ::t-service/homepage
    :type        :string
    :container-class "col-md-5"
    :full-width?  true}))

(defn footer
  "Transport service form -footer element. All transport service form should be using this function."
  [e! {published? ::t-service/published? :as data} schemas]
  (let [name-missing? (str/blank? (::t-service/name data))]
    [:div.row
     (when (not (empty? (:ote.ui.form/missing-required-fields data)))
       [ui/card {:style {:margin-top "0.5em" :margin-bottom "0.5em"}}
        [ui/card-text (tr [:form-help :publish-missing-required])]])

     (if published?
       ;; True
       [buttons/save {:on-click #(e! (ts/->SaveTransportService schemas true))
                      :disabled (not (form/can-save? data))}
        (tr [:buttons :save-updated])]
       ;; False
       [:span
        [buttons/save {:on-click #(e! (ts/->SaveTransportService schemas true))
                       :disabled (not (form/can-save? data))}
         (tr [:buttons :save-and-publish])]
        [buttons/save  {:on-click #(e! (ts/->SaveTransportService schemas false))
                        :disabled name-missing?}
         (tr [:buttons :save-as-draft])]])
     [buttons/cancel {:on-click #(e! (ts/->CancelTransportServiceForm))}
      (tr [:buttons :discard])]]))

(defn place-search-group [e! key]
  (place-search/place-search-form-group
   (tuck/wrap-path e! :transport-service key ::t-service/operation-area)
   (tr [:field-labels :transport-service-common ::t-service/operation-area])
   ::t-service/operation-area))

(defn service-hours-group []
  (let [tr* (tr-key [:field-labels :service-exception])
        write-time (fn [key]
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
      :prepare-for-save values/without-empty-rows
      :table-fields
      [{:name ::t-service/week-days
        :width "40%"
        :type :multiselect-selection
        :options t-service/days
        :show-option (tr-key [:enums ::t-service/day :full])
        :show-option-short (tr-key [:enums ::t-service/day :short])
        :required? true
        :is-empty? validation/empty-enum-dropdown?}
       {:name ::t-service/all-day
        :width "10%"
        :type :checkbox
        :write (fn [data all-day?]
                 (merge data
                        {::t-service/all-day all-day?}
                        (if all-day?
                          {::t-service/from (time/->Time 0 0 nil)
                           ::t-service/to (time/->Time 24 0 nil)}
                          {::t-service/from nil
                           ::t-service/to nil})))}

       {:name ::t-service/from
        :width "25%"
        :type :time
        :write (write-time ::t-service/from)
        :required? true
        :is-empty? time/empty-time?}
       {:name ::t-service/to
        :width "25%"
        :type :time
        :write (write-time ::t-service/to)
        :required? true
        :is-empty? time/empty-time?}]
      :delete?      true
      :add-label (tr [:buttons :add-new-service-hour])}

     {:name ::t-service/service-exceptions
      :type :table
      :prepare-for-save values/without-empty-rows
      :table-fields [{:name ::t-service/description
                      :label (tr* :description)
                      :type :localized-text
                      :is-empty? validation/empty-localized-text?}
                     {:name ::t-service/from-date
                      :type :date-picker
                      :label (tr* :from-date)}
                     {:name ::t-service/to-date
                      :type :date-picker
                      :label (tr* :to-date)}]
      :delete? true
      :add-label (tr [:buttons :add-new-service-exception])})))

(defn name-group [label]
  (form/group
   {:label label
    :columns 3
    :layout :row}

   (form/info (tr [:form-help :name-info]))

   {:name           ::t-service/name
    :type           :string
    :full-width?    true
    :container-class "col-md-6"
    :required?      true}

   {:name ::t-service/description
    :type :localized-text
    :is-empty? validation/empty-localized-text?
    :rows 2
    :full-width? true
    :container-class "col-md-8"}

   (form/subtitle (tr [:field-labels :transport-service ::t-service/available-from-and-to-title]))

   (form/info (tr [:form-help :available-from-and-to]))
    {:name ::t-service/available-from
    :type :date-picker
    :show-clear? true
    :hint-text (tr [:field-labels :transport-service ::t-service/available-from-nil])}

    {:name ::t-service/available-to
    :type :date-picker
    :show-clear? true
    :hint-text (tr [:field-labels :transport-service ::t-service/available-to-nil])}))
