(ns ote.services.index
  "Index page generation."
  (:require [ote.components.http :as http]
            [compojure.core :refer [routes GET]]
            [hiccup.core :refer [html]]
            [hiccup.element :refer [javascript-tag]]
            [ote.localization :as localization :refer [tr]]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [ote.tools.git :refer [current-revision-sha]]
            [ring.middleware.anti-forgery :as anti-forgery]
            [ote.transit :as transit]
            [ote.services.transport :as transport]))

(def stylesheets [{:href "css/normalize.css"}               ;; Normalize css on wide range of browsers
                  {:href "css/bootstrap_style_grid.css"}
                  {:href "css/nprogress.css"}
                  {:href "css/balloon.css"}
                  {:href "css/styles.css"}
                  {:href "https://fonts.googleapis.com/css?family=Montserrat:100,100italic,200,200italic,300,300italic,400,400italic,500,500italic,600,600italic,700,700italic,800,800italic,900,900italic%7Roboto:200,300,300i,400,400i,500,500i,700,700i"
                   :rel "stylesheet"}
                  {:href "https://unpkg.com/leaflet@1.2.0/dist/leaflet.css"
                   :integrity "sha512-M2wvCLH6DSRazYeZRIm1JnYyh22purTM+FDB5CsyxtQJYeKq83arPe5wgbNmcFXGqiSH2XR8dT/fJISVA1r/zQ=="}
                  {:href "https://cdnjs.cloudflare.com/ajax/libs/leaflet.draw/0.4.12/leaflet.draw.css"}
                  {:href"https://fonts.googleapis.com/icon?family=Material+Icons"}])

(defn ote-js-location [dev-mode?]
  (str "js/ote"
       (when-not dev-mode?
         (str "-" (:current-revision-sha (current-revision-sha))))
       ".js"))

(defn google-analytics-scripts [ga-conf]
  (list
   [:script {:async nil :src (str "https://www.googletagmanager.com/gtag/js?id=" (:tracking-code ga-conf))}]
   (javascript-tag
    (str "var host = window.location.hostname; "
         "window.dataLayer = window.dataLayer || []; "
         "function gtag(){dataLayer.push(arguments);} "
         "gtag('js', new Date()); "
         "gtag('config','" (:tracking-code ga-conf) "', { 'anonymize_ip': true }); "
         "if (host === 'localhost' || host.indexOf('testi') !== -1) { "
         "  window['ga-disable-" (:tracking-code ga-conf) "'] = true; "
         "}"))))

(def favicons
  [{:rel "apple-touch-icon" :sizes "180x180" :href "/favicon/apple-touch-icon.png?v=E6jNQXq6yK"}
   {:rel "icon" :type "image/png" :sizes "32x32" :href "/favicon/favicon-32x32.png?v=E6jNQXq6yK"}
   {:rel "icon" :type "image/png" :sizes "16x16" :href "/favicon/favicon-16x16.png?v=E6jNQXq6yK"}
   {:rel "manifest" :href "/favicon/manifest.json?v=E6jNQXq6yK"}
   {:rel "mask-icon" :href "/favicon/safari-pinned-tab.svg?v=E6jNQXq6yK" :color "#5bbad5"}
   {:rel "shortcut icon" :href "/favicon/favicon.ico?v=E6jNQXq6yK"}])

(defn translations [language]
  [:script#ote-translations {:type "x-ote-translations"}
   (transit/clj->transit
    {:language language
     :translations (localization/translations language)})])

(defn user-info [db user]
  [:script#ote-user-info {:type "x-ote-user-info"}
   (transit/clj->transit
    (when user
      (transport/get-user-transport-operators-with-services db (:groups user) (:user user))))])

(defn index-page [db user config]
  (let [dev-mode? (:dev-mode? config)
        ga-conf (:ga config)
        flags (str/join "," (map name (:enabled-features config)))]
    [:html
     [:head

      (for [f favicons]
        [:link f])
      [:meta {:name "theme-color" :content "#ffffff"}]
      [:meta {:name    "viewport"
              :content "width=device-width, initial-scale=1.0"}]
      [:title "FINAP"]
      (for [{:keys [href integrity]} stylesheets]
        [:link (merge {:rel  "stylesheet"
                       :href href}
                      (when (str/starts-with? href "https://")
                        {:crossorigin ""})
                      (when integrity
                        {:integrity integrity}))])
      [:style {:id "_stylefy-constant-styles_"} ""]
      [:style {:id "_stylefy-styles_"}]
      (translations localization/*language*)
      (user-info db user)

      (when (not dev-mode?)
        (google-analytics-scripts ga-conf))]

     [:body (merge
             {:id "main-body"
              :onload "ote.main.main();"
              :features flags
              :data-language localization/*language*}
             (when (bound? #'anti-forgery/*anti-forgery-token*)
               {:data-anti-csrf-token anti-forgery/*anti-forgery-token*}))
      [:div#oteapp]
      (when dev-mode?
        [:script {:src "js/out/goog/base.js" :type "text/javascript"}])
      [:script {:src (ote-js-location dev-mode?) :type "text/javascript"}]
      (when dev-mode?
        [:script {:type "text/javascript"} "goog.require('ote.main');"])]]))

(defn index [db req dev-mode?]
  (http/with-no-cache-headers
    {:status 200
     :headers {"Content-Type" "text/html; charset=UTF-8"}
     :body (html (index-page db (:user req) dev-mode?))}))

(defrecord Index [dev-mode?]
  component/Lifecycle
  (start [{http :http
           db :db
           :as this}]
    (assoc this ::stop
           (http/publish!
            http {:authenticated? false}
            (routes
             (GET "/" req (index db req dev-mode?))
             (GET "/index.html" req (index db req dev-mode?))))))
  (stop [{stop ::stop :as this}]
    (stop)
    (dissoc this ::stop)))
