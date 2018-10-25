(ns ote.views.admin.sea-routes
  "Admin panel views. Note this has a limited set of users and is not
  currently localized, all UI text is in Finnish."
  (:require [cljs-react-material-ui.reagent :as ui]
            [ote.app.controller.admin :as admin-controller]
            [clojure.string :as str]
            [ote.localization :refer [tr tr-key]]
            [ote.ui.common :refer [linkify]]
            [cljs-react-material-ui.icons :as ic]
            [ote.ui.form :as form]
            [ote.app.controller.front-page :as fp]
            [ote.ui.form-fields :as form-fields]))

(defn sea-routes [e! app]
  (let [{:keys [loading? results filters]}
        (get-in app [:admin :sea-routes])
        loc (.-location js/document)]
    [:div.row
     [:div.row.col-md-12 {:style {:padding-top "20px"}}
      [form-fields/field {:update!         #(e! (admin-controller/->UpdateSeaRouteFilters %))
                          :name            :operator-name
                          :label           "Palveluntuottaja"
                          :type            :string
                          :hint-text       "Palveluntuottajan nimi tai sen osa"
                          :full-width?     true
                          :container-class "col-xs-12 col-sm-4 col-md-4"}
       filters]

      [ui/raised-button {:primary  true
                         :disabled (str/blank? filter)
                         :on-click #(e! (admin-controller/->SearchSeaRoutes))
                         :label    "Hae merireitit"}]]

     [:div.row {:style {:padding-top "40px"}}
      (when loading?
        [:span "Ladataan merireittejä..."])

      (when results
        [:div
         [:div "Hakuehdoilla löytyi " (count results) " merireittiä."]
         [ui/table {:selectable false}
          [ui/table-header {:adjust-for-checkbox false
                            :display-select-all  false}
           [ui/table-row
            [ui/table-header-column {:style {:width "30%"}} "Palveluntuottaja"]
            [ui/table-header-column {:style {:width "40%"}} "Merireitti"]
            [ui/table-header-column {:style {:width "10%"}} "Julkaistu?"]
            [ui/table-header-column {:style {:width "20%"}} "GTFS paketti"]
            ]]
          [ui/table-body {:display-row-checkbox false}
           (doall
             (for [{:keys [route-id route-name operator-name operator-id published?] :as sea-route} results]
               ^{:key (str "link_" route-id)}
               [ui/table-row {:selectable false}
                [ui/table-row-column {:style {:width "30%"}} operator-name]
                [ui/table-row-column {:style {:width "40%"}} [:a {:href "#"
                                                                  :on-click #(do
                                                                               (.preventDefault %)
                                                                               (e! (admin-controller/->ChangeRedirectTo :admin))
                                                                               (e! (fp/->ChangePage :edit-route {:id route-id})))} route-name]]
                [ui/table-row-column {:style {:width "10%"}} (if published? "Kyllä" "Ei") ]
                [ui/table-row-column {:style {:width "20%"}}
                 [:a {:href (str (.-protocol loc) "//" (.-host loc) (.-pathname loc)
                     "export/gtfs/" operator-id)}
                  "Lataa gtfs"]]]))]]])]]))
