(ns ote.style.tabs
  "Styles for own tabs component"
  (:require [stylefy.core :as stylefy]
            [ote.theme.colors :as colors]))


(def tab {:font-weight "bold"
          :font-size "1rem"
          :white-space "nowrap"
          :text-align "center"
          :padding "10px 20px 10px 20px"
          :width "100%"
          :border-bottom (str "3px solid" colors/gray300)
          ::stylefy/mode {:hover {:border-bottom "4px solid #1976d2"}}})

(def tab-selected (merge tab {:border-bottom "4px solid #1976d2"
                              :color colors/primary}))

(def grey-border {:width "100%"
                  :height "2px"
                  :background-color colors/gray300
                  :z-index 1})