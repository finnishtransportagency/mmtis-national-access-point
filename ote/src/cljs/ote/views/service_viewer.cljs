(ns ote.views.service-viewer
  (:require [ote.time :as time]
            [stylefy.core :as stylefy]
            [cljs-react-material-ui.icons :as ic]
            [clojure.string :as string]
            [clojure.spec.alpha :as s]
            [ote.ui.leaflet :as leaflet]
            [reagent.core :as r]
            [ote.db.transport-operator :as t-operator]
            [ote.db.transport-service :as t-service]
            [ote.db.netex :as netex]
            [ote.db.common :as common]
            [ote.util.transport-service-util :as tsu]
            [ote.app.controller.service-viewer :as svc]
            [ote.ui.common :as common-ui]
            [ote.style.base :as style-base]
            [ote.localization :refer [tr supported-languages tr-key selected-language tr-tree]]
            [ote.ui.icons :as icons]
            [ote.theme.colors :as colors]
            [ote.ui.link-icon :refer [link-with-icon]]
            [ote.style.service-viewer :as service-viewer]
            [ote.app.controller.place-search :as place-search]
            [ote.ui.form-fields :as form-fields]
            [ote.style.base :as base]
            [clojure.string :as str]))

(defonce shown-language
         (r/atom (string/upper-case (name @selected-language))))

(defn format-time-key->str [time-map first-key second-key]
  (let [v (str (get-in time-map [first-key second-key]))]
    (if (= (count v) 1)
      (str "0" v)
      v)))

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
      (let [raw-lang (::t-service/lang item)
            lang (if raw-lang
                   (keyword (string/lower-case (::t-service/lang item)))
                   (keyword "fi"))
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
   [:h1 {:style {:margin-top "1rem"}} service-name]
   [link-with-icon {:target-blank? true} open-in-new-icon (str "/export/geojson/" o-id "/" s-id) (tr [:service-viewer :open-in-geojson])]])

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
  [title service sub-type-key]
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
       (when (= sub-type-key ::passenger-transportation)
         [common-ui/information-row-with-option (tr [:common-texts :brokerage]) brokerage?-text false])
       #_[common-ui/information-row-with-option
          (tr [:field-labels :transport-service-common ::t-service/contact-email])
          nil false]                                        ;(::t-service/contact-email service)
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
             :let [title (string/join ", "
                                       (map
                                         #(tr [:enums ::t-service/interface-data-content %])
                                         (::t-service/data-content interface)))
                   url (::t-service/url (::t-service/external-interface interface))
                   license (::t-service/license interface)
                   format (first (::t-service/format interface))
                   descriptions (format-descriptions (::t-service/description (::t-service/external-interface interface)))]]
         ^{:key (str (::t-service/id interface) title)}
         [info-sections-2-cols (string/upper-case title)
          [:div
           [common-ui/information-row-with-option
            (tr [:service-search :homepage])
            (when url [common-ui/linkify
                       url
                       url
                       {:target "_blank"}])
            false]
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
      (tr [:service-viewer :not-disclosed])])])

(defn- ote-interfaces
  [title data]
  [:div
   (when data
     (doall
       (for [interface data
             :let [title (string/join ", "
                                      (map
                                        #(tr [:enums ::netex/interface-data-content %])
                                        (::netex/data-content interface)))
                   url (:url interface)
                   format (:format interface)
                   descriptions ""]]
         ^{:key (str (::netex/id interface) title)}
         [info-sections-2-cols (string/upper-case title)
          [:div
           [common-ui/information-row-with-option
            (tr [:service-search :homepage])
            (when url [common-ui/linkify
                       url
                       url
                       {:target "_blank"}])
            false]
           [common-ui/information-row-with-option
            (tr [:field-labels :transport-service-common ::t-service/license])
            nil
            false]]
          [:div
           [common-ui/information-row-with-option (tr [:common-texts :format]) format false]
           [information-row-with-selection
            (tr [:field-labels :transport-service-common ::t-service/external-service-description])
            nil
            false]]
          {:sub-title true}])))])

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
  [title data]
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

