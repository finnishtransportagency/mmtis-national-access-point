(ns ote.views.service-viewer
  (:require [ote.time :as time]
            [stylefy.core :as stylefy]
            [ote.mui-wrapper.icons :as ic]
            [clojure.string :as string]
            [clojure.spec.alpha :as s]
            [ote.ui.leaflet :as leaflet]
            [reagent.core :as r]
            [ote.db.transport-operator :as t-operator]
            [ote.db.transport-service :as t-service]
            [ote.app.controller.service-viewer :as svc]
            [ote.ui.common :as common-ui]
            [ote.style.base :as style-base]
            [ote.localization :refer [tr supported-languages tr-key selected-language]]
            [ote.ui.icons :as icons]
            [ote.theme.colors :as colors]
            [ote.ui.link-icon :refer [link-with-icon]]
            [ote.style.service-viewer :as service-viewer]
            [ote.app.controller.place-search :as place-search]
            [ote.ui.form-fields :as form-fields]
            [ote.style.base :as base]))

(defonce shown-language
  (r/atom (string/upper-case (name @selected-language))))

(defn change-lang-fn
  [new-val]
  (reset! shown-language new-val))


(defn information-row-with-selection
  [title information wide]
  (let [text ((keyword (string/lower-case @shown-language)) information)]
    [:div (stylefy/use-style (dissoc style-base/info-row :padding-right))
     [:strong (stylefy/use-style (if wide
                                   style-base/info-title-25
                                   style-base/info-title-50))
      title]
     [:div (stylefy/use-style (if wide
                                style-base/info-content-75
                                style-base/info-content-50))
      (if text
        [:span text]
        [:span (stylefy/use-style {:color colors/gray650
                                   :font-style "italic"})
         (tr [:service-viewer :not-disclosed])])
      [:div {:style {:padding-left "0.5rem"}}
       [:div
        [form-fields/field
         {:name :select-transport-operator
          :type :selection
          :auto-width true
          :style {:display "inline"
                  :width "55px"
                  :max-height "20px"}
          :show-option identity
          :update! change-lang-fn
          :options (map string/upper-case supported-languages)
          :auto-width? true
          :class-name "language-select"}
         @shown-language]]]]]))

(defn format-descriptions
  [data]
  (reduce
    (fn [new-collection item]
      (let [lang (keyword (string/lower-case (::t-service/lang item)))
            val (::t-service/text item)]
        (assoc new-collection lang val)))
    {}
    data))

(def columns (last (s/describe ::t-operator/transport-operator)))

(defn spacer
  []
  [:hr {:style {:margin "1.5rem 0"
                :border-bottom 0}}])
(defn info-sections-1-col
  ([title sv]
   [info-sections-1-col title sv {}])
  ([title sv settings]
   [:div
    (if (:sub-title settings)
      [:p (stylefy/use-style base/capital-bold) title]
      [:h4 title])
    [:div.info-block (stylefy/use-style service-viewer/info-container)
     [:div (stylefy/use-sub-style service-viewer/info-container :left-block)
      sv]]]))


(defn info-sections-2-cols
  ([title lv rv]
   [info-sections-2-cols title lv rv {}])
  ([title lv rv settings]
   [:div
    (if (:sub-title settings)
      [:p (stylefy/use-style base/capital-bold) title]
      [:h4 title])
    [:div.info-block (stylefy/use-style service-viewer/info-container)
     [:div (stylefy/use-sub-style service-viewer/info-container :left-block)
      lv]
     [:div (stylefy/use-sub-style service-viewer/info-container :right-block)
      rv]]]))

(defn info-sections-3-cols
  ([title lv mv rv]
   [info-sections-3-cols title lv mv rv {}])
  ([title lv mv rv settings]
   [:div
    (if (:sub-title settings)
      [:p (stylefy/use-style base/capital-bold) title]
      [:h4 title])
    [:div.info-block (stylefy/use-style service-viewer/info-container)
     [:div (stylefy/use-sub-style service-viewer/info-container :left-block)
      lv]
     [:div (stylefy/use-sub-style service-viewer/info-container :middle-block)
      mv]
     [:div (stylefy/use-sub-style service-viewer/info-container :right-block)
      rv]]]))

(def open-in-new-icon
  (ic/action-open-in-new {:style {:width 20
                                  :height 20
                                  :margin-right "0.5rem"
                                  :color colors/primary}}))

