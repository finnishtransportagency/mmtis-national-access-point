(ns ote.ui.form
  "Generic form component.
  Forms are automatically generated from schemas that describe the fields.
  Fields can be grouped with a label to give the form more structure."
  (:require [ote.ui.validation :as validation]
            [ote.ui.form-fields :as form-fields]
            [ote.ui.common :as common]
            [cljs-time.core :as t]
            [clojure.string :as str]
            [ote.mui-wrapper.reagent :as ui]
            [reagent.core :as r]
            [ote.localization :refer [tr tr-key]]
            [stylefy.core :as stylefy]
            [ote.style.form :as style-form]
            [ote.mui-wrapper.icons :as ic]))

(defonce keyword-counter (atom 0))

(defn info
  "Create a new info form element that doesn't have any interaction, just shows a help text."
  [text & [options]]
  (let [type (:type options)]
    {:name (keyword (str "info" (swap! keyword-counter inc)))
     :type :component
     :container-style style-form/full-width
     :component (fn [_]
                  [(if (= :generic type)
                     common/generic-help
                     common/help)
                   text])}))

(defn info-with-link
  "Create a new info form element that doesn't have any interaction, just shows a help text."
  [text link-url link-text]
  {:name (keyword (str "info-link" (swap! keyword-counter inc)))
   :type :component
   :container-style style-form/full-width
   :component (fn [_]
                [common/extended-help text link-text link-url nil])})

(defn divider
  "Create a new divider form element that doesn't have any interaction,
  just shows a horizontal divider"
  []
  {:name (keyword (str "divider" (swap! keyword-counter inc)))
   :type :component
   :container-style style-form/full-width
   :component (fn [_]
                [ui/divider])})

(defn subtitle
  "Create a subtitle inside a form group."
  ([text] (subtitle text style-form/subtitle))
  ([text container-style]
   (subtitle :h4 text container-style))
  ([element text container-style]
   {:name (keyword (str "subtitle" (swap! keyword-counter inc)))
    :type :component
    :container-style container-style
    :component (fn [_]
                 [element (stylefy/use-style style-form/subtitle-h {::stylefy/with-classes ["form-subtitle"]}) text])}))

(defrecord Group [label options schemas])

