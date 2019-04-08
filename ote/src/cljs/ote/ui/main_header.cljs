(ns ote.ui.main-header
  "Main header of the OTE app"
  (:require [reagent.core :as r]
            [ote.mui-wrapper.reagent :as ui]
            [ote.mui-wrapper.core :refer [color]]
            [ote.mui-wrapper.icons :as ic]
            [ote.ui.common :refer [linkify ckan-iframe-dialog]]
            [ote.views.transport-operator :as to]
            [ote.views.front-page :as fp]
            [ote.app.controller.front-page :as fp-controller]
            [ote.views.footer :as footer]
            [ote.localization :refer [tr tr-key]]
            [stylefy.core :as stylefy]
            [ote.style.base :as style-base]
            [ote.style.topnav :as style-topnav]
            [ote.views.theme :refer [theme]]
            [ote.app.controller.login :as login]
            [ote.app.controller.flags :as flags]
            [clojure.string :as str]
            [ote.localization :as localization]
            [ote.util.text :as text]))

(defn header-scroll-sensor [is-scrolled? trigger-offset]
  (let [sensor-node (atom nil)
        check-scroll (fn []
                       (let [element-y (.-top (.getBoundingClientRect @sensor-node))]
                         (reset! is-scrolled? (< element-y trigger-offset))))]

    (r/create-class
      {:component-did-mount
       (fn [this]
         (reset! sensor-node (aget this "refs" "sensor"))
         (check-scroll)
         (.addEventListener js/window "scroll" check-scroll))
       :component-will-unmount
       (fn [this]
         (.removeEventListener js/window "scroll" check-scroll))
       :reagent-render
       (fn [_]
         [:span {:ref "sensor"}])})))

