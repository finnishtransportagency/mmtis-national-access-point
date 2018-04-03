(ns ote.views.route.trips
  "Route wizard: route trips table"
  (:require
    [ote.app.controller.route.route-wizard :as rw]
    [ote.time :as time]
    [ote.ui.form-fields :as form-fields]
    [cljs-react-material-ui.reagent :as ui]
    [cljs-react-material-ui.icons :as ic]
    [ote.db.transit :as transit]
    [stylefy.core :as stylefy]
    [ote.style.route :as style-route]
    [reagent.core :as r]
    [ote.ui.common :as common]
    [ote.localization :refer [tr tr-key]]

    ;; Calendar subview
    [ote.views.route.service-calendar :as route-service-calendar]
    [ote.style.form :as style-form]))

(defn badge-content [service-calendars row-idx]
  (if (or (empty? (get-in service-calendars [row-idx]))
          (and
            (empty? (get-in service-calendars [row-idx :rule-dates]))
            (empty? (get-in service-calendars [row-idx ::transit/service-removed-dates]))
            (empty? (get-in service-calendars [row-idx ::transit/service-rules]))))
    {:badge-content "!"
     :secondary true
     :badgeStyle {:width 15
                  :height 15
                  :top 0
                  :right 0
                  :fontSize 12
                  :padding "3px 3px 3px 3px"
                  :background-color "#ed0000"
                  :color "#fff"}
     :style {:padding "0px 5px 0px 5px"}}
    {:style {:padding "0px 5px 0px 5px"}}))

(defn- icon-for-type [type]
  (case type
    :regular [ic/maps-pin-drop {:style style-route/selected-exception-icon}]
    :not-available [ic/notification-do-not-disturb {:style style-route/selected-exception-icon}]
    :phone-agency [ic/communication-call {:style style-route/selected-exception-icon}]
    :coordinate-with-driver [ic/social-people {:style style-route/selected-exception-icon}]
    [ic/maps-pin-drop {:style style-route/exception-icon}]))

(defn exception-icon [e! stop-type type stop-idx trip-idx]
  (let [set-exception! (fn [to-type]
                         #(do
                            (.preventDefault %)
                            (e! (rw/->ShowStopException stop-type stop-idx to-type trip-idx))))]
    [:div
     [ui/icon-menu
      {:icon-button-element (r/as-element
                             [ui/icon-button
                              {:style      {:padding 4
                                            :width   24
                                            :height  24}
                               :icon-style {:height 24
                                            :width  24}}
                              (icon-for-type type)])}
      [ui/menu-item
       {:primary-text (tr [:route-wizard-page :trip-stop-exception stop-type :default])
        :left-icon    (ic/maps-pin-drop)
        :on-click     (set-exception! :regular)}]
      [ui/menu-item
       {:primary-text (tr [:route-wizard-page :trip-stop-exception stop-type :no])
        :left-icon    (ic/notification-do-not-disturb)
        :on-click     (set-exception! :not-available)}]
      [ui/menu-item
       {:primary-text (tr [:route-wizard-page :trip-stop-exception stop-type :agency])
        :left-icon    (ic/communication-call)
        :on-click     (set-exception! :phone-agency)}]
      [ui/menu-item
       {:primary-text (tr [:route-wizard-page :trip-stop-exception stop-type :driver])
        :left-icon    (ic/social-people)
        :on-click     (set-exception! :coordinate-with-driver)}]]]))

(defn route-times-header [stop-sequence]
  [:thead
   [:tr
    [:th ""]
    (doall
     (map-indexed
      (fn [i {::transit/keys [code name]}]
        ^{:key (str code "_" i)}
        [:th {:colSpan 2
              :style {:vertical-align "top"}}
         [:div {:style {:display "inline-block"
                        :width "180px"
                        :overflow-x "hidden"
                        :white-space "pre"
                        :text-overflow "ellipsis"}} name]
         [:div {:style {:display "inline-block"
                        :float "right"
                        :position "relative"
                        :left "16px"
                        :top -20
                        :padding-right "5px"
                        :padding-left "5px"}}
          (when (< i (dec (count stop-sequence)))
            [ic/navigation-chevron-right])]])
      stop-sequence))]
   [:tr
    [:th ""]
    (doall
     (for [{::transit/keys [code name]} stop-sequence]
       (list
        ^{:key (str code "-arr")}
        [:th {:style {:font-size "80%" :font-variant "small-caps"}}
         (tr [:route-wizard-page :trip-stop-arrival-header])]
        ^{:key (str code "-dep")}
        [:th {:style {:font-size "80%" :font-variant "small-caps"}}
         (tr [:route-wizard-page :trip-stop-departure-header])])))]])

