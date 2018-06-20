(ns ote.views.main
  "OTE-sovelluksen päänäkymä"
  (:require [reagent.core :as r]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.core :refer [color]]
            [cljs-react-material-ui.icons :as ic]
            [ote.ui.common :refer [linkify ckan-iframe-dialog]]
            [ote.views.transport-operator :as to]
            [ote.views.front-page :as fp]
            [ote.app.controller.front-page :as fp-controller]
            [ote.views.transport-service :as t-service]
            [ote.views.footer :as footer]
            [ote.localization :refer [tr tr-key] :as localization]
            [ote.views.place-search :as place-search]
            [stylefy.core :as stylefy]
            [ote.style.base :as style-base]
            [ote.style.topnav :as style-topnav]
            [ote.views.theme :refer [theme]]
            [ote.views.service-search :as service-search]
            [ote.ui.form :as form]
            [ote.app.controller.login :as login]
            [ote.app.controller.flags :as flags]
            [ote.ui.common :as common]
            [ote.ui.form-fields :as form-fields]
            [ote.views.admin :as admin]
            [ote.views.operators :as operators]
            [ote.views.route.route-list :as route-list]
            [ote.views.route :as route]
            [ote.views.gtfs-viewer :as gtfs-viewer]
            [ote.views.pre-notices.pre-notice :as notice]
            [ote.views.pre-notices.listing :as pre-notices-listing]
            [ote.views.pre-notices.authority-listing :as pre-notices-authority-listing]
            [ote.views.transit-visualization :as transit-visualization]
            [ote.views.transit-changes :as transit-changes]
            [ote.ui.common :as common-ui]))


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

(defn logged-in? [app]
  (not-empty (get-in app [:user :username])))

(defn- is-user-menu-active [app]
  (when (= true (get-in app [:ote-service-flags :user-menu-open]))
    "active"))

(defn header-links [app]
  (let [operators-list-link (if (flags/enabled? :ote-operators-list)
                             {:page  :operators
                              :label [:common-texts :navigation-organizations]}
                             {:page  :organizations
                              :label [:common-texts :navigation-organizations]
                              :url   "/organization"})]
  (filter some?
          [{:page  :front-page
            :label [:common-texts :navigation-front-page]}

           {:page  :services
            :label [:common-texts :navigation-dataset]}

           operators-list-link

           (when (logged-in? app)
             {:page  :own-services
              :label [:common-texts :navigation-own-service-list]})

           (when (:admin? (:user app))
             {:page :admin
              :label [:common-texts :navigation-admin]})])))

(defn user-menu [e! {:keys [name username transit-authority?]}]
  (when username
    [ui/drop-down-menu
    {:menu-style {}
     :underline-style {}
     :label-style {:color "#FFFFFF" :font-weight 700}
     :list-style {:background-color "#323232"}
     :on-click #(e! (fp-controller/->OpenUserMenu))
     :anchor-origin {:horizontal "right" :vertical "bottom"}
     :target-origin {:horizontal "right" :vertical "top"}
     :selection-renderer (constantly name)}

     (when (flags/enabled? :sea-routes)
      [ui/menu-item {:style {:color "#FFFFFF"}
                    :primary-text (tr [:common-texts :navigation-route])
                    :on-click #(do (.preventDefault %)
                                   (e! (fp-controller/->ChangePage :routes nil)))}])
     (when (flags/enabled? :pre-notice)
       [ui/menu-item {:style {:color "#FFFFFF"}
                      :primary-text (tr [:common-texts :navigation-pre-notice])
                      :on-click #(do (.preventDefault %)
                                     (e! (fp-controller/->ChangePage :pre-notices nil)))}])

     (when (and (flags/enabled? :pre-notice) transit-authority?)
       [ui/menu-item {:style {:color "#FFFFFF"}
                      :primary-text (tr [:common-texts :navigation-authority-pre-notices])
                      :on-click #(do (.preventDefault %)
                                     (e! (fp-controller/->ChangePage :authority-pre-notices nil)))}])


     [ui/menu-item {:style {:color "#FFFFFF"}
                    :primary-text (tr [:common-texts :user-menu-profile])
                    :on-click #(do (.preventDefault %)
                                   (e! (fp-controller/->ToggleUserEditDialog)))}]
     [ui/menu-item {:style {:color "#FFFFFF"}
                    :primary-text (tr [:common-texts :navigation-give-feedback])
                    :on-click #(do (.preventDefault %)
                                   (e! (fp-controller/->OpenNewTab "http://bit.ly/nap-palaute")))}]
     [ui/menu-item {:style {:color "#FFFFFF"}
                    :primary-text (tr [:common-texts :user-menu-log-out])
                    :on-click #(do (.preventDefault %)
                                   (e! (login/->Logout)))} ]

     [ui/menu-item {:primary-text (r/as-element [footer/language-selection e! style-base/language-selection-dropdown {:color "#fff"}])}]]))

