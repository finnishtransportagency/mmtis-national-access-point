(ns ote.views.own-services
  (:require [clojure.string :as s]
            [reagent.core :as reagent]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]
            [ote.ui.icons :as icons]
            [ote.ui.common :refer [linkify ckan-iframe-dialog]]
            [ote.ui.form :as form]
            [ote.ui.form-groups :as form-groups]
            [ote.ui.buttons :as buttons]
            [ote.app.controller.front-page :as fp]
            [ote.app.controller.login :as login]
            [ote.app.controller.transport-service :as ts]
            [ote.app.controller.transport-operator :as to]
            [ote.views.transport-service :as transport-service]
            [ote.db.common :as common]
            [ote.ui.page :as page]
            [ote.localization :refer [tr tr-key]]
            [ote.db.transport-service :as t-service]
            [ote.db.transport-operator :as t-operator]
            [ote.db.modification :as modification]
            [ote.time :as time]
            [stylefy.core :as stylefy]
            [ote.style.base :as style-base]
            [ote.style.buttons :as style-buttons]
            [ote.style.front-page :as style-front-page]
            [ote.theme.colors :as colors]
            [reagent.core :as r]
            [ote.ui.form-fields :as form-fields]
            [ote.ui.common :as ui-common]
            [ote.ui.info :as info]
            [ote.views.transport-operator-selection :as t-operator-sel]
            [ote.ui.list-header :as list-header]
            [clojure.string :as str]
            [ote.app.controller.front-page :as fp]))


