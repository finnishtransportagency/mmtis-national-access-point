(ns ote.views.admin.interfaces
  "Admin panel views. Note this has a limited set of users and is not
  currently localized, all UI text is in Finnish."
  (:require [ote.mui-wrapper.reagent :as ui]
            [ote.ui.form-fields :as form-fields]
            [ote.app.controller.admin :as admin-controller]
            [ote.db.transport-service :as t-service]
            [clojure.string :as str]
            [ote.localization :refer [tr tr-key]]
            [ote.ui.common :refer [linkify]]
            [ote.time :as time]
            [ote.mui-wrapper.icons :as ic]
            [reagent.core :as r]
            [ote.ui.common :as ui-common]
            [ote.ui.common :as common-ui]
            [stylefy.core :as stylefy]
            [ote.style.admin :as style-admin]
            [ote.app.controller.front-page :as fp]
            [ote.ui.form :as form]
            [ote.style.dialog :as style-dialog]))

(defn error-modal [e! interface]
  (let [title (if (:db-error interface)
                "Rajapinnan käsittelyssä tapahtunut virhe"
                "Rajapinnan lataamisessa tapahtunut virhe")]
  (when (:show-error-modal? interface)
    [ui/dialog
     {:open true
      :actionsContainerStyle style-dialog/dialog-action-container
      :modal false
      :auto-scroll-body-content true
      :on-request-close #(e! (admin-controller/->CloseInterfaceErrorModal (:interface-id interface)))
      :title title
      :actions [(r/as-element
                  [ui/flat-button
                   {:label (tr [:buttons :close])
                    :secondary true
                    :primary true
                    :on-click #(e! (admin-controller/->CloseInterfaceErrorModal (:interface-id interface)))}])]}
     [:div.col-md-8
      [:div.row
       [:div.col-md-6 (stylefy/use-style style-admin/modal-data-label) "Rajapinnan osoite: "]
       [:div.col-md-6 [linkify (:url interface) (:url interface) {:target "_blank"}]]]
      [:div.row
       [:div.col-md-6 (stylefy/use-style style-admin/modal-data-label) "Rajapinnan tyyppi: "]
       [:div.col-md-6 (str/join ", " (:format interface))]]
      [:div.row
       [:div.col-md-6 (stylefy/use-style style-admin/modal-data-label) "Virhe: "]
       [:div.col-md-6
        (when (:import-error interface)
          [:p (:import-error interface)])
        (when (:db-error interface)
          [:p (:db-error interface)])]]]])))

(defn operator-modal [e! interface]
  (when (:show-operator-modal? interface)
    [ui/dialog
     {:open                     true
      :actionsContainerStyle style-dialog/dialog-action-container
      :modal                    false
      :auto-scroll-body-content true
      :on-request-close         #(e! (admin-controller/->CloseOperatorModal (:interface-id interface)))
      :title                    "Palvelun ja palveluntuottajan tiedot"
      :actions                  [(r/as-element
                                   [ui/flat-button
                                    {:label     (tr [:buttons :close])
                                     :secondary true
                                     :primary   true
                                     :on-click  #(e! (admin-controller/->CloseOperatorModal (:interface-id interface)))}])]}
     [:div.col-md-6 {:style {:border-right "1px solid gray"}}
      [:div.row [:h2 "Rajapinnan tuottaa"]]
      [:div.row
       [:div.col-md-4 (stylefy/use-style style-admin/modal-data-label) "Nimi: "]
       [:div.col-md-6 [:a {:href     "#"
                           :on-click #(do
                                        (.preventDefault %)
                                        (e! (fp/->ChangePage :transport-operator {:id (:operator-id interface)})))}
                       (:operator-name interface)]]]
      [:div.row
       [:div.col-md-4 (stylefy/use-style style-admin/modal-data-label) "Puhelin: "]
       [:div.col-md-6 (:operator-phone interface)]]
      [:div.row
       [:div.col-md-4 (stylefy/use-style style-admin/modal-data-label) "GSM: "]
       [:div.col-md-6 (:operator-gsm interface)]]
      [:div.row
       [:div.col-md-4 (stylefy/use-style style-admin/modal-data-label) "Sähköposti: "]
       [:div.col-md-6 (:operator-email interface)]]]

     [:div.col-md-6 {:style {:padding-left "20px"}}
      [:div.row [:h2 "Rajapinta kuuluu palveluun:"]]
      [:div.row
       [:div.col-md-4 (stylefy/use-style style-admin/modal-data-label) "Nimi: "]
       [:div.col-md-6 [:a {:href     "#"
                           :on-click #(do
                                        (.preventDefault %)
                                        (e! (fp/->ChangePage :edit-service {:id (:service-id interface)})))}
                       (:service-name interface)]]]
      [:div.row
       [:div.col-md-4 (stylefy/use-style style-admin/modal-data-label) "Puhelin: "]
       [:div.col-md-6 (:service-phone interface)]]
      [:div.row
       [:div.col-md-4 (stylefy/use-style style-admin/modal-data-label) "Sähköposti: "]
       [:div.col-md-6 (:service-email interface)]]]]))



(defn- gtfs-viewer-link [url format]
  (when format
    (let [format (str/lower-case format)]
      (when (or (= "gtfs" format) (= "kalkati.net" format))
        (common-ui/linkify
          (str "#/routes/view-gtfs?url=" (.encodeURIComponent js/window url)
               (when (= "kalkati.net" format)
                 "&type=kalkati"))
          (tr [:service-search :view-routes])
          {:target "_blank"})))))

(def interface-formats [:GTFS :Kalkati.net :ALL])

(defn interface-list [e! app]
  (let [{:keys [loading? results filters]}
        (get-in app [:admin :interface-list])]
    [:div.row
     [:div.row.col-md-12 {:style {:padding-top "20px"}}
      [form/form {:update! #(e! (admin-controller/->UpdateInterfaceFilters %))}
       [(apply
         form/group
         {:label   "Etsi rajapintoja"
          :columns 3
          :layout  :row}

         (map #(merge {:type :string
                       :container-class "col-xs-12 col-sm-4 col-md-4"
                       :full-width? true} %)
              [{:name            :operator-name
                :label "Palveluntuottaja"
                :hint-text       "Palveluntuottajan nimi tai sen osa"}
               {:name            :service-name
                :label "Palvelu"
                :hint-text       "Palvelun nimi tai sen osa"}
               {:name            :interface-format
                :type            :selection
                :options         interface-formats
                :label           "Tyyppi"
                :hint-text       "Palvelun nimi tai sen osa"
                :show-option     (tr-key [:admin-page :interface-formats])
                :update!         #(e! (admin-controller/->UpdatePublishedFilter %))}
               {:name            :interface-url
                :label           "Rajapinnan osoite"}
               {:name            :import-error
                :type            :checkbox
                :label           "Latausvirheet"}
               {:name            :db-error
                :type            :checkbox
                :label           "Käsittelyvirhe"}]))]
       filters]

      [ui/raised-button {:primary  true
                         :disabled (str/blank? filter)
                         :on-click #(e! (admin-controller/->SearchInterfaces))
                         :label    "Hae rajapintoja"}]]

     [:div.row {:style {:padding-top "40px"}}
      (when loading?
        [:span "Ladataan rajapintoja..."])

      (when results
        [:div
         [:div "Hakuehdoilla löytyi " (count results) " rajapintaa. "
          [ui/raised-button {:secondary true
                             :on-click #(e! (admin-controller/->DownloadInterfacesCSV))
                             :label "Lataa CSV"}]]
         [ui/table {:selectable false}
          [ui/table-header {:adjust-for-checkbox false
                            :display-select-all  false}
           [ui/table-row
            [ui/table-header-column {:style {:width "15%"}} "Palveluntuottaja"]
            [ui/table-header-column {:style {:width "15%"}} "Sisältö"]
            [ui/table-header-column {:width "10%"} "Tyyppi"]
            [ui/table-header-column {:width "25%"} "Rajapinta"]
            [ui/table-header-column {:width "15%"} "Viimeisin käsittely"]
            [ui/table-header-column {:width "20%"} "Katso"]]]
          [ui/table-body {:display-row-checkbox false}
           (doall
             (for [{:keys [interface-id operator-name format data-content url imported import-error db-error] :as interface} results]
               ^{:key (str "link_" interface-id)}
               [ui/table-row {:selectable false}
                [ui/table-row-column {:style {:width "15%"}} [:a {:href     "#"
                                                                  :on-click #(do (.preventDefault %)
                                                                                 (e! (admin-controller/->OpenOperatorModal interface-id)))}
                                                              operator-name]]
                [ui/table-row-column {:style {:width "15%"}} (admin-controller/format-interface-content-values data-content)]
                [ui/table-row-column {:style {:width "10%"}} (first format)]
                [ui/table-row-column {:style {:width "25%"}} [linkify url url {:target "_blank"}]]
                [ui/table-row-column {:style {:width "15%"}} (time/format-timestamp-for-ui imported)]
                [ui/table-row-column {:style {:width "20%"}}
                 (when import-error
                   [:a {:style {:color "red"}
                        :href     "#"
                        :on-click #(do (.preventDefault %)
                                       (e! (admin-controller/->OpenInterfaceErrorModal interface-id)))}
                    " Latausvirhe "])
                 (when db-error
                   [:a {:style {:color "red"}
                        :href     "#"
                        :on-click #(do (.preventDefault %)
                                       (e! (admin-controller/->OpenInterfaceErrorModal interface-id)))}
                    " Käsittelyvirhe "])
                 (when (and (nil? import-error) (nil? db-error)) [gtfs-viewer-link url (first format)])]
                (error-modal e! interface)
                (operator-modal e! interface)]))]]])]]))
