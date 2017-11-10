(ns ote.views.main
  "OTE-sovelluksen päänäkymä"
  (:require [reagent.core :as r]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.core :refer [color]]
            [cljs-react-material-ui.icons :as ic]
            [ote.views.transport-operator :as to]
            [ote.views.front-page :as fp]
            [ote.app.controller.front-page :as fp-controller]
            [ote.views.transport-service :as t-service]
            [ote.views.passenger-transportation :as pt]
            [ote.views.terminal :as terminal]
            [ote.views.rental :as rental]
            [ote.views.parking :as parking]
            [ote.views.brokerage :as brokerage]
            [ote.localization :refer [tr tr-key] :as localization]
            [ote.views.place-search :as place-search]
            [ote.ui.debug :as debug]
            [stylefy.core :as stylefy]
            [ote.style.base :as style-base]
            [ote.app.controller.transport-service :as ts]
            [ote.views.theme :refer [theme]]
            [ote.views.service-search :as service-search]))

(defn- is-topnav-active [give-page nav-page]
  (when (= give-page nav-page)
    "active"))

(defn- is-user-menu-active [app]
  (when (= true (get-in app [:ote-service-flags :user-menu-open]))
    "active"))

 (defn user-menu [e! user-name]
   [ui/select-field
    {:value 0 ;; Force user-name to be selected at all times
     :label-style {:color "#f2f2f2"}
     :on-click #(e! (fp-controller/->OpenUserMenu))
     :anchor-origin {:horizontal "right" :vertical "bottom"}
     :target-origin {:horizontal "right" :vertical "top"}
     }
    [ui/menu-item {:value 0 ;; First and only element that can be selected in this select field
                   :primary-text user-name
                   :selected-text-color (color :grey900)
                   :on-click #(e! (fp-controller/->ChangePage :front-page))} ]
    [ui/menu-item {:primary-text "Näytä profiili"
                   :on-click (fn [_] (set! (.-location js/document) "/"))} ] ;; TODO: Fixme
    [ui/menu-item {:primary-text (tr [:common-texts :user-menu-service-guide])
                   :selected-text-color (color :grey900)
                   :on-click #(e! (fp-controller/->ChangePage :front-page))} ]
    [ui/menu-item {:primary-text (tr [:common-texts :user-menu-service-operator])
                   :selected-text-color (color :grey900)
                   :on-click #(e! (fp-controller/->ChangePage :transport-operator))} ]
    [ui/menu-item {:primary-text " Näytä debug state"
                   :selected-text-color (color :grey900)
                   :on-click #(e! (fp-controller/->ToggleDebugState))} ]
    [ui/menu-item {:primary-text "Siirry NAP -palveluun"
                  :on-click (fn [_] (set! (.-location js/document) "/"))} ] ;; TODO: Fixme
    [ui/menu-item {:primary-text "Kirjaudu ulos"
                   :selected-text-color (color :grey900)
                   :on-click (fn [_] (set! (.-location js/document) "/user/_logout"))} ]])

(defn- flash-message [msg]
  [ui/snackbar {:open (boolean msg)
                :message (or msg "")
                :style style-base/flash-message
                :auto-hide-duration 5000}])

(defn- top-nav-links [e! app]

  ;(.log js/console " when " (get app :width))
  [:div
  [:ul
   [:li
    [:a.main-icon {:href     "#"
                   :on-click (fn [_] (set! (.-location js/document) "/"))}
     [:img {:src "img/icons/nap-logo.svg"}]]]
  [:li
   [:a.ote-nav {:class    (is-topnav-active :front-page (:page app))
                :href     "#"
                :on-click (fn [_] (set! (.-location js/document) "/"))}
    (tr [:common-texts :navigation-front-page])]]
  [:li
   [:a.ote-nav {:class    (is-topnav-active :front-page (:page app))
                :href     "#"
                :on-click (fn [_] (set! (.-location js/document) "/dataset"))}
    "Palvelukatalogi"]]
  [:li
   [:a.ote-nav {:class    (is-topnav-active :front-page (:page app))
                :href     "#"
                :on-click (fn [_] (set! (.-location js/document) "/organization"))}
    "Palveluntuottajat"]]
  [:li
   [:a.ote-nav {:class    (is-topnav-active :own-services (:page app))
                :href     "#"
                :on-click #(e! (fp-controller/->ChangePage :own-services))}
    (tr [:common-texts :navigation-own-service-list])]]
   ]
  [:div.user-menu {:class (is-user-menu-active app)}
   (r/as-element (user-menu e! (get-in app [:user :name])))]
   ]
  )

(defn- mobile-top-nav-links [e! app]
  [:ul
    [:li
      [:a.main-icon {:href     "#"
                     :on-click (fn [_] (set! (.-location js/document) "/"))}
       [:img {:src "img/icons/nap-logo.svg"}]]
     [:li {:style {:float "right"}}
      [ui/icon-button {:on-click #(e! (ts/->ModifyTransportService nil))
                       :style {:padding 8
                               :width 60
                               :height 60}
                       :icon-style {:height 40
                                    :width 40}}
       [ic/action-reorder {:style {:color "#fff"
                                   :width 40
                                   :height 40
                                   }}]]]
     ]]

  )


(defn- top-nav [e! app]
  [:div {:class "topnav"}
   [:div.container-fluid

      (if (< 900 (get app :width))
        (top-nav-links e! app)
        (mobile-top-nav-links e! app)
      )
    ]])



(defn ote-application
  "OTE application main view"
  [e! app]

  ;; init - Get operator and service data from DB when refresh or on usage start
  (e! (fp-controller/->GetTransportOperatorData))

  (fn [e! app]
  [:div {:style (stylefy/use-style style-base/body)}
   [theme
    [:div.ote-sovellus
     (top-nav e! app)
     ;; NOTE: debug state is VERY slow if app state is HUGE
     ;; (it tries to pr-str it)

     [:div.container-fluid
      (case (:page app)
        :front-page [fp/front-page e! app]
        :own-services [fp/own-services e! app]
        :transport-service [t-service/select-service-type e! (:transport-service app)]
        :transport-operator [to/operator e! (:transport-operator app)]
        :passenger-transportation [pt/passenger-transportation-info e! (:transport-service app)]
        :terminal [terminal/terminal e! (:transport-service app)]
        :rentals [rental/rental e! (:transport-service app)]
        :parking [parking/parking e! (:transport-service app)]
        :brokerage [brokerage/brokerage e! (:transport-service app)]
        :edit-service [t-service/edit-service e! app]
        :services [service-search/service-search e! (:service-search app)]
        [:div "ERROR: no such page " (pr-str (:page app))])
      ]

     (when-let [msg (:flash-message app)] [flash-message msg])

     (when (= true (get-in app [:ote-service-flags :show-debug]))
       [:div.row
        [debug/debug app]])

     [:footer.site-footer
      [:div.container-fluid
       [:div.row
        [:div.col-md-2.footer-links
          [:a.logo {:href "#" }
            [:img {:src "/livi_logo_valkoinen.svg" :alt "Liikennevirasto Logo"}]]]
        [:div.col-md-8.footer-links
         [:ul.unstyled
          [:li
           [:a {:href "https://www.liikennevirasto.fi/"} "Liikennevirasto.fi"]]]]
        [:div.col-md-2.attribution
         [:p
          [:strong "Toteutettu" ]
          [:a.hide-text.ckan-footer-logo {:href "http://ckan.org"} "CKAN-ohjelmistolla"]]

         ]]]]

     ]]]




    ))
