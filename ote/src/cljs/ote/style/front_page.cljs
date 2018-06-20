(ns ote.style.front-page
  "Front page styles related to hero images and other front page components"
  (:require
    [stylefy.core :as stylefy :refer [use-style use-sub-style]]))

(def width-xxs 480)
(def width-xs 767)
(def width-sm 991)
(def width-md 1199)
(def width-l 1600)
(def width-xl 2000)

(def hero-img {:height          "540px"
               :margin-top      "-20px"
               :background      "url(/img/hero-2000.png)"
               :background-size "cover"
               ::stylefy/media {{:max-width (str width-xl "px")} {:background      "url(/img/hero-2000.png)"}
                                {:max-width (str width-l "px")}  {:background      "url(/img/hero-1600.png)"}
                                {:max-width (str width-sm "px")} {:background      "url(/img/hero-1080.png)"
                                                                  :height          "371px"}
                                {:max-width (str width-xs "px")} {:background      "url(/img/hero-800.png)"
                                                                  :height          "274px"}}})

(def hero-btn {:padding-top "60px"
               ::stylefy/media {{:max-width (str width-xl "px")} {:padding-top "60px"}
                                {:max-width (str width-l "px")} {:padding-top "60px"}
                                {:max-width (str width-sm "px")} {:padding-top "60px"}
                                {:max-width (str width-xs "px")} {:padding-top "20px"}}})

(def h2 {:font-size "2.25em"
         ::stylefy/media {{:max-width (str width-xs "px")} {:font-size "1.5em"}}})

(def fp-btn-blue {:background-image "linear-gradient(90deg, #06c, #0029b8)"})
(def fp-btn-blue-hover {:background-image "-webkit-linear-gradient(45deg, #06c, #0029b8)"})
(def fp-btn-gray-hover {:background-image "-webkit-linear-gradient(45deg, #ccc, #ccc)"})
(def fp-btn-gray {:background-image "linear-gradient(90deg, #ccc, #ccc)"})
(def fp-btn {:display "flex"
             :padding "20px 20px 20px 10px"
             :-webkit-box-pack "center"
             :-webkit-justify-content "center"
             :-ms-flex-pack "center"
             :justify-content "center"
             :box-shadow "3px 3px 8px 0 rgba(0, 0, 0, .2)"
             :-webkit-backface-visibility "visible"
             :backface-visibility "visible"
             :-webkit-transform-origin "50% 50% 0px"
             :-ms-transform-origin "50% 50% 0px"
             :transform-origin "50% 50% 0px"
             :-webkit-transition "all 300ms ease"
             :transition "all 300ms ease"
             :font-family "Roboto, sans-serif"
             :font-weight "400"
             :font-size "1.1rem"
             :text-align "center"
             :color "#fff"
             :border 0
             :cursor "pointer"
             ::stylefy/mode {:hover {:box-shadow "1px 1px 4px 0 rgba(0, 0, 0, .2)"
                                     :-webkit-transform "scale(0.98)"
                                     :-ms-transform "scale(0.98)"
                                     :transform "scale(0.98)"}}})

(def front-page-button (merge
                         fp-btn-blue
                         fp-btn
                         fp-btn-blue-hover))

(def front-page-button-disabled (merge
                                  fp-btn-gray
                                  fp-btn
                                  fp-btn-gray-hover))

(def row-media {:margin-top "20px"
                :margin-bottom "20px"
                ::stylefy/media {{:max-width (str width-xs "px")}
                                 {:display "flex" :flex-direction "column"}}})

(def large-text-container {:padding-left "60px"
                           ::stylefy/media {{:max-width (str width-xs "px")} {:padding-left "10px"
                                                                              :order 2}}})

(def large-icon-container {:display "-webkit-flex"
                           :flex-direction "column"
                           :align-items "center"
                           ::stylefy/media {{:max-width (str width-xs "px")} {:order 1}}})

(def large-icon  {:width 200
                  :height 200
                  :color "#969696"
                  :text-shadow "0 4px 4px rgba(0, 0, 0, .2)"
                  ::stylefy/media {{:max-width (str width-xs "px")} {:width 150
                                                                     :height 150}}})

(def large-font-icon  {:font-size "14rem"
                       :color "#969696"
                       :text-shadow "0 4px 4px rgba(0, 0, 0, .2)"
                       ::stylefy/media {{:max-width (str width-xs "px")} {:font-size "10rem"}}})

