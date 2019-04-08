(ns ote.ui.select_field
  (:require [ote.mui-wrapper.reagent :as ui]
            [ote.style.base :as style-base]
            [reagent.core :as r]))

(defn select-field [{:keys [update! table? label style show-option options
                                error warning auto-width? disabled?
                                option-value class-name ] :as field}]
  ;; Because material-ui selection value can't be an arbitrary JS object, use index
  (let [current-ix-atom (r/atom 0)
        current-name-atom (r/atom nil)
        previous-name-atom (r/atom nil)
        int-cb (fn [ix]
                 (reset! previous-name-atom @current-name-atom)
                 (reset! current-name-atom (nth options ix))
                 (reset! current-ix-atom ix))]
    ;; Wrapper fn required to get reagent re-render element after changes
    (fn [{label :label options :options :as all}]
      [ui/select-field
       (merge
         {:auto-width (boolean auto-width?)
          :style style
          :floating-label-text (when-not table? label)
          :floating-label-fixed true
          :value @current-ix-atom
          :on-change #(do (int-cb %2)
                          (update! {:current @current-name-atom
                                    :previous @previous-name-atom}))
          :error-text (or error warning "")                 ;; Show error text or warning text or empty string
          :error-style (if error                            ;; Error is more critical than required - showing it first
                         style-base/error-element
                         style-base/required-element)
          :className (if class-name class-name "mui-select-button")}
         (when disabled?
           {:disabled true}))
       (doall
         (map-indexed
           (fn [i option]
             (if (= :divider option)
               ^{:key (str "select-field-divider-" i)}
               [ui/divider]
               ^{:key (str "select-field-menuitem-" (show-option option))}
               [ui/menu-item
                (merge {:value i :primary-text (show-option option)}
                       (if (:disabled? option)
                         {:disabled true
                          :style style-base/disabled-control}
                         {:disabled false})
                       )]))
           options))])))