(def own-services-pages #{:own-services :transport-service :new-service :edit-service :transport-operator :organizations})
(def services-pages #{:services})
(def operator-pages #{:operators})

(defn page-active?
"Return true if given current-page belongs to given page-group"
[page-group current-page]
  (cond
    (= page-group :front-page current-page) true
    (= page-group :own-services) (own-services-pages current-page)
    (= page-group :services) (services-pages current-page)
    (= page-group :operators) (operator-pages current-page)
      :default false))

(defn- top-nav-links [e! {current-page :page :as app} desktop? is-scrolled?]
  [:div.navbar (stylefy/use-style style-topnav/clear)
   [:ul (stylefy/use-style style-topnav/ul)
    (when (> (:width app) style-base/mobile-width-px)
      [:li
       [:a
        {:style (merge (if desktop?
                         style-topnav/desktop-link
                         style-topnav/link)
                       (if @is-scrolled?
                         {:padding-top "0px"}
                         {:padding-top "11px"}))
         :href "#"
         :on-click #(do
                      (.preventDefault %)
                      (e! (fp-controller/->ChangePage :front-page nil)))}
        [:img {:style (merge
                        style-topnav/logo
                        (when @is-scrolled?
                          style-topnav/logo-small))
               :src "img/icons/nap-logo.svg"}]]])

    (doall
     (for [{:keys [page label url]} (header-links app)]
       ^{:key page}
       [:li (if desktop? nil (stylefy/use-style style-topnav/mobile-li))
        [:a
         (merge (stylefy/use-style
                 (if (page-active? page current-page)
                   (if desktop? style-topnav/desktop-active style-topnav/active)
                   (if desktop? style-topnav/desktop-link style-topnav/link)))
                {:href     "#"
                 :on-click #(do
                              (.preventDefault %)
                              (if url
                                (e! (fp-controller/->GoToUrl url))
                                (e! (fp-controller/->ChangePage page nil))))})
         (tr label)]]))
    [:div.user-menu {:class (is-user-menu-active app)
                     :style (merge
                              {:transition "padding-top 300ms ease"}
                              (when (> (:width app) style-base/mobile-width-px)
                                {:float "right" :padding-top "12px"})
                              (when @is-scrolled?
                                {:padding-top "0px"}))}
     [user-menu e! (:user app)]]

    (if (nil? (get-in app [:user :username]))
      [:ul (stylefy/use-style style-topnav/ul)
       [:li {:style {:float "right"}}
        [:a (merge (stylefy/use-style
                    (if desktop? style-topnav/desktop-link style-topnav/link))
                   {:href "#"
                    :on-click #(do
                                 (.preventDefault %)
                                 (e! (fp-controller/->ToggleRegistrationDialog)))})
         (tr [:common-texts :navigation-register])]]
       [:li
        (if (flags/enabled? :ote-login)
          [:a (merge (stylefy/use-style
                      (if desktop? style-topnav/desktop-link style-topnav/link))
                     {:style {:float "right"}}
                     {:on-click #(do
                                   (.preventDefault %)
                                   (e! (login/->ShowLoginDialog)))})
           (tr [:common-texts :navigation-login])]
          [linkify "/user/login" (tr [:common-texts :navigation-login])
           (merge (stylefy/use-style
                   (if desktop? style-topnav/desktop-link style-topnav/link))
                  {:style {:float "right"}})])]])

    [:li (if desktop? nil (stylefy/use-style style-topnav/mobile-li))
     [linkify "https://s3.eu-central-1.amazonaws.com/ote-assets/nap-ohje.pdf" (tr [:common-texts :user-menu-nap-help])
      (merge (stylefy/use-style
               (if desktop? style-topnav/desktop-link style-topnav/link))
             {:target "_blank"
              :style (when (> (:width app) style-base/mobile-width-px)
                       {:float "right"})})]]]])

(defn- mobile-top-nav-links [e! app is-scrolled?]
  [:div
   [:ul (stylefy/use-style style-topnav/ul)
    [:li (stylefy/use-style style-topnav/li)
     [:a (merge
          (stylefy/use-style style-topnav/link)
          {:href     "#"
           :on-click #(do
                        (.preventDefault %)
                        (e! (fp-controller/->GoToUrl "/")))})
       [:img {:src "img/icons/nap-logo.svg"}]]]
     [:li (stylefy/use-style style-topnav/right)
      [ui/icon-button {:on-click #(e! (fp-controller/->OpenHeader))
                       :style {:padding 8
                               :width 56
                               :height 56}
                       :icon-style {:height 40
                                    :width 40}}
       [ic/action-reorder {:style {:color "#fff"
                                   :width 40
                                   :height 40
                                   }}]]]]
  (when (get-in app [:ote-service-flags :header-open])
    (top-nav-links e! app false is-scrolled?))])


