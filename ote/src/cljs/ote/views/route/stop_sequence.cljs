(ns ote.views.route.stop-sequence
  "Route wizard: defining a stop sequence"
  (:require [ote.ui.leaflet :as leaflet]
            [ote.app.controller.route.route-wizard :as rw]
            [ote.ui.form-fields :as form-fields]
            [ote.mui-wrapper.reagent :as ui]
            [ote.mui-wrapper.icons :as ic]
            [ote.ui.common :as common]
            [ote.db.transit :as transit]
            [reagent.core :as r]
            [ote.ui.leaflet-draw :as leaflet-draw]
            [clojure.string :as str]
            [ote.localization :refer [tr tr-key selected-language]]
            [ote.style.form :as style-form]
            [stylefy.core :as stylefy]
            [ote.db.transport-service :as t-service]
            [ote.style.base :as style-base]
            [ote.style.dialog :as style-dialog]))


(def stop-marker-style
  #js {:radius 8
       :fillColor "green"
       :opacity 1
       :fillOpacity 0.65})

(defn- stop-marker [e! point lat-lng]
  (-> lat-lng
      (js/L.marker #js {:opacity 0.7
                        :title (t-service/localized-text-with-fallback
                                 @selected-language
                                 (js->clj (aget point "properties" "name")
                                          :keywordize-keys true))})
      (.on  "click"
            (fn [_]
              (e! (rw/->AddStop point))))))

(defn- flip-coords [[c1 c2]]
  [c2 c1])