(defn- passenger-accessibility-and-other-services
  [title data]
  (let [url (:url data)
        guaranteed-descriptions (format-descriptions (get-in data [:descriptions :guaranteed]))
        limited-descriptions (format-descriptions (get-in data [:descriptions :limited]))
        others (:other data)
        other-translations (map
                             #(tr [:enums ::t-service/additional-services %])
                             others)]
    [:section
     [:h4 title]
     (doall
       (for [[k v] (:accessibility-infos data)
             :let [title (tr [:field-labels :transport-service-common k])
                   list (reduce (fn [new-col [status keys]]
                                  (assoc
                                    new-col
                                    status
                                    (mapv #(tr [:enums k %])
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
       [common-ui/information-row-with-option
        (tr [:enums ::t-service/interface-data-content :other-services])
        (when (not-empty other-translations)
          (string/capitalize
            (string/join ", " other-translations)))
        true]
       [common-ui/information-row-with-option
        (tr [:service-viewer :accessibility-website])
        (when url [common-ui/linkify url url {:target "_blank"}])
        true]
       [information-row-with-selection
        (tr [:service-viewer :guaranteed-accessibility-description])
        guaranteed-descriptions
        true]
       [information-row-with-selection
        (tr [:service-viewer :limited-accessibility-description])
        limited-descriptions
        true]]]
     [spacer]]))

(defn- rental-accessibility-and-other-services
  [title data]
  (let [url (:url data)
        guaranteed-vehicle-accessibility (map #(tr [:enums ::t-service/vehicle-accessibility %]) (get data ::t-service/guaranteed-vehicle-accessibility))
        limited-vehicle-accessibility (map #(tr [:enums ::t-service/vehicle-accessibility %]) (get data ::t-service/limited-vehicle-accessibility))
        guaranteed-aid (map #(tr [:enums ::t-service/transportable-aid %]) (get data ::t-service/guaranteed-transportable-aid))
        limited-aid (map #(tr [:enums ::t-service/transportable-aid %]) (get data ::t-service/limited-transportable-aid))
        guaranteed-description (format-descriptions (get data ::t-service/guaranteed-accessibility-description))
        limited-description (format-descriptions (get data ::t-service/limited-accessibility-description))
        accessibility-url (get data ::t-service/accessibility-info-url)]
    [:section
     [:h4 title]
     [info-sections-1-col (string/upper-case (tr [:service-viewer :transport-vehicles]))
      [:div
       [common-ui/information-row-with-option (tr [:service-viewer :guaranteed-accessibility])
        (when (not-empty guaranteed-vehicle-accessibility)
          (string/capitalize
            (string/join ", " guaranteed-vehicle-accessibility)))
        true]
       [common-ui/information-row-with-option (tr [:service-viewer :limited-accessibility])
        (when (not-empty limited-vehicle-accessibility)
          (string/capitalize
            (string/join ", " limited-vehicle-accessibility)))
        true]]]
     [info-sections-1-col (string/upper-case (tr [:field-labels :transport-service-common :ote.db.transport-service/transportable-aid]))
      [:div
       [common-ui/information-row-with-option (tr [:service-viewer :guaranteed-accessibility])
        (when (not-empty guaranteed-vehicle-accessibility)
          (string/capitalize
            (string/join ", " guaranteed-aid)))
        true]

       [common-ui/information-row-with-option (tr [:service-viewer :limited-accessibility])
        (when (not-empty limited-vehicle-accessibility)
          (string/capitalize
            (string/join ", " limited-aid)))
        true]]]
     [info-sections-1-col (string/upper-case (tr [:service-viewer :other-accessibility-descriptions]))
      [:div
       [common-ui/information-row-with-option (tr [:service-viewer :accessibility-website]) accessibility-url true]
       [information-row-with-selection (tr [:service-viewer :guaranteed-accessibility-description]) guaranteed-description true]
       [information-row-with-selection (tr [:service-viewer :limited-accessibility-description]) limited-description true]]]
     [spacer]]))

(defn- price-information
  [title data]
  [:section
   [:h4 title]
   (let [price-classes (:price-classes data)
         payment-methods (map #(tr [:enums ::t-service/payment-methods %]) (:payment-methods data))
         payment-method-desc (format-descriptions (:payment-method-description data))
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
              (::t-service/name class) false]]
            [:div (stylefy/use-sub-style service-viewer/info-seqment :mid)
             [common-ui/information-row-with-option
              (tr [:service-viewer :price/unit])
              (::t-service/price-per-unit class) true]]
            [:div (stylefy/use-sub-style service-viewer/info-seqment :right)
             [common-ui/information-row-with-option
              (tr [:service-viewer :pricing-basis])
              (::t-service/unit class) true]]]))]
      [common-ui/information-row-with-option
       (tr [:parking-page :header-payment-methods])
       (when (not-empty payment-methods) (string/lower-case (string/join ", " payment-methods)))
       true]
      [information-row-with-selection (tr [:service-viewer :payment-method-description]) payment-method-desc true]
      [information-row-with-selection
       (tr [:field-labels :passenger-transportation ::t-service/pricing-description])
       pricing-description
       true]
      [common-ui/information-row-with-option
       (tr [:field-labels :passenger-transportation ::t-service/pricing-url])
       (when pricing-url
         [common-ui/linkify pricing-url pricing-url {:target "_blank"}]) true]])
   [spacer]])

(defn- service-hours [title service-hours-info]
  (let [service-hours (:service-hours service-hours-info)
        exceptions (:exceptions service-hours-info)
        info (:service-hours-info service-hours-info)]
    [:section
     [:h4 title]
     [:div
      (doall
        (for [time service-hours
              :let [starting-time (str (format-time-key->str time ::t-service/from :hours) ":" (format-time-key->str time ::t-service/from :minutes))
                    ending-time (str (format-time-key->str time ::t-service/to :hours) ":" (format-time-key->str time ::t-service/to :minutes))]]
          ^{:key (str (::t-service/week-days time))}
          [:div (stylefy/use-style service-viewer/info-row)
           [:div (stylefy/use-sub-style service-viewer/info-seqment :left)
            [common-ui/information-row-with-option
             (tr [:service-viewer :day-of-week])
             (string/join ", " (map
                                 #(string/lower-case (tr [:enums ::t-service/day :short %]))
                                 (tsu/reorder-week-days (::t-service/week-days time))))
             true]]
           [:div (stylefy/use-sub-style service-viewer/info-seqment :mid)
            [common-ui/information-row-with-option (tr [:common-texts :start-time]) starting-time true]]
           [:div (stylefy/use-sub-style service-viewer/info-seqment :right)
            [common-ui/information-row-with-option (tr [:common-texts :ending-time]) ending-time true]]]))
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
             [common-ui/information-row-with-option (tr [:common-texts :start-time]) start-date true]]
            [:div (stylefy/use-sub-style service-viewer/info-seqment :right)
             [common-ui/information-row-with-option (tr [:common-texts :ending-time]) end-date true]]]))]]
     [spacer]]))

