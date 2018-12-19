(ns ote.views.monitor
  (:require [reagent.core :as r]
            [ote.ui.common :as common]
            [ote.app.controller.monitor :as controller]
            [cljsjs.chartjs]))

;; Patterned after the advice at
;; https://github.com/Day8/re-frame/blob/master/docs/Using-Stateful-JS-Components.md

;; todo: use time type x axis

(defn barchart-inner [dom-name data]
  (let [chart (atom nil)
        update (fn [comp]
                 (println "barchart update fn called"))
        stacked-chart-options {:scales {:yAxes [{:stacked true}]
                                        :xAxes [{:stacked true}]}}]
    (r/create-class {:reagent-render (fn []
                                       [:div
                                        [:canvas {:id dom-name :width 400 :height 400}]])
                     :component-did-mount (fn [comp]
                                            (println "did-mount: saaatiin comp" (pr-str comp))
                                            (let [canvas (.getElementById js/document dom-name)
                                                  ;; datasets [{:label "stufs"
                                                  ;;            :data (first (:datasets data))}]                                                  
                                                  config {:type "bar"
                                                          :options stacked-chart-options
                                                          :data {:labels (:labels data)
                                                                 :datasets (:datasets data)}}
                                                  new-chart (js/Chart. canvas (clj->js config))]
                                              (println "using options:" (pr-str (:options config)))
                                              (reset! chart {:chart new-chart :config config})
                                              (update comp)))
                     :component-did-update update})))

(defn doughnut-inner [dom-name data]
  (let [chart (atom nil)
        update (fn [comp]
                 (println "doughnut update fn called"))]
    (r/create-class {:reagent-render (fn []
                                       (println "using dom name" dom-name)
                                       [:div
                                        [:canvas {:id dom-name :width 400 :height 400}]])
                     :component-did-mount (fn [comp]
                                            (println "did-mount: saaatiin comp" (pr-str comp))
                                            (let [canvas (.getElementById js/document dom-name)
                                                  config {:type "doughnut"
                                                          :options {:responsive true
                                                                    ;; :legend {:position "bottom"}
                                                                    }
                                                          :data {:labels (:labels data)
                                                                 :datasets (:datasets data)}}
                                                  new-chart (js/Chart. canvas (clj->js config))]
                                              (println "using options:" (pr-str (:options config)))
                                              (reset! chart {:chart new-chart :config config})
                                              (update comp)))
                     :component-did-update update})))

(defn monitor-main [e! {:keys [page monitor-data] :as app}]
  (e! (controller/->QueryMonitorReport))
  (if monitor-data
    (let [companies-by-month-data {:labels (mapv :month (:monthly-operators monitor-data))
                                   :datasets [{:label "Rekisteröityneitä palveluntuottajia"
                                               :data (mapv :sum (:monthly-operators monitor-data))
                                               :backgroundColor "rgb(0, 170, 187)"}]}
          provider-share-by-type-data {:labels (mapv :sub-type (:operator-types monitor-data))
                                       :datasets [{:data (mapv :count (:operator-types monitor-data))
                                                   :backgroundColor ["red" "orange" "blue"]
                                                   :label "Palvelut tyypeittäin"}]}]
      (fn []
        [:div {:style {:width "50%"}}
         [:p "Liikkumispalveluiden tuottamiseen osallistuvat yritykset"]
         [barchart-inner "chart-companies-by-month" companies-by-month-data]
         [:p "Palveluntuottajien tämän hetkinen lukumäärä liikkumispalvelutyypeittäin"]
         [doughnut-inner "chart-share-by-type" provider-share-by-type-data]
         (println "doughnut data is " (pr-str provider-share-by-type-data))
         [:p "Tuottajien lukumäärä jaoteltuna liikkumispalvelutyypin mukaan"]
         [barchart-inner "chart-type-by-month" (:monthly-types monitor-data)]]))
    [common/loading-spinner]))
