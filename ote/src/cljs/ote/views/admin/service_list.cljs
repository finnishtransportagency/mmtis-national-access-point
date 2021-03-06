(ns ote.views.admin.service-list
  "Admin panel views. Note this has a limited set of users and is not
  currently localized, all UI text is in Finnish."
  (:require [cljs-react-material-ui.reagent :as ui]
            [ote.ui.form-fields :as form-fields]
            [ote.app.controller.admin :as admin-controller]
            [ote.db.modification :as modification]
            [ote.db.transport-service :as t-service]
            [clojure.string :as str]
            [ote.localization :refer [tr tr-key]]
            [ote.ui.common :refer [linkify]]
            [ote.ui.buttons :as buttons]
            [stylefy.core :as stylefy]
            [ote.style.base :as style-base]
            [ote.time :as time]
            [ote.app.controller.front-page :as fp]))

(def published-types [:YES :NO :ALL])

(defn service-list-page-controls [e! app]
  [:div.row
   [:div.row.col-md-5
    [form-fields/field {:type    :string
                        :label "Hae palvelun nimellä tai sen osalla"
                        :update! #(e! (admin-controller/->UpdateServiceFilter %))
                        :on-enter #(e! (admin-controller/->SearchServices))}
     (get-in app [:admin :service-listing :service-filter])]

    [buttons/save
     {:on-click  #(e! (admin-controller/->SearchServices))
      :disabled (str/blank? filter)
      :style {:margin-left "1rem"}}
     "Hae"]]

   [:div.col-md-5

    [form-fields/field {:type    :string :label "Hae palveluntuottajan nimellä tai sen osalla"
                        :update! #(e! (admin-controller/->UpdateServiceOperatorFilter %))
                        :on-enter #(e! (admin-controller/->SearchServicesByOperator))}
     (get-in app [:admin :service-listing :operator-filter])]

    [buttons/save
     {:on-click  #(e! (admin-controller/->SearchServicesByOperator))
      :disabled (str/blank? filter)
      :style {:margin-left "1rem"}}
     "Hae"]]

   [:div.row.col-md-2
    [form-fields/field {:type        :selection
                        :label       "Julkaistu?"
                        :options     published-types
                        :show-option (tr-key [:admin-page :published-types])
                        :update!     #(e! (admin-controller/->UpdatePublishedFilter %))}
     (get-in app [:admin :service-listing :published-filter])]]])

(defn service-listing [e! app]
  (let [{:keys [loading? results]} (get-in app [:admin :service-listing])
        fd (js/Date. 0)]
    [:div.row
     (when loading?
       [:span "Ladataan palveluita..."])

     (when results
       [:span
        [:div "Hakuehdoilla löytyi " (count results) " palvelua."]
        [ui/table {:selectable false}
         [ui/table-header {:class "table-header-wrap"
                           :adjust-for-checkbox false
                           :display-select-all  false}
          [ui/table-row
           [ui/table-header-column
            {:class "table-header-wrap" :style {:width "20%"}}
            "Nimi"]
           [ui/table-header-column
            {:class "table-header-wrap" :style {:width "20%"}}
            "Palveluntuottaja"]
           [ui/table-header-column
            {:class "table-header-wrap" :style {:width "15%"}}
            "Tyyppi"]
           [ui/table-header-column
            {:class "table-header-wrap" :style {:width "20%"}}
            "Alityyppi"]
           [ui/table-header-column
            {:class "table-header-wrap" :style {:width "10%"}}
            "Julkaistu"]
           [ui/table-header-column
            {:class "table-header-wrap" :style {:width "15%"}}
            "Luotu / Muokattu"]]]
         [ui/table-body {:display-row-checkbox false}
          (doall
            (for [{::t-service/keys    [id name operator-name type sub-type published]
                   ::modification/keys [created modified] :as result} results]
              ^{:key (::t-service/id result)}
              [ui/table-row {:selectable false}
               [ui/table-row-column
                (merge (stylefy/use-style style-base/table-col-style-wrap)
                       {:width "20%"})
                [:a {:href (str "/edit-service/" id)
                     :on-click #(do
                                  (.preventDefault %)
                                  (e! (fp/->ChangePage :edit-service {:id id})))} name]]
               [ui/table-row-column
                (merge (stylefy/use-style style-base/table-col-style-wrap)
                       {:width "20%"})
                operator-name]
               [ui/table-row-column
                (merge (stylefy/use-style style-base/table-col-style-wrap)
                       {:width "15%"})
                (tr [:enums :ote.db.transport-service/type (keyword type)])]
               [ui/table-row-column
                (merge (stylefy/use-style style-base/table-col-style-wrap)
                       {:width "20%"})
                (tr [:enums :ote.db.transport-service/sub-type (keyword sub-type)])]
               [ui/table-row-column
                (merge (stylefy/use-style style-base/table-col-style-wrap)
                       {:width "10%"})
                (cond
                  (= published fd)
                  "Kyllä"
                  (nil? published)
                  "Ei"
                  :else
                  (time/format-timestamp-for-ui published))]
               [ui/table-row-column
                (merge (stylefy/use-style style-base/table-col-style-wrap)
                       {:width "15%"})
                [:span
                 (time/format-timestamp-for-ui created)
                 [:br]
                 (time/format-timestamp-for-ui modified)]]]))]]])]))