(defn esc-press-listener [e! app]
  "Listens to keydown events on document. If esc is clicked call CloseHeaderMenus"
  (let [esc-press (fn [event]
                      (if (= event.keyCode 27)
                        (e! (fp-controller/->CloseHeaderMenus))))]
    (r/create-class
      {:component-did-mount
       (fn [_]
         (.addEventListener js/document "keydown" #(esc-press %)))
       :component-will-unmount
       (fn [_]
         (.removeEventListener js/document "keydown" #(esc-press %)))
       :reagent-render
       (fn [_]
         [:span {:ref "clicksensor"}])})))

(defn logged-in? [app]
  (not-empty (get-in app [:user :username])))

(defn- is-user-menu-active [app]
  (when (= true (get-in app [:ote-service-flags :user-menu-open]))
    "active"))

(defn page-active?
  "Return true if given current-page belongs to given page-group"
  [page-group current-page pages]
  (cond
    (= page-group :front-page current-page) true
    (page-group pages) ((page-group pages) current-page)
    :default false))

(defn- lang-menu [e! app]
  (let [header-open? (get-in app [:ote-service-flags :lang-menu-open])]
    [:div {:style (merge style-topnav/topnav-dropdown
                         (if header-open?
                           {:opacity    1
                            :visibility "visible"}
                           {:opacity    0
                            :visibility "hidden"
                            ;; Remove the element from normal document flow, by setting position absolute.
                            :position   "absolute"}))}
     [:div.container
      [:div.row
       [:div.col-sm-4.col-md-4]
       [:div.col-sm-4.col-md-4]
       [:div.col-sm-8.col-md-4
        [:ul (stylefy/use-style style-topnav/ul)
         (doall
           (for [[lang flag] footer/selectable-languages]
             ^{:key (str "link_"(name lang) "_" flag)}
             [:li
              [:a (merge
                    (stylefy/use-style style-topnav/topnav-dropdown-link)
                    {:key      lang
                     :href     "#"
                     :on-click #(do
                                  (.preventDefault %)
                                  (e! (fp-controller/->OpenLangMenu))
                                  (e! (fp-controller/->SetLanguage lang)))})
               (str (str/upper-case lang) " - " flag)]]))]]]]]))

(defn- user-menu [e! app]
  (when (get-in app [:user :username])
    (let [header-open? (get-in app [:ote-service-flags :user-menu-open])]
      [:div {:style (merge style-topnav/topnav-dropdown
                           (if header-open?
                             {:opacity    1
                              :visibility "visible"}
                             {:opacity    0
                              :visibility "hidden"
                              ;; Remove the element from normal document flow, by setting position absolute.
                              :position   "absolute"}))}
       [:div.container.user-menu
        [:div.row
         [:div.col-sm-2.col-md-4]
         [:div.col-sm-2.col-md-4]
         [:div.col-sm-8.col-md-4
          [:ul (stylefy/use-style style-topnav/ul)
           (when (get-in app [:user :transit-authority?])
             [:li
              [:a (merge (stylefy/use-style
                           style-topnav/topnav-dropdown-link)
                         {:href     "#/email-settings"
                          :on-click #(e! (fp-controller/->OpenUserMenu))})
               (tr [:common-texts :navigation-email-notification-settings])]])
           [:li
            [:a (merge (stylefy/use-style
                         style-topnav/topnav-dropdown-link)
                       {:href     "#/user"
                        :on-click #(e! (fp-controller/->OpenUserMenu))})
             (tr [:common-texts :user-menu-profile])]]
           [:li
            [:a (merge (stylefy/use-style
                         style-topnav/topnav-dropdown-link)
                       {:href     "#"
                        :on-click #(do (.preventDefault %)
                                       (e! (fp-controller/->OpenUserMenu))
                                       (e! (login/->Logout)))})
             (tr [:common-texts :user-menu-log-out])]]]]]]])))

(defn- top-nav-drop-down-menu [e! app is-scrolled? pages]
  (let [header-open? (get-in app [:ote-service-flags :header-open])]
    [:div {:style (merge style-topnav/topnav-dropdown
                         (if header-open?
                           {:opacity 1
                            :visibility "visible"}
                           {:opacity 0
                            :visibility "hidden"
                            ;; Remove the element from normal document flow, by setting position absolute.
                            :position "absolute"}))}
     [:div.container.general-menu
      [:div.row
       [:div.col-sm-4.col-md-4
        [:ul (stylefy/use-style style-topnav/ul)
         [:li
          [:a (merge (stylefy/use-style
                       style-topnav/topnav-dropdown-link)
                     {:href "#/"
                      :on-click #(e! (fp-controller/->OpenHeader))})
           (tr [:common-texts :navigation-front-page])]]
         [:li
          [:a (merge (stylefy/use-style
                       style-topnav/topnav-dropdown-link)
                     {:href "#/services"
                      :on-click #(e! (fp-controller/->OpenHeader))})
           (tr [:document-title :services])]]
         (when (get-in app [:user :username])
           [:li
            [:a (merge (stylefy/use-style
                         style-topnav/topnav-dropdown-link)
                       {:href "#/own-services"
                        :on-click #(e! (fp-controller/->OpenHeader))})
             (tr [:document-title :own-services])]])
         (when (get-in app [:user :username])
           [:li
            [:a (merge (stylefy/use-style
                         style-topnav/topnav-dropdown-link)
                       {:href "#/routes"
                        :on-click #(e! (fp-controller/->OpenHeader))})
             (tr [:common-texts :navigation-route])]])
         (when (get-in app [:user :username])
           [:li
            [:a (merge (stylefy/use-style
                         style-topnav/topnav-dropdown-link)
                       {:href "#/pre-notices"
                        :on-click #(e! (fp-controller/->OpenHeader))})
             (tr [:common-texts :navigation-pre-notice])]])
         (when (and (flags/enabled? :pre-notice) (get-in app [:user :transit-authority?]))
           [:li
            [:a (merge (stylefy/use-style
                         style-topnav/topnav-dropdown-link)
                       {:href     "#/authority-pre-notices"
                        :on-click #(e! (fp-controller/->OpenHeader))})
             (tr [:common-texts :navigation-authority-pre-notices])]])
         (when (:admin? (:user app))
           [:li
            [:a (merge (stylefy/use-style
                         style-topnav/topnav-dropdown-link)
                       {:href "#/admin"
                        :on-click #(do
                                     (e! (fp-controller/->OpenHeader)))})
             (tr [:document-title :admin])]])
         (when (:admin? (:user app))
           [:li
            [:a (merge (stylefy/use-style
                         style-topnav/topnav-dropdown-link)
                       {:href "#/monitor"
                        :on-click #(do
                                     (e! (fp-controller/->OpenHeader)))})
             (tr [:document-title :monitor])]])]]
       [:div.col-sm-4.col-md-4
        [:ul (stylefy/use-style style-topnav/ul)
         [:li
          [linkify (tr [:common-texts :user-menu-nap-help-link]) (tr [:common-texts :user-menu-nap-help])
           (merge (stylefy/use-style
                    style-topnav/topnav-dropdown-link)
                  {:target "_blank"})]]

         ;; TODO: commented out because for now there are no valid videos. Link shall be restored and updated when videos are available.
         ;[:li
         ; [linkify (tr [:common-texts :user-menu-video-tutorials-link]) (tr [:common-texts :user-menu-video-tutorials])
         ;  (merge (stylefy/use-style
         ;           style-topnav/topnav-dropdown-link)
         ;         {:target "_blank"})]]

         [:li
          [linkify "https://github.com/finnishtransportagency/mmtis-national-access-point/blob/master/docs/api/README.md"
           (tr [:common-texts :navigation-for-developers])
           (merge (stylefy/use-style
                    style-topnav/topnav-dropdown-link)
                  {:target "_blank"})]]]]
       [:div.col-sm-4.col-md-4
        [:ul (stylefy/use-style style-topnav/ul)
         (if (nil? (get-in app [:user :username]))
           [:ul (stylefy/use-style style-topnav/ul)
            [:li
             (if (flags/enabled? :ote-login)
               [:a (merge (stylefy/use-style style-topnav/topnav-dropdown-link)
                          {:href "#"
                           :on-click #(do
                                        (.preventDefault %)
                                        (e! (fp-controller/->OpenHeader))
                                        (e! (login/->ShowLoginPage)))})
                (tr [:common-texts :navigation-login])]
               [linkify "/user/login" (tr [:common-texts :navigation-login])
                (merge (stylefy/use-style
                         style-topnav/topnav-dropdown-link))])]
            [:li
             [:a (merge (stylefy/use-style
                          style-topnav/topnav-dropdown-link)
                        {:href "#"
                         :on-click #(do
                                      (.preventDefault %)
                                      (e! (fp-controller/->OpenHeader))
                                      (e! (fp-controller/->ToggleRegistrationDialog)))})
              (tr [:common-texts :navigation-register])]]])

         [:li
          [linkify (tr [:common-texts :navigation-privacy-policy-url]) (tr [:common-texts :navigation-privacy-policy])
           (merge (stylefy/use-style
                    style-topnav/topnav-dropdown-link)
                  {:target "_blank"})]]
         [:li
          [linkify (tr [:common-texts :navigation-feedback-link]) (tr [:common-texts :navigation-give-feedback])
           (merge (stylefy/use-style
                    style-topnav/topnav-dropdown-link)
                  {:target "_blank"})]]]]]]]))

(defn- top-nav-links [e! app is-scrolled?]
  (let [current-language @localization/selected-language]
    [:div.navbar (stylefy/use-style style-topnav/clear)
     [:ul (stylefy/use-style style-topnav/ul)
      [:li
       [:a
        {:style    (merge
                     style-topnav/desktop-link
                     (if @is-scrolled?
                       {:padding-top "0px"}
                       {:padding-top "11px"}))
         :href     "#"
         :on-click #(do
                      (.preventDefault %)
                      (e! (fp-controller/->CloseHeaderMenus))
                      (e! (fp-controller/->ChangePage :front-page nil)))}
        [:img {:style (merge
                        style-topnav/logo
                        (when @is-scrolled?
                          style-topnav/logo-small))
               :src   "img/icons/nap-logo.svg"}]]]

      (doall
        (for [{:keys [page label url]}
              (filter some? [{:page  :services
                              :label [:common-texts :navigation-dataset]}
                             (when (logged-in? app)
                               {:page  :own-services
                                :label [:common-texts :navigation-own-service-list]})])]
          ^{:key page}
          [:li.hidden-xs.hidden-sm
           [:a
            (merge
              (stylefy/use-style
                (merge style-topnav/desktop-link
                       (when @is-scrolled?
                         {:height "56px"})))
              {:href     "#"
               :on-click #(do
                            (.preventDefault %)
                            (e! (fp-controller/->CloseHeaderMenus))
                            (if url
                              (e! (fp-controller/->GoToUrl url))
                              (e! (fp-controller/->ChangePage page nil))))})
            [:div
             (tr label)]]]))

      [:li (stylefy/use-style style-topnav/li-right)
       [:div (merge (stylefy/use-style (merge (if (get-in app [:ote-service-flags :lang-menu-open])
                                                style-topnav/li-right-div-blue
                                                style-topnav/li-right-div-white)
                                              (when @is-scrolled?
                                                {:height "56px"})))
                    {:on-click #(e! (fp-controller/->OpenLangMenu))})
        [:div {:style (merge {:transition "margin-top 300ms ease"}
                             (if @is-scrolled?
                               {:margin-top "15px"}
                               {:margin-top "28px"}))}
         (if (get-in app [:ote-service-flags :lang-menu-open])
          [ic/navigation-close {:style {:color "#fff" :height 24 :width 30 :top 5}}]
          [ic/action-language {:style {:color "#fff" :height 24 :width 30 :top 5}}])]
        [:span {:style {:color "#fff"}} (str/upper-case (name current-language))]]]

      [:li (stylefy/use-style style-topnav/li-right)
       [:div.header-general-menu (merge (stylefy/use-style (merge (if (get-in app [:ote-service-flags :header-open])
                                                                    style-topnav/li-right-div-blue
                                                                    style-topnav/li-right-div-white)
                                                                  (when @is-scrolled?
                                                                    {:height "56px"})))
                                        {:on-click #(e! (fp-controller/->OpenHeader))})
        [:div {:style (merge {:transition "margin-top 300ms ease"}
                             (if @is-scrolled?
                               {:margin-top "15px"}
                               {:margin-top "28px"}))}
         (if (get-in app [:ote-service-flags :header-open])
          [ic/navigation-close {:style {:color "#fff" :height 24 :width 30 :top 5}}]
          [ic/navigation-menu {:style {:color "#fff" :height 24 :width 30 :top 5}}])]
        [:span.hidden-xs {:style {:color "#fff"}} (tr [:common-texts :navigation-general-menu])]]]

      (when (get-in app [:user :username])
        [:li (stylefy/use-style style-topnav/li-right)
         [:div.header-user-menu (merge (stylefy/use-style (merge (if (get-in app [:ote-service-flags :user-menu-open])
                                                                   style-topnav/li-right-div-blue
                                                                   style-topnav/li-right-div-white)
                                                                 (when @is-scrolled?
                                                                   {:height "56px"})))
                                       {:on-click #(e! (fp-controller/->OpenUserMenu))})
          [:div {:style (merge {:transition "margin-top 300ms ease"}
                               (if @is-scrolled?
                                 {:margin-top "15px"}
                                 {:margin-top "28px"}))}
           (if (get-in app [:ote-service-flags :user-menu-open])
            [ic/navigation-close {:style {:color "#fff" :height 24 :width 30 :top 5}}]
            [ic/social-person {:style {:color "#fff" :height 24 :width 30 :top 5}}])]
          [:span.hidden-xs {:style {:color "#fff"}} (text/maybe-shorten-text-to 30 (get-in app [:user :name]))]]])

      (when (nil? (get-in app [:user :username]))
        [:li (stylefy/use-style style-topnav/li-right)
         [:div (merge (stylefy/use-style (merge style-topnav/li-right-div-white
                                                (when @is-scrolled?
                                                  {:height "56px"})))
                      {:on-click #(e! (fp-controller/->ToggleRegistrationDialog))})
          [:div {:style (merge {:transition "margin-top 300ms ease"}
                               (if @is-scrolled?
                                 {:margin-top "0px"}
                                 {:margin-top "0px"}))}
           [:span.hidden-xs {:style {:color "#fff"}} (tr [:common-texts :navigation-register])]]]])

      (when (and (nil? (get-in app [:user :username])) (flags/enabled? :ote-login))
        [:li (stylefy/use-style style-topnav/li-right)
         [:div (merge (stylefy/use-style (merge style-topnav/li-right-div-white
                                                (when @is-scrolled?
                                                  {:height "56px"})))
                      {:on-click #(e! (login/->ShowLoginPage))})
          [:div {:style (merge {:transition "margin-top 300ms ease"}
                               (if @is-scrolled?
                                 {:margin-top "0px"}
                                 {:margin-top "0px"}))}
           [:span.hidden-xs {:style {:color "#fff"}} (tr [:common-texts :navigation-login])]]]])]]))

(defn top-nav [e! app is-scrolled?]
  [:div
   [header-scroll-sensor is-scrolled? -250]
   [esc-press-listener e! app]
   [:div (stylefy/use-style style-topnav/topnav-wrapper)
    [:div
     (stylefy/use-style (merge
                          style-topnav/topnav-desktop
                          (when @is-scrolled?
                            {:height "56px" :line-height "56px"})))
     [:div.container
      [top-nav-links e! app is-scrolled?]]]
    [top-nav-drop-down-menu e! app is-scrolled?]
    [user-menu e! app]
    [lang-menu e! app]]])
