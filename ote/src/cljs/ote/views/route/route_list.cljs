(ns ote.views.route.route-list
  "List own routes"
  (:require
    [ote.localization :refer [tr tr-key]]
    [cljs-react-material-ui.reagent :as ui]
    [ote.app.controller.route.route-list :as route-list]
    [cljs-react-material-ui.icons :as ic]
    [ote.views.transport-operator-selection :as t-operator-sel]
    [ote.app.controller.transport-operator :as to]
    [ote.db.transport-operator :as t-operator]
    [ote.ui.form-fields :as form-fields]
    [ote.db.transit :as transit]
    [ote.db.modification :as modification]
    [ote.time :as time]
    [ote.app.controller.front-page :as fp]
    [ote.ui.common :as common]
    [ote.db.transport-service :as t-service]
    [ote.localization :refer [selected-language]]
    [reagent.core :as r]
    [ote.ui.list-header :as list-header]
    [ote.style.base :as style-base]
    [ote.ui.buttons :as buttons]))

(defn- delete-route-action [e! {::transit/keys [id name]
                                  :keys [show-delete-modal?]
                                  :as route}]
  [:span
   [ui/icon-button {:id       (str "delete-route-" id)
                    :href     "#"
                    :on-click #(do
                                 (.preventDefault %)
                                 (e! (route-list/->OpenDeleteRouteModal id)))}
    [ic/action-delete]]
   (when show-delete-modal?
     [ui/dialog
      {:open    true
       :title   (tr [:route-list-page :delete-dialog-header])
       :actions [(r/as-element
                   [buttons/cancel
                    {:on-click #(do
                                  (.preventDefault %)
                                  (e! (route-list/->CancelDeleteRoute id)))}
                    (tr [:buttons :cancel])])
                 (r/as-element
                   [buttons/delete
                    {:on-click  #(do
                                   (.preventDefault %)
                                   (e! (route-list/->ConfirmDeleteRoute id)))}
                    (tr [:buttons :delete])])]}

      (str (tr [:route-list-page :delete-dialog-remove-route]) (t-service/localized-text-with-fallback @selected-language name))])])


(defn list-routes [e! routes]
  [:div {:style {:padding-top "20px"}}
   [ui/table
    [ui/table-header {:adjust-for-checkbox false
                      :display-select-all  false}
     [ui/table-row {:selectable false}
      [ui/table-header-column {:style {:width "18%"}} (tr [:route-list-page :route-list-table-name])]
      [ui/table-header-column {:class "hidden-xs hidden-sm ":style {:width "10%"}} (tr [:route-list-page :route-list-published?])]
      [ui/table-header-column {:style {:width "10%"}} (tr [:route-list-page :route-list-table-starting-point])]
      [ui/table-header-column {:style {:width "10%"}} (tr [:route-list-page :route-list-table-destination-point])]
      [ui/table-header-column {:style {:width "10%"}} (tr [:route-list-page :route-list-table-valid-from])]
      [ui/table-header-column {:style {:width "10%"}} (tr [:route-list-page :route-list-table-valid-to])]
      [ui/table-header-column {:class "hidden-xs hidden-sm " :style {:width "15%"}} (tr [:route-list-page :route-list-table-created-modified])]
      [ui/table-header-column {:class "hidden-xs hidden-sm " :style {:width "11%"}} (tr [:route-list-page :route-list-table-actions])]]]
    [ui/table-body {:display-row-checkbox false}
     (doall
       (map-indexed
         (fn [i {::transit/keys      [id name published? available-from available-to
                                      departure-point-name destination-point-name]
                 ::modification/keys [created modified] :as row}]
           ^{:key (str "route-" i)}
           [ui/table-row {:key (str "route-" i) :selectable false :display-border false}
            [ui/table-row-column {:style {:width "18%"}}
             [:a {:href     "#"
                  :on-click #(do
                               (.preventDefault %)
                               (e! (fp/->ChangePage :edit-route {:id id})))} (t-service/localized-text-with-fallback @selected-language name)]]
            [ui/table-row-column {:class "hidden-xs hidden-sm " :style {:width "10%"}} (tr [:route-list-page :route-list-published?-values published?])]
            [ui/table-row-column {:style {:width "10%"}} (t-service/localized-text-with-fallback @selected-language departure-point-name)]
            [ui/table-row-column {:style {:width "10%"}} (t-service/localized-text-with-fallback @selected-language destination-point-name)]
            [ui/table-row-column {:style {:width "10%"}} (when available-from (time/format-date available-from))]
            [ui/table-row-column {:style {:width "10%"}} (when available-to (time/format-date available-to))]
            [ui/table-row-column {:class "hidden-xs hidden-sm " :style {:width "15%"}} (time/format-timestamp-for-ui (or modified created))]
            [ui/table-row-column {:class "hidden-xs hidden-sm " :style {:width "13%"}}
             [ui/icon-button {:href     "#"
                              :on-click #(do
                                           (.preventDefault %)
                                           (e! (fp/->ChangePage :edit-route {:id id})))}
              [ic/content-create]]
             [delete-route-action e! row]]])
         routes))]]])

(defn routes [e! {operators :transport-operators-with-services :as app}]
  (e! (route-list/->LoadRoutes))
  (fn [e! {routes :routes-vector operator :transport-operator :as app}]
    [:div
     [list-header/header
      app
      (tr [:route-list-page :header-route-list])
      [common/tooltip
       {:text (if (empty? operators)
                (tr [:route-list-page :create-new-route-disabled])
                (tr [:buttons :add-new-route]))}
       [ui/raised-button {:label    (tr [:buttons :add-new-route])
                          :on-click #(do
                                       (.preventDefault %)
                                       (e! (route-list/->CreateNewRoute)))
                          :primary  true
                          :disabled (empty? operators)
                          :icon     (ic/content-add)}]]
      [t-operator-sel/transport-operator-selection e! app]
      [buttons/open-link
       (tr [:route-list-page :link-to-help-pdf-url])
       (tr [:route-list-page :link-to-help-pdf])]]

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
            [:div (tr [:route-list-page :route-list-active-routes])
             [common/linkify url (tr [:route-list-page :route-list-gtfs-zip-file])]]
            [:div {:style {:width "100%"}}
             (tr [:route-list-page :route-list-copy-link])
             [common/copy-to-clipboard url]]]]]))]))
