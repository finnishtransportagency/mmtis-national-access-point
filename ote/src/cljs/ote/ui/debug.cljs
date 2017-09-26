(ns ote.ui.debug
  "UI component for inspecting Clojure data for debug purposes"
  (:require [reagent.core :as r]))

(defn can-open? [item]
  (some #(% item) [map? coll?]))

(defmulti debug-show (fn [item path open-paths toggle!]
                       (cond
                         (map? item) :map
                         (coll? item) :coll
                         :default :pr-str)))

(defn- show-value [value p open-paths toggle!]
  (let [now #(js/Date.now)
        flash (r/atom (now))]
    (fn [value p open-paths toggle!]
      (let [cls (when (> (- (now) @flash) 1000)
                  (js/setTimeout #(reset! flash (now)) 600)
                  "debug-animate")]
        (if (can-open? value)
          [:span {:class cls}
           (if (open-paths p)
             (debug-show value p open-paths toggle!)
             (let [printed (pr-str value)]
               (if (> (count printed) 100)
                 (str (subs printed 0 100) " ...")
                 printed)))]
          [:span {:class cls} (pr-str value)])))))

(defn- open-cell [value p open-paths toggle!]
  (if (can-open? value)
    [:td {:on-click #(toggle! p)}
     (if (open-paths p)
       "\u25bc"
       "\u25b6")]
    [:td " "]))

(defmethod debug-show :coll [data path open-paths toggle!]
  [:table.debug-coll
   [:thead [:th "#"] [:th " "] [:th "Value"]]
   [:tbody
    (doall
     (map-indexed
      (fn [i value]
        ^{:key i}
        [:tr
         [:td i " "]
         (open-cell value (conj path i) open-paths toggle!)
         [:td [show-value value (conj path i) open-paths toggle!]]])
      data))]])

(defmethod debug-show :map [data path open-paths toggle!]
  [:table.debug-map
   [:thead
    [:tr [:th "Key"] [:th " "] [:th "Value"]]]
   [:tbody
    (for [[key value] (sort-by first (seq data))
          :let [p (conj path key)]]
      ^{:key key}
      [:tr
       [:td (pr-str key)]
       (open-cell value p open-paths toggle!)
       [:td
        [show-value value p open-paths toggle!]]])]])

(defmethod debug-show :pr-str [data p _ _]
  [:span (pr-str data)])

(defn debug [item]
  (let [open-paths (r/atom #{})
        toggle! #(swap! open-paths
                        (fn [paths]
                          (if (paths %)
                            (disj paths %)
                            (conj paths %))))]
    (fn [item]
      [:div.debug
       [debug-show item [] @open-paths toggle!]])))
