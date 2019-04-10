(ns ote.ui.icons
  (:require [clojure.string :as str]
            [clojure.string :as string]))


(defn kebab-case
  "Converts CamelCase / camelCase to kebab-case"
  [s]
  (str/join "-" (map str/lower-case (re-seq #"\w[a-z]+" s))))

(defmacro define-font-icon [name]
  (let [fn-name (symbol (str/replace name "_" "-"))]
    `(defn ~fn-name
       ([] (~fn-name {}))
       ([style#]
        [ote.mui-wrapper.core/font-icon {:class-name "material-icons"
                                                :style style#}
         ~name]))))


(defmacro define-font-icon2 [name]
  (let [fn-name (symbol (kebab-case name))
        mui-name (symbol (str "muic/" name))]
    `(defn ~fn-name
       [& args#]
       (vec (concat [:> ~mui-name] args#)))))

(defmacro define-font-icons [& names]
  `(do
     ~@(for [name names]
         `(define-font-icon2 ~name))))