(defn- delete-service-action [e! {::t-service/keys [id name]
                                  :keys [show-delete-modal?]
                                  :as service}]
  [:span
   [ui/icon-button {:href "#"
                    :on-click #(do
                                 (.preventDefault %)
                                 (e! (ts/->DeleteTransportService id)))}
    [ic/action-delete]]
   (when show-delete-modal?
     [ui/dialog
      {:open true
       :title (tr [:dialog :delete-transport-service :title])
       :actions [(r/as-element
                   [ui/flat-button
                    {:label (tr [:buttons :cancel])
                     :primary true
                     :on-click #(e! (ts/->CancelDeleteTransportService id))}])
                 (r/as-element
                   [ui/raised-button
                    {:label (tr [:buttons :delete])
                     :icon (ic/action-delete-forever)
                     :secondary true
                     :primary true
                     :on-click #(e! (ts/->ConfirmDeleteTransportService id))}])]}

      (tr [:dialog :delete-transport-service :confirm] {:name name})])])

(defn transport-services-table-rows [e! services transport-operator-id]
  [ui/table-body {:display-row-checkbox false}
   (doall
     (map-indexed
       (fn [i {::t-service/keys [id type published? name]
               ::modification/keys [created modified] :as row}]
         ^{:key i}
         [ui/table-row {:selectable false :display-border false}
          [ui/table-row-column
           [:a (merge {:href (str "/#/edit-service/" id)
                       :on-click #(do
                                    (.preventDefault %)
                                    (e! (fp/->ChangePage :edit-service {:id id})))}
                      (stylefy/use-sub-style style-base/front-page-service-table :link)) name]]
          [ui/table-row-column {:class "hidden-xs "} (tr [:field-labels :transport-service ::t-service/published?-values published?])]
          [ui/table-row-column {:class "hidden-xs hidden-sm "} (time/format-timestamp-for-ui modified)]
          [ui/table-row-column {:class "hidden-xs hidden-sm "} (time/format-timestamp-for-ui created)]
          [ui/table-row-column {:class "hidden-xs hidden-sm "}
           (if published?
             (let [url (str "/export/geojson/" transport-operator-id "/" id)]
               [linkify url
                (tr [:own-services-page :open-geojson])
                {:target "_blank"
                 :style {:text-decoration "none"
                         ::stylefy/mode {:hover {:text-decoration "underline"}}}}])
             [:span.draft
              (tr [:field-labels :transport-service ::t-service/published?-values false])])]
          [ui/table-row-column
           [ui/icon-button {:href "#" :on-click #(do
                                                   (.preventDefault %)
                                                   (e! (fp/->ChangePage :edit-service {:id id})))}
            [ic/content-create]]
           [delete-service-action e! row]]])
       services))])

(defn transport-services-listing [e! transport-operator-id services section-label]
  (when (> (count services) 0)
    [:div.row (stylefy/use-style style-base/section-margin)
     [:div {:class "col-xs-12 col-md-12"}
      [:h3 section-label]
      [ui/table (stylefy/use-style style-base/front-page-service-table)
       [ui/table-header {:adjust-for-checkbox false
                         :display-select-all false}
        [ui/table-row {:selectable false}
         [ui/table-header-column {:class "table-header"} (tr [:front-page :table-header-service-name])]
         [ui/table-header-column {:class "hidden-xs table-header "} (tr [:front-page :table-header-NAP-status])]
         [ui/table-header-column {:class "hidden-xs hidden-sm table-header "} (tr [:front-page :table-header-modified])]
         [ui/table-header-column {:class "hidden-xs hidden-sm table-header "} (tr [:front-page :table-header-created])]
         [ui/table-header-column {:class "hidden-xs hidden-sm table-header"} (tr [:front-page :table-header-service-url])]
         [ui/table-header-column {:class "table-header "} (tr [:front-page :table-header-actions])]]]

       (transport-services-table-rows e! services transport-operator-id)]]]))

(defn warn-about-test-server []
  (let [page-url (-> (.-location js/window))]
    (when (s/includes? (str page-url) "testi")              ;; if url contains "testi" show message -> testi.finap.fi
      [:div {:style {:border "red 4px dashed"}}
       [:p {:style {:padding "10px"}} "TÄMÄ ON TESTIPALVELU!"
        [:br]
        "Julkinen NAP-palvelukatalogi löytyy osoitteesta: " [:a {:href "https://finap.fi/#/services"} "finap.fi"]
        ;;TODO: Trafi
        ;[:br]
        ;"Lisätietoa NAP-palvelukatalogin taustoista saat osoitteesta " [:a {:href "https://www.liikennevirasto.fi/nap"} "www.liikennevirasto.fi/nap" ]
        ]])))

(defn own-services-header
  [e! has-services? operator-services state]
  (let [operator (:transport-operator state)
        operators (:transport-operators-with-services state)]
    (when (and (not (empty? operators))
               (not (:new? operator)))
      [:div.row {:style {:margin-bottom "3rem"
                         :margin-top "3rem"
                         :align-items "center"
                         :display "flex"
                         :flex-wrap "wrap"}}
       [:div {:class "col-sm-6"}
        [:h4 {:style {:margin "0"}}
         (tr [:field-labels :select-transport-operator])]
        [form-fields/field
         {:name :select-transport-operator
          :type :selection
          :show-option #(::t-operator/name %)
          :update! #(e! (to/->SelectOperator %))
          :options (mapv :transport-operator operators)
          :auto-width? true
          :class-name "mui-select-button"}
         operator]]
       [:div.col-sm-6.col-md-6
        [:a (merge {:href "#/transport-operator"
                    :on-click #(do
                                 (.preventDefault %)
                                 (e! (to/->CreateTransportOperator)))}
                   (stylefy/use-style style-buttons/outline-button))
         (tr [:buttons :add-new-transport-operator])]]])))

(defn table-container-for-own-services [e! has-services? operator-services state]
  [:div {:class "col-xs-12 col-md-12"}
   [:h3 {:style {:margin-bottom "2rem"}} (tr [:own-services-page :own-services])]
   [info/info-toggle (tr [:own-services-page :own-services-directions-short]) [:p (tr [:own-services-page :own-services-info-long])]]
   [:a (merge {:href (str "#/new-service/" (::t-operator/id (:transport-operator state)))
               :id "new-service-button"
               :on-click #(do
                            (.preventDefault %)
                            (e! (ts/->OpenTransportServiceTypePage)))}
              (stylefy/use-style style-buttons/primary-button))
    (tr [:buttons :add-transport-service])]
   (if (and has-services? (not (empty? operator-services)))
     ;; TRUE -> Table for transport services
     (doall
       (for [type t-service/transport-service-types
             :let [services (filter #(= (:ote.db.transport-service/type %) type) operator-services)]
             :when (not (empty? services))]
         ^{:key type}
         [transport-services-listing
          e! (get-in state [:transport-operator ::t-operator/id])
          services (tr [:titles type])])))
   [:hr {:style {:border-bottom "0"
                 :margin "2rem 0"}}]])


(defn service-provider-controls
  [e! has-services? operator-services {::t-operator/keys [id name ckan-group-id] :as operator} show-add-member-dialog?]
  [:div.col-xs-12
   [:div
    [:h2 {:style {:margin-bottom "2rem"}}
     name]
    [:div {:style {:margin-bottom "2rem"}}
     [:a (merge {:href (str "#/transport-operator/" id)
                 :style {:margin-right "2rem"}
                 :on-click #(do
                              (.preventDefault %)
                              (e! (fp/->ChangePage :transport-operator {:id id})))}
                (stylefy/use-style style-base/blue-link-with-icon))
      (ic/content-create {:style {:width 20
                                  :height 20
                                  :margin-right "0.5rem"
                                  :color colors/primary}})
      (tr [:buttons :edit])]
     [:button (merge {:on-click #(do
                                   (.preventDefault %)
                                   (e! (fp/->ToggleAddMemberDialog)))}
                     (stylefy/use-style style-base/blue-link-with-icon))
      (ic/social-person {:style {:width 20
                                 :height 20
                                 :margin-right "0.5rem"
                                 :color colors/primary}})
      (tr [:buttons :manage-access-rights])]]
    (when show-add-member-dialog?
      [ui-common/ckan-iframe-dialog name
       (str "/organization/member_new/" ckan-group-id)
       #(e! (fp/->ToggleAddMemberDialog))])]

   [:div
    (if (not (and has-services? (not (empty? operator-services))))
      [:p
       [:div {:style {:float "left"}}                       ;;this is done because translations with variables don't support markdown and we have to fix md and variables
        (tr [:own-services-page :own-services-new-provider1])
        [:strong name]]
       (tr [:own-services-page :own-services-new-provider2])])]
   [:hr {:style {:border-bottom "0"}}]])

(defn operator-info-container
  [e! has-services? operator-services state]
  [:div
   [page/page-controls "" (tr [:common-texts :own-api-list])
    [own-services-header e! has-services? operator-services state]]
   [:div.container
    [:div.row
     [service-provider-controls e! has-services? operator-services (:transport-operator state) (:show-add-member-dialog? state) ]
     [table-container-for-own-services e! has-services? operator-services state]]]])


(defn no-operator
  "If user haven't added service-operator, we will ask to do so."
  [e! state]
  [:div.container
   [:div.row
    [:div {:class "col-xs-12 col-sm-12 col-md-12"}

     [:h1 (tr [:front-page :header-no-operator])]
     [:h3 (tr [:front-page :desc-to-add-new-operator])]
     (warn-about-test-server)

     [:p (tr [:front-page :desc-to-add-new-operator-2])]
     [:p (tr [:front-page :desc-to-add-new-operator-3])]
     [ui/raised-button {:label (tr [:front-page :move-to-organizations-page])
                        :primary true
                        :on-click #(do
                                     (.preventDefault %)
                                     (e! (fp/->ChangePage :operators nil)))
                        :style {:margin "20px 0px 20px 0px"}}]
     [:p (tr [:front-page :desc-to-add-new-operator-4])]

     [:div.row {:style {:padding-top "60px"}}
      [:p (tr [:front-page :desc-to-add-new-operator-5])]
      [ui/raised-button {:label (tr [:buttons :add-new-transport-operator])
                         :primary true
                         :on-click #(do
                                      (.preventDefault %)
                                      (e! (to/->CreateTransportOperator)))
                         :style {:margin-top "20px"}}]]]]])

(defn own-services [e! state]
  (e! (fp/->EnsureTransportOperator))

  (fn [e! state]
    (if (and (:transport-operator-data-loaded? state)
             (not (contains? state :transport-operators-with-services)))
      [no-operator e! state]

      ;; Get services by default from first organization
      (let [page (:page state)
            has-services? (not (empty? (map #(get-in % [:transport-service-vector ::t-service/id]) state)))
            operator-services (some #(when (= (get-in state [:transport-operator ::t-operator/id]) (get-in % [:transport-operator ::t-operator/id]))
                                       %)
                                    (:transport-operators-with-services state))
            operator-services (if (empty? operator-services)
                                (:transport-service-vector (first (:transport-operators-with-services state)))
                                (:transport-service-vector state))]
        [:div
         (if has-services?
           [operator-info-container e! has-services? operator-services state]
           ;; Render service type selection page if no services added
           [transport-service/select-service-type e! state])]))))

