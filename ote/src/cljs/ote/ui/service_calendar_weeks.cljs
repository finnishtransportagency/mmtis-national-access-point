(ns ote.ui.service-calendar-weeks
  "Weeks view for the service calendar component."
  (:require [reagent.core :as r]
            [ote.mui-wrapper.reagent :as ui]
            [cljs-time.core :as t]
            [cljs-time.format :as time-format]
            [ote.localization :as lang]
            [ote.db.transport-service :as t-service]
            [stylefy.core :as stylefy]
            [ote.time :as time]
            [ote.mui-wrapper.icons :as ic]))

(def base-day-style {:width 96
                     :height 34
                     :line-height "34px"
                     :text-align "center"
                     :vertical-align "middle"
                     :border "solid 1px black"
                     :cursor "pointer"
                     :user-select "none"})

(def no-day-style (merge base-day-style
                         {:font-size "75%"
                          :color "gray"
                          :background-color "lightGray"}))

(def week-header-item-style {:width 96
                             :height 34
                             :text-align "center"})
(def week-count-style {:width 60
                       :height 34
                       :text-align "center"})

(def selected-day-style (merge base-day-style
                               {:background-color "wheat"
                                :font-weight "bold"}))

(def static-holidays
  [[1 1] [6 1] [1 5] [6 12] [25 12] [26 12]])

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
        ^{:key i}
        [:td (stylefy/use-style no-day-style)
         (t/day day)])
      (separate-weeks (map #(t/plus start-date (t/days %))
                           (range n))))))

(defn- week-days-header []
  ;; week days
  [:tr
   [:td (stylefy/use-style week-count-style) "Vko"]
   (doall
     (map-indexed
       (fn [i week-day]
         (when-not (= week-day ::week-separator)
           ;; Normal week day
           ^{:key i}
           [:td (stylefy/use-style week-header-item-style)
            (lang/tr [:enums ::t-service/day :short week-day])]))

       ;; Add week separators to repeating list of week days
       (apply concat
              (interleave (partition-all 7 week-days)
                          (repeat '(::week-separator))))))])

(defn- all-days [year]
  (apply concat
         (for [month (range 1 13)]
           (month-days year month))))

(defn- month-name [month]
  (let [lang (.get (goog.net.Cookies. js/document) "finap_lang" "fi")]
    (.toLocaleString (doto (js/Date.) (.setMonth (- month 1))) lang #js {:month "short"})))

(defn service-calendar-year [{:keys [selected-date? on-select on-hover
                                     day-style]} year]
  (let [day-style (or day-style (constantly nil))
        weeks (partition-by (complement #{::week-separator})
                            (separate-weeks (all-days year)))
        weeks (filter #(not= ::week-separator (first %)) weeks)
        holidays (map #(t/date-time year (second %) (first %)) static-holidays)]
    [:div.service-calendar-year
     [:h3 year]
     [:div {:style {:display "flex" :align-items "center" :justify-content "center"}}

      (doall
        [:table

         ;; Cell for each day in the month
         (doall
           (map-indexed
             (fn [i week]
               (let [week-num (inc i)]
                 ^{:key (str "week-wrapper-" week-num)}
                 [:tbody
                  (when (= (mod week-num 18) 1)
                    ^{:key (str "week-header-" week-num)}
                    [week-days-header])

                  ^{:key (str "week-" week-num)}
                  [:tr
                   [:td (stylefy/use-style week-count-style)
                    (t/week-number-of-year (first week))]

                   ;; Fill missing days in the first week of the year
                   (when (= week-num 1)
                     (fill-days (t/minus (first week) (t/days (dec (t/day-of-week (first week)))))
                                (dec (t/day-of-week (first week)))))
                   (doall
                     (map-indexed
                       (fn [j day]
                         ^{:key (str "week-" week-num "-day-" j)}
                         [:td.day
                          (let [selected? (selected-date? day)]
                            (merge
                              (stylefy/use-style
                                (merge (if selected?
                                         selected-day-style
                                         base-day-style)
                                       (day-style day selected?)))
                              {:on-mouse-down #(do
                                                 (.preventDefault %)
                                                 (on-select day))
                               :on-mouse-over #(do
                                                 (.preventDefault %)
                                                 (cond
                                                   (pos? (.-buttons %))
                                                   (on-select day)

                                                   on-hover
                                                   (on-hover day)))}))

                          (str (t/day day) "." (t/month day))
                          (when (some #(t/equal? % day) holidays)
                            [ic/toggle-star {:style {:position "relative"
                                                     :top 3 :width 20 :height 20}}])])
                       week))]]))
             weeks))])]]))
