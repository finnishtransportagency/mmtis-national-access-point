(ns dashboard.view
  (:require [dashboard.ui :as ui]))

(def radiator-item-style
  {:border "solid 1px black"
   :border-radius 5
   :margin 5
   :padding 3
   :text-align "center"
   :background-color "white"})

(defn job-style [result]
  (merge radiator-item-style
         {:width 175 :height 50
          :background (case result
                        "FAILURE" "red"
                        "SUCCESS" "lightgreen"
                        "wheat")}))

(defn jenkins-jobs [jobs]
  [:div.jenkins-jobs {:style {:display "flex" :flex-direction "column" :justify-content "center"}}
   [:div "Jenkins build status"]
   (doall
    (for [{:keys [name lastBuild lastSuccessfulBuild progress] :as job} jobs]
      ^{:key name}
      [:div {:style (job-style (:result lastBuild))}
       [:div name]
       (if progress
         [:progress {:style {:width 150}
                     :value progress
                     :max 100}]
         [:div (.toLocaleString (js/Date. (:timestamp lastBuild)))])]))])

(defn published-services [service-count]
  ^{:key service-count} ;; needed to force new DOM node when data changes (to retrigger animation)
  [:div {:style (merge radiator-item-style {:width 200 :height 75 :text-align "center"})}
   "Published service count"
   [:div.highlight {:style {:font-size "250%"}}
    service-count]])

(defn load-percentage [label {:keys [minimum maximum average]}]
  [:div.load-percentage {:style (merge radiator-item-style
                                       {:height 115 :width 150 :text-align "center"})}
   [:div [:b label] " (5 min)"]

   (when average
     [:div
      [ui/gauge average]])
   [:div {:style {:font-size "85%"}}
    (when minimum
      [:span "MIN: " (str (.toFixed minimum 1) "%")])
    " / "
    (when maximum
      [:span "MAX: " (str (.toFixed maximum 1) "%")])]])

(defn dashboard-view [e! app]
  [:div.dashboard {:style {:display "flex" :flex-direction "row"}}
   [jenkins-jobs (:jenkins app)]
   [published-services (:published-services app)]
   [load-percentage "DB Load" (:db-load app)]])
