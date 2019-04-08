(ns ote.ui.page
  "Toplevel page components"
  (:require [ote.mui-wrapper.icons :as ic]
            [ote.mui-wrapper.reagent :as ui]
            [ote.localization :refer [tr tr-key]]
            [stylefy.core :as stylefy]
            [ote.style.base :as style-base]
            [ote.style.form :as style-form]
            [ote.style.page :as style]
            [ote.ui.icons :as icons]
            [reagent.core :as r]
            [clojure.string :as str]
            [ote.util.text :as text]))

(defn page-controls
  "Full-width control at the top of the page that has selections that affect
  the rest of the page."
  [top-link title content]
  [:div.page-controls (stylefy/use-style style/page-controls)
   [:div.container (when (not (empty? top-link)) {:style {:padding-top "1rem"}})
    top-link
    [:h1 title]
    [:div.page-controls-content (stylefy/use-style style/content)
     content]]])
