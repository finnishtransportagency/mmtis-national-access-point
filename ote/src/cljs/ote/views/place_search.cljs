(ns ote.views.place-search
  "View for searching for places and showing them on the map."
  (:require [reagent.core :as r]
            [ote.app.controller.place-search :as ps]
            [ote.ui.form-fields :as form-fields]
            [ote.ui.napit :as napit]
            [ote.ui.leaflet :as leaflet]
            [ote.ui.form :as form]
            [ote.localization :refer [tr]]
            [cljs-react-material-ui.reagent :as ui]
            [ote.db.transport-service :as t-service]
            [ote.db.places :as places]

            )
  )

(defn result-chips [e! results]
  [:div.place-search-results {:style {:display "flex" :flex-wrap "wrap"}}
   (for [{::places/keys [namefin id] :as result} (map :place results)]
     ^{:key id}
     [ui/chip {:style {:margin 4}
               :on-request-delete #(e! (ps/->RemovePlaceById id))} namefin])])

(defn result-geometry [{::places/keys [name location]}]
  [leaflet/FeatureGroup
   [leaflet/geometry {:color "green"
                      :dashArray "5,5"} location]
   [leaflet/Popup [:div name]]])

(defn places-map [e! results]
  (.log js/console "rendering map")
  (r/create-class
   {:should-component-update
    ;; Do NOT rerender unless the geometries have changed
    (fn [_ [_ _ old-results] [_ _ new-results]]
      (not (identical? old-results new-results)))
    :reagent-render
    (fn [e! results]
      [leaflet/Map {:center #js [65 25]  :zoom 5}
       [leaflet/TileLayer {:url "http://{s}.tile.osm.org/{z}/{x}/{y}.png"
                           :attribution "&copy; <a href=\"http://osm.org/copyright\">OpenStreetMap</a> contributors"}]

       (for [{:keys [place geojson]} results]
         ^{:key (::places/id place)}
         [leaflet/GeoJSON {:data geojson
                           :style {:color "green"}}])])}))

(defn marker-map [e! coordinate]
  (.log js/console "rendering marker map coordinate->"coordinate)
  (r/create-class
    {:should-component-update
     ;; Do NOT rerender unless the geometries have changed
     (fn [_ [_ _ old-results] [_ _ new-results]]
       (not (identical? old-results new-results)))
     :reagent-render
     (fn [e! coordinate]
       [leaflet/Map {
                     :center #js [65.01212149716532 25.47065377235413]
                     :zoom 16
                     :on-click  #(e! (ps/->SetMarker %))
                     }
        [leaflet/TileLayer {:url "http://{s}.tile.osm.org/{z}/{x}/{y}.png"
                            :attribution "&copy; <a href=\"http://osm.org/copyright\">OpenStreetMap</a> contributors"}]

        [leaflet/Marker
         {:position [(first (get-in coordinate  [:coordinates  :coordinates]))
                     (second (get-in coordinate  [:coordinates  :coordinates]))]} ]
        ])}))

(defn- completions [completions]
  (apply array
         (map (fn [{::places/keys [id namefin type]}]
                #js {:text namefin
                     :id id
                     :value (r/as-element
                             [ui/menu-item {:primary-text namefin
                                        ;:secondary-text type
                                            }])})
              completions)))

(defn place-search [e! place-search]
  (let [results (:results place-search)]
    [:div.place-search

     [result-chips e! results]

     [ui/auto-complete {:floating-label-text (tr [:place-search :place-auto-complete])
                        :dataSource (completions (:completions place-search))
                        :on-update-input #(e! (ps/->SetPlaceName %))
                        :search-text (or (:name place-search) "")
                        :on-new-request #(e! (ps/->AddPlace (aget % "id")))}]

     [places-map e! results]]))

(defn place-search-form-group [e! label name]
  (form/group
   {:label label
    :columns 3}
   {:type :component
    :name name
    :component (fn [{data :data}]
                 [place-search e! (:place-search data)])}))


(defn place-marker [e! coordinates]
  (let [coordinate  coordinates]
    [:div
     [marker-map e! coordinates]]))

(defn place-marker-form-group [e! label name]
  (form/group
    {:label label
     :columns 3}
    {:type :component
     :name name
     :component (fn [{data :data}] [place-marker e! {:coordinates data}])}))