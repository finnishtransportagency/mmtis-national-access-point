(ns ote.views.route.route-list
  "List own routes"
  (:require
   [ote.localization :refer [tr tr-key]]
   [cljs-react-material-ui.reagent :as ui]
   [ote.app.controller.route.route-list :as route-list]
   [cljs-react-material-ui.icons :as ic]
   [ote.views.transport-operator :as t-operator-view]
   [ote.app.controller.transport-operator :as to]
   [ote.db.transport-operator :as t-operator]
   [ote.ui.form-fields :as form-fields]
   [ote.db.transit :as transit]
   [ote.db.modification :as modification]
   [ote.time :as time]
   [ote.ui.common :as common]))

(defn list-routes [e! routes]
  [:div
   (.log js/console "list-routes routes: " (clj->js routes))
   [ui/table
    [ui/table-header {:adjust-for-checkbox false
                      :display-select-all  false}
     [ui/table-row {:selectable false}
      [ui/table-header-column {:style {:width "3%"}} "Id"]
      [ui/table-header-column {:style {:width "20%"}} "Nimi"]
      [ui/table-header-column "Lähtöpaikka"]
      [ui/table-header-column "Määränpää"]
      [ui/table-header-column "Voimassa lähtien"]
      [ui/table-header-column "Voimassa asti"]
      [ui/table-header-column "Luotu / Muokattu"]
      [ui/table-header-column "Toiminnot"]]]
    [ui/table-body {:display-row-checkbox false}
     (doall
       (map-indexed
        (fn [i {::transit/keys [id name available-from available-to
                                departure-point-name destination-point-name]
                ::modification/keys [created modified] :as row}]
           ^{:key (str "route-" i)}
           [ui/table-row {:key (str "route-" i) :selectable false :display-border false}
            [ui/table-row-column {:style {:width "3%"}} id]
            [ui/table-row-column {:style {:width "20%"}} name]
            [ui/table-row-column departure-point-name]
            [ui/table-row-column destination-point-name]
            [ui/table-row-column (when available-from (time/format-date available-from))]
            [ui/table-row-column (when available-to (time/format-date available-to))]
            [ui/table-row-column (time/format-timestamp-for-ui (or modified created))]
            [ui/table-row-column "poista"]])
         routes))]]])

(defn list-operators [e! app]
  [:div
   [:div {:class "col-md-12"}
    [form-fields/field
     {:label       (tr [:field-labels :select-transport-operator])
      :name        :select-transport-operator
      :type        :selection
      :show-option #(if (nil? %)
                      (tr [:buttons :add-new-transport-operator])
                      (::t-operator/name %))
      :update!     #(if (nil? %)
                      (e! (to/->CreateTransportOperator))
                      (e! (to/->SelectOperatorForTransit %)))
      :options     (into (mapv :transport-operator (:route-list app))
                         [:divider nil])
      :auto-width? true}
     (:transport-operator app)]]])

(defn routes [e! app]
  (e! (route-list/->LoadRoutes))
  (fn [e! {routes :routes-vector operator :transport-operator :as app}]
    [:div
     [:div.row
      [:div.col-xs-12.col-sm-6.col-md-9
       [:h1 (tr [:route-list-page :header-route-list])]]
      [:div.col-xs-12.col-sm-6.col-md-3
       [ui/raised-button {:label    (tr [:buttons :add-new-route])
                          :style    {:float "right"}
                          :on-click #(do
                                       (.preventDefault %)
                                       (e! (route-list/->CreateNewRoute)))
                          :primary  true
                          :icon     (ic/content-add)}]]]

     [:div.row
      [list-operators e! app]]
     (when routes
       [list-routes e! routes])
     (when routes
       (let [loc (.-location js/document)
             url (str (.-protocol loc) "//" (.-host loc) (.-pathname loc)
                      "export/gtfs/" (::t-operator/id operator))]
         [:span
          [:br]
          [common/help
           [:span
            [:div
             "Palveluntuottajan voimassaolevat reitit: "
             [common/linkify url "GTFS Zip tiedosto"]]
            [:div
             "Kopioi osoite: "
             [common/copy-to-clipboard url]]]]]))]))
