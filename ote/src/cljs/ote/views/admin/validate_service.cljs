(ns ote.views.admin.validate-service
  "Services that are in validation."
  (:require [cljs-react-material-ui.reagent :as ui]
            [clojure.string :as str]
            [stylefy.core :as stylefy]
            [ote.localization :refer [tr tr-key]]
            [ote.time :as time]
            [ote.style.base :as style-base]
            [ote.ui.buttons :as buttons]
            [ote.ui.common :refer [linkify]]
            [ote.app.controller.admin-validation :as admin-validation]
            [ote.app.controller.front-page :as fp]
            [reagent.core :as r]
            [ote.style.dialog :as style-dialog]))

(defn page-controls [e! app]
  [:div])

(defn validate-services [e! app]
  (let [services (get-in app [:admin :in-validation :results])]
    [:div.row

     (when services
       [:span
        [ui/table {:selectable false}
         [ui/table-header {:class "table-header-wrap"
                           :adjust-for-checkbox false
                           :display-select-all false}
          [ui/table-row
           [ui/table-header-column {:class "table-header-wrap" :style {:width "20%"}} "Nimi"]
           [ui/table-header-column {:class "table-header-wrap" :style {:width "15%"}} "Palveluntuottaja"]
           [ui/table-header-column {:class "table-header-wrap" :style {:width "15%"}} "Tyyppi"]
           [ui/table-header-column {:class "table-header-wrap" :style {:width "15%"}} "Alityyppi"]
           [ui/table-header-column {:class "table-header-wrap" :style {:width "10%"}} "Tarkistettavaksi"]
           [ui/table-header-column {:class "table-header-wrap" :style {:width "10%"}} "Luotu / Muokattu"]]]
         [ui/table-body {:display-row-checkbox false}
          (doall
            (for [{:keys [id name operator-name type sub-type published validate created modified] :as result} services]
              ^{:key (:id result)}
              [ui/table-row {:selectable false}
               [ui/table-row-column (merge (stylefy/use-style style-base/table-col-style-wrap) {:width "20%"})
                [:a {:href (str "/edit-service/" id)
                     :on-click #(do
                                  (.preventDefault %)
                                  (e! (admin-validation/->EditService id)))} name]]
               [ui/table-row-column (merge (stylefy/use-style style-base/table-col-style-wrap) {:width "15%"})
                operator-name]
               [ui/table-row-column (merge (stylefy/use-style style-base/table-col-style-wrap) {:width "15%"})
                (tr [:enums :ote.db.transport-service/type (keyword type)])]
               [ui/table-row-column (merge (stylefy/use-style style-base/table-col-style-wrap) {:width "15%"})
                (tr [:enums :ote.db.transport-service/sub-type (keyword sub-type)])]
               [ui/table-row-column (merge (stylefy/use-style style-base/table-col-style-wrap) {:width "10%"})
                (time/format-timestamp-for-ui validate)]
               [ui/table-row-column (merge (stylefy/use-style style-base/table-col-style-wrap) {:width "10%"})
                [:span (time/format-timestamp-for-ui created) [:br] (time/format-timestamp-for-ui modified)]]]))]]])]))
