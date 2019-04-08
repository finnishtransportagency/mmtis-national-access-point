(ns ote.ui.service-calendar-compact
  "Dense view for the service calendar component."
  (:require [reagent.core :as r]
            [ote.mui-wrapper.reagent :as ui]
            [cljs-time.core :as t]
            [cljs-time.format :as time-format]
            [ote.localization :as lang]
            [ote.db.transport-service :as t-service]
            [stylefy.core :as stylefy]
            [ote.time :as time]
            [taoensso.timbre :as log]))

(def base-day-style {:width 30
                     :height 30
                     :text-align "center"
                     :border "solid 1px lightgray" ;; "solid 1px black"
                     :cursor "pointer"
                     :user-select "none"})

(def no-day-style (merge base-day-style
                         {:font-size "75%"
                          :color "gray"
                          :background-color "#f9f9f9"}))

(def selected-day-style (merge base-day-style
                               {:background-color "wheat"
                                :font-weight "bold"}))

(def week-separator-style
  {:background "white" ;; "repeating-linear-gradient(45deg, transparent, transparent 3px, #ccc 3px, #ccc 6px)"
   :width "10px"})

(def header-row-style
  {:background-color "#5A5A5A"
   :color "white"
   :height "30px"})

(def header-cell-style
  {:font-weight 100
   :font-size "0.75rem"})

(def month-name-style
  {:font-weight 600
   :font-size "0.75rem"
   :text-transform "capitalize"
   :padding "0px 5px 0px 5px"})


(defn month-days [year month]
  (let [first-date (t/first-day-of-the-month year month)
        days (t/number-of-days-in-the-month year month)]

    (map #(t/plus first-date (t/days %))
         (range days))))

(def week-days [:MON :TUE :WED :THU :FRI :SAT :SUN])

(defn- separate-weeks [[d & dates]]
  (when d
    (let [sunday? (= 7 (t/day-of-week d))]
      (cons d
            (if sunday?
              (cons ::week-separator
                    (lazy-seq (separate-weeks dates)))
              (lazy-seq (separate-weeks dates)))))))

(defn- fill-days [start-date n]
  (doall
   (map-indexed
    (fn [i day]
      (if (= ::week-separator day)
        ^{:key i}
        [:td.week-separator (stylefy/use-style week-separator-style)]
        ^{:key i}
        [:td (stylefy/use-style no-day-style)
         #_(t/day day)]))
     (separate-weeks (map #(t/plus start-date (t/days %))
                          (range n))))))

(defn- week-days-header []
  [:thead
   ;; week days
   [:tr (stylefy/use-style header-row-style)
    [:th " "]
    (doall
     (map-indexed
      (fn [i week-day]
        (if (= week-day ::week-separator)
          ;; Separator for weeks
          ^{:key i}
          [:th " "]

          ;; Normal week day
          ^{:key i}
          [:th (stylefy/use-style header-cell-style)
           (lang/tr [:enums ::t-service/day :short week-day])]))

      ;; Add week separators to repeating list of week days
      (apply concat
             (interleave (partition-all 7 (take 37 (cycle week-days)))
                         (repeat '(::week-separator))))))]])

(defn- month-name [month]
  (let [lang (.get (goog.net.Cookies. js/document) "finap_lang" "fi")]
    (subs
     (.toLocaleString (doto (js/Date.) (.setMonth (- month 1))) lang #js {:month "short"})
     0 3)))

(defn service-calendar-month [{:keys [selected-date? on-select on-hover hover-style
                                      day-style] :as options} year month]
  (r/with-let [hovered-date (r/atom nil)]
    (let [current-hovered-date @hovered-date
          day-style (or day-style (constantly nil))
          start-date (t/first-day-of-the-month year month)
          fill-days-before (dec (t/day-of-week start-date))
          fill-days-after (- 37 (t/number-of-days-in-the-month year month)
                             (dec (t/day-of-week start-date))) ]

      [:tr
       {:on-mouse-out #(reset! hovered-date nil)}
       [:td (stylefy/use-style month-name-style)
        (month-name (t/month start-date))]

       ;; Fill days, so that first week days align
       (fill-days (t/minus start-date (t/days fill-days-before))
                  fill-days-before)

       ;; Cell for each day in the month
       (doall
        (map-indexed
         (fn [i day]
           (if (= ::week-separator day)
             ^{:key i}
             [:td.week-separator (stylefy/use-style week-separator-style)]
             ^{:key i}
             [:td.day
              (let [selected? (selected-date? day)]
                (merge
                 (stylefy/use-style
                  (merge (if selected?
                           selected-day-style
                           base-day-style)
                         (day-style day selected?)
                         (when (and current-hovered-date hover-style (t/equal? current-hovered-date day))
                           (hover-style day))))
                 {:on-mouse-down #(do
                                    (.preventDefault %)
                                    (on-select day))
                  :on-mouse-over #(do
                                    (.preventDefault %)
                                    (cond
                                      (pos? (.-buttons %))
                                      (on-select day)

                                      hover-style
                                      (reset! hovered-date day)

                                      on-hover
                                      (on-hover day)))}))
              (t/day day)]))
         (separate-weeks (month-days year month))))

       ;; Fill days to fill out table
       (fill-days (t/plus (t/last-day-of-the-month year month) (t/days 1))
                  fill-days-after)])))

(defn service-calendar-year [{:keys [selected-date? on-select on-hover hover-style
                                     day-style] :as options} year]
  [:div.service-calendar-year
   [:h3 year]
   [:table

    [week-days-header]

    [:tbody
     (doall
      (for [month (range 1 13)]
        ^{:key month}
        [service-calendar-month options year month]))]]])
