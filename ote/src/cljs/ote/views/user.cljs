(ns ote.views.user
  "User's own info view"
  (:require [reagent.core :as r]
            [ote.mui-wrapper.reagent :as ui]
            [ote.ui.icons :as icons]
            [stylefy.core :as stylefy]
            [ote.ui.form :as form]
            [ote.ui.form-fields :as form-fields]
            [ote.db.user :as user]
            [ote.localization :refer [tr tr-key]]
            [ote.ui.buttons :as buttons]
            [ote.app.controller.login :as lc]
            [clojure.string :as str]
            [ote.ui.list-header :as list-header]))

(defn require-current-password? [user form-data]
  (or (and (not (str/blank? (:username form-data)))
           (not= (:username user) (:username form-data)))
      (and (not (str/blank? (:email form-data)))
           (not= (:email user) (:email form-data)))
      (not (str/blank? (:password form-data)))))


(defn merge-user-data [user form-data]
  (merge (select-keys user #{:username :name :email})
         form-data))

(defn user
  "Edit own user info"
  [e! app]
  (r/create-class
   {:component-will-unmount #(e! (lc/->CancelUserEdit false))
    :reagent-render
    (fn
      [e! {:keys [form-data email-taken username-taken password-incorrect?] :as user}]

      [:div.user-edit.col-xs-12.col-sm-8.col-md-8.col-lg-6
       [list-header/header app (tr [:common-texts :user-menu-profile])]
       [form/form
        {:update! #(e! (lc/->UpdateUser %))
         :name->label (tr-key [:register :fields])
         :footer-fn (fn [data]
                      [:div {:style {:margin-top "1em"}}
                       [buttons/save {:on-click #(e! (lc/->SaveUser
                                                      (merge-user-data
                                                       user
                                                       (form/without-form-metadata data))))
                                      :disabled (form/disable-save? data)}
                        (tr [:buttons :save])]
                       [buttons/cancel {:on-click #(e! (lc/->CancelUserEdit true))}
                        (tr [:buttons :cancel])]])}
        [(form/group
          {:expandable? false :columns 3 :layout :raw :card? false}

          {:name :username :type :string :required? true :full-width? true
           :placeholder (tr [:register :placeholder :username])
           :validate [(fn [data _]
                        (if (< (count data) 3)
                          (tr [:common-texts :required-field])
                          (when (not (user/username-valid? data))
                            (tr [:register :errors :username-invalid]))))
                      (fn [data _]
                        (when (and username-taken (username-taken data))
                          (tr [:register :errors :username-taken])))]
           :should-update-check form/always-update}

          {:name :name :type :string :required? true :full-width? true
           :placeholder (tr [:register :placeholder :name])
           :should-update-check form/always-update}
          {:name :email :type :string :autocomplete "email" :required? true
           :full-width? true :placeholder (tr [:register :placeholder :email])
           :validate [(fn [data _]
                        (when (not (user/email-valid? data))
                          (tr [:common-texts :required-field])))
                      (fn [data _]
                        (when (and email-taken (email-taken data))
                          (tr [:register :errors :email-taken])))]
           :should-update-check form/always-update}

          (form/subtitle :h3 (tr [:register :change-password]) {:margin-top "3rem"})
          {:name :password :type :string :password? true
           :label (tr [:register :fields :new-password])
           :full-width? true
           :validate [(fn [data _]
                        (when (and (not (str/blank? data))
                                   (not (user/password-valid? data)))
                          (tr [:register :errors :password-not-valid])))]
           :should-update-check form/always-update}
          {:name :confirm :type :string :password? true
           :full-width? true
           :validate [(fn [data row]
                        (when (not= data (:password row))
                          (tr [:register :errors :passwords-must-match])))]
           :should-update-check form/always-update}

          ;; Show current password field if it is required for update
          (form/subtitle :h3 (tr [:register :confirm-changes]) {:margin-top "3rem"})
          {:name :current-password :type :string :password? true
           :full-width? true
           :required? true
           :validate [(fn [_ _]
                        (when password-incorrect?
                          (tr [:login :error :incorrect-password])))]})]
        (merge-user-data user form-data)]])}))
