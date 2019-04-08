(ns ote.views.transit-visualization.change-utilities
  "Helper ui components for transit visualization."
  (:require [reagent.core :as r]
            [ote.mui-wrapper.icons :as ic]
            [stylefy.core :as stylefy]
            [ote.style.transit-changes :as style]
            [ote.app.controller.transit-visualization :as tv]
            [ote.ui.icons :as ote-icons]
            [ote.ui.icon_labeled :as icon-l]))

(defn section [{:keys [open? toggle!]} title help-content body-content]
  [:div.transit-visualization-section (stylefy/use-style (if open?
                                                           style/section
                                                           style/section-closed))
   [:div.transit-visualization-section-title (merge
                                               (stylefy/use-style style/section-title)
                                               {:on-click toggle!})
    [(if open?
       ic/navigation-expand-less
       ic/navigation-expand-more) {:color "white"
                                   :style {:position "relative"
                                           :top "6px"
                                           :margin-right "0.5rem"}}]
    title]
   (when open?
     [:span
      [:div.transit-visualization-section-header (stylefy/use-style style/section-header)
       help-content]
      [:div.transit-visualization-section-body (stylefy/use-style style/section-body)
       body-content]])])

(defn route-changes-legend []
  [:div.transit-changes-legend (stylefy/use-style style/transit-changes-legend-container)
   [:div
    [:b "Taulukon ikonien selitteet"]]
   [:div (stylefy/use-style style/transit-changes-icon-legend-row-container)
    (doall
      (for [[icon color label] [[ote-icons/outline-add-box {} "Uusia vuoroja"]
                                [ote-icons/outline-indeterminate-checkbox {} "Poistuvia vuoroja"]
                                [ic/action-timeline {} "Pysäkkimuutoksia per vuoro"]
                                [ic/action-query-builder {} "Aikataulumuutoksia per vuoro"]
                                [ic/content-remove-circle-outline {:color style/remove-color} "Mahdollisesti päättyvä reitti"]]]
        ^{:key (str "transit-visualization-route-changes-legend-" label)}
        [icon-l/icon-labeled style/transit-changes-icon [icon color] label]))]])