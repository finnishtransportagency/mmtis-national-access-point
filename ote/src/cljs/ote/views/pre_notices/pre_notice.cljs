(ns ote.views.pre-notices.pre-notice
  "Pre notice main form"
  (:require [reagent.core :as r]
            [cljs-react-material-ui.reagent :as ui]
            [ote.localization :refer [tr tr-key]]
            [ote.app.controller.pre-notices :as pre-notice]
            [ote.ui.buttons :as buttons]
            [ote.ui.form-fields :as form-fields]
    ;; db
            [ote.db.transport-operator :as t-operator]
            [ote.db.common :as db-common]
            [ote.db.transit :as transit]
            [ote.ui.form :as form]
            [ote.ui.common :as common]
            [cljs-react-material-ui.icons :as ic]
            [ote.style.form :as style-form]
            [stylefy.core :as stylefy]
            [ote.ui.leaflet :as leaflet]))

(def notice-types [:termination :new :schedule-change :route-change :other])

(defn select-operator [e! operator operators]
  [:div
   [:div.row
    [form-fields/field
     {:label       (tr [:field-labels :select-transport-operator])
      :name        :select-transport-operator
      :type        :selection
      :show-option ::t-operator/name
      :update!     #(e! (pre-notice/->SelectOperatorForNotice %))
      :options     operators
      :auto-width? true}
     operator]]
   [:div.row.col-xs-12.col-sm-12.col-md-8
    [:div.col-xs-8.col-sm-6.col-md-6
     [form-fields/field
      {:label     (tr [:field-labels ::t-operator/business-id])
       :name      ::t-operator/business-id
       :type      :string
       :update!   nil
       :disabled? true}
      (::t-operator/business-id operator)]

     [form-fields/field
      {:label     (tr [:field-labels ::db-common/street])
       :name      ::db-common/street
       :type      :string
       :update!   nil
       :disabled? true}
      (::db-common/street (::t-operator/visiting-address operator))]

     [form-fields/field
      {:label     (tr [:field-labels ::db-common/postal_code])
       :name      ::db-common/postal_code
       :type      :string
       :update!   nil
       :disabled? true}
      (::db-common/postal_code (::t-operator/visiting-address operator))]
     [form-fields/field
      {:label     (tr [:field-labels ::db-common/post_office])
       :name      ::db-common/post_office
       :type      :string
       :update!   nil
       :disabled? true}
      (::db-common/post_office (::t-operator/visiting-address operator))]]
    [:div.col-xs-8.col-sm-6.col-md-6
     [form-fields/field
      {:label     (tr [:field-labels ::t-operator/homepage])
       :name      ::t-operator/business-id
       :type      :string
       :update!   nil
       :disabled? true}
      (::t-operator/homepage operator)]
     [form-fields/field
      {:label     (tr [:field-labels ::t-operator/phone])
       :name      ::t-operator/phone
       :type      :string
       :update!   nil
       :disabled? true}
      (::t-operator/phone operator)]
     [form-fields/field
      {:label     (tr [:field-labels ::t-operator/gsm])
       :name      ::t-operator/gsm
       :type      :string
       :update!   nil
       :disabled? true}
      (::t-operator/gsm operator)]
     [form-fields/field
      {:label     (tr [:field-labels ::t-operator/email])
       :name      ::t-operator/email
       :type      :string
       :update!   nil
       :disabled? true}
      (::t-operator/email operator)]]]])

