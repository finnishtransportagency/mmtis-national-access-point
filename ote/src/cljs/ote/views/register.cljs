(ns ote.views.register
  "OTE registration form page."
  (:require [reagent.core :as r]
            [ote.ui.form :as form]
            [ote.app.controller.login :as lc]
            [ote.localization :refer [tr tr-key]]
            [ote.ui.buttons :as buttons]
            [ote.ui.common :as common]
            [ote.style.register :as register-style]
            [stylefy.core :as stylefy]
            [ote.db.user :as user]
            [ote.ui.notification :as notification]))

(defn invitation-token-check
  [token-info]
  (if (or
        (nil? token-info)
        (:loading? token-info)
        (= token-info :token-invalid))
    (notification/notification
      {:text (tr [:register :invalid-token])
       :type :warning})
    (notification/notification
      {:text (tr [:register :valid-token] {:operator-name (:name token-info)})
       :type :success})))

;; PENDING:
;; This form has a new ":show-errors?" flag in the schemas that only
;; shows errors when a particular field has been blurred (focus changed
;; away from it). Focus/blur tracking should be moved to a feature of the form
;; component if it is needed in other components, but it needs too many changes
;; to be worth it for this form alone.

(defn register [e! {:keys [token] :as params} {:keys [form-data email-taken username-taken token-info success?] :as register} user]
  (let [edited (r/atom #{})                                 ; keep track of blurred fields
        edit! #(swap! edited conj %)]
    (fn [e! {:keys [token] :as params} {:keys [form-data email-taken username-taken token-info success?] :as register} user]
      (let [email (:email form-data)]
        [:div
         [:div.col-xs-12.col-md-6
          [:h1 (tr [:register :label])]
          (when (and (some? token) (not success?))
            [invitation-token-check token-info])
          (when success? [:div (stylefy/use-style register-style/success-fade-in)
                          [notification/notification {:type :success
                                                      :text (tr [:register :verification-email-sent-text] {:email email})}]])
          [:div (when success? (stylefy/use-style register-style/form-fadeout))
           [form/form
            {:update! #(e! (lc/->UpdateRegistrationForm %))
             :name->label (tr-key [:register :fields])
             :footer-fn (fn [data]
                          [:span
                           (when (some? user)
                             [common/help (tr [:register :errors :logged-in] user)])
                           [buttons/save {:on-click #(e! (lc/->Register (form/without-form-metadata data)))
                                          :disabled (or (some? user)
                                                      (form/disable-save? data))}
                            (tr [:register :label])]])
             :hide-error-until-modified? true}
            [(form/group
               {:expandable? false :columns 3 :card? false :layout :raw}
               {:element-id "register-username"
                :name :username
                :type :string
                :required? true
                :full-width? true
                :placeholder (tr [:register :placeholder :username])
                :validate [(fn [data _]
                             (if (< (count data) 3)
                               (tr [:common-texts :required-field])
                               (when (not (user/username-valid? data))
                                 (tr [:register :errors :username-invalid]))))
                           (fn [data _]
                             (when (and username-taken (username-taken data))
                               (tr [:register :errors :username-taken])))]
                :on-blur #(edit! :username)
                :show-errors? (or (and username-taken
                                    (username-taken (:username form-data)))
                                (@edited :username))
                :should-update-check form/always-update}
               {:type :component
                :name :spacer
                :component (fn [_]
                             [:div {:style {:margin-top "20px"}}])}
               {:element-id "register-name"
                :name :name
                :type :string
                :required? true
                :full-width? true
                :placeholder (tr [:register :placeholder :name])
                :on-blur #(edit! :name)
                :show-errors? (@edited :name)
                :should-update-check form/always-update}
               {:element-id "register-email"
                :name :email
                :type :string
                :autocomplete "email"
                :required? true
                :full-width? true :placeholder (tr [:register :placeholder :email])
                :validate [(fn [data _]
                             (when (not (user/email-valid? data))
                               (tr [:common-texts :required-field])))
                           (fn [data _]
                             (when (and email-taken (email-taken data))
                               (tr [:register :errors :email-taken])))]
                :on-blur #(edit! :email)
                :show-errors? (or (and email-taken
                                    (email-taken (:email form-data)))
                                (@edited :email))
                :should-update-check form/always-update}
               {:element-id "register-password"
                :name :password
                :type :string
                :password? true
                :required? true
                :full-width? true
                :validate [(fn [data _]
                             (when (not (user/password-valid? data))
                               (tr [:register :errors :password-not-valid])))]
                :on-blur #(edit! :password)
                :show-errors? (@edited :password)
                :should-update-check form/always-update}
               {:element-id "register-confirm"
                :name :confirm
                :type :string
                :password? true
                :required? true
                :full-width? true
                :validate [(fn [data row]
                             (when (not= data (:password row))
                               (tr [:register :errors :passwords-must-match])))]
                :on-blur #(edit! :confirm)
                :show-errors? (@edited :confirm)
                :should-update-check form/always-update})]
            form-data]]]]))))