(defn- vehicle-and-price [title vehicle-classes vehicle-price-url]
  [:section
   [:h4 title]
   (let [vehicle-classes vehicle-classes]
     [:div
      [common-ui/information-row-with-option (tr [:service-viewer :url]) vehicle-price-url true]
      (doall
        (for [vc vehicle-classes
              :let [license-required (get vc ::t-service/license-required)
                    vehicle-type (get vc ::t-service/vehicle-type)
                    minimum-age (get vc ::t-service/minimum-age)]]
          ^{:key (str vc)}
          [info-sections-2-cols vehicle-type
           [:div
            [common-ui/information-row-with-option (tr [:field-labels :rentals :ote.db.transport-service/license-required]) license-required false]
            (doall
              (for [price-row (::t-service/price-classes vc)
                    :let [price (::t-service/price-per-unit price-row)]]
                ^{:key (str vehicle-type "-" license-required "-" price)}
                [common-ui/information-row-with-option (tr [:field-labels :rentals :ote.db.transport-service/price-classes]) (str price " €") false]))]
           [:div
            [common-ui/information-row-with-option (tr [:field-labels :rentals :ote.db.transport-service/minimum-age]) minimum-age false]
            (doall
              (for [price-row (::t-service/price-classes vc)
                    :let [unit (::t-service/unit price-row)]]
                ^{:key (str vehicle-type "-" minimum-age "-" unit)}
                [common-ui/information-row-with-option (tr [:service-viewer :pricing-basis]) unit false]))]]))])
   [spacer]])

