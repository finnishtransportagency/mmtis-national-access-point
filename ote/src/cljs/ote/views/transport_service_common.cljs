(ns ote.views.transport-service-common
  "View parts that are common to all transport service forms."
  (:require [tuck.core :as tuck]
            [ote.db.transport-service :as t-service]
            [ote.db.transport-operator :as t-operator]
            [ote.localization :refer [tr tr-key tr-tree]]
            [ote.ui.form :as form]
            [ote.db.common :as common]
            [ote.ui.common :refer [linkify dialog tooltip-wrapper]]
            [ote.ui.buttons :as buttons]
            [ote.app.controller.transport-service :as ts]
            [ote.views.place-search :as place-search]
            [clojure.string :as str]
            [ote.time :as time]
            [ote.util.values :as values]
            [ote.style.form :as style-form]
            [cljs-react-material-ui.reagent :as ui]
            [ote.ui.validation :as validation]
            [stylefy.core :as stylefy]
            [ote.style.base :as style-base]
            [cljs-react-material-ui.icons :as ic]
            [ote.app.controller.flags :as flags]))

(defn advance-reservation-group
  "Creates a form group for in advance reservation.
   Form displays header text and selection list by radio button group."
  []
  (form/group
   {:label (tr [:field-labels :transport-service-common ::t-service/advance-reservation])
    :columns 3
    :layout :row}

   (form/info (tr [:form-help :advance-reservation-info]))

   {:name ::t-service/advance-reservation
    :type :selection
    :show-option (tr-key [:enums ::t-service/advance-reservation])
    :options t-service/advance-reservation
    :radio? true
    :required? true
    :container-class "col-md-12"}))

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
     :full-width? true
     :container-class "col-xs-12 col-sm-6 col-md-6"}

    {:name ::t-service/description
     :type  :localized-text
     :rows  1
     :read  (comp ::t-service/description service-url-field)
     :write (fn [data desc]
             (assoc-in data [service-url-field ::t-service/description] desc))
     :container-class "col-xs-12 col-sm-6 col-md-6"
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
                     :type :localized-text}]
     :delete?      true
     :add-label    (tr [:buttons :add-new-service-link])}))

(defn- gtfs-viewer-link [{interface ::t-service/external-interface format ::t-service/format}]
  (when (seq format)
    (let [format (str/lower-case (first format))]
      (when (or (= "gtfs" format) (= "kalkati.net" format))
        (linkify
          (str "#/routes/view-gtfs?url=" (.encodeURIComponent js/window
                                                              (::t-service/url interface))
               (when (= "kalkati.net" format)
                 "&type=kalkati"))
          [ui/icon-button
           [(tooltip-wrapper ic/action-visibility) {:style style-base/icon-medium}
            {:text (tr [:form-help :external-interfaces-tooltips :view-routes])}]]
          {:target "_blank"})))))