(defn- service-header
  [service-name o-id s-id]
  [:section
   [common-ui/linkify "/#/services" [:span [icons/arrow-back {:position "relative"
                                                              :top "6px"
                                                              :padding-right "5px"
                                                              :color style-base/link-color}]
                                     (tr [:service-search :back-link])]]
   [:h1 service-name]
   [link-with-icon {} open-in-new-icon (str "/export/geojson/" o-id "/" s-id) (tr [:service-viewer :open-in-geojson])]])

(defn- operator-info
  [title operator]
  (let [url (::t-operator/homepage operator)]
    [:section
     [info-sections-2-cols title
      [:div
       [common-ui/information-row-with-option
        (tr [:field-labels :transport-service-common ::t-service/company-name])
        (::t-operator/name operator) false]
       ;[common-ui/information-row-default (tr [:field-labels ::t-operator/visiting-address]) nil] ;(::common/street address)
       ;[common-ui/information-row-default (tr [:field-labels :transport-service-common ::t-service/contact-phone]) nil] ;(::t-operator/phone operator)
       ;[common-ui/information-row-default (tr [:field-labels :transport-service-common ::t-service/contact-email]) nil] ;(::t-operator/email operator)
       ]
      [:div
       [common-ui/information-row-with-option
        (tr [:field-labels ::t-operator/business-id])
        (::t-operator/business-id operator) false]
       ;[common-ui/information-row-default (tr [:organization-page :address-postal]) nil] ;(when address (str (::common/postal_code address) ", " (::common/post_office address)))
       ;[common-ui/information-row-default (tr [:organization-page :field-phone-mobile]) (::t-operator/phone operator)]
       [common-ui/information-row-with-option
        (tr [:field-labels :transport-service-common ::t-service/homepage])
        (when url
          [common-ui/linkify url url {:target "_blank"}]) false]]]
     [spacer]]))

(defn- service-info
  [title service]
  (let [brokerage?-text (tr [:service-viewer :brokerage (::t-service/brokerage? service)])
        sub-type-text (tr [:enums ::t-service/sub-type (::t-service/sub-type service)])
        transport-type-texts (map
                               (fn [key]
                                 (tr [:enums ::t-service/transport-type key]))
                               (::t-service/transport-type service))
        descriptions (format-descriptions (::t-service/description service))
        published-time (cond
                         (nil? (::t-service/published service))
                         (tr [:service-viewer :no])
                         (= (::t-service/published service) (js/Date. 0))
                         (tr [:service-viewer :yes])
                         :else
                         (time/format-timestamp-for-ui (::t-service/published service)))
        available-from (if (::t-service/available-from service)
                         (time/format-timestamp->date-for-ui (::t-service/available-from service))
                         (tr [:field-labels :transport-service ::t-service/available-from-nil]))
        available-to (if (::t-service/available-to service)
                       (time/format-timestamp->date-for-ui (::t-service/available-to service))
                       (tr [:field-labels :transport-service ::t-service/available-to-nil]))
        url (::t-service/homepage service)]
    [:section
     [info-sections-2-cols title
      [:div
       [common-ui/information-row-with-option (tr [:common-texts :name]) (::t-service/name service) false {:id "service-name"}]
       [common-ui/information-row-with-option
        (tr [:service-search :transport-type])
        (when (not-empty transport-type-texts) (string/join ", " transport-type-texts)) false]
       [information-row-with-selection (tr [:common-texts :description]) descriptions false]
       [common-ui/information-row-with-option (tr [:common-texts :brokerage]) brokerage?-text false]
       #_[common-ui/information-row-with-option
          (tr [:field-labels :transport-service-common ::t-service/contact-email])
          nil false]                                              ;(::t-service/contact-email service)
       ]
      [:div
       [common-ui/information-row-with-option (tr [:field-labels :transport-service ::t-service/type]) sub-type-text false]
       [common-ui/information-row-with-option (tr [:common-texts :published]) published-time false]
       [common-ui/information-row-with-option (tr [:common-texts :available-from]) available-from false]
       [common-ui/information-row-with-option (tr [:common-texts :available-to]) available-to false]
       #_[common-ui/information-row-with-option (tr [:organization-page :field-phone-mobile]) nil false] ;(::t-service/contact-phone service)
       [common-ui/information-row-with-option
        (tr [:field-labels :transport-service-common ::t-service/homepage])
        (when url
          [common-ui/linkify url url {:target "_blank"}]) false]]]
     [spacer]]))


