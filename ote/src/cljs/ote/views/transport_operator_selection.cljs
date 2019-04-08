(ns ote.views.transport-operator-selection
  "Component for selecting a transport operator"
  (:require [reagent.core :as r]
            [ote.mui-wrapper.reagent :as ui]
            [ote.mui-wrapper.icons :as ic]

            [ote.ui.common :as ui-common]
            [ote.ui.form-fields :as form-fields]

            [ote.app.controller.transport-operator :as to]
            [ote.app.controller.front-page :as fp]

            [ote.db.transport-operator :as t-operator]
            [ote.localization :refer [tr tr-key]]))

(defn transport-operator-selection [e! {operator :transport-operator
                                        operators :transport-operators-with-services
                                        show-add-member-dialog? :show-add-member-dialog?} & extended]
  [:div
   ;; Show operator selection if there are operators and we are not creating a new one
   (when (and (not (empty? operators))
              (not (:new? operator)))
     [:div.row
     [:div.col-sm-4.col-md-3
      [form-fields/field
       {:label       (tr [:field-labels :select-transport-operator])
        :name        :select-transport-operator
        :type        :selection
        :show-option #(if (nil? %)
                        (tr [:buttons :add-new-transport-operator])
                        (::t-operator/name %))
        :update!     #(if (nil? %)
                        (e! (to/->CreateTransportOperator))
                        (e! (to/->SelectOperator %)))
        :options     (into (mapv :transport-operator operators)
                           [:divider nil])
        :auto-width? true}
       operator]]

      (when extended
       [:div.col-xs-12.col-sm-3.col-md-2
       [ui/flat-button {:label (tr [:buttons :edit])
                        :style {:margin-top "1.5em"
                                :font-size "8pt"}
                        :icon (ic/content-create {:style {:width 16 :height 16}})
                        :on-click #(do
                                     (.preventDefault %)
                                     (e! (fp/->ChangePage :transport-operator {:id (::t-operator/id operator)})))}]])
      (when extended
       [:div.col-xs-12.col-sm-3.col-md-2
        [ui/flat-button {:label (tr [:buttons :add-new-member])
                         :style {:margin-top "1.5em"
                                 :font-size "8pt"}
                         :icon (ic/content-add {:style {:width 16 :height 16}})
                         :on-click #(do
                                      (.preventDefault %)
                                      (e! (fp/->ToggleAddMemberDialog)))}]
        (when show-add-member-dialog?
          [ui-common/ckan-iframe-dialog (::t-operator/name operator)
           (str "/organization/member_new/" (::t-operator/ckan-group-id operator))
           #(e! (fp/->ToggleAddMemberDialog))])])])])
