(ns ote.views.front-page
  "Front page for OTE service - Select service type and other basic functionalities"
  (:require [reagent.core :as reagent]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]
            [ote.ui.form :as form]
            [ote.ui.form-groups :as form-groups]
            [ote.ui.buttons :as buttons]
            [ote.app.controller.front-page :as fp]
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
            [reagent.core :as r]
            [ote.ui.form-fields :as form-fields]))

(defn select-operator-form-options [e!]
  {:name->label (tr-key [:field-labels])
   :update!     #(e! (to/->SelectOperator %))})

(defn- delete-service-action [e! {::t-service/keys [id name]
                                  :keys [show-delete-modal?]
                                  :as service}]
  [:span
   [ui/icon-button {:on-click #(e! (ts/->DeleteTransportService id))}
    [ic/action-delete]]
   (when show-delete-modal?
     [ui/dialog
      {:open true
       :title (tr [:dialog :delete-transport-service :title])
       :actions [(r/as-element
                  [ui/flat-button
                   {:label (tr [:buttons :cancel])
                    :primary true
                    :on-click #(e! (ts/->CancelDeleteTransportService id))}])
                 (r/as-element
                  [ui/raised-button
                   {:label (tr [:buttons :delete])
                    :icon (ic/action-delete-forever)
                    :secondary true
                    :primary true
                    :on-click #(e! (ts/->ConfirmDeleteTransportService id))}])]}

      (tr [:dialog :delete-transport-service :confirm] {:name name})])])

(defn transport-services-table-rows [e! services transport-operator-id]
  [ui/table-body {:display-row-checkbox false}
   (doall
     (map-indexed
      (fn [i {::t-service/keys [id type published? name]
              ::modification/keys [created modified] :as row}]
         (let [edit-service-url (str "/ote/index.html#/edit-service/" id)]
           ^{:key i}
           [ui/table-row {:selectable false :display-border false}
            [ui/table-row-column {:class "hidden-xs hidden-sm " :style {:width "70px"}} (get row :ote.db.transport-service/id)]
            [ui/table-row-column
             [:a {:href edit-service-url} name]]
            [ui/table-row-column
             (if published?
               (let [url (str "/ote/export/geojson/" transport-operator-id "/" id)]
                 [:a {:href url :target "_blank"} url])
               [:span.draft
                (tr [:field-labels :transport-service ::t-service/published?-values false])])]
            [ui/table-row-column {:class "hidden-xs "} (tr [:field-labels :transport-service ::t-service/published?-values published?])]
            [ui/table-row-column {:class "hidden-xs hidden-sm "} (time/format-timestamp-for-ui modified)]
            [ui/table-row-column {:class "hidden-xs hidden-sm "} (time/format-timestamp-for-ui created)]
            [ui/table-row-column
             [ui/icon-button {:href edit-service-url  }
              [ic/content-create]]
             [delete-service-action e! row]]]))
       services))])

(defn transport-services-listing [e! transport-operator-id services section-label]
  (when (> (count services) 0)
    [:div.row
     [:div {:class "col-xs-12 col-md-12"}
      [:h3 section-label]

      [ui/table (stylefy/use-style style-base/front-page-service-table)
       [ui/table-header {:adjust-for-checkbox false
                         :display-select-all false}
        [ui/table-row {:selectable false}
         [ui/table-header-column {:class "hidden-xs hidden-sm " :style {:width "10px"}} "Id"]
         [ui/table-header-column (tr [:front-page :table-header-service-name])]
         [ui/table-header-column (tr [:front-page :table-header-service-url])]
         [ui/table-header-column {:class "hidden-xs "} (tr [:front-page :table-header-NAP-status])]
         [ui/table-header-column {:class "hidden-xs hidden-sm "} (tr [:front-page :table-header-modified])]
         [ui/table-header-column {:class "hidden-xs hidden-sm "} (tr [:front-page :table-header-created])]
         [ui/table-header-column (tr [:front-page :table-header-actions])]]]

       (transport-services-table-rows e! services transport-operator-id)]]]))



(defn table-container-for-front-page [e! services state]
  [:div
   [:div.row
    [:div {:class "col-xs-12 col-sm-4 col-md-4"}
     [:h3 (tr [:common-texts :own-api-list]) ]
     ]

    [:div {:class "col-xs-12 col-sm-4 col-md-4"}
     (if (second (:transport-operators-with-services state))
     [form-fields/field
      {:label (tr [:field-labels :select-transport-operator])
       :name        :select-transport-operator
       :type        :selection
       :show-option ::t-operator/name
       :update!   #(e! (to/->SelectOperator %))
       :options     (map :transport-operator (:transport-operators-with-services state))
       :auto-width? true}
      (get state :transport-operator)
      ]
    nil)
     ]
    [:div {:class "col-xs-12 col-sm-4 col-md-4"}
     [ui/raised-button {:style {:margin-top "20px"}
                        :label    (tr [:buttons :add-transport-service])
                        :on-click #(do
                                     (.preventDefault %)
                                     (e! (fp/->ChangePage :transport-service)))
                        :primary  true}]]]
   [:div.row
    [:div {:class "col-xs-12 col-md-12"}
     ;; Table for transport services

     (for [type t-service/transport-service-types
           :let [services (filter #(= (:ote.db.transport-service/type %) type) services)]
           :when (not (empty? services))]
       ^{:key type}
       [transport-services-listing
        e!
        (get-in state [:transport-operator ::t-operator/id])
        services
        (tr [:titles type])])]]])

(defn own-services [e! state]
  ;; Get services by default from first organization
  (let [my-services (if (empty? (:transport-service-vector state))
                      (:transport-service-vector (first (:transport-operators-with-services state)))
                      (:transport-service-vector state))]
    [:div
     (if (empty? my-services)
       [transport-service/select-service-type e! state] ;; Render service type selection page if no services added
       (table-container-for-front-page e! my-services state))]))

(defn no-operator [e! state]
  "If user haven't added service-operator, we will ask to do so."
  [:div
   [:div.row
    [:div {:class "col-xs-12 col-sm-12 col-md-12"}
     [:h3 (tr [:front-page :header-no-operator])]
     [:p (tr [:front-page :desc-to-add-new-operator])]
     [:p (tr [:front-page :to-add-new-operator])
      [:a {:href "/organization/new"} (tr [:common-texts :navigation-organizations]) ]
      (tr [:front-page :to-add-new-operator-tab])]
     ]
    ]
   ]
  )