(defn group
  "Create a group of form fields. The first argument
  is the label of the group (or an options map containing a label).
  The rest of the arguments are field schemas for the fields in the group."
  [label-or-options & schemas]
  (if-let [options (and (map? label-or-options)
                       label-or-options)]
    (->Group (:label options)
             (merge {:layout :default}
                    options)
             (filter #(not (nil? %)) schemas))
    (->Group label-or-options
             {:layout :default}
             (filter #(not (nil? %)) schemas))))

(defn row
  "Creates an unlabeled group with all fields side-by-side in a row."
  [& schemas]
  (->Group nil {:row? true} schemas))

(defn group? [x]
  (instance? Group x))

(defn modified?
  "Check if any fields have been modified."
  [data]
  (not (empty? (::modified data))))

(defn missing-required-fields
  "Returns a (possibly empty) set of required fields that have no value in the input form data."
  [data]
  (::missing-required-fields data))

(defn required-fields-missing?
  "Returns true if any required field is missing a value."
  [data]
  (not (empty? (missing-required-fields data))))

(defn errors?
  "Returns true if there are any validation errors in the input form data."
  [data]
  (not (empty? (::errors data))))

(defn valid?
  "Check if input form data is valid. Returns true if there are no validation errors and
  all required fields have a value."
  [data]
  (and (not (errors? data))
       (not (required-fields-missing? data))))

(defn can-save-and-modified?
  "Check if form can be saved and that it has been modified."
  [data]
  (and (modified? data)
       (valid? data)))

(defn can-save?
  "Check if form can be saved."
  [data]
  (valid? data))

(defn disable-save?
  "Check if form save button should be disabled.
  Form save should be disabled if there are validation errors,
  some required fields are missing values or the form hasn't been
  modified at all."
  [data]
  (not (can-save-and-modified? data)))

(defn without-form-metadata
  "Returns form data map without form metadata keys"
  [data]
  (dissoc data
          ::modified
          ::errors
          ::warnings
          ::notices
          ::missing-required-fields
          ::first-modification
          ::latest-modification
          ::schema
          ::closed-groups
          :loading?
          :csv-count
          :map-controls
          :show-delete-dialog?
          :disabled?))

(defrecord ^:private Label [label])
(defn- label? [x]
  (instance? Label x))

(defn- unpack-groups
  "Unpack schemas from groups to a single flat vector without nil values.
  Group labels are interleaved with schemas as Label record instances."
  [schemas]
  (loop [acc []
         [s & schemas] (remove nil? schemas)]
    (if-not s
      acc
      (cond
        (label? s)
        (recur acc schemas)

        (group? s)
        (recur acc
               (concat (remove nil? (:schemas s)) schemas))

        :default
        (recur (conj acc s)
               schemas)))))


(defn validate [data schemas]
  (let [all-schemas (unpack-groups schemas)
        all-errors (validation/validate-row nil data all-schemas :validate)
        all-warnings (validation/validate-row nil data all-schemas :warn)
        all-notices (validation/validate-row nil data all-schemas :notice)
        missing-required-fields (into #{}
                                      (map :name)
                                      (validation/missing-required-fields data all-schemas))]
    (assoc data
      ::errors all-errors
      ::warnings all-warnings
      ::notices all-notices
      ::missing-required-fields missing-required-fields)))

(defn- modification-time [{first ::first-modification
                           latest ::latest-modification :as data}]
  (assoc data
    ::first-modification (or first (t/now))
    ::latest-modification (t/now)))

(defn prepare-for-save [schemas data]
  (reduce
   (fn [prepared-data {:keys [name read write prepare-for-save] :as s}]
     (let [value (prepare-for-save ((or read name) data))]
       (if write
         (write prepared-data value)
         (assoc prepared-data name value))))
   data
   (filter :prepare-for-save (unpack-groups schemas))))

(defn field-ui
  "UI for a single form field"
  [{:keys [columns name label type read fmt col-class required?
           component] :as s}
   data update-fn editable? update-form
   modified? show-errors? errors warnings notices]
  (let [show-errors? (if (contains? s :show-errors?)
                       ;; Field schema has specified show-errors? => use it
                       (:show-errors? s)

                       ;; Use value given from group-ui
                       show-errors?)]
    (if (= type :component)
      [:div.component (component {:update-form! #(update-form s)
                                  :data data})]
      (if editable?
        [:div (stylefy/use-style style-form/form-field)
         [form-fields/field (assoc s
                                   :form? true
                                   :update! update-fn
                                   :error (when (and show-errors? (not (empty? errors)))
                                            (str/join " " errors))

                                   ;; Pass raw error data (for composite fields like tables)
                                   :error-data (when show-errors? errors)
                                   :warning (when (and show-errors? required? (validation/empty-value? data))
                                              (tr [:common-texts :required-field])))
          data]]
        [:div.form-control-static
         (if fmt
           (fmt ((or read #(get % name)) data))
           (form-fields/show-value s data))]))))

;; Grid column classes for columns spanned
(def col-classes {1 ["col-xs-12" "col-md-4" "col-lg-4"]
                  2 ["col-xs-12" "col-md-6" "col-lg-6"]
                  3 ["col-xs-12" "col-md-12" "col-lg-12"]})

(defn group-ui
  "UI for a group of fields in the form"
  [style schemas data update-fn can-edit? current-focus set-focus!
   modified errors warnings notices update-form hide-error-until-modified?]
  [:div.form-group (stylefy/use-style style)
   (doall
    (for [{:keys [name editable? read write container-style container-class] :as s} schemas
          :let [editable? (and can-edit?
                               (or (nil? editable?)
                                   (editable? data)))
                show-errors? (or (not hide-error-until-modified?)
                                 (get modified name))]]
      ^{:key name}
      [:div.form-field {:class container-class :style (merge (if (#{:string :localized-text} (:type s))
                                                               {:margin-bottom "2rem"}
                                                               {})
                                                             container-style)}
       [field-ui (assoc s
                                        ;:col-class col-class
                        :focus (= name current-focus)
                        :on-focus #(set-focus! name))
        ((or read name) data)
        #(update-fn name (or write name) %)
        editable? update-form
        (get modified name)
        show-errors?
        (get errors name)
        (get warnings name)
        (get notices name)]]))])

(defn- with-automatic-labels
  "Add an automatically generated `:label` to fields with the given `:name->label` function.
  If a schema has a manually given label, it is not overwritten."
  [name->label schemas]
  (if-not name->label
    schemas
    (mapv (fn [{:keys [name label type] :as s}]
            (let [schema (assoc s :label (or label
                                             (name->label name)))]
              (if (= type :table)
                ;; Also translate all the table fields for tables
                (update schema :table-fields (partial with-automatic-labels name->label))
                schema)))
          schemas)))

(defn- toggle [set value]
  (let [set (or set #{})]
    (if (set value)
      (disj set value)
      (conj set value))))

(defn- form-group-should-update?
  "Create a function to check if form group should be rerendered.
  A group is rerendered if its open/close status changes or it is
  open and its data has changed. Also should-update-check function can be used to return data
  that is used to determine update change status.
  It takes form elements as vector and when one of those values is changed group will rerender."
  [{schemas :schemas opts :options :as group}]
  (let [read-fn (apply juxt
                       (map (fn [{:keys [name read should-update-check]}]
                              (or should-update-check read name))
                            schemas))]
    (fn [_ old-argv new-argv]
      (let [[_ {old-closed-groups :closed-groups old-data :data :as old-form-options} old-group]
            old-argv

            [_ {new-closed-groups :closed-groups new-data :data :as new-form-options} {:keys [label] :as new-groups}]
            new-argv]
        (let [old-closed (old-closed-groups label)
              new-closed (new-closed-groups label)
              old-group-data (read-fn old-data)
              new-group-data (read-fn new-data)]
          (or
           ;; closed/open status has changed, update
           (not= old-closed new-closed)

           ;; group is not closed and its data has changed
           (and (not new-closed)
                (not= old-group-data new-group-data))))))))

;; Utility to set as :should-update-check that always forces update
(let [c (atom 0)]
  (defn always-update [_]
    (swap! c inc)))

(def balloon-header-tooltip
  "A tooltip icon that shows balloon.css tooltip on hover."
  (let [wrapped (common/tooltip-wrapper ic/action-help {:style {:margin-left 8
                                                                :position "relative"
                                                                :top "-2"}})]
    (fn [opts]
      [wrapped {:style {:width          19
                        :height         19
                        :vertical-align "middle"
                        :color          "white"}}
       opts])))

(defn form-group-ui [form-options group]
  (r/create-class
    {:should-component-update
     (form-group-should-update? group)

     :reagent-render
     (fn [{:keys [name->label update-field-fn can-edit? focus update-form
                  data closed-groups hide-error-until-modified?] :as form-options}
          {:keys [label schemas options] :as group}]
       (let [{::keys [modified errors warnings notices]} data
             columns (or (:columns options) 1)
             tooltip (:tooltip options)
             tooltip-length (or (:tooltip-length options) "medium")
             classes (get col-classes columns)
             schemas (with-automatic-labels name->label schemas)
             layout (:layout options)
             style (case layout
                     :row style-form/form-group-row
                     :raw {} ; no styling

                     ;; Default
                     style-form/form-group-column)
             container-style (:container-style options)
             card-style (:card-style options)
             group-component [group-ui
                              style
                              schemas
                              data
                              update-field-fn
                              can-edit?
                              @focus
                              #(reset! focus %)
                              modified
                              errors warnings notices
                              update-form
                              hide-error-until-modified?]
             card? (get options :card? true)]
         [:div.form-group-container (merge (stylefy/use-style style-form/form-group-container
                                                              {::stylefy/with-classes classes})
                                           {:style container-style})
          (if-not card?
            group-component
            [:div (merge (stylefy/use-style style-form/form-card)
                         {:style card-style})
             (when label
               [:div (stylefy/use-style style-form/form-card-label) label
                (when tooltip
                  [balloon-header-tooltip {:text tooltip :len tooltip-length}])])
             [:div (stylefy/use-style style-form/form-card-body)
              group-component]])]))}))

(defn form
  "Generic form component that takes `options`, a vector of field `schemas` and the
  current state of the form `data`.

  Supported options:

  :update!      Function to call when the form data changes
  :footer-fn    Optional function to create a footer component that is shown under the form.
                Receives the current form state with validation added as parameter.
  :class        Optional extra CSS classes for the form
  :name->label  Optional function to automatically generate a `:label` for field schemas that
                is based on the `:name` of the field. This is useful to automatically take
                a translation as the label.

  :hide-error-until-modified? If set to true, fields don't show any error messages unless
                they have been edited by the user.
  "
  [_ _ data]
  (let [focus (atom nil)
        latest-data (atom data)]
    (r/create-class
     {:component-will-receive-props
      (fn [_ [_ _ _ new-data]]
        (reset! latest-data new-data))

      :reagent-render
      (fn [{:keys [update! class footer-fn can-edit? label name->label hide-error-until-modified?]
            :as options}
           schemas
           {modified ::modified
            :as data}]
        (let [{::keys [errors warnings notices] :as validated-data} (validate data schemas)
              can-edit? (if (some? can-edit?)
                          can-edit?
                          true)
              update-form (fn [new-data & [name]]
                            (assert update! (str ":update! missing, options:" (pr-str options)))
                            (let [modified (or (::modified new-data) #{})
                                  modified (if name
                                             (conj modified name)
                                             modified)]
                              (-> new-data
                                  modification-time
                                  (validate schemas)
                                  (assoc ::modified modified)
                                  update!)))
              update-field-fn (fn [name name-or-write value]
                                (let [data @latest-data
                                      new-data (if (keyword? name-or-write)
                                                 (assoc data name-or-write value)
                                                 (name-or-write data value))]
                                  (update-form new-data name)))
              closed-groups (::closed-groups data #{})]
          [:div.form {:class class}
           (when label
             [:h3.form-label label])
           [:div.form-groups.row
            (doall
             (map-indexed
              (fn [i form-group]
                ^{:key i}
                [form-group-ui
                 {:name->label name->label
                  :data validated-data
                  :update-field-fn update-field-fn
                  :can-edit? can-edit?
                  :focus focus
                  :update-form update-form
                  :closed-groups closed-groups
                  :hide-error-until-modified? hide-error-until-modified?}
                 form-group])

              schemas))]

           (when-let [footer (when footer-fn
                               (footer-fn (assoc validated-data
                                                 ::schema schemas)))]
             [:div.form-footer.row
              [:div.col-md-12 footer]])]))})))