(defn- leaflet-map
  [e! areas]
  (r/create-class
    {:component-did-mount #(leaflet/customize-zoom-controls e! % "leaflet" {:zoomInTitle (tr [:leaflet :zoom-in]) :zoomOutTitle (tr [:leaflet :zoom-out])})
     :component-did-update leaflet/update-bounds-from-layers
     :reagent-render
     (fn [e! areas]
       [leaflet/Map {;;:prefer-canvas true
                     :ref "leaflet"
                     :center #js [65 25]
                     :zoomControl false
                     :zoom 5}
        (leaflet/background-tile-map)
        (for [{:keys [place geojson]} areas]
          ^{:key (:ote.db.places/id place)}
          [leaflet/GeoJSON {:data geojson
                            :style {:color (if (:ote.db.places/primary? place)
                                             "green"
                                             "orange")}}])])}))

(defn- service-area
  [e! title ts]
  (let [areas (get-in (place-search/operation-area-to-places (::t-service/operation-area ts)) [:place-search :results])
        primary-area-names (map :name (get-in ts [:areas :primary]))
        secondary-area-names (map :name (get-in ts [:areas :secondary]))]
    [:section
     [info-sections-2-cols title
      [:div
       [common-ui/information-row-with-option
        (tr [:service-viewer :primary-areas])
        (when (not-empty primary-area-names)
          (string/join ", " primary-area-names))
        false]]
      [:div
       [common-ui/information-row-with-option
        (tr [:service-viewer :secondary-areas])
        (when (not-empty secondary-area-names)
          (string/join ", " secondary-area-names))
        false]]]
     [leaflet-map e! areas]
     [spacer]]))


(defn- published-interfaces
  [title data]
  [:div
   [:h4 title]
   (if data
     (doall
       (for [interface data
             :let [title (tr [:enums ::t-service/interface-data-content (first (::t-service/data-content interface))])
                   url (::t-service/url (::t-service/external-interface interface))
                   license (::t-service/license interface)
                   format (first (::t-service/format interface))
                   descriptions (format-descriptions (::t-service/description (::t-service/external-interface interface)))]]
         ^{:key (str (::t-service/id interface) (tr [:enums ::t-service/interface-data-content (first (::t-service/data-content interface))]))}
         [info-sections-2-cols (string/upper-case title)
          [:div
           [common-ui/information-row-with-option (tr [:service-search :homepage]) (when url [common-ui/linkify
                                                                                           url
                                                                                           url
                                                                                           {:target "_blank"}]) false]
           [common-ui/information-row-with-option
            (tr [:field-labels :transport-service-common ::t-service/license])
            license
            false]]
          [:div
           [common-ui/information-row-with-option (tr [:common-texts :format]) format false]
           [information-row-with-selection
            (tr [:field-labels :transport-service-common ::t-service/external-service-description])
            descriptions
            false]]
          {:sub-title true}]))
     [:h5 (stylefy/use-style (merge
                               style-base/info-content
                               {:color colors/gray650
                                :font-style "italic"}))
      (tr [:service-viewer :not-disclosed])])
   [spacer]])


(defn- luggage-warnings
  [title data]
  (let [warning-texts (format-descriptions data)]
    [:section
     [info-sections-1-col title
      [:div
       [information-row-with-selection (tr [:common-texts :description]) warning-texts true]]]
     [spacer]]))

(defn- real-time-info
  [title data]
  (let [url (::t-service/url data)
        descriptions (format-descriptions (::t-service/description data))]
    [:section
     [info-sections-2-cols title
      [:div
       [common-ui/information-row-with-option
        (tr [:field-labels :transport-service-common ::t-service/homepage])
        (when url [common-ui/linkify url url {:target "_blank"}]) false]]
      [:div
       [information-row-with-selection (tr [:common-texts :description]) descriptions false]]]
     [spacer]]))

(defn- pre-booking
  [title data]
  [:section
   [info-sections-1-col title
    [:div
     [common-ui/information-row-with-option
      (tr [:service-viewer :reservation-possibilities-responsibilities])
      (tr [:enums ::t-service/advance-reservation data])
      true]]]
   [spacer]])

