(ns ote.style.buttons
  (:require
    [stylefy.core :as stylefy]
    [ote.theme.colors :as colors]))

(def action-button-icon
  {:padding "0"
   :margin "0 1em 0 0"
   :color colors/white-basic})

(def outline-btn-hover-focus
  {:text-decoration "none"
   :border-width "2px"
   :box-shadow "1px 1px 2px 0 rgba(0, 0, 0, .2)"
   :padding "19px"
   :transform "scale(0.98)"})

(def primary-btn-hover-focus
  {:text-decoration "none"
   :box-shadow "1px 1px 2px 0 rgba(0, 0, 0, .2)"
   :background-color colors/primary
   :transform "scale(0.98)"})

(def negative-btn-hover-focus
  {:text-decoration "none"
   :box-shadow "0 4px 4px rgba(0,0,0,0.2)"
   :background-color colors/negative-button-hover
   :transform "scale(0.98)"})

(def button-common
  {:padding "20px"
   :height "60px"                                           ;; Hard coded so the height doesn't change with transitions
   :min-width "4rem"
   :display "inline-block"
   :justify-content "center"
   :transition "all 200ms ease"
   :text-decoration "none"
   :box-shadow "4px 4px 8px 0 rgba(0, 0, 0, .2)"
   ::stylefy/vendors ["webkit" "moz" "o"]
   ::stylefy/auto-prefix #{:transition}})

(def disabled-button (merge
                       button-common
                       {:color colors/primary-text
                        :background-color colors/primary-disabled
                        :pointer-events "none"
                        :box-shadow "none"}))

(def negative-button
  (merge button-common
         {:color colors/negative-text
          :border 0
          :background-color colors/negative-button
          ::stylefy/mode {:hover negative-btn-hover-focus
                          :focus negative-btn-hover-focus}}))

(def outline-button
  (merge button-common
         {:background-color "white"
          :color colors/primary
          :border "1px solid"
          :border-color colors/primary
          ::stylefy/mode {:hover outline-btn-hover-focus
                          :focus outline-btn-hover-focus}}))

(def primary-button
  (merge button-common
         {:color colors/primary-text
          :border 0
          :background-color colors/primary-light
          ::stylefy/mode {:hover primary-btn-hover-focus
                          :focus primary-btn-hover-focus}}))

(def svg-button
  {:background-color "transparent"
   :border "none"
   :cursor "pointer"
   ::stylefy/manual [[:&:hover [:svg {:color (str colors/primary " !important")}]]]})
