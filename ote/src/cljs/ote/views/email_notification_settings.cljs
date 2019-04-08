(ns ote.views.email-notification-settings
  "Email notification settings"
  (:require [reagent.core :as reagent]
            [ote.mui-wrapper.reagent :as ui]
            [ote.ui.form :as form]
            [ote.db.user-notifications :as user-notifications]
            [ote.localization :refer [tr tr-key]]
            [reagent.core :as r]
            [ote.ui.buttons :as buttons]
            [ote.app.controller.email-notification-settings :as email-settings]
            [ote.ui.common :as common]))

(defn select-province [e! regions]
  [(form/group
     {:label   ""
      :columns 3
      :card?   false
      :layout  :row}

     {:label                ""
      :name                 ::user-notifications/finnish-regions
      :type                 :checkbox-group
      :show-option          (tr-key [:regions])
      :options              (map #(:id %) regions)
      :full-width?          true
      :container-class      "col-xs-12 col-sm-12 col-md-12"
      :use-label-width?     true
      :checkbox-group-style {:display "flex" :flex-wrap "wrap"}}
     )])

(defn- form-options [e!]
  {:name->label (tr-key [:field-labels :email-notification-settings])
   :update!     #(e! (email-settings/->UpdateSettings %))
   :footer-fn   (fn [_]
                  [:div
                   [buttons/save {:on-click #(e! (email-settings/->SaveEmailNotificationSettings))}
                    (tr [:buttons :save])]])})

(defn email-notification-settings-form [e! state]
  (let [form-data (get-in state [:email-settings :user-notifications])]
    [:div
     [ote.ui.list-header/header state (tr [:email-notification-settings-page :page-title])]
     [:p {:style {:padding-top "20px"}} (tr [:email-notification-settings-page :page-description])]
     [:h2 {:style {:padding-top "20px"}} (tr [:email-notification-settings-page :page-secondary-header])]
     [:p (tr [:email-notification-settings-page :regions-description-text])]
     [form/form
      (form-options e!)
      (select-province e! (get-in state [:email-settings :regions]))
      form-data]]))

(defn email-notification-settings [e! state]
  (if (not (get-in state [:email-settings :regions-loading]))
    [email-notification-settings-form e! state]
    [common/loading-spinner]))
