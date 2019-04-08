(ns ote.ui.circular_progress
  (:require [ote.mui-wrapper.reagent :as ui]
            [ote.style.base :as style-base]))

(defn circular-progress [content]
  [:div
   {:style (merge style-base/msg-container style-base/circular-progress)}
   [ui/circular-progress {:style style-base/circular-progress}]
   (when content
     [:div {:style style-base/circular-progress-label}
      content])])