(defn external-interfaces
  "Creates a form group for external services. Displays help texts conditionally by transport operator type."
  [& [e! type sub-type]]
  (let [type (or type :other)]

    (form/group
      {:label (tr [:field-labels :transport-service-common ::t-service/external-interfaces])
       :columns 3}

      (form/info
        [:div
         [:div {:style {:margin-bottom "5px"}}
          [:b (if (= :schedule sub-type)
                [:span (str (tr [:form-help :external-interfaces-intro-1]) " ")
                 [linkify "https://liikennevirasto.fi/rae" (str (tr [:form-help :RAE-link-text]) ". ")
                  {:target "_blank"}]
                 (when (flags/enabled? :sea-routes)
                  [:span
                   (str (tr [:form-help :external-interfaces-intro-2]) " ")
                    [linkify "/ote/#/routes" (tr [:form-help :SEA-ROUTE-link-text])
                      {:target "_blank"}]])]
                (tr [:form-help :external-interfaces-intro]))]]
         [:div (tr [:form-help :external-interfaces])]
         [dialog
          (tr [:form-help :external-interfaces-read-more :link])
          (tr [:form-help :external-interfaces-read-more :dialog-title])
          [:div
           (tr [:form-help :external-interfaces-read-more :dialog-text])]]
         (when (= :passenger-transportation type)
           [:div {:style {:margin-top "20px"}}
            [:b (tr [:form-help :external-interfaces-payment-systems])]])]
        {:type :generic})

      {:name ::t-service/external-interfaces
       :type :table
       :prepare-for-save values/without-empty-rows
       :table-fields [{:name ::t-service/data-content
                       :type :chip-input
                       :tooltip (tr [:form-help :external-interfaces-tooltips :data-content])
                       :width "20%"
                       :full-width? true
                       :auto-select? true
                       :open-on-focus? true
                       ;; Translate visible suggestion text, but keep the value intact.
                       :suggestions (mapv (fn [val]
                                            {:text (tr [:enums ::t-service/interface-data-content val]) :value val})
                                          t-service/interface-data-contents)
                       :suggestions-config {:text :text :value :value}
                       :write (fn [data vals]
                                (assoc-in data [::t-service/data-content]
                                          ;; Values loose their keyword status inside the component, so we'll make
                                          ;; sure that they will be keywords in the state.
                                          (mapv (comp keyword :value) vals)))
                       :read #(as-> % data
                                    (get-in data [::t-service/data-content])
                                    (mapv (fn [val]
                                            {:text (tr [:enums ::t-service/interface-data-content val]) :value val})
                                          data))
                       :required? true
                       :is-empty? validation/empty-enum-dropdown?}
                      {:name ::t-service/external-service-url
                       :type :string
                       :tooltip (tr [:form-help :external-interfaces-tooltips :external-service-url])
                       :width "20%"
                       :full-width? true
                       :on-blur #(e! (ts/->EnsureExternalInterfaceUrl (-> % .-target .-value)))
                       :read (comp ::t-service/url ::t-service/external-interface)
                       :write #(assoc-in %1 [::t-service/external-interface ::t-service/url] %2)
                       :required? true}
                      {:name :ext-validation
                       :type :component
                       :component (fn [{{external-interface ::t-service/external-interface :as service} :data}]
                                    (let [url-status (get-in external-interface [:url-status :status])]
                                      [:span
                                       (if-not url-status
                                         [gtfs-viewer-link service]
                                         (if (= :success url-status)
                                           [:span [(tooltip-wrapper ic/action-done) {:style (merge style-base/icon-small
                                                                                                   {:color "green"})}
                                                   {:text (tr [:field-labels :transport-service-common :external-interfaces-ok])}]
                                            [gtfs-viewer-link service]]
                                           [(tooltip-wrapper ic/alert-warning) {:style (merge style-base/icon-small
                                                                                              {:color "cccc00"})}
                                            {:text (tr [:field-labels :transport-service-common :external-interfaces-warning])}]))]))
                       :read #(identity %)
                       :width "8%"}
                      {:name ::t-service/format
                       :type :autocomplete
                       :tooltip (tr [:form-help :external-interfaces-tooltips :format])
                       :open-on-focus? true
                       :suggestions ["GTFS" "Kalkati.net" "SIRI" "NeTEx" "GeoJSON" "JSON" "CSV"]
                       :max-results 10
                       :width "15%"
                       :full-width? true
                       :required? true
                       ;; Wrap value with vector to support current type of format field in the database.
                       :write #(assoc-in %1 [::t-service/format] #{%2})
                       :read #(first (get-in % [::t-service/format]))}
                      {:name ::t-service/license
                       :type :autocomplete
                       :open-on-focus? true
                       :tooltip (tr [:form-help :external-interfaces-tooltips :license])
                       :width "17%"
                       :full-width? true
                       :suggestions (tr-tree [:licenses :external-interfaces])
                       :max-results 10}
                      {:name ::t-service/external-service-description
                       :type :localized-text
                       :tooltip (tr [:form-help :external-interfaces-tooltips :external-service-description])
                       :width "20%"
                       :full-width? true
                       :read (comp ::t-service/description ::t-service/external-interface)
                       :write #(assoc-in %1 [::t-service/external-interface ::t-service/description] %2)
                       :required? false}]
       :delete? true
       :add-label (tr [:buttons :add-external-interface])}

      {:name ::t-service/notice-external-interfaces?
       :type :checkbox
       :required? true
       :style style-form/padding-top
       :validate [[:checked?]]})))

