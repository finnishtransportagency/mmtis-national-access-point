(ns ote.views.transport-operator-ytj
  "Form to edit transport operator information." ; TODO: this ytj replaces old solution when ready
  (:require [reagent.core :as r]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]

            [ote.ui.form :as form]
            [ote.ui.form-groups :as form-groups]
            [ote.ui.buttons :as buttons]
            [ote.ui.validation :as ui-validation]
            [stylefy.core :as stylefy]
            [ote.style.form :as style-form]
            [ote.ui.common :as ui-common]
            [ote.ui.form-fields :as form-fields]

            [ote.app.controller.transport-operator :as to]
            [ote.app.controller.front-page :as fp]

            [ote.db.transport-operator :as t-operator]
            [ote.db.common :as common]
            [ote.localization :refer [tr tr-key]]
            [ote.style.base :as style-base]))

(defn- delete-operator [e! operator]
  (when (:show-delete-dialog? operator)
    [ui/dialog
     {:id "delete-transport-operator-dialog"
      :open    true
      :title   (tr [:dialog :delete-transport-operator :title])
      :actions [(r/as-element
                  [ui/flat-button
                   {:label    (tr [:buttons :cancel])
                    :primary  true
                    :on-click #(e! (to/->ToggleTransportOperatorDeleteDialog))}])
                (r/as-element
                  [ui/raised-button
                   {:id "confirm-operator-delete"
                    :label     (tr [:buttons :delete])
                    :icon      (ic/action-delete-forever)
                    :secondary true
                    :primary   true
                    :on-click  #(e! (to/->DeleteTransportOperator (::t-operator/id operator)))}])]}
     (tr [:dialog :delete-transport-operator :confirm] {:name (::t-operator/name operator)})]))

(defn- operator-selection-group [e! state]
  "Form group for querying business id from user and triggering data fetch for it from YTJ"
  (let [operator (:transport-operator state)
        status (get-in state [:ytj-response :status])
        ytj-loading? (fn [state] (:ytj-response-loading state))]
    (form/group
      {
       ;:label
       :columns        1
       ;:tooltip
       ;:tooltip-length "large"
       :card?          false
       :container-style style-form/full-width               ;Full-width to have groups stack vertically
       }

      {:name      ::t-operator/business-id
       :type      :string
       :validate  [[:business-id]]
       :required? true
       ;:disabled? true
       :warning   (tr [:common-texts :required-field])
       :should-update-check form/always-update
       }

      {:name      ::t-operator/btn-submit-business-id
       :type      :external-button
       :label     (tr [:common-texts :fetch-from-ytj])
       :primary   true
       :secondary true
       :on-click  #(e! (to/->FetchYtjOperator (::t-operator/business-id operator)))
       :disabled  (ytj-loading? state)
       }

      (when (:ytj-response state); label composition for error message
        (cond
          (= 200 status)
          (do )
          (= 404 status)
          {:name  :ytj-msg-results-not-found
           :type  :text-label
           :label (tr [:common-texts :data-not-found])}
          :else
          {:name  :ytj-msq-query-error
           :type  :text-label
           :label (tr [:common-texts :server-error-try-later])})
        )

      ; label composition for user instructions how to continue
      (when (and (:ytj-response state) (not= 200 status))
        {:name  :ytj-query-tip-whatnext
         :type  :text-label
         :label (str (tr [:common-texts :check-your-input]) " " (tr [:common-texts :optionally-fill-manually]))})
      )))

(defn- operator-form-groups [e! state]
  "Creates a napote form and resolves data to fields. Assumes expired fields are already filtered from ytj-response."
  (let [response-ok? (= 200 (get-in state [:ytj-response :status]))
        ytj-company-names (:ytj-company-names state)
        ytj-company-names-found? (< 1 (count ytj-company-names))]
    (form/group
      {:label (tr [:common-texts :title-operator-basic-details])
       :columns 1
       :tooltip (tr [:organization-page :basic-info-tooltip])
       :tooltip-length "large"
       :card? false
       }

      {:name       :msg-business-id
       :label      (if ytj-company-names-found?
                     (tr [:common-texts :business-id-and-aux-names])
                     "Toiminimi")
       :type       :text-label
       :h-style    :h3
       :max-length 70}

      (if response-ok?                                      ; Input field if not YTJ results, checkbox-group otherwise
        {:name                :company-names-selected
         ;:label
         ;:help
         :type                :checkbox-group
         :show-option         :name
         :option-enabled?     #(not (:name-nap-match %))
         :options             ytj-company-names
         :full-width?         true
         :should-update-check form/always-update
         }
        {:name       ::t-operator/name
         :label      ""
         :type       :string
         :required?  true
         :max-length 70})

      (when (and response-ok? (not ytj-company-names-found?))
        {:name :msg-no-aux-names-for-business-id
         :label (tr [:common-texts :no-aux-names-for-business-id])
         :type :text-label
         :max-length 128})

      {:name       :msg-business-id-contact-details
       :label      (if ytj-company-names-found?
                     (tr [:common-texts :contact-details-plural])
                     (tr [:common-texts :contact-details])
                     )       :type       :text-label
       :max-length 128
       :h-style    :h3}

      {:name ::ote.db.transport-operator/billing-address
       :type :text-label
       :max-length 128
       :h-style :h4}

      {:name ::common/billing-street
       :label (tr [:field-labels :ote.db.common/street])
       :type   :string
       :disabled? response-ok?
       :max-length 128
       :read (comp ::common/street ::t-operator/billing-address)
       :write (fn [data street]
                (assoc-in data [::t-operator/billing-address ::common/street] street))}

      {:name ::common/billing-postal_code
       :label (tr [:field-labels :ote.db.common/postal_code])
       :type :string
       :disabled? response-ok?
       :regex #"\d{0,5}"
       :read (comp ::common/postal_code ::t-operator/billing-address)
       :write (fn [data postal-code]
                (assoc-in data [::t-operator/billing-address ::common/postal_code] postal-code))}

      {:name ::common/billing-post_office
       :label (tr [:field-labels :ote.db.common/post_office])
       :type :string
       :disabled? response-ok?
       :max-length 64
       :read (comp :ote.db.common/post_office :ote.db.transport-operator/billing-address)
       :write (fn [data post-office]
                (assoc-in data [:ote.db.transport-operator/billing-address :ote.db.common/post_office] post-office))}

      {:name ::ote.db.transport-operator/visiting-address
       :type :text-label
       :max-length 128
       :h-style :h4}

      {:name ::common/street
       :type :string
       :disabled? response-ok?
       :max-length 128
       :read (comp ::common/street ::t-operator/visiting-address)
       :write (fn [data street]
                (assoc-in data [::t-operator/visiting-address ::common/street] street))}

      {:name ::common/postal_code
       :type :string
       :disabled? response-ok?
       :regex #"\d{0,5}"
       :read (comp ::common/postal_code ::t-operator/visiting-address)
       :write (fn [data postal-code]
                (assoc-in data [::t-operator/visiting-address ::common/postal_code] postal-code))}

      {:name :ote.db.common/post_office
       :type :string
       :disabled? response-ok?
       :max-length 64
       :read (comp :ote.db.common/post_office :ote.db.transport-operator/visiting-address)
       :write (fn [data post-office]
                (assoc-in data [:ote.db.transport-operator/visiting-address :ote.db.common/post_office] post-office))}

      {:name ::t-operator/phone :type :string :regex ui-validation/phone-number-regex}

      {:name ::t-operator/gsm :type :string :regex ui-validation/phone-number-regex}

      {:name ::t-operator/email :type :string :max-length 200}

      {:name ::t-operator/homepage :type :string :max-length 200}
      )
    ))

(defn- allow-manual-creation? [state]
  (some? (:ytj-response state))
  )

(defn- operator-form-options [e! state show-actions?]
  {:name->label (tr-key [:field-labels])
   :update!     #(e! (to/->EditTransportOperatorState %))
   :footer-fn   (fn [data]
                  (if show-actions?
                    [:div
                     [buttons/save {:on-click #(e! (to/->SaveTransportOperator))
                                    :disabled (form/disable-save? data)}
                      (tr [:buttons :save])]
                     [buttons/delete {:on-click #(e! (to/->ToggleTransportOperatorDeleteDialog))
                                      :disabled (if (::t-operator/id data) false true)}
                      (tr [:buttons :delete-operator])]]
                    [:div]))})

(defn operator-ytj [e! {operator :transport-operator :as state}]
  (e! (to/->EditTransportOperator (get-in state [:params :id])))
  (fn [e! {operator :transport-operator :as state}]
    (let [ show-id-entry? (empty? (get-in state [:params :id]))
          show-details? (and (:transport-operator-loaded? state)
                             (not (empty? (:ytj-response state))))
          form-options (operator-form-options e! state show-details?)
          form-groups (cond-> []
                              show-id-entry? (conj (operator-selection-group e! state))
                              show-details? (conj (operator-form-groups e! state)))]
      [:div
       [:div
        [:div
         [:h1 (tr [:organization-page
                   (if (:new? operator)
                     :organization-new-title
                     :organization-form-title)])]]]

       [:div.row {:style {:white-space "pre-wrap"}}
        [:p (tr [:organization-page :help-desc-1])]
        [:p (tr [:organization-page :help-desc-2])]]

       [:div.row.organization-info (stylefy/use-style style-form/organization-padding)
        [form/form
         form-options
         form-groups
         (:transport-operator state)]]

       ])))
