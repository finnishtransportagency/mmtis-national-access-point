(ns ote.ui.icons
  (:require [clojure.string :as str]))


(defmacro define-font-icon [name]
  (let [fn-name (symbol (str/replace name "_" "-"))]
    `(defn ~fn-name
       ([] (~fn-name {}))
       ([style#]
        [ote.mui-wrapper.core/font-icon {:class-name "material-icons"
                                                :style style#}
         ~name]))))

(defmacro define-font-icons [& names]
  `(do
     ~@(for [name names]
         `(define-font-icon ~name))))
