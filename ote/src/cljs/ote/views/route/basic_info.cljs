(ns ote.views.route.basic-info
  "Route wizard: basic info form"
  (:require [ote.ui.form :as form]
            [ote.app.controller.route.route-wizard :as rw]
            [ote.db.transport-operator :as t-operator]
            [ote.db.transit :as transit]
            [ote.mui-wrapper.reagent :as ui]
            [ote.localization :refer [tr tr-key]]
            [ote.ui.validation :as validation]))

(defn- ensure-operator
  "When page is refreshed it is possible that operator is not set.
  We set it here."
  [app]
  (let [app (if (nil? (get-in app [:route ::transit/transport-operator-id]))
              (assoc-in app [:route ::transit/transport-operator-id] (get-in app [:transport-operator ::t-operator/id]))
              app)]
    (:route app)))

(defn basic-info [e! {route :route :as app}]
  (let [route (ensure-operator app)]
    [:div.route-basic-info
     [form/form {:update! #(e! (rw/->EditBasicInfo %))}
      [(form/group
         {:label   (tr [:route-wizard-page :basic-info-header])
          :columns 3
          :layout  :row}
         {:name      ::transit/name
          :type      :localized-text
          :label     (tr [:route-wizard-page :basic-info-route-name])
          :is-empty? validation/empty-localized-text?
          :required? true}
         {:name         ::transit/transport-operator-id
          :option-value ::t-operator/id
          :type         :selection
          :label        (tr [:route-wizard-page :basic-info-transport-operator])
          :options      (mapv :transport-operator (:transport-operators-with-services app))
          :show-option  ::t-operator/name}

         ;; Departure and destination
         {:name  ::transit/departure-point-name
          :type  :localized-text
          :label (tr [:route-wizard-page :basic-info-departure-point-name])}
         {:name  ::transit/destination-point-name
          :type  :localized-text
          :label (tr [:route-wizard-page :basic-info-destination-point-name])})]
      route]]))
