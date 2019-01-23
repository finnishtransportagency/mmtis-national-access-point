(ns ote.ui.buttons
  (:require [cljs-react-material-ui.reagent :as ui]
            [ote.localization :refer [tr tr-key]]
            [cljs-react-material-ui.core :refer [color]]
            [stylefy.core :as stylefy]
            [ote.style.base :as style-base]
            [ote.style.buttons :as style-buttons]
            [ote.style.buttons :as style-btns]
            [ote.ui.common :as common]))

(defn- button-container [button]
  [:div (stylefy/use-style style-base/action-button-container)
   button])

(defn save [opts label]
  [button-container
   [:button (merge opts
                   (if (:disabled opts)
                     (stylefy/use-style style-btns/disabled-button)
                     (stylefy/use-style style-btns/primary-button)))
    label]])

(defn cancel [opts label]
  [button-container
   [:button (merge opts
                   (if (:disabled opts)
                     (stylefy/use-style style-btns/disabled-button)
                     (stylefy/use-style style-btns/outline-button)))
    label]])

(defn delete [opts label] ;; TODO: this could be removed after :open-ytj-integration taken into use
  [button-container
   [:button (merge opts
                   (if (:disabled opts)
                     (stylefy/use-style style-btns/disabled-button)
                     (stylefy/use-style style-btns/primary-button))) label]])
(defn open-link
  "Create button like linkify link"
  [url label]
  [button-container
   (common/linkify url
                   [ui/flat-button {:label label :primary true}]
                   {:target "_blank"})])

(defn icon-button
  [opts label]
  [:button.unstyled-button (merge
             (stylefy/use-style style-buttons/svg-button)
             opts)
   label])
