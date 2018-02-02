(ns ote.views.admin
  "Admin panel views. Note this has a limited set of users and is not
  currently localized, all UI text is in Finnish."
  (:require [cljs-react-material-ui.reagent :as ui]
            [ote.ui.form-fields :as form-fields]
            [ote.app.controller.admin :as admin-controller]
            [clojure.string :as str]))

(defn user-listing [e! app]
  (let [{:keys [loading? results filter]} (get-in app [:admin :user-listing])]
    [:div
     [form-fields/field {:type :string :label "Nimen tai sähköpostiosoitteen osa"
                         :update! #(e! (admin-controller/->UpdateUserFilter %))}
      filter]

     [ui/raised-button {:primary true
                        :disabled (str/blank? filter)
                        :on-click #(e! (admin-controller/->SearchUsers))}
      "Hae käyttäjiä"]

     (when loading?
       [:span "Ladataan käyttäjiä..."])

     (when results
       [:span
        [:div "Hakuehdoilla löytyi " (count results) " käyttäjää."]
        [ui/table {:selectable false}
         [ui/table-header {:adjust-for-checkbox false
                           :display-select-all false}
          [ui/table-row
           [ui/table-header-column "Käyttäjätunnus"]
           [ui/table-header-column "Nimi"]
           [ui/table-header-column "Sähköposti"]
           [ui/table-header-column "Organisaatiot"]]]
         [ui/table-body {:display-row-checkbox false}
          (doall
           (for [{:keys [username name email groups]} results]
             [ui/table-row {:selectable false}
              [ui/table-row-column username]
              [ui/table-row-column name]
              [ui/table-row-column email]
              [ui/table-row-column groups]]))]]])]))

(defn business-id-report [e! app]
  (let [{:keys [loading? results]} (get-in app [:admin :business-id-report])]
    [:div

     [ui/raised-button {:primary true
                        :disabled (str/blank? filter)
                        :on-click #(e! (admin-controller/->GetBusinessIdReport))}
      "Hae raportti"]

     (when loading?
       [:span "Ladataan raporttia..."])

     (when results
       [:span
        [:div "Hakuehdoilla löytyi " (count results) " yritystä."]
        [ui/table {:selectable false}
         [ui/table-header {:adjust-for-checkbox false
                           :display-select-all false}
          [ui/table-row
           [ui/table-header-column "Palveluntuottaja"]
           [ui/table-header-column "Palvelun nimi"]
           [ui/table-header-column "Y-Tunnus"]
           [ui/table-header-column "GSM"]]]
         [ui/table-body {:display-row-checkbox false}
          (doall
            (for [{:keys [operator-name service-name business-id gsm]} results]
              [ui/table-row {:selectable false}
               [ui/table-row-column operator-name]
               [ui/table-row-column service-name]
               [ui/table-row-column business-id]
               [ui/table-row-column gsm]]))]]])]))


(defn admin-panel [e! app]
  (let [selected-tab (or (get-in app [:params :admin-page]) "users")]
    [ui/tabs {:value selected-tab
              :on-change #(e! (admin-controller/->ChangeAdminTab %))}
     [ui/tab {:label "Käyttäjät" :value "users"}
      [user-listing e! app]]
     [ui/tab {:label "Välityspalvelut" :value "brokerage"}
      [business-id-report e! app]]]))