(defn- booking-service
  [title data shown-language change-lang-fn]
  (let [descriptions (format-descriptions (::t-service/description data))
        url (::t-service/url data)]
    [:section
     [info-sections-2-cols title
      [:div
       [common-ui/information-row-with-option (tr [:field-labels :transport-service-common ::t-service/homepage])
        (when url [common-ui/linkify url url {:target "_blank"}]) false]]
      [:div
       [information-row-with-selection (tr [:common-texts :description]) descriptions false]]]
     [spacer]]))

(defn- accessibility-and-other-services
  [title data shown-language change-lang-fn]
  (let [url (:url data)
        guaranteed-descriptions (format-descriptions (get-in data [:descriptions :guaranteed]))
        limited-descriptions (format-descriptions (get-in data [:descriptions :limited]))]
    [:section
     [:h4 title]
     (doall
       (for [[k v] (:accessibility-infos data)
             :let [title (tr [:field-labels :transport-service-common k])
                   list (reduce (fn [new-col [status keys]]
                                  (assoc
                                    new-col
                                    status
                                    (mapv
                                      (fn [key]
                                        (tr [:enums k key]))
                                      keys)))
                                {}
                                v)]]
         ^{:key k}
         [info-sections-1-col title
          [:div
           [common-ui/information-row-with-option
            (tr [:service-viewer :guaranteed-accessibility])
            (when (not-empty (:guaranteed list))
              (string/capitalize
                (string/join ", " (:guaranteed list))))
            true]
           [common-ui/information-row-with-option
            (tr [:service-viewer :limited-accessibility])
            (when (not-empty (:limited list))
              (string/capitalize
                (string/join ", " (:limited list))))
            true]]
          {:sub-title true}]))
     [info-sections-1-col (tr [:service-viewer :other-accessibility-info])
      [:div
       [common-ui/information-row-with-option (tr [:service-viewer :accessibility-website]) (when url [common-ui/linkify url url {:target "_blank"}]) true]
       [information-row-with-selection (tr [:service-viewer :guaranteed-accessibility-description]) guaranteed-descriptions true]
       [information-row-with-selection (tr [:service-viewer :limited-accessibility-description]) limited-descriptions true]]]
     [spacer]]))