(defn- custom-stop-dialog [e! route]
  (let [name (-> route :custom-stops last :name)
        name-str (t-service/localized-text-with-fallback @selected-language name)]
    (when (:custom-stop-dialog route)
      [ui/dialog
       {:open true
        :actionsContainerStyle style-dialog/dialog-action-container
        :modal true
        :title (tr [:route-wizard-page :stop-sequence-custom-dialog-title])
        :actions [(r/as-element
                    [ui/flat-button
                     {:disabled (str/blank? name-str)
                      :label (tr [:route-wizard-page :stop-sequence-custom-dialog-add])
                      :primary true
                      :on-click #(e! (rw/->CloseCustomStopDialog))}])]}
       [:span
        [form-fields/field {:style {:margin-bottom "5px"}
                            :type :localized-text
                            :label (tr [:route-wizard-page :stop-sequence-custom-dialog-name])
                            :update! #(e! (rw/->UpdateCustomStop {:name %}))
                            :on-enter #(when (not (str/blank? name-str))
                                         (e! (rw/->CloseCustomStopDialog)))}
         name]
        [common/help (tr [:route-wizard-page :stop-sequence-custom-dialog-help])]]])))

(defn- route-map [e! route]
  (let [dc (atom nil)]
    (r/create-class
      {:component-did-mount
        (fn [this]
          (let [deleting? (atom false)
               ^js/L.Map
               m (aget this "refs" "stops-map" "leafletElement")]

            ;; Keep track if we are in delete mode
            (.on m "draw:deletestart" #(reset! deleting? true))
            (.on m "draw:deletestop" #(reset! deleting? false))

            (leaflet/customize-zoom-controls e! this "stops-map" {:zoomInTitle (tr [:leaflet :zoom-in])
                                                   :zoomOutTitle (tr [:leaflet :zoom-out])})
            (leaflet-draw/install-draw-control!
             this
             {:ref-name                "stops-map"
              ;; Disable all other geometry types
              :disabled-geometry-types #{:circle :circlemarker :rectangle :polyline :polygon}
              :on-control-created (partial reset! dc)
              :on-create               (fn [^js/L.Path layer]
                                         (let [id (leaflet-draw/layer-id layer)]
                                           (.on layer "click"
                                                (fn [_]
                                                  (when-not @deleting?
                                                    (e! (rw/->AddCustomStop id)))))
                                           (e! (rw/->CreateCustomStop id (leaflet-draw/layer-geojson layer)))))
              :on-remove               #(e! (rw/->RemoveCustomStop (leaflet-draw/layer-id %)))
              :on-edit                 #(e! (rw/->UpdateCustomStopGeometry
                                              (leaflet-draw/layer-id %)
                                              (leaflet-draw/layer-geojson %)))

              :add-features?           true
              :localization            {:toolbar  {:buttons {:marker (tr [:route-wizard-page :stop-sequence-leaflet-button-marker])}}
                                        :handlers {:marker {:tooltip {:start (tr [:route-wizard-page :stop-sequence-leaflet-button-start])}}}}
              :leaflet-edit-tr-key :leaflet-port-edit})))
            :component-will-receive-props (fn [this [_ _ route]]
                                       (let [^js/L.map m (aget this "refs" "stops-map" "leafletElement")]
                                         (if (get-in route [:map-controls :show?])
                                           (.addControl m @dc)
                                           (.removeControl m @dc))))
            :reagent-render            (fn [e! route]
                                         [:span
                                          [custom-stop-dialog e! route]
                                          [leaflet/Map {:ref         "stops-map"
                                                        :center      #js [65 25]
                                                        :zoomControl false
                                                        :zoom        5}
                                           (leaflet/background-tile-map)
                                           (when-let [stops (:stops route)]
                                             [leaflet/GeoJSON {:data         stops
                                                               :style        {:color "green"}
                                                               :pointToLayer (partial stop-marker e!)}])

                                           (when-let [stop-sequence (seq (::transit/stops route))]
                                             [leaflet/Polyline
                                              {:positions (clj->js (mapv (comp flip-coords ::transit/location) stop-sequence))
                                               :color     "red"}])]])})))
(defn- map-container [e! route]
  [:div.stops-map {:style {:width "70%" :z-index 99 :position "relative"}}
   [route-map e! route]
   [:span
    (if (get-in route [:map-controls :show?])
      [:span.hide-draw-buttons
       [ui/flat-button {:id :hide-draw-tools
                        :primary true
                        :label (tr [:route-wizard-page :hide-draw-tools])
                        :name :hide-draw-tools
                        :on-click #(e! (rw/->SetDrawControl false))}]]
      [:span.draw-buttons
       [ui/flat-button {:id :show-draw-tools
                        :primary true
                        :label (tr [:route-wizard-page :show-draw-tools])
                        :name :draw-secondary
                        :on-click #(e! (rw/->SetDrawControl true))}]])]])

(defn- route-stops [e! {stop-sequence ::transit/stops :as route}]
  [:div {:style {:width "30%" :margin "1em"}}
   (if (not (get-in route [:map-controls :show?]))
     [:table {:style {:width "100%"}}
      [:thead {:style {:text-align "left"}}
       [:tr
        [:th {:style {:width "50%"}} (tr [:route-wizard-page :stop-sequence-port-header])]

        [:th {:style {:width "10%"}} ""]]]
      [:tbody {:style {:text-align "left"}}

       (doall
         (map-indexed
           (fn [i {::transit/keys [code name arrival-time departure-time]}]
             ^{:key (str code "_" i)}
             [:tr {:style {:border-bottom "solid 1px black"}}
              [:td (t-service/localized-text-with-fallback @selected-language name)]
              [:td [common/tooltip {:text (tr [:route-wizard-page :stop-sequence-delete])
                                    :pos  "left"}
                    [ui/icon-button {:on-click #(e! (rw/->DeleteStop i))}
                     [ic/action-delete]]]]])
           stop-sequence))]
      (when (empty? stop-sequence)
        [:tbody
         [:tr
          [:td {:colSpan 4}
           [common/help (tr [:route-wizard-page :stop-sequence-map-help])]]]
         [:tr
          [:td {:colSpan 4}
           [:span (stylefy/use-style style-base/required-element) (tr [:route-wizard-page :data-missing])]]]])]
     [:div [common/help (tr [:route-wizard-page :stop-sequence-edit-map-help])]])])

(defn stop-sequence [e! {route :route :as app}]
  (e! (rw/->LoadStops))
  (fn [e! {route :route :as app}]
    (if (nil? (get route :stops))
      [common/loading-spinner]
      [:div (stylefy/use-style style-form/form-card)
       [:div (stylefy/use-style style-form/form-card-label) (tr [:route-wizard-page :wizard-step-stop-sequence])]
       [:div (stylefy/use-style style-form/form-card-body)
        [:div {:style {:display "flex" :flex-direction "row"}}
         [map-container e! route]
         [route-stops e! route]]]])))
