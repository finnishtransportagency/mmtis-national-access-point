(ns ote.ui.service-calendar-timeline
  ""
  (:require [reagent.core :as r]
            [ote.mui-wrapper.reagent :as ui]
            [cljs-time.core :as t]
            [cljs-time.format :as time-format]
            [ote.localization :as lang]
            [ote.db.transport-service :as t-service]
            [stylefy.core :as stylefy]
            [ote.time :as time]
            [ote.mui-wrapper.icons :as ic]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [goog.events.MouseWheelHandler.EventType :as MEventType]))

(defn month-days [year month]
  (let [first-date (t/first-day-of-the-month year month)
        days (t/number-of-days-in-the-month year month)]

    (map #(t/plus first-date (t/days %))
         (range days))))

(def week-days [:MON :TUE :WED :THU :FRI :SAT :SUN])

(defn- separate-weeks [[d & dates]]
  (when d
    (let [sunday? (= 7 (t/day-of-week d))]
      (cons d
            (if sunday?
              (cons ::week-separator
                    (lazy-seq (separate-weeks dates)))
              (lazy-seq (separate-weeks dates)))))))

(defn- all-days [year]
  (apply concat
         (for [month (range 1 13)]
           (month-days year month))))

(defn- month-name [month]
  (let [lang (.get (goog.net.Cookies. js/document) "finap_lang" "fi")]
    (.toLocaleString (doto (js/Date.) (.setMonth (- month 1))) lang #js {:month "short"})))


(defn svg-bars [items offset bar-width handle-val bar-style]
  [:g
   (doall
     (map-indexed
       (fn [i val]
         (let [w (- bar-width 4)
               x (- (* (+ w 4) i) offset)]
           ^{:key (str "svg-bar-" i)}
           [:svg {:style {:user-select "none"}
                  :width w :height "100%" :x x :y 0}
            ^{:key (str "svg-bar-rect" i)}
            [:rect {:x 0 :y 0 :width w :height 20 :fill (if bar-style
                                                          (bar-style val)
                                                          "green")}]
            ^{:key (str "svg-bar-line" i)}
            [:line {:x1 "50%" :y1 "40%" :x2 "50%" :y2 "20"
                    :stroke "black" :strokeWidth "1"}]
            ^{:key (str "svg-bar-label-box" i)}
            [:rect {:x 0 :y "40%" :width 50 :height 20 :fill "#fff"}]
            ^{:key (str "svg-bar-label" i)}
            [:text {:x "50%" :y "50%" :dy "0" :text-anchor "middle"} (handle-val val)]]))
       items))])

(defn tmp-bar-colors [items transform]
  (zipmap items (take (count items)
                      (cycle (transform ["#52ef99" "#c82565" "#8fec2f" "#8033cb" "#5c922f" "#fe74fe" "#02531d"
                                         "#ec8fb5" "#23dbe1" "#a4515b" "#169294" "#fd5925" "#3d4e92" "#f4d403"
                                         "#66a1e5" "#d07d09" "#9382e9" "#b9cf84" "#544437" "#f2cdb9"])))))

(defn month-bars [months offset bar-width month-style]
  [svg-bars months offset bar-width #(month-name %) month-style])

(defn week-bars [weeks offset bar-width week-style]
  [svg-bars weeks offset bar-width
   #(str "Vko " (t/week-number-of-year (first %)))
   week-style])

(defn day-bars [days offset bar-width day-style]
  [svg-bars days offset bar-width #(str (t/day %) "." (t/month %)) day-style])

(defn timeline [cur-zoom view-offset weeks width height day-style]
  (let [zoom @cur-zoom
        offset (:cur @view-offset)
        bar-offset (* zoom (+ offset (/ width 2)))
        x-scale (max (+ 1 zoom) 1)]
    [:svg {:xmlns "http://www.w3.org/2000/svg"
           :style {:width "100%" :height "100%"}
           :id "service-calendar-timeline"
           :view-box (str offset " 0 " width " " height)}
     [:g {:transform (str "translate(0," (- (/ height 2) 10) ")")}
      (cond
        (< x-scale 4)

        [month-bars (range 1 13) bar-offset (* x-scale
                                               ;; width / 12 months
                                               (/ width 12))
         (tmp-bar-colors (range 1 13) #(take-nth 6 %))]
        (< x-scale 12)
        [week-bars weeks bar-offset (* x-scale
                                       ;; Un-accurately, width / weeks
                                       (/ width 52))
         (tmp-bar-colors weeks #(take-nth 2 %))]
        (> x-scale 12)
        [day-bars (flatten weeks) bar-offset (* x-scale
                                                ;; Un-accurately, width / 365, not taking in
                                                ;; account any exceptions.
                                                (/ width 365))
         ;; Currently, we'll use background color only from the style to fill rects.
         (fn [day]
           (:background-color (day-style day false)))])]]))



(defn- listen-scroll [el handler]
  ;; MouseWheelHandler allows us to catch scroll events in a consistent manner on various platforms.
  (events/listen (events/MouseWheelHandler. el) MEventType/MOUSEWHEEL handler el))

(defn- listen-mouse-up [el handler]
  (events/listen el EventType/MOUSEUP handler el))


(defn handle-scroll! [cur-zoom e]
  (.preventDefault e)

  (let [new-scroll (- (.-deltaY e))
        zoom-speed 0.02
        prev-zoom @cur-zoom
        zoom (+ prev-zoom (* new-scroll (+ 1 prev-zoom) zoom-speed))]
    (reset! cur-zoom (min (max zoom 0) 40))))

(defn- handle-mouse-move! [view-offset cur-zoom drag-start-x e]
  (.preventDefault e)

  (let [mouse-x (.-clientX e)
        target-el (.-currentTarget e)
        client-rect (.getBoundingClientRect target-el)
        client-left (.-left client-rect)
        x (- mouse-x client-left)
        delta (- drag-start-x x)
        prev-offset (:prev @view-offset)
        zoom-factor (+ @cur-zoom 1)]
    (swap! view-offset assoc :cur (+ prev-offset (/ delta zoom-factor)))))

(defn- handle-mouse-up! [el view-offset mouse-move-key mouse-up-atom]
  (swap! view-offset assoc :prev (:cur @view-offset))
  (events/unlistenByKey mouse-move-key)
  (events/unlisten el EventType/MOUSEUP @mouse-up-atom el))

(defn- listen-mouse-move! [el view-offset cur-zoom drag-start-x]
  (let [mouse-move-key (events/listen
                     el
                     EventType/MOUSEMOVE
                     (partial handle-mouse-move! view-offset cur-zoom drag-start-x)
                     el)
        mouse-up-atom (atom nil)
        mouse-up-fn (partial handle-mouse-up! js/window view-offset mouse-move-key mouse-up-atom)]

    ;; Storing a reference to mouse-up-fn, so we can remove the event handler from window after all is done.
    (reset! mouse-up-atom mouse-up-fn)

    (listen-mouse-up js/window mouse-up-fn)
    mouse-move-key))


(defn- handle-mouse-down! [view-offset cur-zoom e]
  (let [target-el (.-currentTarget e)
        mouse-x (.-clientX e)
        client-rect (.getBoundingClientRect target-el)
        client-left (.-left client-rect)
        drag-start-x (- mouse-x client-left)]
    (listen-mouse-move! target-el view-offset cur-zoom drag-start-x)))

(defn service-calendar-year [{:keys [selected-date? on-select on-hover
                                     day-style]} year]
  (let [dimensions (r/atom {:width nil :height nil})
        cur-zoom (r/atom 0)
        view-offset (r/atom {:cur 0 :prev 0})]
    (r/create-class
      {:component-did-mount (fn [this]
                              (let [node (r/dom-node this)
                                    wrapper-el (first (array-seq (.getElementsByClassName
                                                                   node "service-calendar-timeline-wrapper")))]
                                (swap! dimensions assoc
                                       :width (.-clientWidth wrapper-el)
                                       :height (.-clientHeight wrapper-el))
                                (listen-scroll wrapper-el (partial handle-scroll! cur-zoom))))
       :reagent-render
       (fn []
         (let [day-style (or day-style (constantly nil))
               weeks (partition-by (complement #{::week-separator})
                                   (separate-weeks (all-days year)))
               weeks (filter #(not= ::week-separator (first %)) weeks)]
           [:div.service-calendar-year
            [:h3 year]
            [:div {:style {:display "flex" :align-items "center" :justify-content "center"}}
             [:div {:class "service-calendar-timeline-wrapper"
                    :style {:width "100%" :height "200px"
                            :user-select "none" :border "solid 1px black"}
                    :on-mouse-down (partial handle-mouse-down! view-offset cur-zoom)}
              (when (:width @dimensions)
                [timeline cur-zoom view-offset
                 weeks (:width @dimensions) (:height @dimensions) day-style])]]]))})))
