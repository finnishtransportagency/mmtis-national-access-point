(ns ote.views.transit-visualization.change_icons
  "Icons related to transit visualization."
  (:require [reagent.core :as r]
            [cljs-react-material-ui.icons :as ic]
            [stylefy.core :as stylefy]
            [ote.style.base :as style-base]
            [ote.ui.icon_labeled :as icon-l]
            [ote.ui.icons :as ote-icons]
            [clojure.string :as str]
            [ote.theme.colors :as colors]
            [ote.localization :refer [tr]]
            [ote.time :as time]
            [ote.style.transit-changes :as style]))

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

(defn route-change-icons [{:keys [combined-change-types added-trips removed-trips
                                trip-stop-sequence-changes-lower trip-stop-sequence-changes-upper
                                trip-stop-time-changes-lower trip-stop-time-changes-upper] :as grouped-route-data}]
  [:div (stylefy/use-style (style-base/flex-container "row"))
   (if (str/includes? (str combined-change-types) ":added")
     [:div {:style {:flex "1"}}
      [icon-l/icon-labeled
       [ic/content-add-circle-outline {:color colors/add-color}] nil]]
     [:div {:style {:flex "1"}}
      [icon-l/icon-labeled
       [ic/content-add-circle-outline {:color colors/icon-disabled}] nil]])

   (if (and added-trips (pos? added-trips))
     [:div {:style {:flex "1"}}
      [icon-l/icon-labeled
       [ote-icons/outline-add-box]
       [:span added-trips]]]
     [:div {:style {:flex "1"}}
      [icon-l/icon-labeled
       [ote-icons/outline-add-box-gray]
       [:span added-trips]]])

   (if (and removed-trips (pos? removed-trips))
     [:div {:style {:flex "1"}}
      [icon-l/icon-labeled
       [ote-icons/outline-indeterminate-checkbox]
       [:span removed-trips]]]
     [:div {:style {:flex "1"}}
      [icon-l/icon-labeled
       [ote-icons/outline-indeterminate-checkbox-gray] nil]])

   (if (or (and trip-stop-sequence-changes-upper (pos? trip-stop-sequence-changes-upper))
           (and trip-stop-sequence-changes-lower (pos? trip-stop-sequence-changes-lower)))
     [:div {:style {:flex "1"}}
      [icon-l/icon-labeled
       [ic/action-timeline {:style {:color colors/icon-gray}}]
       [:span (or trip-stop-sequence-changes-upper trip-stop-sequence-changes-lower)]]]
     [:div {:style {:flex "1"}}
      [icon-l/icon-labeled
       [ic/action-timeline {:style {:color colors/icon-disabled}}] nil]])

   (if (or (and trip-stop-time-changes-lower (pos? trip-stop-time-changes-lower))
           (and trip-stop-time-changes-upper (pos? trip-stop-time-changes-upper)))
     [:div {:style {:flex "1"}}
      [icon-l/icon-labeled
       [ic/action-query-builder {:style {:color colors/icon-gray}}]
       [:span (or trip-stop-time-changes-upper trip-stop-time-changes-lower)]]]
     [:div {:style {:flex "1"}}
      [icon-l/icon-labeled
       [ic/action-query-builder {:style {:color colors/icon-disabled}}]  nil]])

   (if (str/includes? (str combined-change-types) ":no-traffic")
     [:div {:style {:flex "0.5"}}
      [icon-l/icon-labeled
       [ic/av-not-interested {:color colors/red-darker}] nil]]
     [:div {:style {:flex "0.5"}}
      [icon-l/icon-labeled
       [ic/av-not-interested {:color colors/icon-disabled}] nil]])

   (if (str/includes? (str combined-change-types) ":removed")
     [:div {:style {:flex "0.5"} :title "Reitti on mahdollisesti päättymässä. Ota yhteyttä liikennöitsijään saadaksesi tarkempia tietoja."}
      [icon-l/icon-labeled
       [ic/content-remove-circle-outline {:color colors/red-darker}] nil]]
     [:div {:style {:flex "0.5"}}
      [icon-l/icon-labeled
       [ic/content-remove-circle-outline {:color colors/icon-disabled}] nil]])])

