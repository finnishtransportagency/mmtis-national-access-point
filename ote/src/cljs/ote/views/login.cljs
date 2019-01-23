(ns ote.views.login
  "Login page view"
  (:require [reagent.core :as r]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]
            [stylefy.core :as stylefy]
            [ote.style.base :as style-base]
            [ote.app.controller.login :as lc]
            [ote.localization :refer [tr tr-key]]
            [ote.ui.form :as form]
            [ote.ui.form-fields :as form-fields]
            [ote.ui.buttons :as buttons]
            [ote.ui.common :refer [linkify] :as common-ui]
            [ote.db.user :as user]
            [clojure.string :as str]))


(defn login-form [e! {:keys [credentials failed? error in-progress?] :as login}]
  [:div.login-form
   (when failed?
     [:div (stylefy/use-style style-base/error-element)
      (tr [:login :error error])])
   [:h1 (tr [:login :label])]
   [form/form {:name->label (tr-key [:field-labels :login])
               :update! #(e! (lc/->UpdateLoginCredentials %))
               :footer-fn (fn [data]
                            [:span.login-dialog-footer
                             [buttons/save {:on-click #(e! (lc/->Login))} (tr [:login :login-button])]])}
    [(form/group
      {:expandable? false :columns 3 :layout :raw :card? false}
      {:name :email
       :label (tr [:field-labels :login :email-or-username])
       :type :string
       :autocomplete "email"
       :on-enter #(e! (lc/->Login))
       :full-width? true}
      {:name :password
       :autocomplete "password"
       :type :string
       :password? true
       :on-enter #(e! (lc/->Login))
       :full-width? true})]
    credentials]])

(defn login-action-cards [e!]
  [:div {:style {:margin "2rem 0 2rem 0"
                 :padding "1rem 0.5rem 1.5rem 1rem"
                 :background-color "#f5f5f5"}}
   [:div
    [:h3 {:style {:margin-top "0.5rem"}} (tr [:login :no-account?])]
    [linkify "#/register" (tr [:login :no-account-button])]]

   [:div {:style {:margin-top "2rem"}}
    [:h3 (tr [:login :forgot-password?])]
    [linkify "#" (tr [:login :forgot-password-button])
     {:on-click #(do
                   (.preventDefault %)
                   (e! (lc/->GoToResetPassword)))}]]])

(defn login [e! {:keys [credentials failed? error in-progress?] :as login}]
  [:div.col-xs-12.col-md-6
   [login-form e! login]
   [login-action-cards e!]])

(defn reset-password-form [e! {:keys [new-password confirm] :as form-data}]
  [:div.reset-password
   [:h1 (tr [:reset-password :label])]
   [form/form {:name->label (tr-key [:register :fields])
               :update! #(e! (lc/->UpdateResetPasswordForm %))
               :footer-fn (fn [data]
                            [:span.login-dialog-footer
                             [ui/raised-button {:primary true
                                                :disabled (or (not= new-password confirm)
                                                              (not (user/password-valid? new-password)))
                                                :on-click #(e! (lc/->ResetPassword))
                                                :label (tr [:register :change-password])}]])}
    [(form/group
      {:expandable? false :columns 3 :layout :raw :card? false}
      {:name :new-password
       :autocomplete "password"
       :type :string
       :password? true
       :full-width? true}
      {:name :confirm
       :autocomplete "password"
       :type :string
       :password? true
       :full-width? true})]
    form-data]])

(defn reset-password-request-form [e! {:keys [email code-sent-for-email] :as form-data}]
  [:div.reset-password-request
   [:h1 (tr [:login :forgot-password?])]
   [form/form {:update! #(e! (lc/->UpdateResetPasswordForm %))
               :footer-fn (fn [data]
                            [:span.login-dialog-footer
                             [ui/raised-button {:primary true
                                                :disabled (or (str/blank? email)
                                                              (= email code-sent-for-email))
                                                :on-click #(e! (lc/->RequestPasswordReset))
                                                :label (tr [:register :change-password])}]])}
    [(form/group
      {:expandable? false :columns 3 :layout :raw :card? false}
      {:name :email
       :label (tr [:field-labels :login :email-or-username])
       :autocomplete "email"
       :type :string
       :full-width? true})]
    form-data]

   (when-not (str/blank? code-sent-for-email)
     [:div (stylefy/use-style {:margin-top "1rem"})
      [common-ui/help (tr [:login :check-email-for-link])]])])

(defn reset-password [e! {:keys [reset-password query] :as app}]
  (if (contains? query :key)
    [reset-password-form e! reset-password]
    [reset-password-request-form e! reset-password]))
