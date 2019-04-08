(ns ote.views.admin.users
  "Admin User List"
  (:require [ote.mui-wrapper.reagent :as ui]
            [ote.ui.form-fields :as form-fields]
            [ote.app.controller.admin :as admin-controller]
            [ote.app.controller.front-page :as fp-controller]
            [clojure.string :as str]
            [ote.localization :refer [tr tr-key]]
            [ote.ui.common :refer [linkify]]
            [ote.time :as time]
            [ote.mui-wrapper.icons :as ic]
            [reagent.core :as r]
            [ote.ui.common :as common-ui]
            [ote.style.dialog :as style-dialog]))

(defn- edit-user-action [e! {:keys [id username show-edit-dialog?] :as user}]
  [:span
   [ui/icon-button {:id       (str "edit-user-" id)
                    :href     "#"
                    :on-click #(do
                                 (.preventDefault %)
                                 (e! (admin-controller/->OpenEditUserDialog id)))}
    [ic/content-create]]
   (when show-edit-dialog?
     ^{:key "ckan-user-edit"}
     [common-ui/ckan-iframe-dialog
      (tr [:common-texts :user-menu-profile])
      (str "/user/edit/" (:username user))
      #(e! (admin-controller/->CloseEditUserDialog id))])])

(defn- delete-user-action [e! {:keys [id show-delete-modal? other-members] :as user}]
  [:span
   [ui/icon-button {:id       (str "delete-user-" id)
                    :href     "#"
                    :on-click #(do
                                 (.preventDefault %)
                                 (e! (admin-controller/->OpenDeleteUserModal id)))}
    [ic/action-delete]]
   (when show-delete-modal?
     (let [admin-list (mapv #(if (> (count (:members %)) 0) true false) other-members)]
       [ui/dialog
        {:open true
         :actionsContainerStyle style-dialog/dialog-action-container
         :title "Poista käyttäjä"
         :actions [(r/as-element
                     [ui/flat-button
                      {:label (tr [:buttons :cancel])
                       :primary true
                       :on-click #(do
                                    (.preventDefault %)
                                    (e! (admin-controller/->CancelDeleteUser id)))}])
                   (if (some false? admin-list)
                     nil
                     (r/as-element
                       [ui/raised-button
                        {:label (tr [:buttons :delete])
                         :icon (ic/action-delete-forever)
                         :secondary true
                         :primary true
                         :on-click #(do
                                      (.preventDefault %)
                                      (e! (admin-controller/->ConfirmDeleteUser id)))}]))]}
        [:div
         (if (some false? admin-list)
           [:p "Ei voida poistaa käyttäjää."]
           [:div
            [:p "Oletko varma, että haluat poistaa käyttäjän?"]
            [:p (str "Käyttäjän id: " id)]

            [form-fields/field {:name        :ensured-id
                                :type        :string
                                :full-width? true
                                :label       "Anna varmistukseksi käyttäjän id"
                                :update!     #(e! (admin-controller/->EnsureUserId id %))}
             (:ensured-id user)]])]]))])


(def groups-header-style {:height "10px" :padding "5px 0 5px 0"})
(def groups-row-style {:height "20px" :padding 0})

(defn groups-list [groups]
  [:div {:style {:border-left "1px solid rgb(224, 224, 224)"}}
   (if (seq groups)
     [:div {:style {:padding "0 0 15px 10px"}}

      [ui/table {:selectable   false
                 :fixed-header true
                 :body-style   {:overflow-y "auto"
                                :max-height "100px"}}
       [ui/table-header {:adjust-for-checkbox false
                         :display-select-all  false}
        [ui/table-row {:style groups-header-style}
         [ui/table-header-column {:style groups-header-style} "Nimi"]
         [ui/table-header-column {:style groups-header-style} "ID"]]]
       [ui/table-body {:display-row-checkbox false
                       :show-row-hover       true}

        (doall
          (for [{:keys [title name]} groups]
            ^{:key (str "group-" name)}
            [ui/table-row {:selectable false
                           :style      groups-row-style}
             [ui/table-row-column {:style groups-row-style} title]
             [ui/table-row-column {:style groups-row-style} name]]))]]]
     [:div {:style {:padding-left "10px"
                    :line-height  "48px"}}
      "Ei palveluntuottajia."])])

(defn users-page-controls [e! app]
  [:div {:style {:margin-bottom "25px"}}
   [form-fields/field {:style   {:margin-right "10px"}
                       :type    :string :label "Nimen tai sähköpostiosoitteen osa"
                       :update! #(e! (admin-controller/->UpdateUserFilter %))
                       :on-enter #(e! (admin-controller/->SearchUsers))}
    (get-in app [:admin :user-listing :user-filter])]

   [ui/raised-button {:primary  true
                      :disabled (str/blank? filter)
                      :on-click #(e! (admin-controller/->SearchUsers))
                      :label    "Hae käyttäjiä"}]])

(defn user-listing [e! app]
  (let [{:keys [loading? results]} (get-in app [:admin :user-listing])]
    [:div
     (if loading?
       [:span "Ladataan käyttäjiä..."]
       [:div {:style {:margin-bottom "10px"}} "Hakuehdoilla löytyi " (count results) " käyttäjää."])

     (when (seq results)
       [:span
        [ui/table {:selectable    false
                   :fixed-header  false
                   :style         {:width "auto" :table-layout "auto"}
                   :wrapper-style {:border-style "solid"
                                   :border-color "rgb(224, 224, 224)"
                                   :border-width "1px 1px 0 1px"}}
         [ui/table-header {:adjust-for-checkbox false
                           :display-select-all  false}
          [ui/table-row
           [ui/table-header-column "Käyttäjätunnus"]
           [ui/table-header-column "Nimi"]
           [ui/table-header-column "Sähköposti"]
           [ui/table-header-column "Palveluntuottajat"]
           [ui/table-header-column "Toiminnot"]]]

         [ui/table-body {:display-row-checkbox false}
          (doall
            (for [{:keys [id username name email groups] :as user} results]
              ^{:key (str "user-" username)}
              [ui/table-row {:style      {:border-bottom "3px solid rgb(224, 224, 224)"}
                             :selectable false}
               [ui/table-row-column username]
               [ui/table-row-column name]
               [ui/table-row-column email]
               [ui/table-row-column {:style {:padding 0}}
                [groups-list groups]]
               [ui/table-row-column
                [delete-user-action e! user]
                [edit-user-action e! user]]]))]]])]))
