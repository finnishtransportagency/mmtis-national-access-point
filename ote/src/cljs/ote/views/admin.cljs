(ns ote.views.admin
  "Admin panel views. Note this has a limited set of users and is not
  currently localized, all UI text is in Finnish."
  (:require [ote.mui-wrapper.reagent :as ui]
            [clojure.string :as str]
            [ote.mui-wrapper.icons :as ic]
            [reagent.core :as r]
            [stylefy.core :as stylefy]

            [ote.db.transport-service :as t-service]
            [ote.db.transport-operator :as t-operator]
            [ote.db.modification :as modification]
            [ote.localization :refer [tr tr-key]]
            [ote.time :as time]

            [ote.app.controller.admin :as admin-controller]
            [ote.app.controller.front-page :as fp]
            [ote.theme.colors :as colors]
            [ote.style.base :as style-base]
            [ote.style.dialog :as style-dialog]
            [ote.ui.common :refer [linkify]]
            [ote.ui.common :as ui-common]
            [ote.ui.page :as page]
            [ote.ui.tabs :as tabs]
            [ote.ui.form-fields :as form-fields]
            [ote.views.admin.interfaces :as interfaces]
            [ote.views.admin.reports :as report-view]
            [ote.views.admin.users :as users]
            [ote.views.admin.service-list :as service-list]
            [ote.views.admin.sea-routes :as sea-routes]))

(def id-filter-type [:operators :services :ALL])

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
      {:open true
       :actionsContainerStyle style-dialog/dialog-action-container
       :title "Poista palveluntuottaja ja kaikki sen tiedot"
       :actions [(r/as-element
                   [ui/flat-button
                    {:label (tr [:buttons :cancel])
                     :primary true
                     :on-click #(do
                                  (.preventDefault %)
                                  (e! (admin-controller/->CancelDeleteOperator id)))}])
                 (r/as-element
                   [ui/raised-button
                    {:label (tr [:buttons :delete])
                     :icon (ic/action-delete-forever)
                     :secondary true
                     :primary true
                     :on-click #(do
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

(defn operator-page-controls [e! app]
  [:div.row
   [form-fields/field {:type :string
                       :label "Hae palveluntuottajan nimellä tai sen osalla"
                       :update! #(e! (admin-controller/->UpdateOperatorFilter %))
                       :on-enter #(e! (admin-controller/->SearchOperators))}
    (get-in app [:admin :operator-list :operator-filter])]
   [ui/raised-button {:primary true
                      :disabled (str/blank? filter)
                      :on-click #(e! (admin-controller/->SearchOperators))
                      :label "Hae palveluntuottajia"}]])

(defn operator-list [e! app]
  (let [{:keys [loading? results]} (get-in app [:admin :operator-list])]
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
            [ui/table-header-column {:style {:width "7%" :padding-left "15px" :padding-right "15px"}} "Id"]
            [ui/table-header-column {:style {:width "9%" :padding-left "15px" :padding-right "15px"}} "Y-tunnus"]
            [ui/table-header-column {:style {:width "20%" :padding-left "15px" :padding-right "15px"}} "Nimi"]
            [ui/table-header-column {:style {:width "10%" :padding-left "15px" :padding-right "15px"}} "GSM"]
            [ui/table-header-column {:style {:width "10%" :padding-left "15px" :padding-right "15px"}} "Puhelin"]
            [ui/table-header-column {:style {:width "18%" :padding-left "15px" :padding-right "15px"}} "Sähköposti"]
            [ui/table-header-column {:style {:width "13%" :padding-left "15px" :padding-right "15px"}} "Käyttäjähallinta"]
            [ui/table-header-column {:style {:width "12%" :padding-left "15px" :padding-right "15px"}} "Toiminnot"]]]
          [ui/table-body {:display-row-checkbox false}
           (doall
             (for [{::t-operator/keys [id name gsm phone business-id ckan-group-id email ]
                    :keys [show-add-member-dialog?] :as result} results]
               ^{:key (::t-operator/id result)}
               [ui/table-row {:selectable false}
                [ui/table-row-column {:style {:width "7%" :padding-left "15px" :padding-right "15px"}} id]
                [ui/table-row-column {:style {:width "9%" :padding-left "15px" :padding-right "15px"}} business-id]
                [ui/table-row-column {:style {:width "20%" :padding-left "15px" :padding-right "15px"}}
                 [:a {:href     (str "#/transport-operator/" id)
                      :on-click #(do
                                   (.preventDefault %)
                                   (e! (admin-controller/->EditTransportOperator business-id)))} name]]
                [ui/table-row-column {:style {:width "10%" :padding-left "15px" :padding-right "15px"}} gsm]
                [ui/table-row-column {:style {:width "10%" :padding-left "15px" :padding-right "15px"}} phone]
                [ui/table-row-column {:style {:width "18%" :padding-left "15px" :padding-right "15px"}} email]
                [ui/table-row-column {:style {:width "13%" :padding-left "15px" :padding-right "15px"}}
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
                [ui/table-row-column {:style {:width "12%" :padding-left "15px" :padding-right "15px"}} [ui/icon-button {:href     "#"
                                                                              :on-click #(do
                                                                                           (.preventDefault %)
                                                                                           (e! (admin-controller/->EditTransportOperator business-id)))}
                                                              [ic/content-create]]
                 [delete-transport-operator-action e! result]]]))]]])]))