(defn- top-nav [e! app is-scrolled? desktop?]
  [:span
   [header-scroll-sensor is-scrolled? -250]
   [:div
    (stylefy/use-style (merge
                         (if desktop? style-topnav/topnav-desktop style-topnav/topnav)
                         (when @is-scrolled?
                           {:height "56px" :line-height "56px"})))
    [:div.container
     (if desktop?
       (top-nav-links e! app true is-scrolled?)
       (mobile-top-nav-links e! app is-scrolled?))]]])

(def grey-background-pages #{:edit-service :services :transport-operator :own-services :new-service :operators :routes :pre-notices})

(defn login-form [e! {:keys [credentials failed? error in-progress?] :as login}]
  [:div.login-form
   (when failed?
     [:div (stylefy/use-style style-base/error-element)
      (tr [:login :error error])])
   [form/form {:name->label (tr-key [:field-labels :login])
               :update! #(e! (login/->UpdateLoginCredentials %))
               :footer-fn (fn [data]
                            [:span.login-dialog-footer
                             [ui/raised-button {:primary true
                                                :on-click #(e! (login/->Login))
                                                :label (tr [:login :login-button])}]])}
    [(form/group
      {:label (tr [:login :label]) :expandable? false
       :columns 3}
      {:name :email
       :label (tr [:field-labels :login :email-or-username])
       :type :string
       :autocomplete "email"
       :on-enter #(e! (login/->Login))}
      {:name :password
       :autocomplete "passoword"
       :type :string
       :password? true
       :on-enter #(e! (login/->Login))})]
    credentials]])

(defn login-action-cards [e!]
  [:div {:style {:flex-basis 0 :flex-grow 1}}
   [:div (stylefy/use-style (merge (style-base/flex-container "column")
                                   {:margin-left "1em"
                                    :padding-left "1em"}))
    [ui/card
     [ui/card-header {:title (tr [:login :no-account?])}]
     [ui/card-text
      [:div
       [:div (tr [:login :no-account-help])]
       [ui/flat-button {:on-click #(e! (fp-controller/->ToggleRegistrationDialog))
                        :label (tr [:login :no-account-button])
                        :primary true}]]]]

    [ui/card
     [ui/card-header {:title (tr [:login :forgot-password?])}]
     [ui/card-text
      [:div
       [:div (tr [:login :forgot-password-help])]
       [ui/flat-button {:on-click #(e! (fp-controller/->ToggleUserResetDialog))
                        :label (tr [:login :forgot-password-button])
                        :primary true}]]]]]])

(defn mobile-login-form [e! {:keys [credentials failed? error] :as login}]
  [:span
   [ui/card
    [ui/card-title {:title (tr [:login :label])}]
    [ui/card-text
     [:div (stylefy/use-style (merge (style-base/flex-container "column")
                                     {:justify-content "center"}))
      (when failed?
        [:div (stylefy/use-style style-base/error-element)
         (tr [:login :error error])])
      [form-fields/field {:label (tr [:field-labels :login :email-or-username])
                          :type :string :name :email
                          :style {:width "95%" :align-self "center"}
                          :update! #(e! (login/->UpdateLoginCredentials {:email %}))}
       (:email credentials)]
      [form-fields/field {:label (tr [:field-labels :login :password])
                          :type :string :name :password
                          :password? true
                          :style {:width "95%" :align-self "center"}
                          :update! #(e! (login/->UpdateLoginCredentials {:password %}))}
       (:password credentials)]
      [:div (stylefy/use-style (style-base/flex-container "row"))
       [ui/raised-button {:style {:flex-basis 0 :flex-grow 1}
                          :primary true
                          :on-click #(e! (login/->Login))
                          :label (tr [:login :login-button])}]
       [ui/raised-button {:style {:flex-basis 0 :flex-grow 1}
                          :secondary true
                          :on-click #(e! (login/->LoginCancel))
                          :label (tr [:buttons :cancel])}]]]]]
   [login-action-cards e!]])

(defn login-dialog [e! {:keys [credentials failed? error in-progress?] :as login}]
  [ui/dialog {:open true
              :on-request-close #(e! (login/->LoginCancel))}
   [:div (stylefy/use-style (merge (style-base/flex-container "row")))
    [:div {:style {:flex-basis 0 :flex-grow 1}}
     [login-form e! login]]
    [login-action-cards e!]]])

(defn document-title [page]
  (set! (.-title js/document)
        (case page
          :services (tr [:document-title :services])
          :operators (tr [:document-title :operators])
          :own-services (tr [:document-title :own-services])
          :admin (tr [:document-title :admin])

          ;; default title for other pages
          (tr [:document-title :default])))

  ;; Render as an empty span
  [:span])

(defn ckan-dialogs [e! {:keys [login show-register-dialog? show-reset-dialog?
                               show-user-edit-dialog? user]}]
  [:span
   (when (:show? login)
     ^{:key "login"}
     [login-dialog e! login])

   (when show-register-dialog?
     ^{:key "ckan-register"}
     [ckan-iframe-dialog
      (tr [:common-texts :navigation-register])
      "/user/register"
      ;; On modal close
      #(e! (fp-controller/->ToggleRegistrationDialog))
      ;; On ckan close i.e. user has been registered.
      #(e! (fp-controller/->ToggleRegistrationDialog))])

   (when show-reset-dialog?
            ^{:key "ckan-reset"}
     [ckan-iframe-dialog
      (tr [:login :forgot-password?])
      "/user/reset"
      #(e! (fp-controller/->ToggleUserResetDialog))
      #(e! (fp-controller/->UserResetRequested))])

   (when show-user-edit-dialog?
     ^{:key "ckan-user-edit"}
     [ckan-iframe-dialog
      (tr [:common-texts :user-menu-profile])
      (str "/user/edit/" (:username user))
      #(e! (fp-controller/->ToggleUserEditDialog))])])

(defn- scroll-to-page
  "Invisible component that scrolls to the top of the window whenever it is mounted."
  [page]
  (r/create-class
   {:component-did-mount #(.scrollTo js/window 0 0)
    :reagent-render
    (fn [page]
      [:span])}))

(defn ote-application
  "OTE application main view"
  [e! app]
  (let [is-scrolled? (r/atom false)]
    (fn [e! {loaded? :transport-operator-data-loaded?
             login :login
             :as app}]
      (let [desktop? (> (:width app) style-base/mobile-width-px)]
        [:div {:style (stylefy/use-style style-base/body)}
         [theme e! app
          [:div.ote-sovellus
           [top-nav e! app is-scrolled? desktop?]

           (if (and (:show? login) common/mobile?)
             [mobile-login-form e! login]
             [:span
              [ckan-dialogs e! app]
              (if (not loaded?)
                [:div.loading [:img {:src "/base/images/loading-spinner.gif"}]]
                [:div.wrapper
                 (stylefy/use-style (merge
                                      {:transition "margin-top 300ms ease"}
                                      (when (grey-background-pages (:page app))
                                        {:background-color "rgba (58, 57, 57, 0.1)"})
                                      (if (or (not desktop?) @is-scrolled?)
                                        {:margin-top "56px"})))
                 [:div (if (= :front-page (:page app))
                         {:class "container-fluid"}
                         {:style {:padding-bottom "20px"}
                          :class "container"})
                  [document-title (:page app)]

                  ;; Ensure that a new scroll-to-page component is created when page changes
                  ^{:key (name (:page app))}
                  [scroll-to-page (:page app)]

                  (case (:page app)
                    :front-page [fp/front-page e! app]
                    :own-services [fp/own-services e! app]
                    :transport-service [t-service/select-service-type e! app]
                    :transport-operator [to/operator e! app]

                    ;; Routes for the service form, one for editing an existing one by id
                    ;; and another when creating a new service
                    :edit-service [t-service/edit-service-by-id e! app]
                    :new-service [t-service/create-new-service e! app]

                    :services [service-search/service-search e! app]

                    :admin [admin/admin-panel e! app]

                    :operators [operators/operators e! app]

                    :routes [route-list/routes e! app]
                    :new-route [route/new-route e! app]
                    :edit-route [route/edit-route-by-id e! app]

                    ;; 60days pre notice views
                    :new-notice [notice/new-pre-notice e! app]
                    :edit-pre-notice [notice/edit-pre-notice-by-id e! app]
                    :pre-notices [pre-notices-listing/pre-notices e! app]

                    :view-gtfs [gtfs-viewer/gtfs-viewer e! app]
                    :transit-visualization [transit-visualization/transit-visualization e! (:transit-visualization app)]

                    (:transit-changes :authority-pre-notices)
                    [transit-changes/transit-changes e! app]

                    [:div (tr [:common-texts :no-such-page]) (pr-str (:page app))])]])])
           [footer/footer e!]]]]))))
