(ns ote.ui.mui-chip-input
  (:require material-ui-chip-input
            [ote.mui-wrapper.reagent :as ui]
            [reagent.core :as r]
            [clojure.string :as str]))

;; TODO: Todo implement custom cljs version of mui chip-input component mirroring the amazing features of the component.

;; FIXME: Chips are always placed above input field in form group. Suspecting that flexbox set in the form-group is affecting this somehow.
;;        Correct behaviour is: place chips inside the input field until there is no horizontal space left, after that create a new line.
(def default-props {:ref "chip-input"
                    :floating-label-fixed true

                    ;; == Autocomplete options ==
                    :filter (or filter (aget js/MaterialUI "AutoComplete" "caseInsensitiveFilter"))
                    :max-search-results 10
                    :open-on-focus true
                    :clear-on-blur true

                    ;; == Chip options ==
                    :allow-duplicates false
                    ; Vector of key-codes for triggering new chip creation, 13 => enter, 32 => space
                    :new-chip-key-codes [13]

                    ;; == Styling ==
                    :full-width false
                    :full-width-input false
                    ; Modify original style
                    ; For some reason, the text input underline is misplaced compared to other original mui-textfields.
                    ;        This change fixes those problems, in combination with conditional chipContainerStyle change.
                    :inputStyle {:margin-top 12
                                 :margin-bottom 14}
                    :listStyle {:width "auto"}})

(def chip-input* (r/adapt-react-class (aget js/window "TextField")))

(defn- auto-select [c orig-chip search-str dataSourceConfig js-handleAddChip]
  (let [autocomplete (aget c "autoComplete")
        ;; RequestLists contains filtered suggestions from AutoComplete
        first-match (first (js->clj (aget autocomplete "requestsList") :keywordize-keys true))
        suggestions (js->clj (aget c "props" "dataSource") :keywordize-keys true)
        dataSourceConfig (or dataSourceConfig {:text :text :value :value})
        text-key (:text dataSourceConfig)

        ;; Pass original chip if we find a perfect match from suggestions. This enables chip add by clicking on the suggestion menu.
        ;; Otherwise, match first item from the filter requestList and try to find a match from suggestions.
        ;; RequestList does not contain chip :value, so we have to find our real Chip from the suggestions vector.
        chip (if (some #(= search-str (or (text-key %) %)) suggestions)
               orig-chip
               ;; Note: requestList values i.e. first-match always have the :text key and dataSourceConfig has no relation to it.
               (first (filter #(= (or (text-key %) %) (:text first-match)) suggestions)))]

    ;; Call the original ChipInput.handleAddChip function
    (when chip (.call js-handleAddChip c chip))))

(defn chip-input [props]
  (let [ref (atom nil)]
    (r/create-class
      {:component-did-mount
       (fn [this]
         (let [c (reset! ref (aget this "refs" "chip-input"))
               js-handleAddChip (aget c "handleAddChip")
               auto-select? (:auto-select? props)
               dataSourceConfig (:dataSourceConfig props)
               text-key (:text dataSourceConfig)]

           ;; Autoselect first matching suggestion if :auto-select prop is true after pressing :new-chip-key-code key.
           ;; This will override the normal handleAddChip member function of chip-input with our custom function.
           (aset c "handleAddChip"
                 (fn [js-chip]
                   (let [chip (js->clj js-chip :keywordize-keys true)
                         search-str (or (text-key chip) chip)]
                     (cond
                       (str/blank? search-str) nil
                       auto-select? (auto-select c chip search-str dataSourceConfig js-handleAddChip)
                       :else (.call js-handleAddChip c chip)))))))

       :reagent-render
       (fn [props]
         [chip-input* (merge
                        default-props
                        ; Remove margin if there are no chips (works only in "controlled" case i.e. :value prop is used).
                        (when (empty? (:value props))
                          {:chipContainerStyle {:margin-top 0}})
                        ;; Remove custom prop to prevent Reactunknown prop warning
                        (dissoc props :auto-select?))])})))