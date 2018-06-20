(ns ote.views.admin
  "Admin panel views. Note this has a limited set of users and is not
  currently localized, all UI text is in Finnish."
  (:require [cljs-react-material-ui.reagent :as ui]
            [ote.ui.form-fields :as form-fields]
            [ote.app.controller.admin :as admin-controller]
            [ote.db.transport-service :as t-service]
            [ote.db.transport-operator :as t-operator]
            [ote.db.modification :as modification]
            [clojure.string :as str]
            [ote.app.controller.front-page :as fp]
            [ote.localization :refer [tr tr-key]]
            [ote.ui.common :refer [linkify]]
            [ote.time :as time]
            [cljs-react-material-ui.icons :as ic]
            [reagent.core :as r]
            [ote.ui.common :as ui-common]
            [ote.views.admin.interfaces :as interfaces]
            [ote.views.admin.users :as users]))

(def id-filter-type [:operators :services :ALL])
(def published-types [:YES :NO :ALL])

(defn- delete-transport-operator-action [e! {::t-operator/keys [id name]
                                :keys [show-delete-modal?]
                                :as operator}]
  [:span
   [ui/icon-button {:id       (str "delete-operator-" id)
                    :href     "#"
                    :on-click #(do
                                 (.preventDefault %)
                                 (e! (admin-controller/->OpenDeleteOperatorModal id)))}
    [ic/action-delete]]
   (when show-delete-modal?
     [ui/dialog
      {:open    true
       :title   "Poista palveluntuottaja ja kaikki sen tiedot"
       :actions [(r/as-element
                   [ui/flat-button
                    {:label    (tr [:buttons :cancel])
                     :primary  true
                     :on-click #(do
                                  (.preventDefault %)
                                  (e! (admin-controller/->CancelDeleteOperator id)))}])
                 (r/as-element
                   [ui/raised-button
                    {:label     (tr [:buttons :delete])
                     :icon      (ic/action-delete-forever)
                     :secondary true
                     :primary   true
                     :on-click  #(do
                                   (.preventDefault %)
                                   (e! (admin-controller/->ConfirmDeleteOperator id)))}])]}

      [:div [:p "Oletko varma, että haluat poistaa palveluntuottajan?
      Samalla poistetaan kaikki palvelut ja tiedot mitä tuottajalle on syötetty."]

       [form-fields/field {:name :ensured-id
                           :type :string
                           :full-width? true
                           :label "Anna varmistukseksi palveluntuottajan id"
                           :update! #(e! (admin-controller/->EnsureServiceOperatorId id %))}
        (:ensured-id operator)]]])])

