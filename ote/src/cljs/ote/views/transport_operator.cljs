(ns ote.views.transport-operator
  "Form to edit transport operator information."
  (:require [reagent.core :as r]
            [ote.mui-wrapper.reagent :as ui]
            [ote.mui-wrapper.icons :as ic]

            [ote.ui.form :as form]
            [ote.ui.form-groups :as form-groups]
            [ote.ui.buttons :as buttons]
            [ote.ui.validation :as ui-validation]
            [ote.ui.info :as info]
            [ote.ui.select_field :as sf]
            [ote.ui.warning_msg :as msg-warn]
            [ote.ui.success_msg :as msg-succ]
            [ote.ui.circular_progress :as prog]
            [stylefy.core :as stylefy]
            [ote.style.form :as style-form]
            [ote.style.form-fields :as style-fields]
            [ote.ui.common :as ui-common]
            [ote.ui.form-fields :as form-fields]

            [ote.app.controller.flags :as flags]
            [ote.app.controller.transport-operator :as to]
            [ote.app.controller.front-page :as fp]

            [ote.db.transport-operator :as t-operator]
            [ote.db.common :as common]
            [ote.localization :refer [tr tr-key]]
            [ote.style.base :as style-base]
            [ote.ui.common :as uicommon]
            [ote.style.dialog :as style-dialog])
  )

;; Returns boolean about if there are any orphan nap operators which need renaming to ytj-company-names
(defn- unmerged-ytj-nap-ops? [orphans]
  (some? (some #(if (:merge-handled? (:transport-operator %)) false true)
               orphans)))

