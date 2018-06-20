(ns ote.ui.form-fields
  "UI components for different form fields."
  (:require [reagent.core :as r]
            [cljs-react-material-ui.reagent :as ui]
            [ote.ui.mui-chip-input :refer [chip-input]]
            [clojure.string :as str]
            [ote.localization :as localization :refer [tr tr-key]]
            [cljs-react-material-ui.icons :as ic]
            [stylefy.core :as stylefy]
            [ote.style.form-fields :as style-form-fields]
            [ote.style.base :as style-base]
            [ote.ui.validation :as valid]
            [ote.time :as time]
            [ote.ui.buttons :as buttons]
            [ote.ui.common :as common]
            [ote.style.form :as style-form]
            [ote.db.transport-service :as t-service]
            [ote.util.values :as values]
            [goog.string :as gstr]))



(def text-field
  "Temporary Material-ui reagent TextField fix. Requires Reagent v0.8.0-alpha2.
  Fixes caret positioning when editing TextField input.
  See: https://github.com/madvas/cljs-react-material-ui/issues/17"

  (r/adapt-react-class
    (aget js/MaterialUI "TextField")
    {:synthetic-input
     ;; A valid map value for `synthetic-input` does two things:
     ;; 1) It implicitly marks this component class as an input type so that interactive
     ;;    updates will work without cursor jumping.
     ;; 2) Reagent defers to its functions when it goes to set a value for the input component,
     ;;    or signal a change, providing enough data for us to decide which DOM node is our input
     ;;    node to target and continue processing with that (or any arbitrary behaviour...); and
     ;;    to handle onChange events arbitrarily.
     ;;
     ;;    Note: We can also use an extra hook `on-write` to execute more custom behaviour
     ;;    when Reagent actually writes a new value to the input node, from within `on-update`.
     ;;
     ;;    Note: Both functions receive a `next` argument which represents the next fn to
     ;;    execute in Reagent's processing chain.
     {:on-update (fn [next root-node rendered-value dom-value component]
                   (let [input-node (.querySelector root-node "input")
                         textarea-nodes (array-seq (.querySelectorAll root-node "textarea"))
                         textarea-node (when (= 2 (count textarea-nodes))
                                         ;; We are dealing with EnhancedTextarea (i.e.
                                         ;; multi-line TextField)
                                         ;; so our target node is the second <textarea>...
                                         (second textarea-nodes))
                         target-node (or input-node textarea-node)]
                     (when target-node
                       ;; Call Reagent's input node value setter fn (extracted from input-set-value)
                       ;; which handles updating of a given <input> element,
                       ;; now that we have targeted the correct <input> within our component...
                       (next target-node rendered-value dom-value component
                             ;; Also hook into the actual value-writing step,
                             ;; since `input-node-set-value doesn't necessarily update values
                             ;; (i.e. not dirty).
                             {:on-write
                              (fn [new-value]
                                ;; `blank?` is effectively the same conditional as Material-UI uses
                                ;; to update its `hasValue` and `isClean` properties, which are
                                ;; required for correct rendering of hint text etc.
                                (if (clojure.string/blank? new-value)
                                  (.setState component #js {:hasValue false :isClean false})
                                  (.setState component #js {:hasValue true :isClean false})))}))))
      :on-change (fn [next event]
                   ;; All we do here is continue processing but with the event target value
                   ;; extracted into a second argument, to match Material-UI's existing API.
                   (next event (-> event .-target .-value)))}}))

(defn read-only-atom [value]
  (r/wrap value
          #(assert false (str "Can't write to a read-only atom: " (pr-str value)))))

(defmulti field
  "Create an editable form field UI component. Dispatches on `:type` keyword.
  A field must always have an `:update!` callback the component calls to update a new value."
  (fn [t _] (:type t)))

(defmulti show-value
  "Create a read-only display for a value. Dispatches on `:type` keyword.
  This is not meant to be a 'disabled' input field, but for showing a readable value.
  Default implementation just converts input value to string."
  (fn [t _] (:type t)))

(defmethod show-value :default [_ data]
  [:span (str data)])

(defmethod show-value :component [skeema data]
  (let [komponentti (:component skeema)]
    [komponentti data]))

(def tooltip-icon
  "A tooltip icon that shows balloon.css tooltip on hover."
  (let [wrapped (common/tooltip-wrapper ic/action-help {:style {:margin-left 8}})]
    (fn [opts]
      [wrapped {:style {:width          16 :height 16
                        :vertical-align "middle"
                        :color          "gray"}}
       opts])))

(defn placeholder [{:keys [placeholder placeholder-fn row] :as field} data]
  (or placeholder
      (and placeholder-fn (placeholder-fn row))
      ""))

(defmethod field :string [{:keys [update! label name max-length min-length regex
                                  focus on-blur form? error warning table? full-width?
                                  style input-style hint-style password? on-enter
                                  hint-text autocomplete disabled? id]
                           :as   field} data]
  [text-field
   (merge
    {:id id
     :name name
     :floating-label-text (when-not table? label)
     :floating-label-fixed true
     :on-blur           on-blur
     :hint-text         (or hint-text "")
     :on-change         #(let [v %2]
                           (if regex
                             (when (re-matches regex v)
                               (update! v))
                             (update! v)))
     :value             (or data "")
     :error-text        (or error warning "") ;; Show error text or warning text or empty string
     :error-style       (if error ;; Error is more critical than required - showing it first
                          style-base/error-element
                          style-base/required-element)
     :hint-style (merge style-base/placeholder
                        hint-style)}
    (when max-length
      {:max-length max-length})
    (when full-width?
      {:full-width true})
    (when disabled?
      {:disabled true})
    (when style
      {:style style})
    (when input-style
      {:input-style input-style})
    (when password?
      {:type "password"})
    (when autocomplete
      {:autoComplete autocomplete})
    (when on-enter
      {:on-key-press #(when (= "Enter" (.-key %))
                        (on-enter))}))])

(defmethod field :file-and-delete [{:keys [on-delete table-data row-number disabled? allowed-file-types] :as f} data]
  (let [row-data (get table-data row-number)]
    (if (or (empty? row-data) (:error row-data))
      [:div
      (field (assoc f :type :file
                      :error (:error row-data)))
       (when allowed-file-types
        [:span [:br] (str (tr [:form-help :allowed-file-types])  (str/join ", " allowed-file-types))])]
      [ui/icon-button (merge
                        {:on-click #(on-delete row-number)}
                        (when disabled?
                          {:disabled true}))
       [ic/action-delete]])))

(defmethod field :file [{:keys [label button-label name disabled? on-change
                                error warning]
                         :as field} data]
  [:div (stylefy/use-style style-form-fields/file-button-wrapper)
   [:button (merge
              (stylefy/use-sub-style style-form-fields/file-button-wrapper :button)
              (when disabled?
                {:disabled true}))
    (if-not (empty? label) label button-label)]
   [:input
    (merge (stylefy/use-sub-style
             style-form-fields/file-button-wrapper :file-input)
           {:id "hidden-file-input"
            :type "file"
            :name name
            :on-change on-change
            ;; Hack to hide file input tooltip on different browsers.
            ;; String with space -> hide title on Chrome, FireFox and some other browsers. Not 100% reliable.
            :title " "}
           (when disabled?
             {:disabled true}))]
   (when (or error warning)
     [:div (stylefy/use-style style-base/required-element)
      (if error error warning)])])

(defmethod field :text-area [{:keys [update! table? label name rows error tooltip tooltip-length
                                     max-length on-blur warning full-width?
                                     style input-style hint-style on-enter
                                     hint-text disabled? id]
                              :as field} data]
  [:span
   (when tooltip
     [:div {:style {:padding-top "10px"}}
      [:span (stylefy/use-style style-form-fields/compensatory-label) label]
      (r/as-element [tooltip-icon {:text tooltip :len (or tooltip-length "medium")}])])
   [:div
    [text-field
     (merge
       {:id id
        :name name
        :floating-label-text (when-not (or table? tooltip) label)
        :floating-label-fixed true
        :on-change #(update! %2)
        :on-blur on-blur
        :hint-text (or hint-text (placeholder field data))
        :hint-style (merge style-base/placeholder
                           hint-style)
        :value (or data "")
        :multi-line true
        :rows rows
        ;; Show error text or warning text or empty string
        :error-text (or error warning "")
        ;; Error is more critical than required - showing it first
        :error-style (if error
                       style-base/error-element
                       style-base/required-element)}
       (when max-length
         {:max-length max-length})
       (when full-width?
         {:full-width true})
       (when disabled?
         {:disabled true})
       (when style
         {:style style})
       (when input-style
         {:input-style input-style})
       (when on-enter
         {:on-key-press #(when (= "Enter" (.-key %))
                           (on-enter))}))]]])

(def languages ["FI" "SV" "EN"])

(defmethod field :localized-text [{:keys [update! table? is-empty? label name rows rows-max warning error full-width? style]
                                   :as   field} data]
  (r/with-let [selected-language (r/atom (first languages))]
    (let [data (or data [])
          languages (or (:languages field) languages)
          language @selected-language
          language-data (some #(when (= language (:ote.db.transport-service/lang %)) %) data)
          rows (or rows 1)]
      [:div {:style (merge
                      ;; Push localized text field down for table-row-column top padding amount when in table column.
                      (when table? {:margin-top "15px"})
                      (when full-width? style-form/full-width)
                      style)}
       [text-field
        (merge
          {:name name
           :floating-label-text (when-not table? label)
           :floating-label-fixed true
           :hintText (placeholder field data)
           :on-change #(let [updated-language-data
                             {:ote.db.transport-service/lang language
                              :ote.db.transport-service/text %2}]
                         (update!
                           (if language-data
                             (mapv (fn [lang]
                                     (if (= (:ote.db.transport-service/lang lang) language)
                                       updated-language-data
                                       lang)) data)
                             (conj data updated-language-data))))
           :value (or (:ote.db.transport-service/text language-data) "")
           :multi-line true
           :rows rows
           :rows-max (or rows-max 200)
           :error-text (or error "")}
          (when full-width?
            {:full-width true}))]
       [:div (stylefy/use-style style-form-fields/localized-text-language-container)
        (doall
          (for [lang languages]
            ^{:key lang}
            [:a (merge
                  (stylefy/use-style
                    (if (= lang language)
                      style-form-fields/localized-text-language-selected
                      style-form-fields/localized-text-language))
                  {:href "#" :on-click #(do (.preventDefault %)
                                            (reset! selected-language lang))})
             lang]))]
       (when (or error warning)
         [:div (stylefy/use-style style-base/required-element)
          (if error error warning)])
       (when (and (not error) (not warning) is-empty? (is-empty? data))
         [:div (stylefy/use-style style-base/required-element)
          (tr [:common-texts :required-field])])])))

(defmethod field :autocomplete [{:keys [update! label name error warning regex
                                        max-length style hint-style hint-text
                                        filter suggestions max-results
                                        on-blur
                                        form? table? full-width? open-on-focus?] :as field}
                                data]

  (let [handle-change! #(let [v %1]
                          (if regex
                            (when (re-matches regex v)
                              (update! v))
                            (update! v)))]
    [ui/auto-complete
     (merge
       {:name name
        :floating-label-text (when-not table? label)
        :floating-label-fixed true
        :dataSource suggestions
        :filter (or filter (aget js/MaterialUI "AutoComplete" "caseInsensitiveFilter"))
        :max-search-results (or max-results 10)
        :open-on-focus open-on-focus?
        :search-text (or data "")

        :hint-text (or hint-text (placeholder field data))
        :full-width full-width?


        ;; Show error text or warning text or empty string
        :error-text (or error warning "")
        ;; Error is more critical than required - showing it first
        :error-style (if error
                       style-base/error-element
                       style-base/required-element)
        :hint-style (merge style-base/placeholder
                           hint-style)
        :on-update-input handle-change!
        :on-new-request handle-change!}
       (when on-blur
         {:on-blur on-blur})
       (when max-length
         {:max-length max-length})
       (when style
         {:style style}))]))

(defmethod field :chip-input [{:keys [update! label name error warning regex on-blur
                                      max-length style hint-style
                                      filter suggestions suggestions-config default-values max-results
                                      auto-select? open-on-focus? clear-on-blur?
                                      allow-duplicates? add-on-blur? new-chip-key-codes
                                      form? table? full-width? full-width-input? disabled?] :as field}
                              data]

  (let [chips (set (or data #{}))
        handle-add! #(let [v (js->clj % :keywordize-keys true)]
                       (if regex
                         (when (re-matches regex v)
                           (update! (conj chips v)))
                         (update! (conj chips v))))
        handle-del! (fn [_ idx] (let [chips (vec chips)
                                      chips (concat (subvec chips 0 idx) (subvec chips (inc idx)))]
                                  (update! chips)))]
    [chip-input
     (merge
       {:name name
        :floating-label-text (when-not table? label)
        :floating-label-fixed true
        :hintText (placeholder field data)
        :disabled disabled?
        :value chips

        ;; == Autocomplete options ==
        :dataSource suggestions
        :filter (or filter (aget js/MaterialUI "AutoComplete" "caseInsensitiveFilter"))
        :max-search-results (or max-results 10)
        :open-on-focus open-on-focus?
        :clear-on-blur clear-on-blur?
        :auto-select? auto-select?

        ;; == Chip options ==
        :allow-duplicates allow-duplicates?
        ; Vector of key-codes for triggering new chip creation, 13 => enter, 32 => space
        :new-chip-key-codes (or new-chip-key-codes [13])

        ;; Show error text or warning text or empty string
        :error-text (or error warning "")

        ;; == Styling ==
        :full-width full-width?
        :full-width-input full-width-input?

        ;; Error is more critical than required - showing it first
        :error-style (if error
                       style-base/error-element
                       style-base/required-element)
        :hint-style (merge style-base/placeholder
                           hint-style)

        ;; == Event handlers ==
        :on-blur (fn [event]
                   (let [val (.. event -target -value)]
                     (when (and add-on-blur? (not (str/blank? val)))
                       (handle-add! val)))
                   (when on-blur (on-blur event)))
        :on-request-add handle-add!
        :on-request-delete handle-del!}
       ;; Define suggestions data element format.
       ;; Will be used internally like:
       ;;   dataSourceConfig: {:value :key}
       ;;   dataSource element: {:key 2}
       ;;   ((:value dataSourceConfig) {:key 2}) -> 2
       (when suggestions-config
         {:dataSourceConfig suggestions-config})
       (when max-length
         {:max-length max-length})
       (when style
         {:style style}))]))

(defn radio-selection [{:keys [update! label name show-option options error warning] :as field}
                       data]
  (let [option-idx (zipmap options (map str (range)))]
    [:div.radio (stylefy/use-style style-form-fields/radio-selection)
     [ui/radio-button-group
      {:value-selected (or (option-idx data) "")
       :name (str name)
       :on-change (fn [_ value]
                    (let [option (some #(when (= (second %) value)
                                          (first %)) option-idx)]
                      (update! option)))}
      (doall
       (map (fn [option]
              [ui/radio-button
               {:id (str "radio-" name)
                :label (show-option option)
                :value (option-idx option)
                :key (str "radio-" (option-idx option))}])
            options))]
     (when (or error warning)
       [:div
        (stylefy/use-sub-style style-form-fields/radio-selection :required)
        (if error error warning)])]))

(defn field-selection [{:keys [update! table? label name style show-option options form?
                               error warning auto-width? disabled?
                               option-value] :as field}
                             data]
  ;; Because material-ui selection value can't be an arbitrary JS object, use index
  (let [option-value (or option-value identity)
        option-idx (zipmap (map option-value options) (range))]
    [ui/select-field
     (merge
      {:auto-width (boolean auto-width?)
       :style style
       :floating-label-text (when-not table? label)
       :floating-label-fixed true
       :value (option-idx data)
       :on-change #(update! (option-value (nth options %2)))
       :error-text        (or error warning "") ;; Show error text or warning text or empty string
       :error-style       (if error             ;; Error is more critical than required - showing it first
                            style-base/error-element
                            style-base/required-element)}
      (when disabled?
        {:disabled true}))
     (doall
      (map-indexed
       (fn [i option]
         (if (= :divider option)
           ^{:key i}
           [ui/divider]
           ^{:key i}
           [ui/menu-item {:value i :primary-text (show-option option)}]))
       options))]))

(defmethod field :selection [{radio? :radio? :as field} data]
  (if radio?
    [radio-selection field data]
    [field-selection field data]))

(defmethod field :multiselect-selection
  [{:keys [update! table? label name style show-option show-option-short options form? error warning
           auto-width? full-width? id]
    :as field}
   data]
  ;; Because material-ui selection value can't be an arbitrary JS object, use index
  (let [selected-set (set (or data #{}))
        option-idx (zipmap options (range))]
    [:div
      [ui/select-field
       (merge
        {:id id
         :style style
         :floating-label-text (when-not table? label)
         :floating-label-fixed true
         :multiple true
         :value (clj->js (map option-idx selected-set))
         :selection-renderer (fn [values]
                               (str/join ", " (map (comp (or show-option-short show-option) (partial nth options)) values)))
         :on-change (fn [event index values]
                      (cond
                        ;; Select all - if :ALL option is present, if first element is selected and if all options aren't selected
                        (and (some #(= :ALL %) options) (some #(= 0 %) values) (<= (count values) (count (drop 1 options))))
                          (update! (drop 1 options))
                        ;;Deselect all
                        (and (some #(= :ALL %) options) (some #(= 0 %) values) (= (count options) (count values)))
                          (update! (into #{} nil))
                        ;; Select one
                        :else (update! (into #{}
                                             (map (partial nth options))
                                             values))))}
        (when auto-width?
          {:auto-width true})
        (when full-width?
          {:full-width true}))
       ;; Add selected value to vector
       (doall
        (map-indexed
         (fn [i option]
           ^{:key i}
           [ui/menu-item {:value i
                          :primary-text (show-option option)
                          :inset-children true
                          :checked (boolean (selected-set option))}])
         options))]
    (when (or error warning)
      [:div (stylefy/use-style style-base/required-element)
       (if error error warning)])]))

(def phone-regex #"\+?\d+")

(defmethod field :phone [opts data]
  [field (assoc opts
                :type :string
                :regex phone-regex)])

(def number-regex #"\d*([\.,]\d{0,2})?")

(defmethod field :number [_  data]
  ;; Number field contains internal state that has the current
  ;; typed in text (which may be an incompletely typed number).
  ;;
  ;; The value updated to the app model is always a parsed number.
  (let [fmt #(if % (str/replace (.toFixed % 2) #"(,|\.)00" "") "")
        state (r/atom {:value data
                       :txt (fmt data)})]
    (r/create-class
     {:component-will-receive-props
      (fn [_ [_ _ new-value]]
        (swap! state
               (fn [{:keys [value txt] :as state}]
                 (if (not= value new-value)
                   {:value new-value
                    :txt (fmt new-value)}
                   state))))
      :reagent-render
      (fn [{:keys [update! currency?] :as opts} data]
        [:span [field (assoc opts
                             :type :string
                             :regex number-regex
                             :update! #(let [new-value (if (str/blank? %)
                                                         nil
                                                         (-> %
                                                             (str/replace #"," ".")
                                                             (js/parseFloat %)))]
                                         (reset! state {:value new-value
                                                        :txt %})
                                         (update! new-value)))
                (:txt @state)]
         (when currency? "€")])})))

;; Matches empty or any valid hour (0 (or 00) - 23)
(def hour-regex #"^(^$|0?[0-9]|1[0-9]|2[0-3])$")

(def unrestricted-hour-regex #"\d*")

;; Matches empty or any valid minute (0 (or 00) - 59)
(def minute-regex #"^(^$|0?[0-9]|[1-5][0-9])$")

(defmethod field :time [{:keys [update! error warning required? unrestricted-hours?] :as opts}
                        {:keys [hours hours-text minutes minutes-text] :as data}]
  [:div (stylefy/use-style style-base/inline-block)
   [field (merge
           {:type :string
            :name "hours"
            :regex (if unrestricted-hours?
                     unrestricted-hour-regex
                     hour-regex)
            :warning (when
                       (and required? (empty? data))
                       true)
            :style {:width 30}
            :input-style {:text-align "right"}
            :hint-style {:position "absolute" :right "0"}
            :update! (fn [hour]
                       (let [h (if (str/blank? hour)
                                 nil
                                 (js/parseInt hour))]
                         (update! (assoc (time/->Time h minutes nil)
                                         :hours-text hour))))}
           (when (not hours)
             {:hint-text (tr [:common-texts :hours-placeholder])}))
    (if (not hours)
      ""
      (or hours-text (str hours)))]
   "."
   [field (merge
           {:type :string
            :name "minutes"
            :regex minute-regex
            :warning (when
                       (and required? (empty? data))
                       true)
            :style {:width 30}
            :update! (fn [minute]
                       (let [m (if (str/blank? minute)
                                 nil
                                 (js/parseInt minute))]
                         (update! (assoc (time/->Time hours m nil)
                                         :minutes-text minute))))}
           (when (not minutes)
             {:hint-text (tr [:common-texts :minutes-placeholder])}))
    (if (not minutes)
      ""
      (or minutes-text (gstr/format "%02d" minutes)))]

   (when warning
     [:div (stylefy/use-style style-base/error-element) warning])])

(def time-unit-order [:minutes :hours :days])

(defn- normalize-interval [{:keys [minutes hours days] :as interval}]
  (cond
    (and minutes (not= 0 minutes))
    [:minutes (+ minutes (* 60 (or hours 0)) (* 60 24 (or days 0)))]

    (and hours (not= 0 hours))
    [:hours (+ hours (* 24 (or days 0)))]

    days
    [:days days]))

(defmethod field :interval [{:keys [update! enabled-label] :as opts} data]
  (let [[unit amount] (or (normalize-interval data) [:hours 0])]
    [:div {:style {:width "100%" :padding-top "0.5em"}}
     [ui/toggle {:label enabled-label
                 :label-position "right"
                 :toggled (not (nil? data))
                 :on-toggle #(update!
                              (if data
                                nil
                                (time/interval 0 :days)))}]
     (when-not (nil? data)
       [:div
        [field (assoc opts
                      :update! (fn [num]
                                 (let [unit (or (::preferred-unit data) unit)]
                                   (update!
                                    (assoc (if (str/blank? num)
                                             (time/interval 0 unit)
                                             (time/interval (js/parseInt num) unit))
                                           ::preferred-unit unit))))
                      :hint-text (tr [:common-texts :time-unlimited])
                      :type :string
                      :regex #"\d{0,4}"
                      :style {:width 200}) amount]
        [field (assoc opts
                      :update! (fn [unit]
                                 (assoc (update! (time/interval amount unit))
                                        ::preferred-unit unit))
                      :label (tr [:common-texts :time-unit])
                      :name :maximum-stay-unit
                      :type :selection
                      :show-option (tr-key [:common-texts :time-units])
                      :options [:minutes :hours :days]
                      :style {:width 150
                              :position "relative"
                              :top 15})
         (or (::preferred-unit data) unit)]])]))

(defmethod field :time-picker [{:keys [update! ok-label cancel-label default-time] :as opts} data]
  (let [time-picker-time (if (= nil? data) default-time data)]
  [ui/time-picker
   {:format "24hr"
    :cancel-label cancel-label
    :ok-label ok-label
    :minutes-step 1
    :default-time (time/to-js-time time-picker-time)
    :on-change (fn [event value]
                 (update! (time/parse-time (time/format-js-time value))))}]))

(defmethod field :date-picker [{:keys [update! required? table? label ok-label cancel-label
                                       show-clear? hint-text id date-fields?] :as opts} data]
  (let [warning (when (and required? (not data))
                  (tr [:common-texts :required-field]))]
    [:div (stylefy/use-style style-base/inline-block)
     [ui/date-picker (merge {:style {:display "inline-block"}
                             :text-field-style {:width "150px"}
                             :hint-text (or hint-text "")
                             :floating-label-text (when-not table? label)
                             :floating-label-fixed true

                             ;; Show warning text or empty string
                             :error-text (or warning "")
                             :error-style style-base/required-element
                             :auto-ok true
                             :value (if date-fields?
                                      (when (time/valid-date? data)
                                        (time/date-fields->native (merge time/midnight data)))
                                      data)
                             :on-change (fn [_ date]
                                          (update! (if date-fields?
                                                     (time/date-fields-only date)
                                                     date)))
                             :format-date time/format-date
                             :ok-label (or ok-label (tr [:buttons :save]))
                             :cancel-label (or cancel-label (tr [:buttons :cancel]))
                             :locale (case @localization/selected-language
                                       :fi "fi-FI"
                                       :sv "sv-SE"
                                       :en "en-UK"
                                       "fi-FI")
                             :Date-time-format js/Intl.DateTimeFormat}
                            (when (not (nil? id))
                              {:id (str "date-picker-" id)}))]
     (when show-clear?
       [ui/icon-button {:on-click #(update! nil)
                        :disabled (not data)
                        :style {:width 16 :height 16
                                :position "relative"
                                :padding 0
                                :left -15
                                :top "5px"}
                        :icon-style {:width 16 :height 16}}
        [ic/content-clear {:style {:width 16 :height 16}}]])]))

(defmethod field :default [opts data]
  [:div.error "Missing field type: " (:type opts)])


(defmethod field :table [{:keys [table-fields table-wrapper-style update! delete? add-label add-label-disabled? error-data id] :as opts} data]
  (let [data (if (empty? data)
               ;; table always contains at least one row
               [{}]
               data)]
    [:div
     [:div.table-wrapper {:style table-wrapper-style
                          :id id}
      ;; We need to make overflow visible to allow css-tooltips to be visible outside of the table wrapper or body.
      [ui/table {:wrapperStyle {:overflow "visible"}
                 :bodyStyle {:overflow "visible"}}
       [ui/table-header (merge {:adjust-for-checkbox false :display-select-all false}
                               {:style style-form-fields/table-header})
        [ui/table-row (merge {:selectable false}
                             {:style style-form-fields/table-header-row})
         (doall
          (for [{:keys [name label width tooltip tooltip-pos tooltip-len] :as tf} table-fields]
            ^{:key name}
            [ui/table-header-column {:style
                                     (merge {:width width :white-space "pre-wrap"}
                                            style-form-fields/table-header-column)}
             label
             (when tooltip
               [tooltip-icon {:text tooltip :pos  tooltip-pos :len tooltip-len}])]))
         (when delete?
           [ui/table-header-column {:style (merge {:width "70px"} style-form-fields/table-header-column)}
            (tr [:buttons :delete])])]]

       [ui/table-body {:display-row-checkbox false}
        (doall
         (map-indexed
          (fn [i row]
            (let [{:keys [errors missing-required-fields]} (and error-data
                                                                (< i (count error-data))
                                                                (nth error-data i))]
              ^{:key i}
              [ui/table-row (merge {:id (str "row_" i)
                                    :selectable false :display-border false}
                                   ;; If there are errors or missing fields, make the
                                   ;; row taller to show error messages
                                   (when (or errors missing-required-fields)
                                     {:style {:height 65}}))
               (doall
                (for [{:keys [name read write width type component] :as tf} table-fields
                      :let [field-error (get errors name)
                            missing? (get missing-required-fields name)
                            update-fn (if write
                                        #(update data i write %)
                                        #(assoc-in data [i name] %))
                            value ((or read name) row)]]
                  ^{:key name}
                  [ui/table-row-column {:style (merge style-form-fields/table-row-column
                                                      {:width width})}
                   (if (= :component type)
                     (component {:update-form! #(update! (update-fn %))
                                 :data value})
                     [field (merge (assoc tf
                                          :table? true
                                          :row-number i
                                          :table-data data
                                          :update! #(update! (update-fn %)))
                                   (when missing?
                                     {:warning (tr [:common-texts :required-field])})
                                   (when field-error
                                     {:error field-error}))
                      value])]))
               (when delete?
                 [ui/table-row-column {:style (merge style-form-fields/table-row-column {:width "70px"})}
                  [ui/icon-button {:on-click #(update! (vec (concat (when (pos? i)
                                                                      (take i data))
                                                                    (drop (inc i) data))))}
                   [ic/action-delete]]])]))
          data))]]]
     (when add-label
       [:div (stylefy/use-style style-base/button-add-row)
        [buttons/save (merge {:on-click #(update! (conj (or data []) {}))
                              :label add-label
                              :label-style style-base/button-label-style
                              :disabled (if add-label-disabled?
                                          (add-label-disabled? (last data))
                                          (values/effectively-empty? (last data)))}
                             (when (not (nil? id))
                               {:id (str id "-button")}))]])]))

(defn- checkbox-container [update! table? label warning error style checked? disabled?]
  [:div (when error (stylefy/use-style style-base/required-element))
   [ui/checkbox {:label    (when-not table? label)
                 :checked  (boolean checked?)
                 :on-check #(update! (not checked?))
                 :disabled disabled?
                 :style    style}]
   (when error
     (tr [:common-texts :required-field]))])

(defmethod field :checkbox [{:keys [update! table? label warning error style extended-help disabled?]} checked?]
  (if extended-help
    [:div {:style {:margin-right (str "-" (:margin-right style-form/form-field))}}
     [common/extended-help
      (:help-text extended-help)
      (:help-link-text extended-help)
      (:help-link extended-help)]
     (checkbox-container update! table? label warning error style checked? disabled?)]
    (checkbox-container update! table? label warning error style checked? disabled?)))

(defmethod field :checkbox-group [{:keys [update! table? label show-option options help error warning header? option-enabled? option-addition]} data]
  ;; Options:
  ;; :header? Show or hide the header element above the checkbox-group. Default: true.
  ;; :option-enabled? Is option checkable. Default: true
  ;; option-addition is a map, that knows which option needs additions and the addition. e.g. {:value: :other :addition [ReagentObject]}
  (let [selected (set (or data #{}))
        option-enabled? (or option-enabled? (constantly true))]
    [:div.checkbox-group
     (when (not (false? header?))
       [:h4 (stylefy/use-style style-form-fields/checkbox-group-label) label])
     (when help
       [common/help help])
     (doall
       (map-indexed
         (fn [i option]
           (let [checked? (boolean (selected option))
                 is-addition-valid (and (not (nil? option-addition)) (= option (:value option-addition)) checked?)
                 addition (when is-addition-valid (:addition option-addition))]
             ^{:key i}
             [:div {:style {:display "flex"}}
              [:span
               [ui/checkbox {:id (str i "_" (name option))
                             :label      (when-not table? (show-option option))
                             :checked    checked?
                             :disabled   (not (option-enabled? option))
                             :labelStyle (merge style-base/checkbox-label
                                                (if (not (option-enabled? option))
                                                  style-base/disabled-color
                                                  {:color "rgb(33, 33, 33)"}))
                             :on-check   #(update! ((if checked? disj conj) selected option))}]]
              (when is-addition-valid
                [:span {:style {:padding-left "20px"}} addition])]))
         options))
     (when (or error warning)
       [:div
        (stylefy/use-sub-style style-form-fields/radio-selection :required)
        (if error error warning)])]))

(defn- csv-help-text []
  [:div.row
   [:div (stylefy/use-style style-base/link-icon-container)
    [ic/action-get-app {:style style-base/link-icon}]]
   [:div
    (ote.ui.common/linkify "/ote/csv/palveluyritykset.csv"  (tr [:form-help :csv-file-example]) {:target "_blank"})]])


(defn company-csv-url-input [update! on-url-given companies-csv-url data]
  [:div
   [:div.row (stylefy/use-style style-base/divider)]
   [:div.row
    (csv-help-text)
    [:div.col-md-6
     [field {:name            ::t-service/companies-csv-url
             :label           (tr [:field-labels :transport-service-common ::t-service/companies-csv-url])
             :hint-text       "https://finap.fi/ote/csv/palveluyritykset.csv"
             :full-width?     true
             :on-blur         on-url-given
             :update!         #(update! {::t-service/companies-csv-url %})
             :container-class "col-xs-12 col-sm-6 col-md-6"
             :type            :string}
      companies-csv-url]]]

   (let [success (if (= :success (get-in data [:csv-count :status]))
                   true
                   false)
         amount (if (get-in data [:csv-count :count])
                  (get-in data [:csv-count :count])
                  nil)]
     (cond
       (and data success) [:div.row {:style {:color "green"}} (tr [:csv :parsing-success] {:count amount})]
       (and data (= false success)) [:div.row (stylefy/use-style style-base/required-element)
                                     (tr [:csv (get-in data [:csv-count :error])])]
       :else [:span]))])

(defn company-csv-file-input [on-file-selected data]
  [:div
   [:div.row (stylefy/use-style style-base/divider)]
   [:div.row
    (csv-help-text)
    [:div.row {:style {:padding-top "20px"}}
     [field {:name      ::t-service/csv-file
             :type      :file
             :label     (if (get data ::t-service/company-csv-filename)
                          (tr [:buttons :update-csv])
                          (tr [:buttons :upload-csv]))
             :accept    ".csv"
             :on-change on-file-selected
             }]]
    (when (get data ::t-service/company-csv-filename)
      [:div.row {:style {:padding-top "20px"}} (get data ::t-service/company-csv-filename)])
    [:div.row {:style {:padding-top "20px"}}
     (let [success (boolean (:csv-imported data))
           amount (if (get data ::t-service/companies)
                    (count (get data ::t-service/companies))
                    nil)]
       (when success
         [:span {:style {:color "green"}} (tr [:csv :parsing-success] {:count amount})]))]]])

(defn company-input-fields [update! companies data]
  [:div.row
   [:div.row (stylefy/use-style style-base/divider)]
   [:div.row
    [field {:name                ::t-service/companies
            :type                :table
            :update!             #(update! {::t-service/companies %})
            :table-wrapper-style {:max-height "300px" :overflow "scroll"}
            :prepare-for-save    values/without-empty-rows
            :table-fields        [{:name      ::t-service/name
                                   :type      :string
                                   :label     (tr [:field-labels :transport-service-common ::t-service/company-name])
                                   :required? true
                                   }
                                  {:name      ::t-service/business-id
                                   :type      :string
                                   :label     (tr [:field-labels :transport-service-common ::t-service/business-id])
                                   :validate  [[:business-id]]
                                   :required? true
                                   :regex     #"\d{0,7}(-\d?)?"}]
            :delete?             true
            :add-label           (tr [:buttons :add-new-company])
            :error-data          (::t-service/companies (:ote.ui.form/warnings data))}
     companies]]])

(defmethod field :company-source [{:keys [update! enabled-label on-file-selected on-url-given] :as opts}
                                  {::t-service/keys [company-source companies companies-csv-url passenger-transportation] :as data}]
  (let [select-type #(update! {::t-service/company-source %})
        selected-type (if company-source
                        (name company-source)
                        "none")]
    [:div
     [:div.row
      [:h3 (tr [:passenger-transportation-page :header-select-company-list-type])]]
     [:div.row
      [ui/radio-button-group {:name           (str "brokerage-companies-selection")
                              :value-selected selected-type}
       [ui/radio-button {:label    (tr [:passenger-transportation-page :radio-button-no-companies])
                         :id       "radio-company-none"
                         :value    "none"
                         :on-click #(select-type :none)}]
       [ui/radio-button {:label    (tr [:passenger-transportation-page :radio-button-url-companies])
                         :id       "radio-company-csv-url"
                         :value    "csv-url"
                         :on-click #(select-type :csv-url)}]
       [ui/radio-button {:label    (tr [:passenger-transportation-page :radio-button-csv-companies])
                         :id       "radio-company-csv-file"
                         :value    "csv-file"
                         :on-click #(select-type :csv-file)}]
       [ui/radio-button {:label    (tr [:passenger-transportation-page :radio-button-form-companies])
                         :value    "form"
                         :id       "radio-company-form"
                         :on-click #(select-type :form)}]]

      (when-not (nil? data)
        (case company-source
          :none [:div.row " "]
          :csv-url [company-csv-url-input update! on-url-given companies-csv-url data]
          :csv-file [company-csv-file-input on-file-selected data]
          :form [company-input-fields update! companies data]
          ;; default
          ""))]]))
