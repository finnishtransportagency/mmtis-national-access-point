(ns ote.ui.page
  "Toplevel page components"
  (:require [cljs-react-material-ui.icons :as ic]
            [cljs-react-material-ui.reagent :as ui]
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
   [:div.container ; (stylefy/use-style style/content)
    top-link
    [:h1 title]
    [:div.page-controls-content {:style {:padding-bottom "1.25rem"}}
     content]]])
