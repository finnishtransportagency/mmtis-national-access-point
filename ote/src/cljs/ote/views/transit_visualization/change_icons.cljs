(ns ote.views.transit-visualization.change_icons
  "Icons related to transit visualization."
  (:require [reagent.core :as r]
            [ote.mui-wrapper.icons :as ic]
            [stylefy.core :as stylefy]
            [ote.style.transit-changes :as style]
            [ote.app.controller.transit-visualization :as tv]
            [ote.style.base :as style-base]
            [ote.ui.icon_labeled :as icon-l]
            [ote.ui.icons :as ote-icons]
            [clojure.string :as str]
            [ote.time :as time]
            [ote.theme.colors :as colors]))

;; Utility methods

(defn format-range [lower upper]
  (if (and (nil? lower) (nil? upper))
    "0"
    (if (or (= lower upper)
            (nil? upper))
      (str lower)
      (str lower "\u2014" upper))))

;; Ui elements
(defn stop-seq-changes-icon [lower upper with-labels?]
  (let [changes (format-range lower upper)]
    [icon-l/icon-labeled
     [ic/action-timeline {:style {:color colors/gray700}}]
     [:span
      changes
      (when with-labels? " pysäkkimuutosta")]]))

(defn stop-time-changes-icon [lower upper with-labels?]
  (let [changes (format-range lower upper)]
    [icon-l/icon-labeled
     [ic/action-query-builder {:color colors/gray700}]
     [:span
      changes
      (when with-labels? " aikataulumuutosta")]]))

(defn change-icons
  ([diff]
   [change-icons diff false])
  ([{:keys [change-type added-trips removed-trips
            trip-stop-sequence-changes-lower trip-stop-sequence-changes-upper
            trip-stop-time-changes-lower trip-stop-time-changes-upper different-week-date] :as diff}
    with-labels?]
   [:div (stylefy/use-style (style-base/flex-container "row"))
    [:div {:style {:width "15%"}}
     [icon-l/icon-labeled
      [ote-icons/outline-add-box {:color (if (= 0 added-trips)
                                           style/no-change-color
                                           style/add-color)}]
      [:span (or added-trips (:gtfs/added-trips diff))      ;; :changes and :changes-route* have different namespace
       (when with-labels? " lisättyä vuoroa")]]]

    [:div {:style {:width "15%"}}
     [icon-l/icon-labeled
      [ote-icons/outline-indeterminate-checkbox {:color (if (= 0 removed-trips)
                                                          style/no-change-color
                                                          style/remove-color)}]
      [:span (or removed-trips (:gtfs/removed-trips diff))
       (when with-labels? " poistettua vuoroa")]]]

    [:div {:style {:width "25%"}}
     [stop-seq-changes-icon trip-stop-sequence-changes-lower trip-stop-sequence-changes-upper with-labels?]]


    [:div {:style {:width "25%"}}
     [stop-time-changes-icon trip-stop-time-changes-lower trip-stop-time-changes-upper with-labels?]]

    ;; Add route ending icon if route ending change is detected
    (when (str/includes? (str change-type) "removed")
      [:div {:style {:width "10%"} :title "Reitti on mahdollisesti päättymässä. Ota yhteyttä liikennöitsijään saadaksesi tarkempia tietoja."}
       [icon-l/icon-labeled
        [ic/content-remove-circle-outline {:color style/remove-color}] nil]])]))