(defn- price-information
  [title data shown-language change-lang-fn]
  [:section
   [:h4 title]
   (let [price-classes (:price-classes data)
         payment-methods (map #(tr [:enums ::t-service/payment-methods %]) (:payment-methods data))
         description (format-descriptions (:payment-method-description data))
         pricing-description (format-descriptions (get-in data [:pricing ::t-service/description]))
         pricing-url (get-in data [:pricing ::t-service/url])]
     [:div
      [:div {:style {:margin-bottom "1rem"}}
       (doall
         (for [class price-classes]
           ^{:key (str (::t-service/name class) (::t-service/price-per-unit class))}
           [:div (stylefy/use-style service-viewer/info-row)
            [:div (stylefy/use-sub-style service-viewer/info-seqment :left)
             [common-ui/information-row-with-option
              (tr [:field-labels :parking ::t-service/price-class-name])
              (::t-service/name class) true]]
            [:div (stylefy/use-sub-style service-viewer/info-seqment :mid)
             [common-ui/information-row-with-option
              (tr [:service-viewer :price/unit])
              (::t-service/price-per-unit class) true]]
            [:div (stylefy/use-sub-style service-viewer/info-seqment :right)
             [common-ui/information-row-with-option
              (tr [:service-viewer :pricing-basis])
              (::t-service/unit class) true]]]))]
      [common-ui/information-row-with-option (tr [:parking-page :header-payment-methods])
       (when (not-empty payment-methods) (string/lower-case (string/join ", " payment-methods))) true]
      [information-row-with-selection (tr [:common-texts :description]) description true]
      [information-row-with-selection
       (tr [:field-labels :passenger-transportation ::t-service/pricing-description])
       pricing-description true]
      [common-ui/information-row-with-option
       (tr [:field-labels :passenger-transportation ::t-service/pricing-url])
       (when pricing-url
         [common-ui/linkify pricing-url pricing-url {:target "_blank"}]) true]])
   [spacer]])

(defn- service-hours
  [title data shown-language change-lang-fn]
  [:section
   [:h4 title]
   (let [service-times (:service-hours data)
         exceptions (:exceptions data)
         info (:service-hours-info data)]
     [:div
      (doall
        (for [time service-times
              :let [start-minutes (if (= (get-in time [::t-service/from :minutes]) 0)
                                    "00"
                                    (get-in time [::t-service/from :minutes]))
                    end-minutes (if (= (get-in time [::t-service/to :minutes]) 0)
                                  "00"
                                  (get-in time [::t-service/to :minutes]))]]
          ^{:key (str (::t-service/week-days time))}
          [:div (stylefy/use-style service-viewer/info-row)
           [:div (stylefy/use-sub-style service-viewer/info-seqment :left)
            [common-ui/information-row-with-option
             (tr [:service-viewer :day-of-week])
             (string/join ", " (map
                                 #(string/lower-case (tr [:enums ::t-service/day :short %]))
                                 (::t-service/week-days time))) true]]
           [:div (stylefy/use-sub-style service-viewer/info-seqment :mid)
            [common-ui/information-row-with-option
             (tr [:common-texts :start-time])
             (str (get-in time [::t-service/from :hours]) ":" start-minutes) true]]
           [:div (stylefy/use-sub-style service-viewer/info-seqment :right)
            [common-ui/information-row-with-option
             (tr [:common-texts :ending-time])
             (str (get-in time [::t-service/to :hours]) ":" end-minutes) true]]]))
      [:div {:style {:margin-bottom "0.5rem"}}
       (doall
         (for [exception exceptions
               :let [description (::t-service/description exception)
                     start-date (time/format-timestamp->date-for-ui (::t-service/from-date exception))
                     end-date (time/format-timestamp->date-for-ui (::t-service/to-date exception))]]
           ^{:key (str start-date end-date)}
           [:div (stylefy/use-style service-viewer/info-row)
            [:div (stylefy/use-sub-style service-viewer/info-seqment :left)
             [information-row-with-selection (tr [:service-viewer :exception]) (format-descriptions description) true]]
            [:div (stylefy/use-sub-style service-viewer/info-seqment :mid)
             [common-ui/information-row-with-option (tr [:common-texts :start-time]) start-date] true]
            [:div (stylefy/use-sub-style service-viewer/info-seqment :right)
             [common-ui/information-row-with-option (tr [:common-texts :ending-time]) end-date true]]]))]
      [information-row-with-selection
       (tr [:field-labels :transport-service-common ::t-service/service-hours-info])
       (format-descriptions info) true]])
   [spacer]])

(defn service-view
  [e! {{to :transport-operator ts :transport-service} :service-view}]
  (let [interfaces (::t-service/external-interfaces ts)
        warnings (get-in ts [::t-service/passenger-transportation ::t-service/luggage-restrictions])
        real-time-info-data (get-in ts [::t-service/passenger-transportation ::t-service/real-time-information])
        pre-booking-data (get-in ts [::t-service/passenger-transportation ::t-service/advance-reservation])
        booking-data (get-in ts [::t-service/passenger-transportation ::t-service/booking-service])
        accessibility-data (:accessibility ts)
        pricing-data (:pricing-info ts)
        service-hours-data (:service-hours-info ts)]
    (if (or (= (:error to) 404)
            (= (:error ts) 404))
      [:h2 (tr [:common-texts :data-not-found])]
      [:div
       [service-header (::t-operator/name to) (::t-operator/id to) (::t-service/id ts)]
       [operator-info (tr [:service-viewer :operator-info]) to]
       [service-info (tr [:service-viewer :transport-service-info]) ts]
       [service-area e! (tr [:service-viewer :service-area]) ts]
       [published-interfaces (tr [:service-viewer :published-interfaces]) interfaces]
       [luggage-warnings (tr [:service-viewer :luggage-warnings]) warnings]
       [real-time-info (tr [:service-viewer :real-time-info]) real-time-info-data]
       [pre-booking (tr [:service-viewer :advance-reservation]) pre-booking-data]
       [booking-service (tr [:service-viewer :reservation-service]) booking-data]
       [accessibility-and-other-services (tr [:service-viewer :accessibility-and-other-services]) accessibility-data]
       [price-information (tr [:service-viewer :price-information]) pricing-data]
       [service-hours (tr [:service-viewer :service-hours]) service-hours-data]])))
