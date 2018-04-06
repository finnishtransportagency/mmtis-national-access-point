(ns ote.views.route.stop-sequence
  "Route wizard: defining a stop sequence"
  (:require [ote.ui.leaflet :as leaflet]
            [ote.app.controller.route.route-wizard :as rw]
            [ote.ui.form-fields :as form-fields]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]
            [ote.ui.common :as common]
            [ote.db.transit :as transit]
            [reagent.core :as r]
            [ote.ui.leaflet-draw :as leaflet-draw]
            [clojure.string :as str]
            [ote.localization :refer [tr tr-key selected-language]]
            [ote.style.form :as style-form]
            [stylefy.core :as stylefy]
            [ote.db.transport-service :as t-service]))


(def stop-marker-style
  #js {:radius 8
       :fillColor "green"
       :opacity 1
       :fillOpacity 0.65})

(defn- stop-marker [e! point lat-lng]
  (-> lat-lng
      (js/L.marker #js {:opacity 0.7
                        :title (t-service/localized-text-for
                                 @selected-language
                                 (js->clj (aget point "properties" "name")
                                          :keywordize-keys true))})
      (.on  "click"
            (fn [_]
              (e! (rw/->AddStop point))))))

(defn- flip-coords [[c1 c2]]
  [c2 c1])

(defn- custom-stop-dialog [e! route]
  (when (:custom-stop-dialog route)
    [ui/dialog
     {:open true
      :modal true
      :title (tr [:route-wizard-page :stop-sequence-custom-dialog-title])
      :actions [(r/as-element
                 [ui/flat-button
                  {:label (tr [:route-wizard-page :stop-sequence-custom-dialog-add])
                   :primary true
                   :on-click #(e! (rw/->CloseCustomStopDialog))}])]}
     [:div
      [form-fields/field {:style {:margin-bottom "5px"}
                          :type :localized-text
                          :label (tr [:route-wizard-page :stop-sequence-custom-dialog-name])
                          :update! #(e! (rw/->UpdateCustomStop {:name %}))
                          :on-enter #(e! (rw/->CloseCustomStopDialog))}
       (-> route :custom-stops last :name)]
      [common/help (tr [:route-wizard-page :stop-sequence-custom-dialog-help])]]]))

(defn- route-map [e! route]
  (r/create-class
   {:component-did-mount
    (fn [this]
      (let [deleting? (atom false)
            ^js/L.Map
            m (aget this "refs" "stops-map" "leafletElement")]

        ;; Keep track if we are in delete mode
        (.on m "draw:deletestart" #(reset! deleting? true))
        (.on m "draw:deletestop" #(reset! deleting? false))

        (leaflet-draw/install-draw-control!
            this
          {:add? true
           :ref-name "stops-map"
           ;; Disable all other geometry types
           :disabled-geometry-types #{:circle :circlemarker :rectangle :polyline :polygon}
           :on-create (fn [^js/L.Path layer]
                        (let [id (leaflet-draw/layer-id layer)]
                          (.on layer "click"
                               (fn [_]
                                 (when-not @deleting?
                                   (e! (rw/->AddCustomStop id)))))
                          (e! (rw/->CreateCustomStop id (leaflet-draw/layer-geojson layer)))))
           :on-remove #(e! (rw/->RemoveCustomStop (leaflet-draw/layer-id %)))
           :on-edit #(e! (rw/->UpdateCustomStopGeometry
                          (leaflet-draw/layer-id %)
                          (leaflet-draw/layer-geojson %)))

           :add-features? true
           :localization {:toolbar {:buttons {:marker (tr [:route-wizard-page :stop-sequence-leaflet-button-marker])}}
                          :handlers {:marker {:tooltip {:start (tr [:route-wizard-page :stop-sequence-leaflet-button-start])}}}}})))
    :reagent-render
    (fn [e! route]
      [:div.stops-map {:style {:width "70%"}}
       [custom-stop-dialog e! route]
       [leaflet/Map {:ref "stops-map"
                     :center #js [65 25]
                     :zoomControl true
                     :zoom 5}
        (leaflet/background-tile-map)
        (when-let [stops (:stops route)]
          [leaflet/GeoJSON {:data stops
                            :style {:color "green"}
                            :pointToLayer (partial stop-marker e!)}])

        (when-let [stop-sequence (seq (::transit/stops route))]
          [leaflet/Polyline
           {:positions (clj->js (mapv (comp flip-coords ::transit/location) stop-sequence))
            :color "red"}])]])}))

(defn- route-stops [e! stop-sequence]
  [:div {:style {:width "30%" :margin "1em"}}
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
          [:td (t-service/localized-text-for @selected-language name)]
          [:td [common/tooltip {:text (tr [:route-wizard-page :stop-sequence-delete])}
                [ui/icon-button {:on-click #(e! (rw/->DeleteStop i))}
                 [ic/action-delete]]]]])
       stop-sequence))]
    (when (empty? stop-sequence)
      [:tbody
       [:tr
        [:td {:colSpan 4}
         [common/help (tr [:route-wizard-page :stop-sequence-map-help])]]]])]])

(defn stop-sequence [e! {route :route :as app}]
  (e! (rw/->LoadStops))
  (fn [e! {route :route :as app}]
    (if (nil? (get route :stops))
      [:div.loading [:img {:src "/base/images/loading-spinner.gif"}]]
      [:div (stylefy/use-style style-form/form-card)
       [:div (stylefy/use-style style-form/form-card-label) (tr [:route-wizard-page :wizard-step-stop-sequence])]
       [:div (stylefy/use-style style-form/form-card-body)
        [:div {:style {:display "flex" :flex-direction "row"}}
         [route-map e! route]
         [route-stops e! (::transit/stops route)]]]])))
