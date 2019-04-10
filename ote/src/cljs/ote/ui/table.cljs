(ns ote.ui.table
  "Simple Material UI data table"
  (:require [ote.mui-wrapper.reagent :as ui]
            [ote.ui.common :as common]
            [ote.style.table :as table-style]
            [reagent.core :as r]
            [ote.mui-wrapper.icons :as ic]
            [ote.theme.colors :as colors]
            [ote.theme.transitions :as transitions]
            [stylefy.core :as stylefy]))

(def tooltip-icon
  "A tooltip icon that shows balloon.css tooltip on hover."
  (let [wrapped (common/tooltip-wrapper ic/action-help {:style {:margin-left 8}})]
    (fn [opts]
      [wrapped {:style {:width 16 :height 16
                        :vertical-align "middle"
                        :color "gray"}}
       opts])))

(defn table [{:keys [height name->label key-fn
                     label-style
                     row-style show-row-hover? selected-route
                     on-select row-selected? no-rows-message class] :as opts} headers rows]
  (let [random-table-id (str "tid-"(rand-int 999))
        table-row-color "#FFFFFF"
        table-row-color-alt colors/gray200
        table-row-hover-color colors/gray400]
    [ui/table (merge
                {:wrapperStyle {:overflow "visible"}
                 :body-style {:padding "3px"}
                 ;; FIXME: When we have tooltips in header labels, body does not need overflow: visible.
                 ;;        But, if any row includes tooltips, the body must also have visible overflow.
                 ;;        For now, leaving this commented out.
                 ;:bodyStyle {:overflow "visible"}
                 }
                (when on-select
                  {:on-row-selection (fn [selected-rows]
                                       (when (seq selected-rows)
                                         (let [rows (vec rows)]
                                           (on-select (map (partial nth rows) selected-rows)))))})
                (when height
                  {:height height
                   :fixed-header true})
                (when class
                  {:class class}))
     [ui/table-header {:key (str random-table-id "-header")
                       :style {:overflow "visible" :background-color colors/gray300}
                       :adjust-for-checkbox false
                       :display-select-all false}
      [ui/table-row {:key (str random-table-id "-header-row")
                     :style {:overflow "visible"}
                     :selectable false}
       (doall
         (map-indexed
           (fn [i {:keys [name width tooltip tooltip-pos tooltip-len]}]
             ^{:key (str i "-" name "-header-column")}
             [ui/table-header-column {:style
                                      (merge
                                        (when width
                                          {:width width})
                                        {:white-space "pre-wrap"
                                         :overflow "visible"
                                         :color colors/gray900
                                         :font-size "1em"
                                         :font-weight "bold"}
                                        label-style)}
              (name->label name)
              (when tooltip
                [tooltip-icon {:text tooltip :pos tooltip-pos :len tooltip-len}])])
           headers))]]
     [ui/table-body {:display-row-checkbox false}
      (if (empty? rows)
        [:tr [:td {:colSpan (count headers)}
              [:div (stylefy/use-style table-style/no-rows-message) no-rows-message]]]
        (doall
          (map-indexed
            (fn [i row]
              ^{:key (if key-fn (key-fn row) (str i "-" random-table-id "-body-row"))}
              [ui/table-row (merge
                              (stylefy/use-style (merge {:background-color (if (even? i) table-row-color table-row-color-alt)} ; Add stripes
                                                        (when on-select
                                                          {:cursor "pointer"
                                                           :transition (str "background-color " transitions/fast-ease-in-out)
                                                           ::stylefy/mode {:hover {:background-color table-row-hover-color}}})
                                                        (when (if row-selected? (row-selected? row) false)
                                                          {:outline (str "solid 3px " colors/primary-dark)
                                                           ::stylefy/mode {:hover {:background-color (if (even? i) table-row-color table-row-color-alt)}}})
                                                        (when row-style row-style)))
                              {:selectable (boolean on-select)
                               :display-border false})

               (doall
                 (map-indexed
                   (fn [i {:keys [name width col-style format read]}]
                     (let [value ((or format identity) (if read (read row) (get row name)))]
                       ^{:key (str i "-" random-table-id "-row-col-" name)}
                       [ui/table-row-column {:style (merge
                                                      {:white-space "pre-wrap"
                                                       :overflow "visible"}
                                                      (when width {:width width})
                                                      (when col-style col-style))}
                        (cond
                          (vector? value)
                          (r/as-element value)

                          (string? value)
                          value

                          :else (str value))]))
                   headers))])
            rows)))]]))
