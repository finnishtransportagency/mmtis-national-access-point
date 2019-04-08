(ns ote.views.gtfs-viewer
  "GTFS viewer"
  (:require [ote.app.controller.gtfs-viewer :as gc]
            [reagent.core :as r]
            [ote.mui-wrapper.reagent :as ui]
            [ote.gtfs.query :as gq]
            [clojure.string :as str]
            [ote.ui.table :as table]
            [ote.ui.leaflet :as leaflet]
            [ote.time :as time]
            [ote.style.base :as style-base]
            [stylefy.core :refer [use-style]]))

(defn routes-table [e! {:gtfs/keys [agency-txt routes-txt trips-txt]
                        selected-route :selected-route}]
  (let [agency-by-id (into {} (map (juxt :gtfs/agency-id identity)) agency-txt)
        trips-by-route (group-by :gtfs/route-id trips-txt)]
    [table/table {:height "200px"
                  :name->label #(case %
                                  :agency "Liikennöitsijä"
                                  :name "Linja"
                                  :trips "Vuoroja")
                  :key-fn :gtfs/route-id
                  :row-selected? #(= (:route selected-route) %)
                  :on-select #(when (seq %)
                                (e! (gc/->SelectRoute (first %))))}
     [{:name :agency
       :read (comp :gtfs/agency-name agency-by-id :gtfs/agency-id)}
      {:name :name
       :read (fn [{:gtfs/keys [route-short-name route-long-name]}]
               [:span [:b route-short-name] " " route-long-name])}
      {:name :trips
       :read #(count (trips-by-route (:gtfs/route-id %)))}]
     routes-txt]))

(defn stop-popup [stop-id name {:gtfs/keys [stop-times-txt]}]
  (let [stop-times (for [{arr :gtfs/arrival-time
                          dep :gtfs/departure-time :as st} stop-times-txt
                         :when (and (= (:gtfs/stop-id st) stop-id)
                                    (or arr dep))]
                     (or arr dep))]
    [:div
     [:b name]
     [:ul
      (doall
       (for [st (if (> (count stop-times) 6)
                  (concat (take 3 stop-times)
                          [:ellipsis]
                          (reverse (take 3 (reverse stop-times))))
                  stop-times)
             :let [time (time/format-time st)]]
         (if (= :ellipsis st)
           ^{:key "ellipsis"}
           [:ul "\u22ee"]
           ^{:key time}
           [:ul time])))]]))

(defn trips-map [_ _]
  (r/create-class
   {:component-did-update leaflet/update-bounds-from-layers
    :reagent-render
    (fn [e! {selected-route :selected-route :as gtfs}]
      [leaflet/Map {:ref "leaflet"
                    :center #js [65 25]
                    :zoomControl true
                    :zoom 5
                    :style {:height 600}}
       (leaflet/background-tile-map)

       ;; Show polyline for each stop sequence
       (doall
        (map-indexed
         (fn [i {:keys [positions color]}]
           ^{:key i}
           [leaflet/Polyline {:positions positions
                              :color color}])
         (:lines selected-route)))

       ;; Show marker for all stops
       (doall
        (for [{:gtfs/keys [stop-id stop-lat stop-lon stop-name]}
              (:stops selected-route)
              :let [stop-times (get (:stop-times-for-stop selected-route) stop-id)]]
          ^{:key stop-id}
          [leaflet/FeatureGroup
           [leaflet/Marker
            {:position [stop-lat stop-lon]
             :title stop-name
             :icon (js/L.divIcon #js {:className "route-stop-icon"})}]
           [leaflet/Popup {}
            [stop-popup stop-id stop-name gtfs]]]))

       (doall
        (map-indexed
         (fn [i {:keys [position bearing]}]
           ^{:key i}
           [leaflet/Marker
            {:position (clj->js position)
             :icon (js/L.divIcon #js {:className "route-bearing"
                                      :html (str "<div style=\"transform: translateY(-12px) rotate(" bearing "deg);\">^</div>")})}])
         (:bearing-markers selected-route)))])}))

(defn gtfs-viewer [e! {gtfs :gtfs-viewer}]
  [:div.gtfs-viewer
   (if-let [err (:error-message gtfs)]
     [:div (use-style (merge style-base/error-element
                             {:margin-top "40px"}))
      err]
     [:span
      [routes-table e! gtfs]
      [trips-map e! gtfs]])])
