(ns ote.services.localization
  "Services for fetching localization messages."
  (:require [com.stuartsierra.component :as component]
            [ote.components.http :as http]
            [ote.localization :as localization]
            [compojure.core :refer [routes GET]]))

(defn- fetch-language [language-name]
  (http/transit-response (localization/translations (keyword language-name))))

(defn- set-language
  "Change NAP language to fi,sv,en. E.g. www.finap.fi/ote/lang/en"
  [language-name]
  {:status 302
   :headers {"Location" "/"}
   :cookies {"finap_lang" {:path "/" :value language-name}}
   :body "Setting a cookie."})

(defrecord Localization []
  component/Lifecycle
  (start [{http :http :as this}]
    (assoc
      this ::stop
           (http/publish! http {:authenticated? false}
                          (routes
                            (GET "/language/:lang" [lang]
                              (fetch-language lang))
                            (GET "/lang/:lang" [lang]
                              (set-language lang))))))
  (stop [{stop ::stop :as this}]
    (stop)
    (dissoc this ::stop)))