(defn trip-row
  "Render a single row of stop times."
  [e! stop-count edit-service-calendar service-calendars row-idx {stops ::transit/stop-times :as trip}]
  ^{:key row-idx}
  [:tr {:style {:max-height "40px"}}
   [:td [:div
         [:span {:data-balloon        (tr [:route-wizard-page :trip-stop-calendar])
                 :data-balloon-pos    "right"
                 :data-balloon-length "medium"
                 :style {:overflow "visible"}}
          [ui/badge
           (badge-content service-calendars row-idx)
          [ui/icon-button {
                           :style (if (= edit-service-calendar row-idx)
                                    {:border-radius "25px" :background-color "#b3b3b3"}
                                    {})
                           :href     "#"
                           :on-click #(do
                                        (.preventDefault %)
                                        (e! (rw/->EditServiceCalendar row-idx)))}
             [ic/action-today]]]]]]
   (map-indexed
     (fn [stop-idx {::transit/keys [arrival-time departure-time pickup-type drop-off-type] :as stop}]
       (let [update! #(e! (rw/->EditStopTime row-idx stop-idx %))
             style {:style {:padding-left     "5px"
                            :padding-right    "5px"
                            :width            "125px"
                            :background-color (if (even? stop-idx)
                                                "#f4f4f4"
                                                "#fafafa")}}]
         (list
           (if (zero? stop-idx)
             ^{:key (str stop-idx "-first")}
             [:td style " - "]
             ^{:key (str stop-idx "-arr")}
             [:td style
              [:div.col-md-11
                [form-fields/field {:type    :time
                                    :required? true
                                    :update! #(update! {::transit/arrival-time %})}
               arrival-time]]
              [:div.col-md-1 {:style {:margin-left "-10px"}}
                [exception-icon e! :arrival drop-off-type stop-idx row-idx]]])
           (if (= stop-idx (dec stop-count))
             ^{:key (str stop-idx "-last")}
             [:td style " - "]
             ^{:key (str stop-idx "-dep")}
             [:td style
              [:div.col-md-11
                [form-fields/field {:type    :time
                                    :required? true
                                    :update! #(update! {::transit/departure-time %})}
                         departure-time]]
              [:div.col-md-1 {:style {:margin-left "-10px"}}
                [exception-icon e! :departure pickup-type stop-idx row-idx]]]))))
     stops)])

(defn trips-list [e! route]
  (let [stop-sequence (::transit/stops route)
        stop-count (count stop-sequence)
        trips (::transit/trips route)
        empty-calendar? (empty? (first (::transit/service-calendars route)))]
    [:div.route-times
     [:div {:style {:overflow "auto"}}
      [:table {:style {:text-align "center"}}
       [route-times-header stop-sequence]
       [:tbody
        (doall (map-indexed (partial trip-row e! stop-count
                                     (:edit-service-calendar route)
                                     (::transit/service-calendars route)) trips))]]]

     (when (:edit-service-calendar route)
       [route-service-calendar/service-calendar e! route])

     (when empty-calendar?
       [:div {:style {:margin-top "10px"}}
        [common/help (tr [:form-help :trip-editor-no-calendar])]])

     [:div
      (tr [:route-wizard-page :trip-schedule-new-trip])
      [form-fields/field {:type :time
                          :update! #(e! (rw/->NewStartTime %))} (:new-start-time route)]
      [ui/raised-button {:style {:margin-left "5px"}
                         :primary true
                         :disabled (or (time/empty-time? (:new-start-time route)) empty-calendar?)
                         :on-click #(e! (rw/->AddTrip))
                         :label (tr [:route-wizard-page :trip-add-new-trip])}]]]))

(defn trips [e! {route :route :as app}]
  (if (empty? (::transit/trips route))
    (e! (rw/->InitRouteTimes))
    (e! (rw/->CalculateRouteTimes)))
  (fn [e! {route :route :as app}]
    [:div {:style {:padding-top "20px"}}
     [:div (stylefy/use-style style-form/form-card)
      [:div (stylefy/use-style style-form/form-card-label) (tr [:route-wizard-page :wizard-step-times])]
      [:div (merge (stylefy/use-style style-form/form-card-body))
       (if (seq (get-in route [::transit/trips 0 ::transit/stop-times]))
         [trips-list e! route]
         [common/help (tr [:form-help :trip-editor-no-stop-sequence])])]]]))