(defn- show-added-trips [diff with-labels?]
  [:div {:style {:flex "1"}}
   [icon-l/icon-labeled
    [ote-icons/outline-add-box {:color (if (= zero? (:added-trips diff))
                                         colors/icon-disabled
                                         colors/add-color)}]
    [:span (or (:added-trips diff)
               0)
     (when with-labels? " lisättyä vuoroa")]]])

(defn- show-removed-trips [diff with-labels?]
  [:div {:style {:flex "1"}}
   [icon-l/icon-labeled
    [ote-icons/outline-indeterminate-checkbox {:color (if (= zero? (:removed-trips diff))
                                                        colors/icon-disabled
                                                        colors/remove-color)}]
    [:span (or (:removed-trips diff)
               0)
     (when with-labels? " poistettua vuoroa")]]])

(defn- show-trip-sequences [diff with-labels? use-flex?]
  [:div (when use-flex? {:style {:flex 1}})
   [stop-seq-changes-icon (:trip-stop-sequence-changes-lower diff) (:trip-stop-sequence-changes-upper diff) with-labels?]])

(defn- show-stop-times
  "This element should be rendered differently every time it is used. Which is really nice. So we need to know how many other elements
  there are in the same row when this is used. This is designed for four elements. When only two elements are in the same row, this element
  can't be as far as it is when there is four."
  [diff with-labels? use-flex?]
  [:div
   (when use-flex? {:style {:flex 1}})
   [stop-time-changes-icon (:trip-stop-time-changes-lower diff) (:trip-stop-time-changes-upper diff) with-labels?]])

(defn change-icons-for-calendar
  ([diff]
   [change-icons-for-calendar diff false])
  ([{:keys [change-type] :as diff}
    with-labels?]
   [:div (stylefy/use-style (style-base/flex-container "row"))
    [show-added-trips diff with-labels?]
    [show-removed-trips diff with-labels?]
    [show-trip-sequences diff with-labels? true]
    [show-stop-times diff with-labels? true]

    (if (str/includes? (str change-type) "no-traffic")
      [:div {:style {:flex "0.5"} :title (tr [:transit-changes :no-traffic])}
       [icon-l/icon-labeled
        [ic/av-not-interested {:color colors/remove-color}] nil]]
      [:div {:style {:flex "0.5"}}])

    (if (str/includes? (str change-type) "removed")
      [:div {:style {:flex "0.4"} :title "Reitti on mahdollisesti päättymässä. Ota yhteyttä liikennöitsijään saadaksesi tarkempia tietoja."}
       [icon-l/icon-labeled
        [ic/content-remove-circle-outline {:color colors/remove-color}] nil]]
      [:div {:style {:flex "0.5"}}])]))

(defn change-icons-for-trips
  ([diff]
   [change-icons-for-trips diff false])
  ([diff with-labels?]
   [:div (stylefy/use-style (style-base/flex-container "row"))
    [show-added-trips diff with-labels?]
    [show-removed-trips diff with-labels?]
    [show-trip-sequences diff with-labels? true]
    [show-stop-times diff with-labels? true]]))

(defn change-icons-for-stops
  ([diff]
   [change-icons-for-stops diff false])
  ([diff with-labels?]
   [:div (stylefy/use-style (style-base/flex-container "row"))
    [show-trip-sequences diff with-labels? false]
    [show-stop-times diff with-labels? false]]))

(defn change-icons-for-dates
  ([compare-data]
   (change-icons-for-dates compare-data nil nil))
  ([compare-data date1-label date2-label]
   [:div (stylefy/use-style (style-base/flex-container "row"))
    [:div {:style {:flex "1"}}
     [:div (stylefy/use-style style/map-different-date1)]
     (time/format-date (:date1 compare-data)) date1-label]
    [:div {:style {:flex "1"}}
     [:div (stylefy/use-style style/map-different-date2)]
     (time/format-date (:date2 compare-data)) date2-label]]))

(defn date-comparison-icons [compare-data]
  (when (seq (:differences compare-data))
    [:div {:style {:padding "0.5rem 0rem 1rem 0rem"}}
     [change-icons-for-trips (:differences compare-data) true]
     [:div {:style {:padding-bottom "1rem"}}]
     (change-icons-for-dates compare-data)]))
