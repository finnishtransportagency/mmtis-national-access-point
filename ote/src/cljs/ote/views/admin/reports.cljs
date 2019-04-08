(ns ote.views.admin.reports
  "Admin panel views. Note this has a limited set of users and is not
  currently localized, all UI text is in Finnish."
  (:require [ote.mui-wrapper.reagent :as ui]
            [ote.ui.form-fields :as form-fields]
            [ote.app.controller.admin :as admin-controller]
            [ote.db.transport-service :as t-service]
            [clojure.string :as str]
            [ote.localization :refer [tr tr-key]]
            [ote.ui.common :refer [linkify]]
            [ote.time :as time]
            [ote.mui-wrapper.icons :as ic]
            [reagent.core :as r]
            [ote.ui.common :as ui-common]
            [ote.ui.common :as common-ui]
            [stylefy.core :as stylefy]
            [ote.style.base :as style-base]
            [ote.style.admin :as style-admin]
            [cljs-time.core :as t]))

(defn reports [e!]
  [:div
   [:h2 "Palveluntuottajaraportit"]
   [:div (stylefy/use-style (style-base/flex-container "column"))
    [linkify "/admin/reports/transport-operator/all-emails" "Käytössä olevien käyttäjien ja palveluiden sähköpostit"]
    [linkify "/admin/reports/transport-operator/no-services" "Ei palveluita"]
    [linkify "/admin/reports/transport-operator/unpublished-services" "Julkaisemattomia palveluita"]
    [linkify "/admin/reports/transport-operator/brokerage" "Välityspalvelut"]
    [linkify "/admin/reports/transport-operator/taxi-operators" "Taksipalveluita tuottavat yritykset ja aliyritykset"]
    [linkify "/admin/reports/transport-operator/request-operators" "Tilausliikennettä tuottavat yritykset ja aliyritykset"]
    [linkify "/admin/reports/transport-operator/schedule-operators" "Säännöllistä aikataulun mukaista liikennettä tuottavat yritykset ja aliyritykset"]
    [linkify "/admin/reports/transport-operator/payment-interfaces" "Lippu- ja Maksujärjestelmän avanneet yritykset"]]])

