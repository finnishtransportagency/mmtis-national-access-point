(ns ote.views.route.trips
  "Route wizard: route trips table"
  (:require [ote.app.controller.route :as rc]
            [ote.time :as time]
            [ote.ui.form-fields :as form-fields]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]
            [ote.db.transit :as transit]
            [stylefy.core :as stylefy]
            [ote.style.route :as style-route]
            [reagent.core :as r]))

(defn exception-icon [e! stop-type drop-off-type pickup-type stop-idx]
  [:span
   [ui/icon-menu
    {:icon-button-element (r/as-element
                            [ui/icon-button
                             {:style      {:padding 8
                                           :width   24
                                           :height  24}
                              :icon-style {:height 24
                                           :width  24}}
                             (cond
                               (and (nil? drop-off-type) (nil? pickup-type))
                               [ic/maps-pin-drop {:style style-route/exception-icon}]
                               (and (= "arrival" stop-type) (= drop-off-type "reqular")) [ic/maps-pin-drop {:style style-route/selected-exception-icon}]
                               (and (= "departure" stop-type) (= pickup-type "reqular")) [ic/maps-pin-drop {:style style-route/selected-exception-icon}]
                               (and (= "arrival" stop-type) (= drop-off-type "not-available")) [ic/notification-do-not-disturb {:style style-route/selected-exception-icon}]
                               (and (= "departure" stop-type) (= pickup-type "not-available")) [ic/notification-do-not-disturb {:style style-route/selected-exception-icon}]
                               (and (= "arrival" stop-type) (= drop-off-type "phone-agency")) [ic/communication-call {:style style-route/selected-exception-icon}]
                               (and (= "departure" stop-type) (= pickup-type "phone-agency")) [ic/communication-call {:style style-route/selected-exception-icon}]
                               (and (= "arrival" stop-type) (= drop-off-type "coordinate-with-driver")) [ic/social-people {:style style-route/selected-exception-icon}]
                               (and (= "departure" stop-type) (= pickup-type "coordinate-with-driver")) [ic/social-people {:style style-route/selected-exception-icon}]
                               :else [ic/maps-pin-drop {:style style-route/exception-icon}])
                             ])}
    [ui/menu-item {:primary-text (if (= "arrival" stop-type) "Poistumismahdollisuus oletuksena" "Nousu mahdollista oletuksena")
                   :left-icon    (ic/maps-pin-drop)
                   :on-click     #(do
                                    (.preventDefault %)
                                    (e! (rc/->ShowStopException stop-type stop-idx "reqular")))}]
    [ui/menu-item {:primary-text (if (= "arrival" stop-type) "Ei poistumismahdollisuutta" "Ei nousumahdollisuutta")
                   :left-icon    (ic/notification-do-not-disturb)
                   :on-click     #(do
                                    (.preventDefault %)
                                    (e! (rc/->ShowStopException stop-type stop-idx "not-available")))}]
    [ui/menu-item {:primary-text (if (= "arrival" stop-type) "Poistumisesta sovittava palveluntuottajan kanssa" "Nousemisesta sovittava palveluntuottajan kanssa")
                   :left-icon    (ic/communication-call)
                   :on-click     #(do
                                    (.preventDefault %)
                                    (e! (rc/->ShowStopException stop-type stop-idx "phone-agency")))}]
    [ui/menu-item {:primary-text (if (= "arrival" stop-type) "Poistumisesta sovittava kuljettajan kanssa" "Noususta sovittava kuljettajan kanssa")
                   :left-icon    (ic/social-people)
                   :on-click     #(do
                                    (.preventDefault %)
                                    (e! (rc/->ShowStopException stop-type stop-idx "coordinate-with-driver")))}]]])

(defn route-times-header [stop-sequence]
  [:thead
   [:tr
    (doall
     (map-indexed
      (fn [i {::transit/keys [code name]}]
        ^{:key code}
        [:th {:colSpan 2
              :style {:vertical-align "top"}}
         [:div {:style {:display "inline-block"
                        :width "160px"
                        :overflow-x "hidden"
                        :white-space "pre"}} name]
         [:div {:style {:display "inline-block"
                        :float "right"
                        :position "relative"
                        :left 14
                        :top -20}}
          (when (< i (dec (count stop-sequence)))
            [ic/navigation-chevron-right])]])
      stop-sequence))]
   [:tr
    (doall
     (for [{::transit/keys [code name]} stop-sequence]
       (list
        ^{:key (str code "-arr")}
        [:th {:style {:font-size "80%" :font-variant "small-caps"}} "tulo"]
        ^{:key (str code "-dep")}
        [:th {:style {:font-size "80%" :font-variant "small-caps"}} "lähtö"])))]])

(defn trip-row
  "Render a single row of stop times."
  [e! stop-count i {stops ::transit/stop-times :as trip}]
  ^{:key i}
  [:tr
   (map-indexed
     (fn [j {::transit/keys [arrival-time departure-time stop-idx]
             :keys          [pickup-type drop-off-type stop-type] :as stop}]
       (let [update! #(e! (rc/->EditStopTime i j %))
             style {:style {:padding-left     "5px"
                            :padding-right    "5px"
                            :width            "125px"
                            :background-color (if (even? j)
                                                "#f4f4f4"
                                                "#fafafa")}}]
         (list
           (if (zero? j)
             ^{:key (str j "-first")}
             [:td style " - "]
             ^{:key (str j "-arr")}
             [:td style
              [form-fields/field {:type    :time
                                  :update! #(update! {::transit/arrival-time %})}
               arrival-time]
              (exception-icon e! "arrival" pickup-type drop-off-type stop-idx)])
           (if (= j (dec stop-count))
             ^{:key (str j "-last")}
             [:td style " - "]
             ^{:key (str j "-dep")}
             [:td style [form-fields/field {:type    :time
                                            :update! #(update! {::transit/departure-time %})}
                         departure-time]
              (exception-icon e! "departure" pickup-type drop-off-type stop-idx)]))))
     stops)])

(defn trips [e! _]
  (e! (rc/->InitRouteTimes))
  (fn [e! {route :route :as app}]
    (let [stop-sequence (::transit/stops route)
          stop-count (count stop-sequence)
          trips (::transit/trips route)]

      [:div.route-times
       [:table {:style {:text-align "center"}}
        [route-times-header stop-sequence]
        [:tbody
         (doall (map-indexed (partial trip-row e! stop-count) trips))]]
       [:div
        "Uuden vuoron lähtöaika: "
        [form-fields/field {:type :time
                            :update! #(e! (rc/->NewStartTime %))} (:new-start-time route)]
        [ui/raised-button {:primary true
                           :disabled (time/empty-time? (:new-start-time route))
                           :on-click #(e! (rc/->AddTrip))}
         "Lisää vuoro"]]])))
