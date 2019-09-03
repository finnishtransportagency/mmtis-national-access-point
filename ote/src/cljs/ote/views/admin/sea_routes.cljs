(ns ote.views.admin.sea-routes
  "Admin panel views. Note this has a limited set of users and is not
  currently localized, all UI text is in Finnish."
  (:require [cljs-react-material-ui.reagent :as ui]
            [ote.app.controller.admin :as admin-controller]
            [clojure.string :as str]
            [ote.localization :refer [tr tr-key]]
            [ote.ui.common :refer [linkify]]
            [ote.localization :refer [selected-language]]
            [ote.app.controller.front-page :as fp]
            [ote.ui.form-fields :as form-fields]
            [ote.db.transport-service :as t-service]
            [ote.db.transport-operator :as t-operator]
            [ote.db.transit :as transit]
            [ote.time :as time]))

(defn sea-routes-page-controls [e! app]
  [:div.row {:style {:padding-top "20px"}}
   [form-fields/field {:update!         #(e! (admin-controller/->UpdateSeaRouteFilters %))
                       :on-enter        #(e! (admin-controller/->SearchSeaRoutes))
                       :name            :operator-name
                       :label           "Palveluntuottaja"
                       :type            :string
                       :hint-text       "Palveluntuottajan nimi tai sen osa"
                       :container-class "col-xs-12 col-sm-4 col-md-4"}
    (get-in app [:admin :sea-routes :filters])]

   [ui/raised-button {:primary  true
                      :disabled (str/blank? filter)
                      :on-click #(e! (admin-controller/->SearchSeaRoutes))
                      :label    "Hae merireitit"}]])

(defn sea-routes [e! app]
  (let [{:keys [loading? results filters]}
        (get-in app [:admin :sea-routes])
        loc (.-location js/document)]
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
            [ui/table-header-column {:style {:width "25%"}} "Palveluntuottaja"]
            [ui/table-header-column {:style {:width "25%"}} "Merireitti"]
            [ui/table-header-column {:style {:width "10%"}} "Julkaistu?"]
            [ui/table-header-column {:style {:width "15%"}} "Luotu"]
            [ui/table-header-column {:style {:width "15%"}} "Viim. Ajopäivä"]
            [ui/table-header-column {:style {:width "10%"}} "GTFS paketti"]]]
          [ui/table-body {:display-row-checkbox false}
           (doall
             (for [{::transit/keys [id name operator-name operator-id published? created to-date] :as sea-route} results]
               ^{:key (str "link_" id)}
               [ui/table-row {:selectable false}
                [ui/table-row-column {:style {:width "25%"}} operator-name]
                [ui/table-row-column {:style {:width "25%"}} [:a {:href (str "/#/edit-route/" id)
                                                                  :on-click #(do
                                                                               (.preventDefault %)
                                                                               (e! (admin-controller/->ChangeRedirectTo :admin))
                                                                               (e! (fp/->ChangePage :edit-route {:id id})))}
                                                              (t-service/localized-text-with-fallback @selected-language name)]]
                [ui/table-row-column {:style {:width "10%"}} (if published? "Kyllä" "Ei") ]
                [ui/table-row-column {:style {:width "15%"}} (time/format-timestamp-for-ui created) ]
                [ui/table-row-column {:style {:width "15%"}} (time/format-date to-date)]
                [ui/table-row-column {:style {:width "10%"}}
                 [:a {:href (str (.-protocol loc) "//" (.-host loc) (.-pathname loc)
                     "export/gtfs/" operator-id)}
                  "Lataa gtfs"]]]))]]])]))