(def front-page-h1  {:position "static"
                      :display "flex"
                      :padding-top "40px"
                      :-webkit-box-orient "vertical"
                      :-webkit-box-direction "normal"
                      :-webkit-flex-direction "column"
                      :flex-direction "column"
                      :-webkit-box-pack "start"
                      :-ms-flex-pack "start"
                      :justify-content "flex-start"
                      :-webkit-box-align "center"
                      :-ms-flex-align "center"
                      :align-items "center"
                      :font-family "Montserrat, sans-serif"
                      :color "#fff"
                      :font-size "6em"
                      :font-weight "200"
                      :text-shadow "0 2px 10px rgba(0, 0, 0, .5)"
                      ::stylefy/media {{:max-width (str width-sm "px")} {:padding-top "20px"
                                                                         :font-size "4em"
                                                                         :font-weight "300"}
                                       {:max-width (str width-xs "px")} {:padding-top "20px"
                                                                         :font-size "2.5em"
                                                                         :font-weight "400"}
                                       {:min-width "0px" :max-width (str width-xxs "px")} {:padding-top "20px"
                                                                                           :font-size "2em"
                                                                                           :font-weight "400"}}})

(def front-page-hero-text {:display "flex"
                           :margin-top "60px"
                           :-webkit-box-orient "vertical"
                           :-webkit-box-direction "normal"
                           :-webkit-flex-direction "column"
                           :-ms-flex-direction "column"
                           :flex-direction "column"
                           :-webkit-box-pack "start"
                           :-webkit-justify-content "flex-start"
                           :-ms-flex-pack "start"
                           :justify-content "flex-start"
                           :v-webkit-box-align "center"
                           :-webkit-align-items "center"
                           :-ms-flex-align "center"
                           :align-items "center"
                           :font-family "Montserrat, sans-serif"
                           :color "#fafafa"
                           :font-size "2.25em"
                           :line-height "1.5em"
                           :font-weight "300"
                           :text-align "center"
                           :text-shadow "0 1px 5px rgba(0, 0, 0, .5)"
                           ::stylefy/media {{:max-width (str width-sm "px")} {:margin-top "40px"
                                                                              :font-size "1.6em"
                                                                              :font-weight "400"}
                                            {:max-width (str width-xs "px")} {:margin-top "20px"
                                                                              :font-size "1.2em"
                                                                              :font-weight "400"}
                                            {:min-width "0px" :max-width (str width-xxs "px")} {:margin-top "20px"
                                                                                                :font-size "1.1em"
                                                                                                :font-weight "400"}}})

(def third-column-text {:margin-bottom 40
                        :color "#c8c8c8"
                        :font-size "0.875em"
                        :font-weight 300
                        :-webkit-align-items "flex-start"})

(def lower-section {:padding-top      "80px"
                    :padding-bottom   "100px"
                    :background-image "linear-gradient(45deg, #ddd, #f8f8f8 46%, #f1f1f1)"
                    :box-shadow       "4px 0 50px 0 rgba(0, 0, 0, .2), 4px 0 20px 0 #fff"})

(def media-transport-service {::stylefy/media {{:max-width (str width-xs "px")} {:padding-top "60px"}}})

(def lower-section-data-container {:display "-webkit-flex" :-webkit-flex-direction "column" :align-items "center"})

(def lower-section-title {:font-size "1.5em"
                          :font-weight 600
                          ::stylefy/media {{:max-width (str width-xs "px")} {:font-size "1.25em"
                                                                             :font-weight 500}}})

(def lower-section-icon  {:width 120
                          :height 120
                          :color "#969696"
                          :text-shadow "0 4px 4px rgba(0, 0, 0, .2)"
                          ::stylefy/media {{:max-width (str width-xs "px")} {:width 80
                                                                             :height 80}}})

(def lower-section-font-icon  {:font-size "8em"
                               :color "#969696"
                               :text-shadow "0 4px 4px rgba(0, 0, 0, .2)"
                               ::stylefy/media {{:max-width (str width-xs "px")} {:font-size "6em"}
                                                {:min-width "0px" :max-width (str width-xxs "px")} {:font-size "5em"}}})

(def lower-section-text {:text-align "center" :font-size "1em" :font-weight 400 :text-aign "left" :line-height "1.5"})

(def footer-logo-ul {
                     ::stylefy/media {{:max-width (str width-xs "px")} {:display "flex"
                                                                        :align-items "center"
                                                                        :width "90px" }}})

(def footer-logo {:width "160px"
                  ::stylefy/media {{:max-width (str width-xs "px")} {:width "90px" }}})

(def footer-small-icon {:position "relative" :height 22 :width 30 :top 5 :color "#fff" :padding-right "10px"})

(def footer-3-container {:padding-top "60px"
                         ::stylefy/media {{:max-width (str width-xs "px")} {:padding-top "40px" }}})