(defn companies-group
  "Creates a form group for companies. A parent company can list its companies."
  [e!]
  (form/group
    {:label (tr [:field-labels :transport-service-common ::t-service/companies])
    :columns 3}

    (form/info (tr [:form-help :companies-main-info]) {:type :generic})

    {:name ::t-service/company-source
     :read identity
     :write #(merge %1 %2)
     :type :company-source
     :enabled-label (tr [:field-labels :parking :maximum-stay-limited])
     :container-style style-form/full-width
     :on-file-selected #(ts/read-companies-csv! e! (.-target %))
     :on-url-given #(e! (ts/->EnsureCsvFile))}))

(defn brokerage-group
  "Creates a form group for brokerage selection."
  [e!]
  (form/group
    {:label   (tr [:passenger-transportation-page :header-brokerage])
     :columns 3}

    {:name          ::t-service/brokerage?
     :extended-help {:help-text      (tr [:form-help :brokerage?])
                     :help-link-text (tr [:form-help :brokerage-link])
                     :help-link      "https://www.trafi.fi/tieliikenne/ammattiliikenne/liikenneluvat_trafiin/valitys-_ja_yhdistamispalvelut"}
     :type          :checkbox}))


(defn contact-info-group []
  (form/group
   {:label  (tr [:passenger-transportation-page :header-contact-details])
    :columns 3
    :layout :row}

   (form/info (tr [:form-help :description-why-contact-info]))

   {:name        ::common/street
    :type        :string
    :container-class "col-xs-12 col-sm-6 col-md-4"
    :full-width?  true
    :read (comp ::common/street ::t-service/contact-address)
    :write (fn [data street]
             (assoc-in data [::t-service/contact-address ::common/street] street))
    :label (tr [:field-labels ::common/street])
    :required? true}

   {:name        ::common/postal_code
    :type        :string
    :container-class "col-xs-12 col-sm-6 col-md-2"
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
    :container-class "col-xs-12 col-sm-6 col-md-5"
    :full-width?  true
    :read (comp ::common/post_office ::t-service/contact-address)
    :write (fn [data post-office]
             (assoc-in data [::t-service/contact-address ::common/post_office] post-office))
    :label (tr [:field-labels ::common/post_office])
    :required? true}

   {:name        ::t-service/contact-email
    :type        :string
    :container-class "col-xs-12 col-sm-6 col-md-4"
    :full-width?  true}

   {:name        ::t-service/contact-phone
    :type        :string
    :container-class "col-xs-12 col-sm-6 col-md-2"
    :max-length  16
    :full-width? true}

   {:name        ::t-service/homepage
    :type        :string
    :container-class "col-xs-12 col-sm-6 col-md-5"
    :full-width?  true}))

(defn footer
  "Transport service form -footer element. All transport service form should be using this function."
  [e! {published? ::t-service/published? :as data} schemas app]
  (let [name-missing? (str/blank? (::t-service/name data))
        show-footer? (if (get-in app [:transport-service ::t-service/id])
                       (ts/is-service-owner? app)
                       true)]

    (when true ;show-footer? - Take owner check away for now
      [:div.row
       (when (not (form/can-save? data))
         [ui/card {:style {:margin-bottom "1em"}}
          [ui/card-text {:style {:color "#be0000" :padding-bottom "0.6em"}} (tr [:form-help :publish-missing-required])]])

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
        (tr [:buttons :discard])]])))

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
                      :type :localized-text}
                     {:name ::t-service/from-date
                      :type :date-picker
                      :label (tr* :from-date)}
                     {:name ::t-service/to-date
                      :type :date-picker
                      :label (tr* :to-date)}]
      :delete? true
      :add-label (tr [:buttons :add-new-service-exception])}

     {:name ::t-service/service-hours-info
      :label (tr [:field-labels :transport-service-common ::t-service/service-hours-info])
      :type :localized-text
      :full-width? true
      :container-class "col-xs-12"})))

(defn name-group [label]
  (form/group
   {:label label
    :columns 3
    :layout :row}

   (form/info (tr [:form-help :name-info]))

   {:name           ::t-service/name
    :type           :string
    :full-width?    true
    :container-class "col-xs-12 col-sm-12 col-md-6"
    :required?      true}

   {:name ::t-service/description
    :type :localized-text
    :rows 2
    :full-width? true
    :container-class "col-xs-12 col-sm-12 col-md-8"}

   (form/subtitle (tr [:field-labels :transport-service ::t-service/available-from-and-to-title]))

   (form/info (tr [:form-help :available-from-and-to]))
    {:name ::t-service/available-from
    :type :date-picker
    :show-clear? true
    :hint-text (tr [:field-labels :transport-service ::t-service/available-from-nil])
     :container-class "col-xs-12 col-sm-6 col-md-3"}

    {:name ::t-service/available-to
    :type :date-picker
    :show-clear? true
    :hint-text (tr [:field-labels :transport-service ::t-service/available-to-nil])
     :container-class "col-xs-12 col-sm-6 col-md-3"}))

(defn transport-type [sub-type]
  (form/group
   {:label (tr [:field-labels :transport-service-common ::t-service/transport-type])
    :columns 3
    :layout :row}

   (when (not= sub-type :taxi)
     (form/info (tr [:form-help :transport-type-info])))

   {:name ::t-service/transport-type
    :type :checkbox-group
    :container-class "col-md-12"
    :header? false
    :required? true
    :options t-service/transport-type
    :show-option (tr-key [:enums ::t-service/transport-type])
    :option-enabled? (fn [option]
                       (if (= sub-type :taxi)
                         false
                          true))}))


(defn place-search-dirty-event [e!]
  ;; To set transport service form dirty when adding / removing places using the place-search component,
  ;; we'll have to manually trigger EditTransportService event with empty data.
  #(do
     (e! (ts/->EditTransportService {}))
     (e! %)))
