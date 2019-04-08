(ns ote.ui.warning_msg
  (:require [ote.localization :refer [tr tr-key]]
            [ote.mui-wrapper.icons :as ic]
            [ote.style.base :as style-base]
            [stylefy.core :as stylefy]))

(defn warning-msg [content]
  [:div (stylefy/use-style (merge style-base/msg-container style-base/msg-warning))
   [ic/alert-warning {:style (merge style-base/icon-labeled-icon style-base/msg-warning)}]
   (when content
     [:div content])])

