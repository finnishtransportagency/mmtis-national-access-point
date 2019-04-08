(ns ote.views.transit-visualization.calendar
  "Calendar section in transit visualization page."
  (:require [reagent.core :as r]
            [ote.mui-wrapper.icons :as ic]
            [stylefy.core :as stylefy]
            [ote.time :as time]
            [ote.mui-wrapper.reagent :as ui]
            [ote.localization :refer [tr]]
            [ote.ui.icons :as ote-icons]
            [ote.ui.icon_labeled :as icon-l]
            [ote.ui.service-calendar :as service-calendar]
            [ote.ui.table :as table]
            [ote.theme.colors :as colors]
            [ote.style.transit-changes :as style]
            [ote.style.base :as style-base]
            [ote.views.transit-visualization.change-utilities :as tv-section]
            [ote.views.transit-visualization.change-icons :as tv-change-icons]
            [ote.app.controller.transit-visualization :as tv]))

;; Utility methods
(defn select-day [e! day loading?]
  (when-not loading?
    (e! (tv/->SelectDatesForComparison day))))

(defn day-style [hash->color date->hash date1 date2 day]
  (let [prev-week-date (time/format-date-iso-8601 (time/days-from day -7))
        next-week-date (time/format-date-iso-8601 (time/days-from day 7))
        prev-week-hash (date->hash prev-week-date)
        next-week-hash (date->hash next-week-date)

        d (time/format-date-iso-8601 day)
        hash (date->hash d)
        hash-color (hash->color hash)]
    (merge
      {:font-size "0.75rem"
       :background-color hash-color
       :color "rgb (0, 255, 255)"
       :transition "box-shadow 0.25s"
       :box-shadow "inset 0 0 0 2px transparent, inset 0 0 0 3px transparent, inset 0 0 0 100px transparent"}
      #_ (when (and prev-week-hash hash (not= hash prev-week-hash) (> day (time/now)))
        {:box-shadow "inset 0 0 0 1px black,
                     inset 0 0 0 2px transparent"})
      (cond (and (= (time/format-date-iso-8601 date1) d) (some? date2))
            (style/date1-highlight-style hash-color)

            (= (and (some? date2) (time/format-date-iso-8601 date2)) d)
            (style/date2-highlight-style hash-color)

            (and (= (time/format-date-iso-8601 date1) d) (nil? date2))
            (style/date-highlight-style hash-color colors/gray800)))))

(defn comparison-dates [{:keys [date1 date2]}]
  [:div (stylefy/use-style (merge (style-base/flex-container "row")
                                  {:justify-content "space-between"
                                   :width "20%"}))
   [:div [:div {:style (merge {:display "inline-block"
                               :position "relative"
                               :top "5px"
                               :margin-right "0.5em"
                               :width "20px"
                               :height "20px"}
                              (style/date1-highlight-style))}]
    (time/format-date date1)]
   [:div [:div {:style (merge {:display "inline-block"
                               :position "relative"
                               :top "5px"
                               :margin-right "0.5em"
                               :width "20px"
                               :height "20px"}
                              (style/date2-highlight-style))}]
    (time/format-date date2)]])

(defn comparison-date-changes [{diff :differences :as compare}]
  [:span
   [comparison-dates compare]

   (when (seq diff)
     [tv-change-icons/change-icons diff true])])

;; Ui
(defn route-calendar [e! {:keys [date->hash hash->color show-previous-year? compare open-sections route-changes] :as transit-visualization} routes selected-route]
  (let [current-year (time/year (time/now))
        changes (filter (fn [x]
                          (if (= (:route-hash-id x) (:route-hash-id selected-route)) true false)
                          ) routes)]
    [:div.route-service-calendar
     [tv-section/section {:toggle! #(e! (tv/->ToggleSection :route-service-calendar))
               :open? (get open-sections :route-service-calendar true)}
      "Kalenteri"
      [:div
       [:p (tr [:transit-visualization-page :calendar-description-1])]
       [:p (tr [:transit-visualization-page :calendar-description-2])]
       [:div (stylefy/use-style (merge (style-base/flex-container "row")
                                       {:margin-top "1rem"
                                        :margin-bottom "1rem"}))
        [ui/checkbox {:label "Näytä myös edellinen vuosi"
                      :checked show-previous-year?
                      :on-check #(e! (tv/->ToggleShowPreviousYear))}]]]
      [:div
      [:div.change-list
       [tv-section/route-changes-legend]
       [table/table {:name->label str
                     :label-style style-base/table-col-style-wrap
                     :show-row-hover? true
                     :on-select #(when (first %)
                                   (do
                                     (e! (tv/->SelectRouteForDisplay (first %)))))
                     :row-selected? #(= (:different-week-date %) (:different-week-date selected-route))}
        [{:name "Aikaa muutokseen"
          :read :different-week-date
          :format (fn [different-week-date]
                    [:div
                     [:span (stylefy/use-style { ;; nowrap for the "3 pv" part to prevent breaking "pv" alone to new row.
                                               :white-space "nowrap"})
                      (str (time/days-until different-week-date) " pv ")]
                     [:span (stylefy/use-style {:color "gray"
                                               :overflow-wrap "break-word"})
                      (str "(" (time/format-timestamp->date-for-ui different-week-date) ")")]])}
         {:name "Muutos tunnistettu"
          :read :transit-change-date
          :format (fn [transit-change-date]
                    (time/format-timestamp->date-for-ui transit-change-date))}
         {:name "Muutokset" :width "30%"
          :read identity
          :col-style style-base/table-col-style-wrap
          :format (fn [{change-type :change-type :as route}]
                    (case change-type
                          :no-traffic
                      [icon-l/icon-labeled style/transit-changes-icon
                       [ic/av-not-interested]
                       "Tauko liikennöinnissä"]

                      :added
                      [icon-l/icon-labeled style/transit-changes-icon
                       [ic/content-add-box {:color style/add-color}]
                       "Uusi reitti"]

                      :removed
                      [icon-l/icon-labeled
                       [ic/content-remove-circle-outline {:color style/remove-color}]
                       "Mahdollisesti päättyvä reitti"]

                      :no-change
                      [icon-l/icon-labeled style/transit-changes-icon
                       [ic/navigation-check]
                       "Ei muutoksia"]

                      :changed
                      [tv-change-icons/change-icons route]))}]
        changes]]

      [:div.route-service-calendar-content
       [service-calendar/service-calendar {:selected-date? (constantly false)
                                           :on-select #(select-day e! % (:route-differences-loading? transit-visualization))
                                           :day-style (r/partial day-style hash->color date->hash
                                                                 (:date1 compare) (:date2 compare))
                                           :years (vec (concat (when show-previous-year?
                                                                 [(dec current-year)])
                                                               [current-year]
                                                               [(inc current-year)]))
                                           :hover-style #(let [d (time/format-date-iso-8601 %)
                                                               hash (date->hash d)
                                                               hash-color (hash->color hash)]
                                                           (style/date-highlight-style hash-color
                                                                                       style/date-highlight-color-hover))}]
       (when (and (:date1 compare) (:date2 compare))
         [:div
          [:h3 "Valittujen päivämäärien väliset muutokset"]
          [comparison-date-changes compare]])]]]]))