(ns ote.style.form-fields
  (:require [stylefy.core :as stylefy]
            [ote.style.base :as style-base]))

(def localized-text-language-container
  {:height "20px"
   :font-size "12px"
   :margin-top "-4px"})

(def localized-text-language
  {:display "inline-block"
   :font-weight "bold"
   :color "white!important"
   :background-color "#7FD4EF"
   :border-radius "4px"
   :text-align "center"
   :width "20px"
   :height "20px"
   :line-height "20px"
   :text-decoration "none"
   :margin-left "0.5em"
   :cursor "pointer"
   ::stylefy/mode {:hover {:background-color "#00A9DF" :text-decoration "none!important"}}})

(def localized-text-language-selected
  (merge localized-text-language
         {:background-color "#00A9DF"}))

(def checkbox-group-label {:margin-bottom "4px"})

(def table-header {:overflow "visible" :border-bottom "0px solid white"})
(def table-header-row {:overflow "visible" :color "black" :border-bottom "0px solid white"})
(def table-header-column {:overflow "visible" :color "black" :padding "15px 10px 0 0"})
(def table-row-column {:color "black" :overflow "visible" :padding "0 10px 15px 0"})
(def radio-selection {::stylefy/sub-styles
                      {:required (merge style-base/required-element {:margin-top "15px"})}})
