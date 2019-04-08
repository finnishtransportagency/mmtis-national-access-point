(ns ote.views.route.service-calendar
  (:require [reagent.core :as r]
            [ote.app.controller.route.route-wizard :as rw]
            [ote.time :as time]
            [ote.ui.service-calendar :as service-calendar]
            [ote.mui-wrapper.reagent :as ui]
            [ote.mui-wrapper.icons :as ic]
            [ote.db.transit :as transit]
            [ote.localization :refer [tr tr-key]]
            [ote.style.form :as style-form]
            [stylefy.core :as stylefy]
            [ote.ui.form-fields :as form-fields]
            [ote.db.transport-service :as t-service]
            [ote.localization :refer [tr selected-language]]
            [ote.ui.common :as common]
            [ote.style.dialog :as style-dialog]))

(defn rule-fields [calendar]
  (into [{:id "from-date"
          :type  :date-picker :date-fields? true
          :name  ::transit/from-date
          :label (tr [:route-wizard-page :route-calendar-from-date])
          :width "29%"}
         {:id "to-date"
          :type  :date-picker :date-fields? true
          :name  ::transit/to-date
          :label (tr [:route-wizard-page :route-calendar-to-date])
          :width "29%"}]
        (for [[name label] [[::transit/monday (tr [:enums :ote.db.transport-service/day :short :MON])]
                            [::transit/tuesday (tr [:enums :ote.db.transport-service/day :short :TUE])]
                            [::transit/wednesday (tr [:enums :ote.db.transport-service/day :short :WED])]
                            [::transit/thursday (tr [:enums :ote.db.transport-service/day :short :THU])]
                            [::transit/friday (tr [:enums :ote.db.transport-service/day :short :FRI])]
                            [::transit/saturday (tr [:enums :ote.db.transport-service/day :short :SAT])]
                            [::transit/sunday (tr [:enums :ote.db.transport-service/day :short :SUN])]]]
          {:type      :checkbox
           :name      name
           :disabled? (not (rw/valid-calendar-rule-dates? calendar))
           :label     label
           :width     "6%"})))

(defn- rules-table [e! trip-idx calendar]
  [:div.row {:style {:padding "20px"}}
   [form-fields/field {:id "new-calendar-period"
                       :type :table
                       :update! #(e! (rw/->EditServiceCalendarRules {::transit/service-rules %} trip-idx))
                       :table-fields (rule-fields calendar)
                       :delete? true
                       :add-label (tr [:route-wizard-page :route-calendar-add-new-period])
                       :add-label-disabled? rw/empty-calendar-from-to-dates?}
    (::transit/service-rules calendar)]])

(defn day-style-fn [calendar]
  (let [rule-dates (or (:rule-dates calendar) #{})
        removed-dates (or (::transit/service-removed-dates calendar) #{})
        added-dates (or (::transit/service-added-dates calendar) #{})]
    (fn [day selected?]
      (let [day (time/date-fields day)]
        (cond
          (removed-dates day)
          {:background-color "#c27272"
           :text-decoration "line-through"}

          (rule-dates day)
          {:background-color "lightblue"}

          (added-dates day)
          {:background-color "wheat"})))))

(defn service-calendar [e! {trip-idx :edit-service-calendar
                            trips ::transit/trips
                            stops ::transit/stops :as route}]
  (let [times (get-in trips [trip-idx ::transit/stop-times])
        departure-time (::transit/departure-time (first times))
        departure-stop (::transit/name (first stops))]
    [ui/dialog
     {:open true
      :actionsContainerStyle style-dialog/dialog-action-container
      :content-style {:width "95%" :max-width js/document.body.clientWidth}
      :modal false
      :auto-scroll-body-content true
      :title (r/as-element
               [:div (tr [:route-wizard-page :route-calendar-group-name]
                         {:departure-time (and departure-time (time/format-time departure-time))
                          :departure-stop (t-service/localized-text-with-fallback @selected-language departure-stop)})
                [ui/raised-button {:secondary true
                                   :icon (ic/action-delete)
                                   :style {:float "right"}
                                   :on-click #(e! (rw/->ClearServiceCalendar trip-idx))
                                   :label (tr [:buttons :route-calendar-clear])}]])
      :actions [(r/as-element
                  [ui/flat-button
                   {:label (tr [:buttons :cancel])
                    :secondary true
                    :on-click #(e! (rw/->CancelServiceCalendar))}])
                (r/as-element
                  [ui/flat-button
                   {:label (tr [:buttons :approve])
                    :primary true
                    :on-click #(e! (rw/->CloseServiceCalendar))}])]}
     (let [calendar (get-in route [::transit/service-calendars trip-idx])]
       [:div.route-service-calendar {:style {:padding-top "20px"}}

        [common/generic-help (tr [:route-wizard-page :route-calendar-guidance-info])]
        [rules-table e! trip-idx calendar]

        [service-calendar/service-calendar
         {:selected-date? (constantly false)
          :on-select #(e! (rw/->ToggleDate % trip-idx))
          :day-style (day-style-fn calendar)}]])]))
