(ns ote.ui.list-header
  "Header component for OTE list pages"
  (:require [ote.mui-wrapper.reagent :as ui]
            [ote.ui.common :as common]
            [stylefy.core :as stylefy]
            [ote.style.base :as style-base]))

(defn header [app title add-button operator-selection user-guide-button]
  [:div
   (when title
     [:div.row (stylefy/use-style style-base/mobile-extra-padding) [:h1 title]])
   (when user-guide-button
     [:div.row user-guide-button])
   (when operator-selection
     [:div.row operator-selection])
   (when add-button
     [:div.row add-button])])