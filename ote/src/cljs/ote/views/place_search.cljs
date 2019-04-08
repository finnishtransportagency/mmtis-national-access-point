(ns ote.views.place-search
  "View for searching for places and showing them on the map."
  (:require [reagent.core :as r]
            [ote.app.controller.place-search :as ps]
            [ote.ui.form-fields :as form-fields]
            [ote.ui.buttons :as buttons]
            [ote.ui.leaflet :as leaflet]
            [ote.ui.leaflet-draw :as leaflet-draw]
            [ote.ui.form :as form]
            [ote.localization :refer [tr tr-tree]]
            [ote.mui-wrapper.reagent :as ui]
            [ote.db.transport-service :as t-service]
            [ote.db.places :as places]
            [goog.object :as gobj]
            cljsjs.leaflet
            [stylefy.core :as stylefy]
            [ote.ui.common :as common]
            [ote.mui-wrapper.icons :as ic]
            [ote.style.base :as style-base]
            [ote.theme.screen-sizes :refer [width-xxs width-xs width-sm width-md width-l width-xl]]
            [clojure.string :as str]))

(set! *warn-on-infer* true)

(defn monkey-patch-chip-backspace
  "Pre 1.0 fix for bug in MaterialUI Chip which unconditionally
  cancels a backspace events (causing an embedded input field to
  not be able to erase text."
  [this]
  (let [refs (aget this "refs")]
    (gobj/forEach
     refs
     (fn [chip ref _]
       (when-not (aget chip "__backspace_monkey_patch")
         (let [old (aget chip "handleKeyDown")]
           (aset chip "handleKeyDown"
                 (fn [event]
                   (when-not (= "Backspace" (.-key event))
                     (old event))))
           (aset chip "__backspace_monkey_patch" true)))))))

(defn result-chips [e! results primary?]
  (r/create-class
   {:component-did-mount monkey-patch-chip-backspace
    :component-did-update monkey-patch-chip-backspace
    :reagent-render
    (fn [e! results]
      [:div.place-search-results {:style {:display "flex" :flex-wrap "wrap"}}
       (for [{::places/keys [namefin type id] editing? :editing? :as result} (map :place results)]
         ^{:key id}
         [:span
          [ui/chip {:ref id
                    :style {:margin 4 :background-color (if primary? "green" "orange") }
                    :labelStyle {:color "white" :font-weight "bold"}
                    ;; Toggle edit mode when clicking (for hand drawn geometries)
                    :on-click
                    (if (and (= "drawn" type) (not editing?))
                      #(e! (ps/->EditDrawnGeometryName id))
                      (constantly false))

                    :on-request-delete #(e! (ps/->RemovePlaceById id))}
          (if editing?
            [ui/text-field
             {:id (str "result-" namefin)
              :value namefin
              :floating-label-text (tr [:place-search :rename-place])
              :on-key-press #(when (= "Enter" (.-key %1))
                               (e! (ps/->EditDrawnGeometryName id)))
              :on-change #(e! (ps/->SetDrawnGeometryName id %2))}]
            namefin)]])])}))

(defn result-geometry [{::places/keys [name location]}]
  [leaflet/FeatureGroup
   [leaflet/geometry {:color "green"
                      :dashArray "5,5"} location]
   [leaflet/Popup [:div name]]])



(defn places-map [e! results show?]
  (let [dc (atom nil)]
    (r/create-class
     {:component-did-mount #(do
                              (leaflet/customize-zoom-controls e! %  "leaflet" {:zoomInTitle (tr [:leaflet :zoom-in])
                                                                     :zoomOutTitle (tr [:leaflet :zoom-out])})
                              (leaflet-draw/install-draw-control!
                                  %
                                  {:on-control-created (partial reset! dc)
                                   :on-create (fn [^js/L.Path layer]
                                                (e! (ps/->AddDrawnGeometry (.toGeoJSON layer))))})
                              (leaflet/update-bounds-from-layers %))

      :component-did-update leaflet/update-bounds-from-layers

      :component-will-receive-props (fn [this [_ _ _ show?]]
                                      (let [^js/L.map m (aget this "refs" "leaflet" "leafletElement")]
                                        (if show?
                                          (.addControl m @dc)
                                          (.removeControl m @dc))))
      :reagent-render
      (fn [e! results show?]
        [leaflet/Map { ;;:prefer-canvas true
                      :ref "leaflet"
                      :center #js [65 25]
                      :zoomControl false
                      :zoom 5}
         (leaflet/background-tile-map)

         (for [{:keys [place geojson]} results]
           ^{:key (::places/id place)}
           [leaflet/GeoJSON {:data geojson
                             :style {:color (if (::places/primary? place)
                                              "green"
                                              "orange")}}])])})))

(defn- completions [completions]
  (apply array
         (map (fn [{::places/keys [id namefin type]}]
                #js {:text namefin
                     :id id
                     :value (r/as-element
                             [ui/menu-item {:primary-text namefin}])})
              completions)))


(def tooltip-icon
  "A tooltip icon that shows balloon.css tooltip on hover."
  (let [wrapped (common/tooltip-wrapper ic/action-help {:style {:margin-left 8}})]
    (fn [opts]
      [wrapped {:style {:width          16 :height 16
                        :vertical-align "middle"
                        :color          "gray"}}
       opts])))

(defn  place-search [e! place-search]
  (let [{primary-results true
         secondary-results false} (group-by (comp ::places/primary? :place) (:results place-search))]
    [:div.place-search (stylefy/use-style (merge (style-base/flex-container "row")
                                                 {:flex-wrap "wrap"}))
     [:div (stylefy/use-style {:width "100%"
                               ::stylefy/media {{:min-width (str width-xs "px")} {:width "30%"}}})
      [:div {:style {:font-weight "bold"}}
       [:span (tr [:place-search :primary-header])]
       [:span [tooltip-icon {:text (tr [:place-search :primary-tooltip])}]]]

      [result-chips e! primary-results true]
      [ui/auto-complete {:id :place-auto-complete-primary
                         :name :place-auto-complete-primary
                         :floating-label-text (tr [:place-search :place-auto-complete-primary])
                         :filter (constantly true) ;; no filter, backend returns what we want
                         :dataSource (completions (:completions place-search))
                         :maxSearchResults 12
                         :on-update-input #(e! (ps/->SetPrimaryPlaceName %))
                         :search-text (or (:primary-name place-search) "")
                         :on-new-request #(e! (ps/->AddPlace (aget % "id") true))}]
      [:div {:style {:font-weight "bold" :margin-top "60px"}}
       [:span (tr [:place-search :secondary-header])]
       [:span [tooltip-icon {:text (tr [:place-search :secondary-tooltip])}]]]

      [result-chips e! secondary-results false]
      [ui/auto-complete {:id :place-auto-complete-secondary
                         :name :place-auto-complete-secondary
                         :floating-label-text (tr [:place-search :place-auto-complete-secondary])
                         :filter (constantly true) ;; no filter, backend returns what we want
                         :dataSource (completions (:completions place-search))
                         :maxSearchResults 12
                         :on-update-input #(e! (ps/->SetSecondaryPlaceName %))
                         :search-text (or (:secondary-name place-search) "")
                         :on-new-request #(e! (ps/->AddPlace (aget % "id") false))}]]

     [:div (stylefy/use-style {:width "100%"
                               :z-index 99
                               ::stylefy/media {{:min-width (str width-xs "px")} {:width "70%"}}})
       [places-map e! (:results place-search) (:show? place-search)]

      [:span
       (if (:show? place-search)
         [:span.hide-draw-buttons
          [ui/flat-button {:id :hide-draw-tools
                           :primary true
                           :label (tr [:place-search :hide-draw-tools])
                           :name :hide-draw-tools
                           :on-click #(e! (ps/->SetDrawControl false nil))}]]
         [:span.draw-buttons
          [ui/flat-button {:id :draw-primary
                           :primary true
                           :label (tr [:place-search :draw-primary])
                           :name :draw-primary
                           :on-click #(e! (do
                                            (ps/->SetDrawControl true true)))}]
          [ui/flat-button {:id :draw-secondary
                           :primary true
                           :label (tr [:place-search :draw-secondary])
                           :name :draw-secondary
                           :on-click #(e! (ps/->SetDrawControl true false))}]])]]]))

(defn place-search-form-group [e! label name]
  (let [empty-places? #(not-any? (comp ::places/primary? :place) (get-in % [:place-search :results]))]
    (form/group
     {:label label
      :columns 3}

     (form/info (tr [:form-help :operation-area]))

     {:type :component
      :name name
      :required? true
      :is-empty? empty-places?
      :component (fn [{data :data}]
                   [:span
                    (when (empty-places? data)
                      [:div (stylefy/use-style style-base/required-element)
                       (tr [:common-texts :required-field])])
                    ;; Meta-key helps to avoid map re-rendering.
                    ;; Component knows this way that same element is in use.
                    ^{:key "place-search"}
                    [place-search e! (:place-search data)]])})))
