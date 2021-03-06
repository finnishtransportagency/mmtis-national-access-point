(ns ote.ui.icons
  (:require [cljs-react-material-ui.core])
  (:require-macros [ote.ui.icons :refer [define-font-icon define-font-icons]]))

(define-font-icons
  "airport_shuttle"
  "flag"
  "all_out"
  "train"
  "developer_mode"
  "arrow_back"
  "screen_rotation"
  "add_box"
  ;; Add more font icons here
  )

(defn outline-ballot []
  [:img {:src "/img/icons/outline-ballot-24px.svg"}])

(defn outline-ballot-disabled []
  [:img {:src "/img/icons/outline-ballot-disabled-24px.svg"}])

(defn outline-add-box []
  [:img {:src "/img/icons/outline-add-box.svg"}])

(defn outline-add-box-gray []
  [:img {:src "/img/icons/outline-add-box-gray.svg"}])

(defn outline-indeterminate-checkbox []
  [:img {:src "/img/icons/outline-indeterminate-checkbox.svg"}])

(defn outline-indeterminate-checkbox-gray []
  [:img {:src "/img/icons/outline-indeterminate-checkbox-gray.svg"}])