(defn transport-type [e! app]
  (fn [e! {pre-notice :pre-notice :as app}]
    [:div {:style {:padding-top "20px"}}
    [form/form {:update! #(e! (pre-notice/->EditForm %))}
     [(form/group
        {:label  (tr [:pre-notice-page :notice-type-title])
         :columns 3
         :layout  :row}

        {:name            ::transit/pre-notice-type
         :type            :checkbox-group
         :container-class "col-md-12"
         :header?         false
         :required?       true
         :options         notice-types
         :show-option     (tr-key [:enums :ote.db.notice/notice-type])})]
     pre-notice]]))

(defn effective-dates [e! app]
  (fn [e! {pre-notice :pre-notice :as app}]
    (let [effective-dates (:ote.db.notice/effective-dates pre-notice)]
      [form/form {:update! #(e! (pre-notice/->EditForm %))}
       [(form/group
          {:label   (tr [:pre-notice-page :effective-dates-title])
           :columns 3
           :layout  :row}

          {:name         ::transit/effective-dates
           :type         :table
           :table-fields [{:name  ::transit/effective-date
                           :type  :date-picker
                           ;:required? true
                           :label (tr [:pre-notice-page :effective-date-from])}

                          {:name  ::transit/effective-date-description
                           :type  :string
                           :label (tr [:pre-notice-page :effective-date-description])
                           ;:required? true
                           }]
           :delete?      true
           :add-label    (tr [:buttons :add-new-effective-date])}
          )]
       pre-notice])))

(defn notice-area [e! app]
  (fn [e! {pre-notice :pre-notice :as app}]
    [:div {:style {:padding-top "0px"}}
     [:div (stylefy/use-style style-form/form-card)
      [:div (stylefy/use-style style-form/form-card-label) (tr [:pre-notice-page :route-and-area-information-title])]
      [:div (merge (stylefy/use-style style-form/form-card-body))
       [:div.row
        [:div.col-md-6
         [form-fields/field
          {:id "route-description"
           :label "Muuttuvan reitin tai reittien nimet / kuvaukset"
           :type :string
           :hint-text "esim. Tampere - Pori tai Oulu - Seinäjoki"
           :full-width? true
           }]
         [form-fields/field
          {:id "regions"
           :label "Lisää maakunta tai maakunnat, joita muutos koskee "
           :type :multiselect-selection
           :show-option :name
           :options (:regions pre-notice)
           :update! #(e! (pre-notice/->GetRegionLocation "01"))
           ;; chip-näkymä
           ;; lista maakunnista
           :hint-text "Hae maakunta nimellä"
           :full-width? true
           }]]
        [:div.col-md-6
         [leaflet/Map {:ref         "notice-area-map"
                       :center      #js [65 25]
                       :zoomControl true
                       :zoom        5}
          (leaflet/background-tile-map)
          (when-let [regions (:locations pre-notice)]
            [leaflet/GeoJSON {:data  regions
                              :style {:color "green"}
                                        ;:pointToLayer (partial stop-marker e!)
                              }])]]]]]]))

(defn notice-attatchments [e! app]
  (fn [e! {pre-notice :pre-notice :as app}]
    [:div {:style {:padding-top "20px"}}
     [form/form {:name->label (tr-key [:field-labels :pre-notice])
                 :update! #(e! (pre-notice/->EditForm %))}
      [(form/group
         {:label   (tr [:pre-notice-page :effective-dates-title])
          :columns 3
          :layout  :row}
         (form/info (tr [:form-help :pre-notice-attatchment-info]) {:type :generic})

         {:name ::transit/url
          :type :string
          }
         )]
      pre-notice]]))



(defn new-pre-notice [e! app]
  (let [operator (:transport-operator app)
        operators (mapv :transport-operator (:transport-operators-with-services app))]
    [:span
     [:h1 (tr [:pre-notice-page :pre-notice-form-title])]
     ;; Select operator
     [select-operator e! operator operators]
     [transport-type e! app]
     [effective-dates e! app]
     [notice-area e! app]
     [notice-attatchments e! app]
     (when (not (pre-notice/valid-notice? (:route app)))
       [ui/card {:style {:margin "1em 0em 1em 0em"}}
        [ui/card-text {:style {:color "#be0000" :padding-bottom "0.6em"}} (tr [:pre-notice-page :publish-missing-required])]])
     [:div.col-xs-12.col-sm-6.col-md-6 {:style {:padding-top "20px"}}
      [buttons/save {:disabled (not (pre-notice/valid-notice? (:pre-notice app)))
                     :on-click #(do
                                  (.preventDefault %)
                                  (e! (pre-notice/->SaveToDb true)))}
       (tr [:buttons :save-and-send])]
      [buttons/save {:on-click #(do
                                  (.preventDefault %)
                                  (e! (pre-notice/->SaveToDb false)))}
       (tr [:buttons :save-as-draft])]
      [buttons/cancel {:on-click #(do
                                    (.preventDefault %)
                                    (e! (pre-notice/->CancelNotice)))}
       (tr [:buttons :cancel])]]]))