(defn- delete-operator [e! operator service-vector]
  ;; When operator is passed from :transport-operators-to-save list they do not have business-id
  (let [toggle-dialog (if (nil? (::t-operator/business-id operator))
                        (to/->ToggleListTransportOperatorDeleteDialog operator)
                        (to/->ToggleSingleTransportOperatorDeleteDialog))
        operator-services (:transport-service-vector (some #(when (= (::t-operator/id operator) (get-in % [:transport-operator ::t-operator/id]))
                                                                %)
                                                           service-vector))]
  (when (:show-delete-dialog? operator)
    [:div
     [ui/dialog
      {:id (str "delete-transport-operator-dialog-" (::t-operator/id operator))
       :open true
       :actionsContainerStyle style-dialog/dialog-action-container
       :title (tr [:dialog :delete-transport-operator :title])
       :actions [(r/as-element
                   [buttons/delete
                    {:id "confirm-operator-delete"
                     :disabled (if (empty? operator-services)
                                 false
                                 true)
                     :on-click #(e! (to/->DeleteTransportOperator (::t-operator/id operator)))}
                    (tr [:buttons :delete])])
                 (r/as-element
                   [buttons/cancel
                    {:on-click #(e! toggle-dialog)}
                    (tr [:buttons :cancel])])]}
      [:div
       (if (empty? operator-services)
         (tr [:dialog :delete-transport-operator :confirm] {:name (::t-operator/name operator)})
         (tr [:organization-page :help-operator-how-delete]))]]])))

(defn- business-id-selection [e! state ytj-supported?]
  "Form group for querying business id from user and triggering data fetch for it from YTJ"
  (let [operator (:transport-operator state)
        status (get-in state [:ytj-response :status])
        ytj-loading? (fn [state] (:ytj-response-loading state)) ;; Declared as function because a form field needs a fn
        ytj-response? (:ytj-response state)
        ytj-success? (= 200 status)]
    (form/group
      (merge {:card? false}
             (when ytj-supported?                           ;; Sets input and button on same row
               {:layout :row}))


      {:element-id "heading-business-id-title"
       :name :heading-business-id-title
       :label (tr [:organization-page :business-id-heading])
       :type :text-label
       :full-width? true
       :h-style :h3}

      {:name ::t-operator/business-id
       :label ""
       :element-id "input-business-id"
       :type :string
       :validate [[:business-id]]
       :required? true
       :warning (tr [:common-texts :required-field])
       :should-update-check form/always-update
       :disabled? (ytj-loading? state)
       :style (if ytj-supported?
                (when (ytj-loading? state) style-base/disabled-control)
                style-fields/form-field)
       :on-change #(e! (to/->EnsureUniqueBusinessId %))}

      (when ytj-supported?
        ;; Disabled when business-id is taken or if business-id is not valid or if loading is ongoing
        {:element-id "btn-submit-business-id"
         :name ::t-operator/btn-submit-business-id
         :type :external-button
         :label (tr [:organization-page :fetch-from-ytj])
         :primary true
         :secondary true
         :on-click #(e! (to/->FetchYtjOperator (::t-operator/business-id operator)))
         :disabled (or
                     (empty? (::t-operator/business-id operator))
                     (not (nil? (get-in state [:transport-operator :ote.ui.form/errors ::t-operator/business-id])))
                     (:business-id-exists? operator)
                     (ytj-loading? state))})

      (when ytj-supported?
        {:name :loading-spinner-ytj
         :type :loading-spinner
         :display? (ytj-loading? state)})

      (when (and ytj-supported? ytj-response?)
        (if ytj-success?
          {:name :ytj-result-msg
           :type :result-msg-success
           :content (tr [:organization-page :fetch-from-ytj-success])}
          {:name :ytj-result-msg
           :type :result-msg-warning
           :content (if (= 404 status)
                      (str (tr [:common-texts :data-not-found])
                           " " (tr [:common-texts :optionally-fill-manually]))
                      (str (tr [:common-texts :server-error])
                           " " (tr [:common-texts :server-error-try-later])
                           " " (tr [:common-texts :optionally-fill-manually])))}))

      ; label composition for existing business-id
      (when (get-in state [:transport-operator :business-id-exists?])
        {:element-id "label-business-id-is-not-unique"
         :name :business-id-is-not-unique
         :type :result-msg-warning
         :content (tr [:common-texts :business-id-is-not-unique])}))))

(defn- operator-form-groups [e! {operator :transport-operator :as state} creating? ytj-supported?]
  "Creates a napote form and resolves data to fields. Assumes expired fields are already filtered from ytj-response."
  (let [ytj-response-ok? (= 200 (get-in state [:ytj-response :status]))
        disable-ytj-address-billing? (= (get-in state [:ytj-flags :use-ytj-addr-billing?]) true)
        disable-ytj-address-visiting? (= (get-in state [:ytj-flags :use-ytj-addr-visiting?]) true)
        ytj-company-names (:ytj-company-names state)
        ytj-company-names-found? (pos-int? (count ytj-company-names))]
    (form/group
      {:label (tr [:common-texts :title-operator-basic-details])
       :columns 1
       :tooltip (tr [:organization-page :basic-info-tooltip])
       :tooltip-length "large"
       :card? false}

      (when ytj-supported?
        {:name :heading1-divider
         :type :divider})
      ;; Because user not allowed to edit business id himself,
      ;; business-id field is input in business-id-selection when creating, a read-only label here when only modifying.
      (when-not creating?
        {:element-id "heading-business-id"
         :name :heading-business-id
         :label (str (tr [:organization-page :business-id-heading]) " " (::t-operator/business-id operator))
         :type :text-label
         :h-style :h2})

      {:name :heading2
       :label (tr [:organization-page (if ytj-company-names-found?
                                        :business-id-and-aux-names
                                        :business-or-aux-name)])
       :type :text-label
       :h-style :h3
       :full-width? true}

      (when ytj-response-ok?
        {:name          :help-checkbox-group
         :type          :info-toggle
         :label         (tr [:common-texts :instructions])
         :body          [:div (tr [:organization-page :help-operator-edit-selection])]
         :default-state true})

      (if ytj-response-ok?                                      ;; Input field if not YTJ results, checkbox-group otherwise
        {:name                :transport-operators-to-save
         :type                :checkbox-group-with-delete
         :show-option         ::t-operator/name
         :option-enabled?     #(nil? (::t-operator/id %))
         :options             ytj-company-names
         :should-update-check form/always-update
         :required?           true
         :on-delete (fn [data]
                      (do
                        (e! (to/->ToggleListTransportOperatorDeleteDialog data))
                        (delete-operator e! data (:transport-operators-with-services state))))}
        {:element-id "input-operator-name"
         :name       ::t-operator/name
         :label      ""
         :type       :string
         :required?  true
         :style      style-fields/form-field})

      (when (and ytj-response-ok? (not ytj-company-names-found?))
        {:name :msg-no-aux-names-for-business-id
         :label (tr [:organization-page :no-aux-names-for-business-id])
         :type :text-label
         :max-length 128})

      {:name       :msg-business-id-contact-details
       :label      (if ytj-company-names-found?
                     (tr [:organization-page :contact-details-plural])
                     (tr [:organization-page :contact-details]))
       :type       :text-label
       :max-length 128
       :h-style    :h3}

      (when ytj-response-ok?
        {:name          :help-operator-contact-details
         :type          :info-toggle
         :label         (tr [:common-texts :instructions])
         :body          [:div
                         (tr [:organization-page :help-operator-contact-entry])
                         [uicommon/extended-help-link
                          (tr [:organization-page :help-ytj-contact-change-link])
                          (tr [:organization-page :help-ytj-contact-change-link-desc])]]
         :default-state true})

      {:name ::ote.db.transport-operator/visiting-address
       :type :text-label
       :style style-fields/form-field
       :h-style :h4}

      {:element-id "input-operator-addrVisitStreet"
       :name ::common/street
       :type :string
       :disabled? disable-ytj-address-visiting?
       :style style-fields/form-field
       :read (comp ::common/street ::t-operator/visiting-address)
       :write (fn [data street]
                (assoc-in data [::t-operator/visiting-address ::common/street] street))}

      {:element-id "input-operator-addrVisitPostalCode"
       :name ::common/postal_code
       :type :string
       :disabled? disable-ytj-address-visiting?
       :style style-fields/form-field
       :regex #"\d{0,5}"
       :read (comp ::common/postal_code ::t-operator/visiting-address)
       :write (fn [data postal-code]
                (assoc-in data [::t-operator/visiting-address ::common/postal_code] postal-code))}

      {:element-id "input-operator-addrVisitCity"
       :name :ote.db.common/post_office
       :type :string
       :disabled? disable-ytj-address-visiting?
       :style style-fields/form-field
       :read (comp :ote.db.common/post_office :ote.db.transport-operator/visiting-address)
       :write (fn [data post-office]
                (assoc-in data [:ote.db.transport-operator/visiting-address :ote.db.common/post_office] post-office))}

      {:name :heading-address-postal
       :label (tr [:organization-page :address-postal])
       :type :text-label
       :h-style :h4}

      {:element-id "input-operator-addrBillingStreet"
       :name        ::common/billing-street
       :label       (tr [:organization-page :address-postal-street])
       :type        :string
       :disabled?   disable-ytj-address-billing?
       :style       style-fields/form-field
       :read        (comp ::common/street ::t-operator/billing-address)
       :write       (fn [data street]
                      (assoc-in data [::t-operator/billing-address ::common/street] street))}

      {:element-id "input-operator-addrBillingPostalCode"
       :name ::common/billing-postal_code
       :label (tr [:field-labels :ote.db.common/postal_code])
       :type :string
       :disabled? disable-ytj-address-billing?
       :style style-fields/form-field
       :regex #"\d{0,5}"
       :read (comp ::common/postal_code ::t-operator/billing-address)
       :write (fn [data postal-code]
                (assoc-in data [::t-operator/billing-address ::common/postal_code] postal-code))}

      {:element-id "input-operator-addrBillingCity"
       :name ::common/billing-post_office
       :label (tr [:field-labels :ote.db.common/post_office])
       :type :string
       :disabled? disable-ytj-address-billing?
       :style style-fields/form-field
       :read (comp :ote.db.common/post_office :ote.db.transport-operator/billing-address)
       :write (fn [data post-office]
                (assoc-in data [:ote.db.transport-operator/billing-address :ote.db.common/post_office] post-office))}

      {:name :heading-contact-details-other
       :label (tr [:organization-page :contact-details-other])
       :type :text-label
       :h-style :h4}

      {:element-id "input-operator-telephone"
       :name ::t-operator/phone
       :label (tr [:organization-page :field-phone-telephone] )
       :type :string
       :disabled? (get-in state [:ytj-flags :use-ytj-phone?] false)
       :style style-fields/form-field
       :regex ui-validation/phone-number-regex}

      {:element-id "input-operator-mobilePhone"
       :name ::t-operator/gsm
       :label (tr [:organization-page :field-phone-mobile] )
       :type :string :disabled? (get-in state [:ytj-flags :use-ytj-gsm?] false)
       :style style-fields/form-field
       :regex ui-validation/phone-number-regex}

      {:element-id "input-operator-email"
       :name ::t-operator/email
       :type :string
       :disabled? (get-in state [:ytj-flags :use-ytj-email?] false)
       :style style-fields/form-field}

      {:element-id "input-operator-web"
       :name ::t-operator/homepage
       :type :string
       :disabled? (get-in state [:ytj-flags :use-ytj-homepage?] false)
       :style style-fields/form-field})))

(defn- operator-merge-section [e! {nap-orphans :ytj-orphan-nap-operators :as operator} ytj-company-names]
  [:div {:style style-base/wizard-container}
   [:div [:h3 (tr [:organization-page :heading-operator-edit])]]
   [info/info-toggle (tr [:common-texts :instructions]) (tr [:organization-page :help-merge-company-names]) true]
   (doall
     (for [n nap-orphans
           :let [nap-op (:transport-operator n)
                 control-disabled? false]
           :when n]
       ^{:key (str "operator-merge-section-item-" (::t-operator/name nap-op))}
       [:div {:style (merge
                       (style-base/flex-container "row")
                       (style-base/align-items "center")
                       (style-base/justify-content "flex-start")
                       (when control-disabled? style-base/disabled-control))}
        [:div "\"" [:strong (::t-operator/name nap-op)] "\" " (tr [:organization-page :merge-operator-to-ytj])]
        [:div {:style style-base/item-list-row-margin}]
        [sf/select-field
         {:options
          (vec (concat
                 [{::t-operator/name "" :placeholder true}] ;; menuitem for "no selection" goes first
                 ytj-company-names))
          :show-option #(::t-operator/name %)
          :style style-base/item-list-row-margin
          :update! #(e! (to/->OperatorRename nap-op %))}]

        (when (= false (:save-success? nap-op))
          [msg-warn/warning-msg (str (tr [:common-texts :save-failure])
                                     " "
                                     (tr [:common-texts :server-error-try-later]))])

        (when (= true (:save-success? nap-op))
          [msg-succ/success-msg (tr [:common-texts :save-success])])]))

   [buttons/save {:on-click #(e! (to/->UserCloseMergeSection nil))
                  :disabled (unmerged-ytj-nap-ops? nap-orphans)
                  :style style-form/action-control-section-margin}
    (tr [:buttons :next])]])

(defn- operator-form-options [e! state show-actions?]
  {:name->label (tr-key [:field-labels])
   :update! #(e! (to/->EditTransportOperatorState %))
   :footer-fn (fn [data]
                [:div
                 [:div
                  [:p
                   (tr [:common-texts :nap-data-license])
                   [ui-common/linkify (tr [:common-texts :nap-data-license-url]) (tr [:common-texts :nap-data-license-url-label]) {:target "_blank"}] "."]]
                 [:div {:style style-form/action-control-section-margin}
                  [:div
                   (when show-actions?
                     [buttons/save {:id "btn-operator-save"
                                    :on-click #(e! (to/->SaveTransportOperator))
                                    :disabled (or (get-in state [:transport-operator :business-id-exists?])
                                                  (form/disable-save? data))}
                      (tr [:buttons :save])])

                   [buttons/cancel {:on-click #(e! (to/->CancelTransportOperator))}
                    (tr [:buttons :cancel])]]

                  (when (and show-actions? (empty? (:ytj-company-names state)))
                    (when (not (get-in state [:transport-operator :new?]))
                      [:div
                       [:br]
                       [ui/divider]
                       [:br]
                       [:div [:h3 (tr [:dialog :delete-transport-operator :title-base-view])]]
                       [info/info-toggle (tr [:common-texts :instructions]) (tr [:organization-page :help-operator-how-delete]) true]
                       [buttons/save {:on-click #(e! (to/->ToggleSingleTransportOperatorDeleteDialog))
                                      :disabled (if (and
                                                      (empty? (:transport-service-vector state))
                                                      (::t-operator/id data))
                                                  false
                                                  true)}
                        (tr [:buttons :delete-operator])]]))]])})

(defn operator [e! {operator :transport-operator :as state}]
  (let [creating? (nil? (get-in state [:params :id]))
        ytj-supported? (flags/enabled? :open-ytj-integration)
        show-ytj-id-entry? (empty? (get-in state [:params :id]))
        show-details? (or (not ytj-supported?)
                          (and (:transport-operator-loaded? state)
                               (some? (:ytj-response state))))
        show-merge-companies? (and (pos-int? (count (get-in state [:transport-operator :ytj-orphan-nap-operators])))
                                   (pos-int? (count (:ytj-company-names state))))
        form-groups (cond-> []
                            show-ytj-id-entry? (conj (business-id-selection e! state ytj-supported?))
                            show-details? (conj (operator-form-groups e! state creating? ytj-supported?)))]
    [:div
     [:div
      [:div
       [:h1 (tr [:organization-page
                 (if (:new? operator)
                   :organization-new-title
                   :organization-form-title)])]]]
     [:div
      [info/info-toggle (tr [:common-texts :instructions] true)
       (if ytj-supported?
         [:div
          [:div (tr [:organization-page :help-ytj-integration-desc])]
          [:div (tr [:organization-page :help-desc-1])]
          [uicommon/extended-help-link (tr [:organization-page :help-about-ytj-link]) (tr [:organization-page :help-about-ytj-link-desc])]
          [uicommon/extended-help-link (tr [:organization-page :help-ytj-contact-change-link]) (tr [:organization-page :help-ytj-contact-change-link-desc])]]
         [:div
          [:div (tr [:organization-page :basic-info-tooltip])]])]]

     ;; When business-id has multiple companies create list of delete-operator dialogs. Otherwise add only one
     (if (empty? (:ytj-company-names state))
       [delete-operator e! operator (:transport-operators-with-services state)]
       (for [o (get-in state [:transport-operator :transport-operators-to-save])]
         ^{:key (str "operator-delete-control-" (::t-operator/name o) "-" (::t-operator/id o) )}
         [delete-operator e! o (:transport-operators-with-services state)]))

     (if show-merge-companies?
       (operator-merge-section e! operator (:ytj-company-names state))
       [form/form
        (operator-form-options e! state show-details?)
        form-groups
        operator])]))