(defn operator-list [e! app]
  (let [{:keys [loading? results operator-filter]} (get-in app [:admin :operator-list])]
    [:div.row
     [:div.row.col-md-12
      [form-fields/field {:type :string :label "Hae palveluntuottajan nimellä tai sen osalla"
                          :update! #(e! (admin-controller/->UpdateOperatorFilter %))}
       operator-filter]

      [ui/raised-button {:primary true
                         :disabled (str/blank? filter)
                         :on-click #(e! (admin-controller/->SearchOperators))
                         :label "Hae palveluntuottajia"}]]
     [:div.row
      (when loading?
        [:span "Ladataan palveluntuottajia..."])

      (when results
        [:span
         [:div "Hakuehdoilla löytyi " (count results) " palveluntuottajaa."]
         [ui/table {:selectable false}
          [ui/table-header {:adjust-for-checkbox false
                            :display-select-all false}
           [ui/table-row
            [ui/table-header-column {:style {:width "7%"}} "Id"]
            [ui/table-header-column {:style {:width "10%"}} "Y-tunnus"]
            [ui/table-header-column {:width "21%"} "Nimi"]
            [ui/table-header-column {:width "10%"} "GSM"]
            [ui/table-header-column {:width "10%"} "Puhelin"]
            [ui/table-header-column {:width "18%"} "Sähköposti"]
            [ui/table-header-column {:width "14%"} "Käyttäjähallinta"]
            [ui/table-header-column {:width "10%"} "Toiminnot"]]]
          [ui/table-body {:display-row-checkbox false}
           (doall
             (for [{::t-operator/keys [id name gsm phone business-id ckan-group-id email ]
                    :keys [show-add-member-dialog?] :as result} results]
               ^{:key (::t-operator/id result)}
               [ui/table-row {:selectable false}
                [ui/table-row-column {:style {:width "7%"}} id]
                [ui/table-row-column {:style {:width "10%"}} business-id]
                [ui/table-row-column {:style {:width "21%"}}
                 [:a {:href     "#"
                      :on-click #(do
                                   (.preventDefault %)
                                   (e! (fp/->ChangePage :transport-operator {:id id})))} name]]
                [ui/table-row-column {:style {:width "10%"}} gsm]
                [ui/table-row-column {:style {:width "10%"}} phone]
                [ui/table-row-column {:style {:width "18%"}} email]
                [ui/table-row-column {:style {:width "14%"}}
                 [ui/flat-button {:label (tr [:buttons :add-new-member])
                                  :style {:margin-top "1.5em"
                                          :font-size "8pt"}
                                  :on-click #(do
                                               (.preventDefault %)
                                               (e! (admin-controller/->ToggleAddMemberDialog id)))}]
                 (when show-add-member-dialog?
                   [ui-common/ckan-iframe-dialog (::t-operator/name result)
                    (str "/organization/member_new/" ckan-group-id)
                    #(e! (admin-controller/->ToggleAddMemberDialog id))])]
                [ui/table-row-column {:style {:width "10%"}} [ui/icon-button {:href     "#"
                                                                              :on-click #(do
                                                                                           (.preventDefault %)
                                                                                           (e! (fp/->ChangePage :transport-operator {:id id})))}
                                                              [ic/content-create]]
                 [delete-transport-operator-action e! result]]]))]]])]]))

(defn business-id-report [e! app]
  (let [{:keys [loading? business-id-filter results]} (get-in app [:admin :business-id-report])]
    [:div
      [:div.row
       [:h1 "Y-tunnus raportti"]
       [:p "Listataan joko Palveluntuottajien y-tunnukset, palveluihin lisättyjen yritysten y-tunnukset tai molemmat.
       Palveluista mukaan on otettu vain jo julkaistut palvelut."]]
      [:div.row
       [form-fields/field {:type :selection
                           :label "Y-tunnuksen lähde"
                           :options id-filter-type
                           :show-option (tr-key [:admin-page :business-id-filter])
                           :update! #(e! (admin-controller/->UpdateBusinessIdFilter %))}
        business-id-filter]
       [ui/raised-button {:label "Hae raportti"
                          :primary true
                          :disabled (str/blank? filter)
                          :on-click #(e! (admin-controller/->GetBusinessIdReport))}]]

       (when loading?
         [:div.row "Ladataan raporttia..."])

       (if results
         [:div.row
          [:div "Hakuehdoilla löytyi " (count results) " yritystä."]
          [ui/table {:selectable false}
           [ui/table-header {:adjust-for-checkbox false
                             :display-select-all false}
            [ui/table-row
             [ui/table-header-column {:width "7%"} "Id"]
             [ui/table-header-column {:width "20%"} "Yritys"]
             [ui/table-header-column {:width "13%"} "Y-tunnus"]
             [ui/table-header-column {:width "15%"} "GSM"]
             [ui/table-header-column {:width "10%"} "Puhelin"]
             [ui/table-header-column {:width "20%"} "Sähköposti"]
             [ui/table-header-column {:width "15%"} "Lähde"]]]
           [ui/table-body {:display-row-checkbox false}
            (doall
             (for [{:keys [id operator business-id gsm phone email source]} results]
               [ui/table-row {:selectable false}
                [ui/table-row-column {:width "7%"} id]
                [ui/table-row-column {:width "20%"} operator]
                [ui/table-row-column {:width "13%"} business-id]
                [ui/table-row-column {:width "15%"} gsm]
                [ui/table-row-column {:width "10%"} phone]
                [ui/table-row-column {:width "20%"} email]
                [ui/table-row-column {:width "15%"} (if (= "service" source) "Palvelu" "Palveluntuottaja")]]))]]]
         [:div "Hakuehdoilla ei löydy yrityksiä"])]))

(defn service-listing [e! app]
  (let [{:keys [loading? results service-filter operator-filter published-filter]} (get-in app [:admin :service-listing])]
    [:div.row
     [:div.row.col-md-5
      [form-fields/field {:type    :string :label "Hae palvelun nimellä tai sen osalla"
                          :update! #(e! (admin-controller/->UpdateServiceFilter %))}
       service-filter]

      [ui/raised-button {:label    "Hae"
                         :primary  true
                         :disabled (str/blank? filter)
                         :on-click #(e! (admin-controller/->SearchServices))}]]
     [:div.col-md-5

      [form-fields/field {:type    :string :label "Hae palveluntuottajan nimellä tai sen osalla"
                          :update! #(e! (admin-controller/->UpdateServiceOperatorFilter %))}
       operator-filter]

      [ui/raised-button {:label    "Hae"
                         :primary  true
                         :disabled (str/blank? filter)
                         :on-click #(e! (admin-controller/->SearchServicesByOperator))}]]

     [:div.row.col-md-2
      [form-fields/field {:type :selection
                          :label "Julkaistu?"
                          :options published-types
                          :show-option (tr-key [:admin-page :published-types])
                          :update! #(e! (admin-controller/->UpdatePublishedFilter %))}
       published-filter]]
     [:div.row
     (when loading?
       [:span "Ladataan palveluita..."])

     (when results
       [:span
        [:div "Hakuehdoilla löytyi " (count results) " palvelua."]
        [ui/table {:selectable false}
         [ui/table-header {:adjust-for-checkbox false
                           :display-select-all false}
          [ui/table-row
           [ui/table-header-column {:style {:width "20%"}} "Nimi"]
           [ui/table-header-column {:style {:width "20%"}}  "Palveluntuottaja"]
           [ui/table-header-column {:style {:width "20%"}} "Tyyppi"]
           [ui/table-header-column {:style {:width "20%"}} "Alityyppi"]
           [ui/table-header-column {:style {:width "5%"}}  "Julkaistu"]
           [ui/table-header-column {:style {:width "15%"}} "Luotu"]]]
         [ui/table-body {:display-row-checkbox false}
          (doall
            (for [{::t-service/keys [id name operator-name type sub-type published?]
                   ::modification/keys [created] :as result} results]
              ^{:key (::t-service/id result)}
              [ui/table-row {:selectable false}
               [ui/table-row-column {:style {:width "20%"}}  [:a {:href "#" :on-click #(do
                                                                (.preventDefault %)
                                                                (e! (fp/->ChangePage :edit-service {:id id})))} name]]
               [ui/table-row-column {:style {:width "20%"}}  operator-name]
               [ui/table-row-column {:style {:width "20%"}} (tr [:enums :ote.db.transport-service/type (keyword type)])]
               [ui/table-row-column {:style {:width "20%"}} (tr [:enums :ote.db.transport-service/sub-type (keyword sub-type)])]
               [ui/table-row-column {:style {:width "5%"}} (if published? "Kyllä" "Ei") ]
               [ui/table-row-column {:style {:width "15%"}}  (time/format-timestamp-for-ui created)]]))]]])]]))

(defn admin-panel [e! app]
  (let [selected-tab (or (get-in app [:admin :tab :admin-page]) "users")]
    [ui/tabs {:value     selected-tab
              :on-change #(e! (admin-controller/->ChangeAdminTab %))}
     [ui/tab {:label "Käyttäjät" :value "users"}
      [users/user-listing e! app]]
     [ui/tab {:label "Palvelut" :value "services"}
      [service-listing e! app]]
     [ui/tab {:label "Y-tunnus raportti" :value "businessid"}
      [business-id-report e! app]]
     [ui/tab {:label "Palveluntuottajat" :value "operators"}
      [operator-list e! app]]
     [ui/tab {:label "Rajapinnat" :value "interfaces"}
      [interfaces/interface-list e! app]]]))
