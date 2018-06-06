(ns ote.style.admin
  "Admin panel styles"
  (:require [stylefy.core :as stylefy]
            [garden.units :refer [pt px em]]))

(def modal-data-label {:font-size "1.1em"
                       :color "#323232"
                       :font-weight "400"
                       :text-align "right"
                       :padding-right "20px"})