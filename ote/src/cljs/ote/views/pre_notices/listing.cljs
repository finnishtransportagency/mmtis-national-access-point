(ns ote.views.pre-notices.listing
  "Pre notices listing view"
  (:require [reagent.core :as r]
            [ote.app.controller.pre-notices :as pre-notice]
            [ote.ui.table :as table]
            [ote.ui.list-header :as list-header]
            [ote.localization :refer [tr tr-key]]
            [ote.db.transit :as transit]
            [ote.db.modification :as modification]
            [ote.db.transport-operator :as t-operator]
            [ote.views.transport-operator-selection :as t-operator-sel]
            [ote.mui-wrapper.icons :as ic]
            [ote.mui-wrapper.reagent :as ui]
            [ote.time :as time]
            [clojure.string :as str]
            [ote.app.controller.front-page :as fp]
            [ote.ui.common :as common]
            [ote.style.dialog :as style-dialog]))

(defn pre-notice-type->str
  [types]
  (str/join ", "
            (map (tr-key [:enums ::transit/pre-notice-type]) types)))


(defn pre-notices-table [e! pre-notices state]
  (let [notices (filter #(= state (::transit/pre-notice-state %)) pre-notices)]
    [:div.row
     [table/table {:class (name state)
                   :name->label (tr-key [:pre-notice-list-page :headers])
                   :key-fn ::transit/id
                   :no-rows-message (case state
                                      :draft (tr [:pre-notice-list-page :no-pre-notices-for-operator])
                                      :sent (tr [:pre-notice-list-page :no-pre-notices-sent]))}
      [{:name ::transit/pre-notice-type
        :format pre-notice-type->str}
       {:name ::transit/route-description}
       {:name ::modification/created
        :read (comp time/format-timestamp-for-ui ::modification/created)}
       {:name ::modification/modified
        :read (comp time/format-timestamp-for-ui ::modification/modified)}
       {:name ::transit/pre-notice-state
        :format (tr-key [:enums ::transit/pre-notice-state])}
       {:name :actions
        :read (fn [row]
                [:div
                 [:span.edit-pre-notice
                  [ui/icon-button {:href     "#"
                                   :on-click #(do
                                                (.preventDefault %)
                                                (e! (fp/->ChangePage :edit-pre-notice {:id (::transit/id row)})))}
                   (case state
                     :draft [ic/content-create]
                     :sent [ic/action-visibility])]]

                 (when (= :draft state)
                   [:span.delete-pre-notice
                    [ui/icon-button {:href     "#"
                                     :on-click #(do
                                                  (.preventDefault %)
                                                  (e! (pre-notice/->DeletePreNotice row)))}
                     [ic/action-delete]]])])}]
      notices]]))

(defn pre-notices [e! {:keys [transport-operator pre-notices delete-pre-notice-dialog] :as app}]
  (if (= :loading pre-notices)
    [common/loading-spinner]
    (let [pre-notices (filter #(= (::t-operator/id transport-operator)
                                  (::t-operator/id %))
                              pre-notices)]
      [:div
       [:div {:style {:margin-bottom "20px"}}
        [list-header/header
         app
         (tr [:pre-notice-list-page :header-pre-notice-list])
         [ui/raised-button {:id "add-new-pre-notice"
                            :label (tr [:buttons :add-new-pre-notice])
                            :on-click #(do
                                         (.preventDefault %)
                                         (e! (pre-notice/->CreateNewPreNotice)))
                            :primary true
                            :icon (ic/content-add)}]
         [t-operator-sel/transport-operator-selection e! app]]]
       [:div {:style {:margin-bottom "40px"}}
        [pre-notices-table e! pre-notices :draft]
        (when delete-pre-notice-dialog
          [ui/dialog
           {:open true
            :actionsContainerStyle style-dialog/dialog-action-container
            :title (tr [:pre-notice-list-page :delete-pre-notice-dialog :label])
            :actions [(r/as-element
                        [ui/flat-button
                         {:label (tr [:buttons :cancel])
                          :primary true
                          :on-click #(e! (pre-notice/->DeletePreNoticeCancel))}])
                      (r/as-element
                        [ui/raised-button
                         {:label (tr [:buttons :delete])
                          :icon (ic/action-delete-forever)
                          :secondary true
                          :primary true
                          :on-click #(e! (pre-notice/->DeletePreNoticeConfirm))}])]}
           (tr [:pre-notice-list-page :delete-pre-notice-dialog :content])])]
       [:h3 (tr [:pre-notice-list-page :sent-pre-notices])]
       [pre-notices-table e! pre-notices :sent]])))
