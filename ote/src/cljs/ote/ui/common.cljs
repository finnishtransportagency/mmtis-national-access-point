(ns ote.ui.common
  "Common small UI utilities"
  (:require [ote.mui-wrapper.icons :as ic]
            [ote.mui-wrapper.reagent :as ui]
            [ote.localization :refer [tr tr-key]]
            [stylefy.core :as stylefy]
            [ote.style.base :as style-base]
            [ote.style.form :as style-form]
            [ote.theme.colors :as colors]
            [ote.ui.icons :as icons]
            [reagent.core :as r]
            [clojure.string :as str]
            [reagent.core :as reagent]

            [goog.crypt.Md5]
            [goog.crypt]

            [ote.util.text :as text]
            [ote.app.routes :as routes]))

(def mobile?
  (let [ua (str/lower-case js/window.navigator.userAgent)]
    (boolean (some (partial str/includes? ua) ["android" "iphone" "ipad" "mobile"]))))

(defn linkify
  ([url label]
   (linkify url label nil))
  ([url label {:keys [icon target style] :as props}]
   (let [a-props (dissoc
                  (if (= target "_blank")
                    ;; https://mathiasbynens.github.io/rel-noopener/ Avoid a browser vulnerability by using noopener noreferrer.
                    (assoc props :rel "noopener noreferrer")
                    props)
                  :icon :style)]

     (if-not url
       [:span]
       ;; Lazy check if url has a protocol, or if it is a relative path
       (let [url (cond
                   ;; URL with protocol, use as is
                   (re-matches #"^(\w+:|.?.?/).*" url)
                   url

                   ;; User specified link without protocol (like "www.serviceprovider.fi/foo")
                   (re-matches #"^[^/]+\..*" url)
                   (str "http://" url)

                   ;; Internal relative link, like "pre-notice/attachment/1"
                   :default
                   url)]
         [:a (merge
               (stylefy/use-style (merge style-base/base-link
                                         (when style
                                           style)))
              {:href url}
              a-props)
          (if icon
            (let [[icon-elt icon-attrs] icon]
              [:span [icon-elt (merge icon-attrs
                                      {:style {:position "relative"
                                               :top "6px"
                                               :padding-right "5px"
                                               :color style-base/link-color}})]
               label])
            label)])))))


(defn tooltip-wrapper
  "Wrap any ui component with balloon.css tooltip bindings."
  [component & [wrapper-opts]]
  (fn [data {:keys [text pos len] :as opts}]
    [:span (merge {:data-balloon        text
                   :data-balloon-pos    (or pos "up")
                   :data-balloon-length (or len "medium")}
                  wrapper-opts)
     (component data)]))

(defn tooltip
  "Render child-component with tooltip"
  [{:keys [text pos len]} child-component]
  [:span {:data-balloon text
          :data-balloon-pos (or pos "up")
          :data-balloon-length (or len "medium")}
   child-component])

(defn dialog
  "Creates a dialog with a link trigger. The body can be in hiccup format."
  [link-label title body]
  (reagent/with-let
    [open? (reagent/atom false)]
    [:span
     [:a {:href "#" :on-click #(do (.preventDefault %)
                                   (reset! open? true))} link-label]
     [ui/dialog
      {:open @open?
       :auto-scroll-body-content true
       :title (or title "")
       :actions [(reagent/as-element [ui/flat-button {:label (tr [:buttons :close])
                                                      :on-click #(reset! open? false)}])]
       :on-request-close #(reset! open? false)}
      body]]))

;; Full width gray generic help box
(defn generic-help [help]
  [:div.help (stylefy/use-style style-base/generic-help)
   [:div (stylefy/use-style style-form/help-text-element) help]])

(defn help [help]
  [:div.help (stylefy/use-style style-base/help)
   [:div (stylefy/use-style style-form/help-text-element) help]])

(defn extended-help-link [help-link help-link-text]
  [:div (stylefy/use-style style-base/help-link-container)
   [:div (stylefy/use-style style-base/link-icon-container)
    [ic/action-open-in-new {:style style-base/link-icon}]]
   [:div
    (linkify help-link help-link-text {:target "_blank"})]])

(defn extended-help
  "Used currently only in passenger transportation form. Give help-text link-text, link address and one form element
   as a parameter."
  [help-text help-link-text help-link component]
  [:div.help (stylefy/use-style style-base/generic-help)
   [:div (stylefy/use-style style-form/help-text-element) help-text]
   [:div (extended-help-link help-link help-link-text)]
   component])



(defn shortened-description [desc max-length]
  (if (< max-length (count desc))
    [:span (text/shorten-text-to max-length desc)]
    [:span desc]))



(defn table2 [& items]
  [:table
   [:tbody
    (map-indexed
     (fn [i [left right]]
       ^{:key i}
       [:tr
        [:td {:style {:vertical-align "top"}} left]
        [:td right]])
     (partition 2 items))]])

(defn scroll-sensor [on-scroll]
  (let [sensor-node (atom nil)
        check-scroll (fn [event]
                       (let [viewport-y-min 0
                             viewport-y-max (.-innerHeight js/window)
                             element-y (.-top (.getBoundingClientRect @sensor-node))]

                         (when (<= viewport-y-min element-y viewport-y-max)
                           (on-scroll))))]

    (r/create-class
     {:component-did-mount
      (fn [this]
        (reset! sensor-node (aget this "refs" "sensor"))
        (.addEventListener js/window "scroll" check-scroll))
      :component-will-unmount
      (fn [this]
        (.removeEventListener js/window "scroll" check-scroll))
      :reagent-render
      (fn [_]
        [:span {:ref "sensor"}])})))

(defn copy-to-clipboard [text-to-copy]
  (let [id (name (gensym "ctc"))
        copy! #(let [elt (.getElementById js/document id)]
                 (.select elt)
                 (.execCommand js/document "Copy"))]
    [:div {:style {:display "inline-block"}}
     [:input {:id id
              :readOnly true
              :on-focus copy!
              :style {:width "250px"}
              :value text-to-copy}]
     [ui/flat-button {:icon (ic/content-content-copy)
                      :on-click copy!}]]))

(defn should-component-update?
  "Helper function to create a :should-component-update lifecycle function.
  Uses get-in to fetch the given accessor paths from both the old and the new
  arguments and returns true if any path's values differ.

  For example if the component has arguments: [e! my-thing foo]
  the path to access key :name from my-thing is: [0 :name]."
  [& accessor-paths]
  (fn [_ old-argv new-argv]
    (let [old-argv  (subvec old-argv 1)
          new-argv (subvec new-argv 1)]
      (boolean
       (some #(not= (get-in old-argv %) (get-in new-argv %))
             accessor-paths)))))


(defn gravatar
  ([email]
   (gravatar {:size 32 :default "mm"} email))
  ([{:keys [size default]} email]
   (let [hash (-> (goog.crypt.Md5.)
                  (doto (.update email))
                  .digest
                  goog.crypt/byteArrayToHex)]
     [:img {:src (str "https://www.gravatar.com/avatar/" hash "?s=" size "&d=" default)}])))

(defn ckan-iframe-dialog
  ([title url on-close]
   (ckan-iframe-dialog title url on-close on-close))
  ([title url on-close on-ckan-close]
   (r/create-class
    {:component-did-mount
     (fn [_]
       (aset js/window "closeOteCkanDialog" on-ckan-close))
     :reagent-render
     (fn [title url on-close _]
       [ui/dialog
        {:open true
         :modal true
         :title   title
         :actions [(r/as-element
                    [ui/flat-button
                     {:label     (tr [:buttons :close])
                      :primary   true
                      :on-click  on-close}])]}
        [:iframe {:style {:width "100%"
                          :height "400px"
                          :border "none"}
                  :src url}]])})))

(defonce keyframes
  (stylefy/keyframes "fade-out"
                     [:from {:opacity 1}]
                     [:to {:opacity 0 :visibility "hidden"}]))


(defn rotate-device-notice []
  (when mobile?
    [:div (stylefy/use-style {:display "flex"
                              :align-items "center"
                              :justify-content "center"
                              :position "fixed"
                              :opacity 1
                              :visibility "visible"
                              :top 0
                              :left 0
                              :height "100%"
                              :width "100%"
                              :margin "auto"
                              :z-index 9999
                              :animation "5s cubic-bezier(0.550, 0.085, 0.680, 0.530) 3s forwards fade-out"
                              ::stylefy/media {{:orientation "landscape"} {:display "none"}}})

     [:div (stylefy/use-style {:display "flex"
                               :align-items "center"
                               :justify-content "space-between"
                               :flex-flow "column"
                               :width "66%"
                               :max-width "400px"
                               :max-height "300px"
                               :text-align "center"
                               :border-radius "15px"
                               :border "1px solid black"
                               :padding "1rem"
                               :background-color "rgba(255, 255, 255, 0.85)"})
      [icons/screen-rotation {:font-size "10rem"
                              :color "#969696"
                              :margin-bottom "1rem"}]
      [:span (tr [:common-texts :rotate-device-90])]]]))

(defn back-link [url label]
  [linkify url [:span [icons/arrow-back {:position "relative"
                                         :top "6px"
                                         :padding-right "5px"
                                         :color style-base/link-color}]
                label]])

;; This is implemented because IE craps itself sometimes with the linkify
(defn back-link-with-event [site-keyword label]
  [:a (merge
        {:href "#/transit-changes"}
        {:on-click #(do
                      (.preventDefault %)
                      (routes/navigate! site-keyword))}
        (stylefy/use-style (merge {:color colors/primary
                                   :text-decoration "none"
                                   ::stylefy/mode {:hover {:text-decoration "underline"}}})))
   [:span [icons/arrow-back {:position "relative"
                             :top "6px"
                             :padding-right "5px"
                             :color style-base/link-color}]
    label]])

(defn loading-spinner []
  [:div.loading [:img {:src "/base/images/loading-spinner.gif"}]])


(defn information-row-default
  ([title information]
   [information-row-default title information {}])
  ([title information {:keys [id] :as options}]
   [:div (merge (when id
                  {:id id})
                (stylefy/use-style style-base/info-row))
    [:strong (stylefy/use-style style-base/info-title)
     title]
    (if information
      [:span (stylefy/use-style style-base/info-content)
       information]
      [:span (stylefy/use-style (merge
                                  style-base/info-content
                                  {:color colors/gray650
                                   :font-style "italic"}))
       (tr [:service-viewer :not-disclosed])])]))


(defn information-row-with-option
  ([title information wide]
   [information-row-with-option title information wide nil])
  ([title information wide {:keys [id] :as options}]
   [:div (merge (when id
                  {:id id})
                (stylefy/use-style style-base/info-row))
    [:strong (stylefy/use-style
               (if wide
                 style-base/info-title-25
                 style-base/info-title-50))
     title]
    (if information
      [:span (stylefy/use-style
               (if wide
                 style-base/info-content-75
                 style-base/info-content-50))
       information]
      [:span (stylefy/use-style (merge
                                  (if wide
                                    style-base/info-content-75
                                    style-base/info-content-50)
                                  {:color colors/gray650
                                   :font-style "italic"}))
       (tr [:service-viewer :not-disclosed])])]))