(ns ote.views.ckan-org-editor
  "OTE organization data editor for CKAN organization info page. (CKAN embedded view)"
  (:require [ote.app.controller.ckan-org-editor :as org-edit]
            [ote.views.theme :refer [theme]]
            [cljs-react-material-ui.reagent :as ui]
            [clojure.string :as str]
            [ote.app.controller.transport-operator :as to]
            [ote.db.transport-operator :as to-definitions]
            [ote.db.common :as common]
            [stylefy.core :as stylefy]
            [ote.style.ckan :as style-ckan]
            [ote.style.base :as style-base]
            [ote.localization :refer [tr tr-or]]
            [ote.views.transport-operator :as to-view]))

(defn editor [e! status]
  ;; init
  (e! (org-edit/->StartEditor))
  (fn [e! {:keys [transport-operator] :as app}]
    [theme
     [:div.container
      [to-view/operator e! transport-operator]]]))