(defn- restrictions-and-payment-methods
  [title luggage-restrictions payment-methods]
  (let [luggage-restrictions (format-descriptions luggage-restrictions)
        payment-methods (string/join ", "
                                     (map #(tr [:enums ::t-service/payment-methods %]) payment-methods))]
    [:section
     [info-sections-2-cols title
      [:div
       [common-ui/information-row-with-option (tr [:service-viewer :payment-methods]) payment-methods false]]
      [:div
       [information-row-with-selection (tr [:service-viewer :luggage-warnings]) luggage-restrictions false]]]
     [spacer]]))

(defn- additional-services [title data]
  [:section
   [info-sections-3-cols title
    [:div
     (doall
       (for [services-row data
             :let [translated-service (tr [:enums ::t-service/additional-services (get services-row ::t-service/additional-service-type)])]]
         ^{:key (str services-row)}

         [common-ui/information-row-with-option (tr [:field-labels :rentals :ote.db.transport-service/additional-service-type]) translated-service false]))]
    [:div
     (doall
       (for [services-row data
             :let [price (get-in services-row [::t-service/additional-service-price ::t-service/price-per-unit])]]
         ^{:key (str services-row price)}

         [common-ui/information-row-with-option (tr [:field-labels :rentals :ote.db.transport-service/price-classes])
          price false]))]
    [:div
     (doall
       (for [services-row data
             :let [unit (get-in services-row [::t-service/additional-service-price ::t-service/unit])]]
         ^{:key (str services-row unit)}

         [common-ui/information-row-with-option (tr [:service-viewer :pricing-basis])
          unit false]))]]
   [spacer]])

(defn- additional-service-links [title data]
  [:section
   [info-sections-2-cols title
    [:div
     (doall
       (for [row data
             :let [url (::t-service/url row)]]
         ^{:key (str row url)}
         [common-ui/information-row-with-option (tr [:service-viewer :url]) url false]))]
    [:div
     (doall
       (for [row data
             :let [description (format-descriptions (::t-service/description row))]]
         ^{:key (str row description)}
         [information-row-with-selection (tr [:common-texts :description]) description false]))]]
   [spacer]])

(defn- usage-area
  [title data]
  [:section
   [info-sections-1-col title
    [:div
     [common-ui/information-row-with-option (tr [:common-texts :description]) data true]]]
   [spacer]])

(defn- pick-up-locations [title data url app-state]
  [:section
   [:h4 title]
   [common-ui/information-row-with-option (tr [:field-labels :rentals :ote.db.transport-service/pick-up-locations-url]) url true]
   (doall
     (for [row data
           :let [service-hours-info (get row ::t-service/service-hours-info)
                 service-exceptions (get row ::t-service/service-exceptions)
                 street (get-in row [::t-service/pick-up-address :ote.db.common/street])
                 post-office (get-in row [::t-service/pick-up-address :ote.db.common/post_office])
                 post-code (get-in row [::t-service/pick-up-address :ote.db.common/postal_code])
                 country-code (get-in row [::t-service/pick-up-address :ote.db.common/country_code])
                 country (some #(when (= country-code (name (first %)))
                                  (second %))
                               (tr-tree [:country-list]))]]
       ^{:key (str row)}
       [:div
        [:h4 (string/upper-case (::t-service/pick-up-name row))]
        ; Address
        [:div (stylefy/use-style service-viewer/info-row)
         [:div (stylefy/use-sub-style service-viewer/info-seqment :left)
          [common-ui/information-row-with-option (tr [:field-labels :ote.db.common/street]) street true]]
         [:div (stylefy/use-sub-style service-viewer/info-seqment :mid)
          [common-ui/information-row-with-option (tr [:field-labels :ote.db.common/postal_code]) post-code true]]]
        [:div (stylefy/use-style service-viewer/info-row)
         [:div (stylefy/use-sub-style service-viewer/info-seqment :left)
          [common-ui/information-row-with-option (tr [:field-labels :ote.db.common/post_office]) post-office true]]
         [:div (stylefy/use-sub-style service-viewer/info-seqment :mid)
          [common-ui/information-row-with-option (tr [:common-texts :country]) country true]]]


        ; Service hours
        (doall
          (for [she (::t-service/service-hours row)
                :let [week-days (string/join ", " (map
                                                    #(string/lower-case (tr [:enums ::t-service/day :short %]))
                                                    (tsu/reorder-week-days (::t-service/week-days she))))
                      start-time (str (format-time-key->str she ::t-service/from :hours) ":" (format-time-key->str she ::t-service/from :minutes))
                      end-time (str (format-time-key->str she ::t-service/to :hours) ":" (format-time-key->str she ::t-service/to :minutes))]]
            ^{:key (str she week-days)}
            [:div (stylefy/use-style service-viewer/info-row)
             [:div (stylefy/use-sub-style service-viewer/info-seqment :left)
              [common-ui/information-row-with-option (tr [:service-viewer :day-of-week]) week-days true]]
             [:div (stylefy/use-sub-style service-viewer/info-seqment :mid)
              [common-ui/information-row-with-option (tr [:field-labels :transport-service :ote.db.transport-service/from]) start-time true]]
             [:div (stylefy/use-sub-style service-viewer/info-seqment :right)
              [common-ui/information-row-with-option (tr [:field-labels :transport-service :ote.db.transport-service/to]) end-time true]]]))

        ; Exceptions
        (doall
          (for [se service-exceptions
                :let [desc (format-descriptions (::t-service/description se))
                      start-date (time/format-timestamp->date-for-ui (::t-service/from-date se))
                      end-date (time/format-timestamp->date-for-ui (::t-service/to-date se))]]
            ^{:key (str desc start-date end-date)}
            [:div (stylefy/use-style service-viewer/info-row)
             [:div (stylefy/use-sub-style service-viewer/info-seqment :left)
              [information-row-with-selection (tr [:service-viewer :exception]) desc true]]
             [:div (stylefy/use-sub-style service-viewer/info-seqment :mid)
              [common-ui/information-row-with-option (tr [:service-viewer :starting-date]) start-date true]]
             [:div (stylefy/use-sub-style service-viewer/info-seqment :right)
              [common-ui/information-row-with-option (tr [:service-viewer :ending-date]) end-date true]]]))

        (when service-hours-info
          [:div (stylefy/use-style service-viewer/info-row)
           [:div (stylefy/use-sub-style service-viewer/info-seqment :left)
            [information-row-with-selection
             (tr [:field-labels :transport-service-common :ote.db.transport-service/service-hours-info])
             (format-descriptions service-hours-info) true]]])]))])

(defn- indoor-map [title data]
  (let [desc (format-descriptions (::t-service/description data))
        url (::t-service/url data)]
    [:section
     [info-sections-2-cols title
      [:div
       [common-ui/information-row-with-option (tr [:service-viewer :url]) url true]]
      [:div
       [information-row-with-selection (tr [:common-texts :description]) desc true]]]
     [spacer]]))

(defn- assistance-info [title data]
  (let [place-description (format-descriptions (::t-service/assistance-place-description data))
        description (format-descriptions (::t-service/description data))
        reservation? (or (::t-service/assistance-by-reservation-only data) false) ; Should be true or false. Its false if nil
        requirements-hours-before (get-in data [::t-service/notification-requirements ::t-service/hours-before])
        requirements-url (get-in data [::t-service/notification-requirements ::t-service/url])
        requirements-telephone (get-in data [::t-service/notification-requirements ::t-service/telephone])
        requirements-email (get-in data [::t-service/notification-requirements ::t-service/email])]
    [:section
     [info-sections-2-cols title
      [:div
       [information-row-with-selection (tr [:service-viewer :assistance-description]) description false]
       [common-ui/information-row-with-option
        (tr [:field-labels :terminal :ote.db.transport-service/assistance-by-reservation])
        (tr [:service-viewer :reservation-only reservation?]) false]
       [common-ui/information-row-with-option
        (tr [:service-viewer :assistance-email])
        requirements-email false]
       [common-ui/information-row-with-option
        (tr [:service-viewer :assistance-www])
        requirements-url false]]
      [:div
       [information-row-with-selection (tr [:service-viewer :assistance-place-description]) place-description false]
       [common-ui/information-row-with-option (tr [:field-labels :terminal :ote.db.transport-service/hours-before]) requirements-hours-before false]
       [common-ui/information-row-with-option
        (tr [:service-viewer :assistance-phone])
        requirements-telephone false]]]
     [spacer]]))

(defn- parking-facilities [title data]
  [:section
   [info-sections-2-cols title
    [:div
     (doall
       (for [row data
             :let [facility (::t-service/parking-facility row)]]
         ^{:key (str row facility)}
         [common-ui/information-row-with-option
          (tr [:field-labels :parking :ote.db.transport-service/parking-facility])
          (tr [:enums ::t-service/parking-facility facility]) false]))]
    [:div
     (doall
       (for [row data
             :let [capacity (::t-service/capacity row)]]
         ^{:key (str row capacity)}
         [common-ui/information-row-with-option
          (tr [:field-labels :parking :ote.db.transport-service/capacity])
          capacity false]))]]
   [spacer]])

(defn- charging-points [title description]
  [:section
   [info-sections-1-col title
    [:div
     [information-row-with-selection (tr [:common-texts :description]) (format-descriptions description) true]]]
   [spacer]])

(defn- accessibility-info [title accessibility accessibility-description accessibility-info-url information-service-accessibility]
  [:section
   [info-sections-1-col title
    [:div
     [common-ui/information-row-with-option
      (tr [:service-viewer :accessibility])
      (string/join ", " (map (fn [key] (tr [:enums ::t-service/accessibility key])) accessibility))
      true]
     [common-ui/information-row-with-option (tr [:field-labels :terminal :ote.db.transport-service/information-service-accessibility])
      (string/join ", " (map (fn [key] (tr [:enums ::t-service/information-service-accessibility key])) information-service-accessibility))
      true]
     [common-ui/information-row-with-option (tr [:service-viewer :accessibility-website]) accessibility-info-url true]
     [information-row-with-selection (tr [:field-labels :transport-service-common ::t-service/accessibility-description]) (format-descriptions accessibility-description) true]]]
   [spacer]])

(defn- parking-restrictions [title data]
  (let [parking-restricted? (or (not (empty? data)) false)
        restriction-time-type (cond
                                (and
                                  (not (nil? (:minutes data)))
                                  (> (:minutes data) 0)) :minutes
                                (and
                                  (not (nil? (:hours data)))
                                  (> (:hours data) 0)) :hours
                                (and
                                  (not (nil? (:days data)))
                                  (> (:days data) 0)) :days
                                :default :hours)
        restriction-value (restriction-time-type data)]
    [:section
     [info-sections-1-col title
      [:div
       [common-ui/information-row-with-option
        (tr [:service-viewer :parking-restricted-text])
        (tr [:service-viewer :parking-restricted? parking-restricted?]) false]
      [common-ui/information-row-with-option
       (tr [:field-labels :parking :ote.db.transport-service/maximum-stay])
       (str restriction-value " " (string/lower-case (tr [:common-texts :time-units restriction-time-type]))) false]]]
     [spacer]]))

(defn service-view
  [e! {{to :transport-operator ts :transport-service} :service-view :as app-state}]
  (let [service-sub-type (get ts ::t-service/sub-type)
        sub-type-key (svc/create-sub-type-key service-sub-type)
        interfaces (::t-service/external-interfaces ts)
        luggage-restrictions (get-in ts [sub-type-key ::t-service/luggage-restrictions])
        real-time-info-data (get-in ts [sub-type-key ::t-service/real-time-information])
        pre-booking-data (get-in ts [sub-type-key ::t-service/advance-reservation])
        booking-data (get-in ts [sub-type-key ::t-service/booking-service])
        accessibility-data (:accessibility ts)
        pricing-data (:pricing-info ts)
        rental-payment-methods (get-in ts [::t-service/rentals ::t-service/payment-methods])
        rentals (sub-type-key ts)
        service-hours-info (:service-hours-info ts)
        vehicle-classes (get-in ts [::t-service/rentals ::t-service/vehicle-classes])
        vehicle-price-url (get-in ts [::t-service/rentals ::t-service/vehicle-price-url])
        rental-additional-services (get-in ts [::t-service/rentals ::t-service/rental-additional-services])
        usage-area-description (get-in ts [sub-type-key ::t-service/usage-area])
        rental-pick-up-locations (get-in ts [::t-service/rentals ::t-service/pick-up-locations])
        pick-up-locations-url (get-in ts [::t-service/rentals ::t-service/pick-up-locations-url])]
    (if (or (= (:error to) 404)
            (= (:error ts) 404))
      [:h2 (tr [:common-texts :data-not-found])]
      [:div
       [service-header (::t-operator/name to) (::t-operator/id to) (::t-service/id ts)]
       [operator-info (tr [:service-viewer :operator-info]) to]
       [service-info (tr [:service-viewer :transport-service-info]) ts sub-type-key]
       [service-area e! (tr [:service-viewer :service-area]) ts]
       [published-interfaces (tr [:service-viewer :published-interfaces]) interfaces]
       [ote-interfaces "ote-netex-rajapinnat" (:ote-interfaces ts)]
       [spacer]

       (case service-sub-type
         :rentals
         [:div
          [vehicle-and-price (tr [:service-viewer :vehicles-and-pricing-information]) vehicle-classes vehicle-price-url]
          [restrictions-and-payment-methods (tr [:service-viewer :restrictions-and-payment-methods]) luggage-restrictions rental-payment-methods]
          [rental-accessibility-and-other-services (tr [:service-viewer :accessibility-info]) rentals]
          [additional-services (tr [:service-viewer :additional-services]) rental-additional-services]
          [usage-area (tr [:service-viewer :usage-area]) usage-area-description]
          [real-time-info (tr [:service-viewer :real-time-info]) real-time-info-data]
          [pre-booking (tr [:service-viewer :advance-reservation]) pre-booking-data]
          [booking-service (tr [:service-viewer :reservation-service]) booking-data]
          [pick-up-locations (tr [:service-viewer :pick-up-locations]) rental-pick-up-locations pick-up-locations-url app-state]]

         :terminal
         [:div
          [service-hours (tr [:service-viewer :service-hours]) service-hours-info]
          [indoor-map (tr [:field-labels :terminal ::t-service/indoor-map]) (get-in ts [sub-type-key ::t-service/indoor-map])]
          [assistance-info (tr [:service-viewer :assistance-info]) (get-in ts [sub-type-key ::t-service/assistance])]
          [accessibility-info (tr [:service-viewer :accessibility-info])
           (get-in ts [sub-type-key ::t-service/accessibility]) (get-in ts [sub-type-key ::t-service/accessibility-description])
           (get-in ts [sub-type-key ::t-service/accessibility-info-url]) (get-in ts [sub-type-key ::t-service/information-service-accessibility])]]

         :parking
         [:div
          [real-time-info (tr [:service-viewer :real-time-info]) real-time-info-data]
          [pre-booking (tr [:service-viewer :advance-reservation]) pre-booking-data]
          [booking-service (tr [:service-viewer :reservation-service]) booking-data]
          [additional-service-links (tr [:field-labels :parking :ote.db.transport-service/additional-service-links])
           (get-in ts [sub-type-key ::t-service/additional-service-links])]
          [parking-facilities (tr [:parking-page :header-facilities-and-capacities]) (get-in ts [sub-type-key ::t-service/parking-capacities])]
          [charging-points (tr [:parking-page :header-charging-points]) (get-in ts [sub-type-key ::t-service/charging-points])]
          [price-information (tr [:service-viewer :price-information]) pricing-data]
          [accessibility-info (tr [:service-viewer :accessibility-info])
           (get-in ts [sub-type-key ::t-service/accessibility]) (get-in ts [sub-type-key ::t-service/accessibility-description])
           (get-in ts [sub-type-key ::t-service/accessibility-info-url]) (get-in ts [sub-type-key ::t-service/information-service-accessibility])]
          [service-hours (tr [:service-viewer :service-hours]) service-hours-info]
          [parking-restrictions (tr [:service-viewer :parking-restrictions]) (get-in ts [sub-type-key ::t-service/maximum-stay])]]

         ; Default = passenger-transportation
         [:div
          [luggage-warnings (tr [:service-viewer :luggage-warnings]) luggage-restrictions]
          [real-time-info (tr [:service-viewer :real-time-info]) real-time-info-data]
          [pre-booking (tr [:service-viewer :advance-reservation]) pre-booking-data]
          [booking-service (tr [:service-viewer :reservation-service]) booking-data]
          [passenger-accessibility-and-other-services (tr [:service-viewer :accessibility-and-other-services]) accessibility-data]
          [price-information (tr [:service-viewer :price-information]) pricing-data]
          [service-hours (tr [:service-viewer :service-hours]) service-hours-info]])])))
