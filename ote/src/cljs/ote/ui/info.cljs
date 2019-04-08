(ns ote.ui.info
  (:require [ote.mui-wrapper.reagent :as ui]
            [ote.localization :refer [tr tr-key]]
            [stylefy.core :as stylefy]
            [ote.mui-wrapper.icons :as ic]
            [ote.style.base :as style-base]
            [ote.ui.common :as common]
            [reagent.core :as r]
            [ote.style.info-style :as style-info]))

(defn info-toggle
  "Displays a clickable area that opens to show more info. Can be given default parameter for open state."
  ([title inner-component]
   (info-toggle title inner-component true))
  ([title inner-component default-open?]
   (let [is-open? (r/atom default-open?)]
     (fn []
       [:div (stylefy/use-style style-info/info-container)
        [:button (merge (stylefy/use-style style-info/info-button)
                        {:on-click #(swap! is-open? not)})
         [:span (stylefy/use-style style-info/info-icon)
          [ic/action-help]] title]
        [:div
         (if @is-open?
           (stylefy/use-style style-info/info-open)
           (stylefy/use-style style-info/info-closed))
         inner-component]]))))

