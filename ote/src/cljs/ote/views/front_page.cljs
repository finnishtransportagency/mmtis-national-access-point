(ns ote.views.front-page
  "Front page for OTE service - Select service type and other basic functionalities"
  (:require [clojure.string :as s]
            [reagent.core :as reagent]
            [ote.mui-wrapper.reagent :as ui]
            [ote.mui-wrapper.icons :as ic]
            [ote.ui.icons :as icons]
            [ote.ui.common :refer [linkify ckan-iframe-dialog]]
            [ote.ui.form :as form]
            [ote.ui.form-groups :as form-groups]
            [ote.ui.buttons :as buttons]
            [ote.app.controller.front-page :as fp]
            [ote.app.controller.login :as login]
            [ote.app.controller.transport-service :as ts]
            [ote.app.controller.transport-operator :as to]
            [ote.views.transport-service :as transport-service]
            [ote.db.common :as common]
            [ote.localization :refer [tr tr-key]]
            [ote.db.transport-service :as t-service]
            [ote.db.transport-operator :as t-operator]
            [ote.db.modification :as modification]
            [ote.time :as time]
            [stylefy.core :as stylefy]
            [ote.style.base :as style-base]
            [ote.style.front-page :as style-front-page]
            [reagent.core :as r]
            [ote.ui.form-fields :as form-fields]
            [ote.ui.common :as ui-common]
            [ote.views.transport-operator-selection :as t-operator-sel]
            [ote.ui.list-header :as list-header]
            [clojure.string :as str]))


(let [host (.-host (.-location js/document))]
  (def test-env? (or (str/includes? host "test")
                     (str/includes? host "localhost"))))

(defn test-env-warning []
  [:div.test-env-warning
   {:style {:margin "0.2em"
            :border "4px dashed red"}}
   [:p {:style {:margin "10px 0px 0px 10px"
                :font-weight "bold"}}
    "TÄMÄ ON TESTIPALVELU!"]
   [:p {:style {:margin "10px"}}
    "Julkinen NAP-palvelukatalogi löytyy osoitteesta: "
    [linkify "https://finap.fi/ote/#/services" "finap.fi"]]
   [:p {:style {:margin "10px"}}
    "Lisätietoa NAP-palvelukatalogin taustoista saat osoitteesta "
    [linkify (tr [:common-texts :footer-livi-url-link])
     (tr [:common-texts :footer-livi-url-link])]]])

(defn front-page
  "Front page info"
  [e! {user :user :as app}]
  [:div
   [:div.hero (stylefy/use-style style-front-page/hero-img)
    [:div.container {:style {:padding-top "20px" }}
     [:h1 (stylefy/use-style style-front-page/front-page-h1) "NAP"]
     [:div (stylefy/use-style style-front-page/front-page-hero-text) (tr [:front-page :hero-title])
      [:div.row (stylefy/use-style style-front-page/hero-btn)
       [:a {:on-click   #(do
                            (.preventDefault %)
                            (e! (fp/->ChangePage :services nil)))}
       [:button (stylefy/use-style style-front-page/transport-service-btn)
        [:span [ic/device-dvr {:style {:height 23 :width 40 :padding-top 0 :color "#fff"}}]]
        (tr [:buttons :transport-service-catalog])]]]]]]

    (when test-env?
     [test-env-warning])

   [:div.container
    [:div.row (stylefy/use-style style-front-page/row-media)
     [:div.col-xs-12.col-sm-3.col-md-3 (stylefy/use-style style-front-page/large-icon-container)
      [icons/all-out style-front-page/large-font-icon]]
     [:div.col-xs-12.col-sm-9.col-md-9 (stylefy/use-style style-front-page/large-text-container)
      [:h2
       (stylefy/use-style style-front-page/h2)
       (tr [:front-page :title-NAP])]
      [:p {:style {:font-size "1em" :font-weight 400 :text-align "left" :line-height "1.5"}}
       (tr [:front-page :column-NAP])]]]

    [:div.row (stylefy/use-style style-front-page/row-media)
     [:div.col-xs-12.col-sm-9.col-md-9 (stylefy/use-style style-front-page/large-text-container)
      [:h2
       (stylefy/use-style style-front-page/h2)
       (tr [:front-page :title-transport-services])]
      [:p {:style {:font-size "1em" :font-weight 400 :text-align "left" :line-height "1.5"}}
       (tr [:front-page :column-transport-services])]]
     [:div.col-xs-12.col-sm-3.col-md-3 (stylefy/use-style style-front-page/large-icon-container)
      [icons/airport-shuttle style-front-page/large-font-icon]]]

    [:div.row (stylefy/use-style style-front-page/row-media)
     [:div.col-xs-12.col-sm-3.col-md-3 (stylefy/use-style style-front-page/large-icon-container)
      [icons/flag style-front-page/large-font-icon]]
     [:div.col-xs-12.col-sm-9.col-md-9 (stylefy/use-style style-front-page/large-text-container)
      [:h2
       (stylefy/use-style style-front-page/h2)
       (tr [:front-page :title-essential-info])]
      [:p {:style {:font-size "1em" :font-weight 400 :text-align "left" :line-height "1.5"}}
       (tr [:front-page :column-essential-info])]]]]

   [:div (stylefy/use-style style-front-page/lower-section)
    [:div.container
     [:div.col-md-6
      [:div (stylefy/use-style style-front-page/lower-section-data-container)
       [icons/train style-front-page/lower-section-font-icon]
       [:h3 (stylefy/use-style style-front-page/lower-section-title) (tr [:front-page :title-transport-operator])]
       [:p (stylefy/use-style style-front-page/lower-section-text)
        (tr [:front-page :column-transport-operator])]

       [:div {:style {:padding-top "20px"}}
        (if (not (get-in app [:user :username]))
          [:a {:style    {:text-decoration "none"}
               :on-click #(do
                            (.preventDefault %)
                            (e! (fp/->ToggleRegistrationDialog)))}
           [:button (stylefy/use-style style-front-page/front-page-button)
            [:span [ic/social-person-add {:style {:height 23 :width 40 :padding-top 0 :color "#fff"}}]]
            (tr [:buttons :register-to-service])]]
          [:div (stylefy/use-style style-front-page/front-page-button-disabled)
            [:span [ic/social-person-add {:style {:height 23 :width 40 :padding-top 0 :color "#fff"}}]]
            (tr [:buttons :register-to-service])])]]]

     [:div.col-md-6 (stylefy/use-style style-front-page/media-transport-service)
      [:div (stylefy/use-style style-front-page/lower-section-data-container)
       [icons/developer-mode style-front-page/lower-section-font-icon]
       [:h3 (stylefy/use-style style-front-page/lower-section-title) (tr [:front-page :title-developer])]
       [:p (stylefy/use-style style-front-page/lower-section-text)
        (tr [:front-page :column-developer])]
       [:div {:style {:padding-top "20px"}}
        [:a {:on-click #(do
                          (.preventDefault %)
                          (e! (fp/->ChangePage :services nil)))}
         [:button (stylefy/use-style style-front-page/front-page-button)
          [:span [ic/device-dvr {:style {:height 23 :width 40 :padding-top 0 :color "#fff"}}]]
          (tr [:buttons :check-out-the-service])]]]]]]]])
