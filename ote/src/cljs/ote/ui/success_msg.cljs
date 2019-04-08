(ns ote.ui.success_msg
  (:require [ote.localization :refer [tr tr-key]]
            [ote.mui-wrapper.icons :as ic]
            [ote.style.base :as style-base]
            [stylefy.core :as stylefy]))
(defn success-msg [content]
  [:div (stylefy/use-style (merge style-base/msg-container style-base/msg-success))
   [ic/action-check-circle {:style (merge style-base/icon-labeled-icon style-base/msg-success)}]
   (when content [:div {:style style-base/icon-labeled-icon} content])])