(def services-row-style {:height "20px" :padding "0 0 0px 1px"})

(defn services-list [e! services]
  [:div {:style {:border-left "1px solid rgb(224, 224, 224)"}}
   (if (seq services)
     [:div {:style {:padding "0 0 0px 5px"}}

      [ui/table {:selectable false
                 :fixed-header true
                 :body-style {:overflow-y "auto"
                              :max-height "100px"}}
       [ui/table-body {:display-row-checkbox false
                       :show-row-hover true}
        (doall
         (for [{:keys [id name transport-type sub-type brokerage? operation-area]} services]
           ^{:key (str "service-" name)}
           [ui/table-row {:selectable false :style services-row-style}
            [ui/table-row-column {:style services-row-style
                                  :width "25%"}
             [:a {:href "#" :on-click #(do
                                         (.preventDefault %)
                                         (e! (fp/->ChangePage :edit-service {:id id})))} name] ]
            [ui/table-row-column {:style services-row-style :width "15%"} (str/join ", " (mapv #(tr [:enums :ote.db.transport-service/transport-type (keyword %)]) transport-type))]
            [ui/table-row-column {:style services-row-style :width "25%"}  (tr [:enums :ote.db.transport-service/sub-type (keyword sub-type)])]
            [ui/table-row-column {:style (merge services-row-style {:text-align "center"}) :width "10%" }  (when brokerage? "X")]
            [ui/table-row-column {:style services-row-style :width "25%"} (str/join ", " operation-area)]
            ]))]]]
     [:div {:style {:padding-left "10px"
                    :line-height "48px"}}
      "Ei palveluja."])])

(defn business-id-page-controls [e! app]
  [:div
   [:div.row
    [:p "Listataan joko Palveluntuottajien y-tunnukset, palveluihin lisättyjen yritysten y-tunnukset tai molemmat.
       Palveluista mukaan on otettu vain jo julkaistut palvelut."]]
   [:div.row
    [form-fields/field {:type        :selection
                        :label       "Y-tunnuksen lähde"
                        :options     id-filter-type
                        :show-option (tr-key [:admin-page :business-id-filter])
                        :update!     #(e! (admin-controller/->UpdateBusinessIdFilter %))
                        :on-enter    #(e! (admin-controller/->GetBusinessIdReport))}
     (get-in app [:admin :business-id-report :business-id-filter])]
    [ui/raised-button {:label    "Hae raportti"
                       :primary  true
                       :disabled (str/blank? filter)
                       :on-click #(e! (admin-controller/->GetBusinessIdReport))}]]])

(defn business-id-report [e! app]
  (let [{:keys [loading? results]} (get-in app [:admin :business-id-report])]
    [:div
     (when loading?
       [:div.row "Ladataan raporttia..."])

     (if results
       [:div.row
        [:div "Hakuehdoilla löytyi " (count results) " yritystä."]
        [:div
         [ui/table {:selectable false}
          [ui/table-header {:adjust-for-checkbox false
                            :display-select-all false}
           [ui/table-row
            [ui/table-header-column {:width "3%"  } "Id"]
            [ui/table-header-column {:width "10%" } "Yritys"]
            [ui/table-header-column {:width "6%"  :style {:padding 10}} "Y-tunnus"]
            [ui/table-header-column {:width "8%"  :style {:padding 10}} "Palvelu"]
            [ui/table-header-column {:width "7%"  :style {:padding 10}} "Liikennemuoto"]
            [ui/table-header-column {:width "10%" :style {:padding 10}} "Palvelutyyppi"]
            [ui/table-header-column {:width "7%"  :style {:padding 10}} "Välityspalvelu"]
            [ui/table-header-column {:width "10%" :style {:padding 10}} "Toiminta-alue"]
            [ui/table-header-column {:width "7%"  } "GSM"]
            [ui/table-header-column {:width "7%"  } "Puhelin"]
            [ui/table-header-column {:width "10%" } "Sähköposti"]
            [ui/table-header-column {:width "8%"  } "Lähde"]]]
          [ui/table-body {:display-row-checkbox false}
           (doall
            (for [{:keys [id operator business-id services gsm phone email source]} results]
              ^{:key (str "report-" business-id)}
              [ui/table-row {:selectable false}
               [ui/table-row-column {:width "3%"} id]
               [ui/table-row-column {:width "10%" :style services-row-style}
                [:a {:href     "#"
                     :on-click #(do
                                  (.preventDefault %)
                                  (e! (fp/->ChangePage :transport-operator {:id id})))} operator]]
               [ui/table-row-column {:width "6%" :style services-row-style} business-id]
               [ui/table-row-column {:width "40%" :style services-row-style}
                [services-list e! services]]
               [ui/table-row-column {:width "7%" :style services-row-style} gsm]
               [ui/table-row-column {:width "7%" :style services-row-style} phone]
               [ui/table-row-column {:width "10%" :style services-row-style} email]
               [ui/table-row-column {:width "8%" :style services-row-style} (if (= "service" source) "Palvelu" "Palveluntuottaja")]]))]]]]
       [:div "Hakuehdoilla ei löydy yrityksiä"])]))

(defn admin-panel [e! app]
  (let [page (:page app)
        tabs [{:label "Käyttäjä" :value "users"}
              {:label "Palvelut" :value "services"}
              {:label "Y-tunnus raportti" :value "businessid"}
              {:label "Palveluntuottajat" :value "operators"}
              {:label "Rajapinnat" :value "interfaces"}
              {:label "CSV Raportit" :value "reports"}
              {:label "Merireitit" :value "sea-routes"}]
        selected-tab (or (get-in app [:admin :tab :admin-page]) "users")]
    [:div
     [:div {:style {:position "absolute" :top "90px" :right "20px"}}
      [:a (merge {:href "/#/admin/detected-changes/detect-changes"
                  :style {:margin-right "2rem"}
                  :id "admin-transit-changes-settings-btn"
                  :on-click #(do
                               (.preventDefault %)
                               (e! (fp/->ChangePage :admin-detected-changes nil)))}
                 (stylefy/use-style style-base/blue-link-with-icon))
       [ic/action-settings]
       [:span {:style {:padding-left "0.5rem"}}
        "Muutostunnistuksen asetukset"]]]
     [page/page-controls "" "Ylläpitopaneeli"
      [:div {:style {:padding-bottom "20px"}}
       [tabs/tabs tabs {:update-fn #(e! (admin-controller/->ChangeTab %))
                        :selected-tab (get-in app [:admin :tab :admin-page])}]
      ;; Show search parameters in page-controls section
      (when (= "users" selected-tab)
        [users/users-page-controls e! app])
      (when (= "services" selected-tab)
        [service-list/service-list-page-controls e! app])
      (when (= "businessid" selected-tab)
        [business-id-page-controls e! app])
      (when (= "operators" selected-tab)
        [operator-page-controls e! app])
      (when (= "sea-routes" selected-tab)
        [sea-routes/sea-routes-page-controls e! app])]]
     [:div.container {:style {:margin-top "20px"}}
      (case selected-tab
        "users" [users/user-listing e! app]
        "services" [service-list/service-listing e! app]
        "businessid" [business-id-report e! app]
        "operators" [operator-list e! app]
        "interfaces" [interfaces/interface-list e! app]
        "reports" [report-view/reports  e! app]
        "sea-routes" [sea-routes/sea-routes e! app]
        ;;default
        [users/user-listing e! app])]]))
