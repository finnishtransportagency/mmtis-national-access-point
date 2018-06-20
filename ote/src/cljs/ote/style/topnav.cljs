(ns ote.style.topnav
  (:require [stylefy.core :as stylefy]))

(def topnav {:min-height "56px"
             :overflow "hidden"
             :background-color "#323232"
             :box-shadow "0 4px 4px 0 rgba(0, 0, 0, .2)"
             :position "fixed"
             :width "100%"
             :top 0
             :z-index 100
             :transition "all 300ms ease"})

(def topnav-desktop (merge topnav {:height "80px"
                                   :line-height "80px"
                                   :position "fixed"
                                   :width "100%"
                                   :top 0
                                   :z-index 100
                                   :transition "all 300ms ease"}))

(def clear {:clear "both"})

(def ul {:list-style-type "none" })

(def li {:display "inline-block"
         :padding-top "10px"})

(def mobile-li {:padding-top "10px" :padding-bottom "10px"})

(def link {:transition "padding-top 300ms ease, height 300ms ease"
           :bottom "0"
           :color "#ffffff"
           :text-align "center"
           :padding "10px 15px 10px 15px"
           :text-decoration "none"
           :font-size "0.875em"
           ::stylefy/mode {:hover {:background "rgba(0, 0, 0, 0.2)"}}})

(def logo {:transition "margin-top 300ms ease, height 300ms ease"
           :margin-top "10px"
           :margin-left "-8px"
           :display "block"
           :height "40px"
           :flex "0 auto"})

(def logo-small
  (merge logo
         {:transition "margin-top 300ms ease, height 300ms ease"
          :margin-top "16px"
          :height "24px"}))


(def link-left (merge link
                      {:float "left"}))
(def desktop-link
  (merge link-left
         {:padding-top "0px"
          :padding-right "15px"
          :padding-bottom "0px"
          :padding-left "15px"}))

(def active-style {:background "rgba(0, 0, 0, 0.3)"})

(def active (merge link active-style))

(def desktop-active (merge desktop-link active-style))

(def right {:float "right"})
