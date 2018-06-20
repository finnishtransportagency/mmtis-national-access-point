(ns ote.views.transit-changes
  "Transit changes view. Shows when regular route based traffic schedules
  change with links to detailed view (transit visualization)"
  (:require [reagent.core :as r]
            [ote.app.controller.transit-changes :as tc]
            [ote.ui.common :as common]
            [ote.ui.table :as table]
            [clojure.string :as str]
            [ote.time :as time]
            [cljs-time.core :as t]
            [ote.localization :refer [tr]]
            [cljs-react-material-ui.reagent :as ui]
            [ote.app.controller.front-page :as fp]
            [ote.views.pre-notices.authority-listing :as pre-notices-authority-listing]
            [cljs-react-material-ui.icons :as ic]
            [ote.style.transit-changes :as style]
            [stylefy.core :refer [use-style]]
            [stylefy.core :as stylefy]))

(defn week-day-short [week-day]
  (tr [:enums :ote.db.transport-service/day :short
       (case week-day
         :monday :MON
         :tuesday :TUE
         :wednesday :WED
         :thursday :THU
         :friday :FRI
         :saturday :SAT
         :sunday :SUN)]))


(defn- change-description [{:keys [next-different-week week-start-date diff-week-start-date diff-days]}]
  (when next-different-week
    (let [{:keys [current-week different-week current-week-traffic
                  different-week-traffic change-date]} next-different-week
          current-week-start (time/format-date-opt week-start-date)
          diff-week-start (time/format-date-opt diff-week-start-date)]
      [:div
       [:span (str diff-week-start " alkava viikko (" different-week ") eroaa " current-week-start " alkaneesta viikosta ("
                   current-week "). \n")]
       [:span (str
                (cond
                  (and (not (empty? (:days-with-traffic current-week-traffic)))
                       (empty? (:days-with-traffic different-week-traffic)))
                  "Mahdollinen liikennöinnin päättyminen"

                  (and (empty? (:days-with-traffic current-week-traffic))
                       (not (empty? (:days-with-traffic different-week-traffic))))
                  "Mahdollinen liikennöinnin alkaminen"

                  :default
                  (str "Eri liikennöinti päivinä "
                       (str/join ", "
                                 (mapv week-day-short diff-days)) ".")))]])))


(defn transit-changes-legend []
  [:div.transit-changes-legend (use-style style/transit-changes-legend)
   [:div [:b "Taulukon muutosikonien selitteet"]]
   (for [[icon label] [[ic/content-add-circle-outline " Uusia reittejä"]
                       [ic/content-remove-circle-outline " Päättyviä reittejä"]
                       [ic/editor-format-list-bulleted " Uusia/vähennettyjä vuoroja"]
                       [ic/action-timeline " Pysäkkimuutoksia"]
                       [ic/action-schedule " Aikataulumuutoksia"]]]
     ^{:key label}
     [:div (use-style style/transit-changes-legend-icon)
      [icon]
      [:div (use-style style/change-icon-value) label]])])

(def change-keys #{:routes-added :routes-removed
                   :stop-sequence-changes :trip-count-difference
                   :stop-time-changes})

(defn cap-number [n]
  [:div (use-style style/change-icon-value)
   (if (> n 500)
     "500+"
     (str n))])

(defn change-icons [{:keys [routes-added routes-removed
                            stop-sequence-changes trip-count-difference
                            stop-time-changes]}]
  [:div.transit-change-icons
   [:div (use-style style/transit-changes-legend-icon)
    [ic/content-add-circle-outline {:color (if (= 0 routes-added)
                                             style/no-change-color
                                             style/add-color)}] (cap-number routes-added)]
   [:div (use-style style/transit-changes-legend-icon)
    [ic/content-remove-circle-outline {:color (if (= 0 routes-removed)
                                                style/no-change-color
                                                style/remove-color)}] (cap-number routes-removed)]
   [:div (use-style style/transit-changes-legend-icon)
    [ic/editor-format-list-bulleted {:color
                                     (cond (neg? trip-count-difference) style/remove-color
                                           (pos? trip-count-difference) style/add-color
                                           :default style/no-change-color)}]
    (cap-number trip-count-difference)]
   [:div (use-style style/transit-changes-legend-icon)
    [ic/action-timeline] (cap-number stop-sequence-changes)]
   [:div (use-style style/transit-changes-legend-icon)
    [ic/action-schedule] (cap-number stop-time-changes)]])


(defn detected-transit-changes [e! {:keys [loading? changes] :as transit-changes}]
  [:div.transit-changes
   [:h3 "Säännöllisen markkinaehtoisen reittiliikenteen tulevat muutokset"]
   [:p
    "Taulukossa on listattu " [:b "säännöllisen aikataulun mukaisen liikenteen"]
    " palveluista havaittuja muutoksia. "
    "Voit tarkastella yksittäisessä palvelussa tapahtuvia muutoksia yksityiskohtaisemmin napsauttamalla taulukon riviä. "
    "Yksyityiskohtaiset tiedot avautuvat erilliseen näkymään."]
   [transit-changes-legend]
   [table/table {:no-rows-message (if loading?
                                    "Ladataan muutoksia, odota hetki..."
                                    "Ei löydettyjä muutoksia")
                 :name->label str
                 :stripedRows    true
                 :row-style {:cursor "pointer"}
                 :show-row-hover? true
                 :on-select (fn [evt]
                              (let [change (first evt)
                                    {date1 :date1 date2 :date2} (:first-diff-dates change)]
                                (e! (tc/->ShowChangesForOperator (:transport-operator-id change)
                                                                 (time/format-date-opt date1) (time/format-date-opt date2)))))}
    [{:name "Palveluntuottaja" :read :transport-operator-name :width "20%"}
     {:name "Palvelu" :read :transport-service-name :width "20%"}
     {:name "Aikaa muutokseen" :width "10%"
      :read (comp :change-date :next-different-week)
      :format #(str (t/in-days (t/interval (t/now) %)) " pv")}
     {:name "Muutokset" :read #(select-keys % change-keys) :width "50%"
      :format change-icons
      :col-style {:white-space "pre-wrap"}}]
    changes]])

(defn transit-changes [e! {:keys [page transit-changes] :as app}]
  [ui/tabs {:value (name page)
            :on-change #(e! (fp/->ChangePage (keyword %) nil))}
   [ui/tab {:label "Lomakeilmoitukset" :value "authority-pre-notices"}
    [pre-notices-authority-listing/pre-notices e! app]]
   [ui/tab {:label "Tunnistetut muutokset" :value "transit-changes"}
    [detected-transit-changes e! transit-changes]]])
