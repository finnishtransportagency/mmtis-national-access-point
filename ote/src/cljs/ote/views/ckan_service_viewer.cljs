(ns ote.views.ckan-service-viewer
  "NAP viewer view. Shows a resource embedded in CKAN resource page.
  Loads GeoJSON data from given URL (proxied by our backend) and
  displays a map and data from the geojson file."
  (:require [ote.app.controller.ckan-service-viewer :as v]
            [ote.ui.leaflet :as leaflet]
            [ote.ui.common :as common :refer [linkify]]
            [clojure.string :as str]
            [ote.localization :refer [tr tr-or]]
            [stylefy.core :as stylefy]
            [ote.style.ckan :as style-ckan]
            [ote.style.base :as style-base]
            [ote.style.service-viewer :as style]
            [ote.app.controller.transport-service :as ts]
            [ote.ui.buttons :as buttons]
            [ote.mui-wrapper.reagent :as ui]
            [ote.ui.leaflet :as leaflet]
            [ote.views.theme :refer [theme]]
            [ote.views.place-search :as place-search]
            [reagent.core :as r]
            [ote.util.values :as values]
            cljsjs.leaflet
            [ote.time :as time]
            [cljs-time.coerce :as coerce]))

(defn ignore-key? [key]
  (let [ends-with-keys-to-ignore ["-imported" "-csv-url" "operator-id"]
        keys-to-ignore ["ckan-dataset-id" "ckan-resource-id" "created" "id"]
        ends-with-ignore (some #(str/ends-with? key %) ends-with-keys-to-ignore)
        ignore (if ends-with-ignore
                 ends-with-ignore
                 (some #(= key %) keys-to-ignore))]
    ignore))

(defmulti transform-value (fn [key value] key))

(defmethod transform-value "url" [_ value] (linkify value value {:target "_blank"}))
(defmethod transform-value "homepage" [_ value] (linkify value value {:target "_blank"}))
(defmethod transform-value "csv-url" [_ value] (linkify value (tr [:service-search :load-csv-file]) {:target "_blank"}))
(defmethod transform-value "contact-email" [_ value] (linkify (str "mailto:" value) value))
(defmethod transform-value "available-to" [_ value] (time/format-timestamp->date-for-ui (coerce/from-string value)))
(defmethod transform-value "available-from" [_ value] (time/format-timestamp->date-for-ui (coerce/from-string value)))
(defmethod transform-value "maximum-stay" [_ value]
  (when value
    (let [interval (time/iso-8601-period->interval value)]
      (apply str
             (for [key [:years :months :days
                        :hours :minutes :seconds]
                   :let [value (get interval key)]]
               (when (and value (not (zero? value)))
                 (str value " " (tr [:viewer (name key)]) " ")))))))

(defn lang-name-for [lang]
  ;; Show unicode country flags for supported languages, otherwise show language code
  (case lang
    "FI" "suomi" ; finnish flag
    "SV" "svenska" ; swedish flag
    "EN" "english" ; united kingdom flag
    lang))

(defmethod transform-value :default [_ value]
  (tr-or (tr [:viewer :values value]) (str value)))

(declare properties-table records-table)

(def keyset-formatter {#{"hours" "minutes" "seconds"}
                       (fn [{:strs [hours minutes seconds] :as v}]
                         (time/format-time {:hours hours
                                            :minutes minutes
                                            :seconds seconds}))
                       #{"lang" "text"}
                       (fn [{:strs [lang text]}]
                         (str (lang-name-for lang) ": " text))})

(defn show-value [key value]
  (let [formatter (when (map? value)
                    (keyset-formatter (set (keys value))))
        value ((or formatter identity) value)
        has-published-time? (not= (js/Date. value) (js/Date. 0))]
    (cond
      (= key "published")
      [:span
       (if has-published-time?
         (time/format-timestamp-for-ui (js/Date. value))
         (tr [:viewer "published"]))]

      ;; This is an object, show key/value table
      (map? value)
      [properties-table value]

      ;; This is a collection of maps, each having the same keys
      ;; we can show this as a table
      (and (coll? value)
           (every? map? value)
           (apply = (map keys value)))
      (if-let [fmt (keyset-formatter (set (keys (first value))))]
        ;; If there is a formatter for this keyset, just join the items
        [:div.list-of-values
         (doall
          (map-indexed
           (fn [i v]
             ^{:key i}
             [:div.list-item (fmt v)])
           value))]
        ;; otherwise, show a table
        [records-table value])

      ;; Collection of values
      (coll? value)
      [:span
       (doall
        (map-indexed
         (fn [i value]
           ^{:key i}
           [:div (stylefy/use-style style-ckan/info-block)
            [show-value "" value]]) value))]

      ;; Other values, like strings and numbers
      :default
      [:span (stylefy/use-style style/value)
       (transform-value key value)])))

(defn properties-table [properties]
  [:table.table.table-striped.table-bordered.table-condensed
   (stylefy/use-style style/properties-table)
   [:tbody
    (doall
     (map
      (fn [[key value] stripe-style]
        ^{:key key}
        [:tr (stylefy/use-style stripe-style)
         [:th (merge {:scope "row" :width "25%"}
                     (stylefy/use-style style/th))
          (tr-or (tr [:viewer key]) key)]
         [:td (stylefy/use-style style/value)
          [show-value key value]]])

      (filter (fn [[key value]]
                (and (not (ignore-key? key))
                     (not (values/effectively-empty? value))))
              (sort-by first properties))
      (cycle style/striped-styles)))]])

(defn records-table [rows]
  (let [headers (filter (complement ignore-key?) (keys (first rows)))
        labels (into {}
                     (map (juxt identity #(tr [:viewer %])))
                     headers)
        sorted-headers (sort-by (comp str/lower-case labels) headers)]
    [:table
     [:thead
      (doall
       (for [h sorted-headers]
         ^{:key h}
         [:th {:scope "col"} (labels h)]))]
     [:tbody
      (doall
       (map-indexed
        (fn [i row]
          ^{:key i}
          [:tr
           (for [h sorted-headers]
             ^{:key h}
             [:td [show-value h (get row h)]])])
        rows))]]))

(defn show-features [{:strs [features] :as resource}]
  (let [{:strs [transport-operator transport-service] :as props}
        (-> features first (get "properties"))]
    [:div
     (when transport-operator
       [:div {:style {:padding-top "20px"}}
        [:h2 (tr [:viewer "transport-operator"])]
        [properties-table transport-operator]])
     (when transport-service
       [:div {:style {:padding-top "20px"}}
        [:h2 (tr [:viewer "transport-service"])]
        [properties-table transport-service]])]))

(defn operation-area [e! {:keys [geojson] :as app}]
  (r/create-class
    {:display-name "operation-area-map"
     :component-did-mount #(do
                             (leaflet/customize-zoom-controls e! % "leaflet" {:zoomInTitle (tr [:leaflet :zoom-in])
                                                                              :zoomOutTitle (tr [:leaflet :zoom-out])})
                             (leaflet/update-bounds-on-load %))
     :component-did-update leaflet/update-bounds-from-layers
     :reagent-render
     (fn [e! {:keys [geojson] :as app}]
       [:div {:style {:z-index 99 :position "relative"}}
       [leaflet/Map {:ref         "leaflet"
                     :center      #js [65 25]
                     :zoomControl false
                     :zoom        5}
        [leaflet/TileLayer {:url         "http://{s}.tile.osm.org/{z}/{x}/{y}.png"
                            :attribution "&copy; <a href=\"https://gist.github.com/hrbrmstr/91ea5cc9474286c72838\">Continents</a>
| &copy; <a href=\"https://www.maanmittauslaitos.fi/en/opendata-licence-cc40\">MML</a>
| &copy; <a href=\"http://osm.org/copyright\">OpenStreetMap</a> contributors<br>"}]

        ;; Go through the geometrycollection and separately show each geometry.
        ;; The GeoJSON export adds a GeoJSON-CSS style to each geometry which has
        ;; a color based on the operation area primary? flag.
        (doall
         (map-indexed
          (fn [i geom]
            ^{:key i}
            [leaflet/GeoJSON {:data geom
                              :style {:color (aget geom "style" "fill")}}])
          (seq (aget geojson "features" 0 "geometry" "geometries"))))]])}))

(defn viewer [e! _]
  (e! (v/->StartViewer))
  (fn [e! {:keys [authorized? logged-in? loading? geojson resource] :as app}]
    (if loading?
      [common/loading-spinner]
      [theme e! app
        [:div.transport-service-view
          [:div.row.pull-right
            (if (and logged-in? authorized?)
              [:div {:style {
                             :margin-right "-15px"  ;; Ugly hack to fix our buttons margins
                             :margin-top "-25px" ;; Ugly hack to fix ckan styles
                             :padding-bottom "10px"
                             }}
                [buttons/save {:on-click #(e! (ts/->OpenTransportServicePage (last (clojure.string/split (get app :url) "/"))))
                             :disabled false
                             :primary  true}
                (tr [:buttons :edit-service])]])]

         [operation-area e! app]
         [show-features resource]]])))
