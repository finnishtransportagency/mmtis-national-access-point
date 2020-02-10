(ns ote.views.admin.company-csv
  "CSV files that are uploaded by users."
  (:require [reagent.core :as r]
            [cljs-react-material-ui.reagent :as ui]
            [clojure.string :as str]
            [stylefy.core :as stylefy]
            [ote.localization :refer [tr tr-key]]
            [ote.time :as time]
            [ote.style.base :as style-base]
            [ote.ui.common :refer [linkify]]
            [ote.app.controller.admin :as admin]
            [ote.style.dialog :as style-dialog]))

(defn page-controls [e! app]
  [:div])

(defn company-csv-list [e! app]
  (r/create-class
    {:component-will-mount #(e! (admin/->FetchCompanyCsvs))
     :reagent-render
     (fn [e! app]
       (let [csv-files (get-in app [:admin :company-csv :results])
             loading? (get-in app [:admin :company-csv :loading?])
             show-validation-modal? (get-in app [:admin :company-csv :open-validation-warning?])
             ;; validation warning is as string in database. Converting it to map
             warning-text (cljs.reader/read-string
                            (get-in app [:admin :company-csv :validation-warning]))]
         [:div.row
          (when loading?
            [:p "Ladataan csv tiedostoja..."])
          (when csv-files
            [:span
             [ui/table {:selectable false}
              [ui/table-header {:class "table-header-wrap"
                                :adjust-for-checkbox false
                                :display-select-all false}

               [ui/table-row
                [ui/table-header-column {:class "table-header-wrap" :style {:width "20%"}} "Palvelu"]
                [ui/table-header-column {:class "table-header-wrap" :style {:width "20%"}} "S3 tiedostoavain"]
                [ui/table-header-column {:class "table-header-wrap" :style {:width "20%"}} "Tiedoston nimi"]
                [ui/table-header-column {:class "table-header-wrap" :style {:width "15%"}} "Validointivaroitukset"]
                [ui/table-header-column {:class "table-header-wrap" :style {:width "7%"}} "Toimivat rivit"]
                [ui/table-header-column {:class "table-header-wrap" :style {:width "7%"}} "Hajonneet rivit"]
                [ui/table-header-column {:class "table-header-wrap" :style {:width "11%"}} "Ladattu"]]]
              [ui/table-body {:display-row-checkbox false}
               (doall
                 (for [{:keys [id service-name file-key csv-file-name validation-warning created failed-companies-count valid-companies-count] :as r} csv-files]
                   ^{:key id}
                   [ui/table-row {:selectable false}
                    [ui/table-row-column (merge (stylefy/use-style style-base/table-col-style-wrap) {:width "20%"})
                     service-name]
                    [ui/table-row-column (merge (stylefy/use-style style-base/table-col-style-wrap) {:width "20%"})
                     file-key]
                    [ui/table-row-column (merge (stylefy/use-style style-base/table-col-style-wrap) {:width "20%"})
                     (linkify (str "transport-service/company-csv/" file-key) csv-file-name {:target "_blank"})]
                    [ui/table-row-column (merge (stylefy/use-style style-base/table-col-style-wrap) {:width "15%"})
                     (when validation-warning
                       (.log js/console "warning-text 1" (:corrupted-headers warning-text))
                       (.log js/console "warning-text 2" warning-text)
                       [:button {:on-click #(e! (admin/->OpenValidationWarningModal validation-warning))}
                        "Tiedostossa vaarallisia merkkejä."])]
                    [ui/table-row-column (merge (stylefy/use-style style-base/table-col-style-wrap) {:width "7%"})
                     valid-companies-count]
                    [ui/table-row-column (merge (stylefy/use-style style-base/table-col-style-wrap) {:width "7%"})
                     failed-companies-count]
                    [ui/table-row-column (merge (stylefy/use-style style-base/table-col-style-wrap) {:width "11%"})
                     (time/format-timestamp-for-ui created)]]))]]])

          (when show-validation-modal?
            [ui/dialog
             {:open true
              :actionsContainerStyle style-dialog/dialog-action-container
              :title "Vaarallisia merkkejä löydetty csv tiedostosta"
              :actions [(r/as-element
                          [ui/flat-button
                           {:label (tr [:buttons :cancel])
                            :primary true
                            :on-click #(do
                                         (.preventDefault %)
                                         (e! (admin/->CloseValidationWarningModal)))}])]}

             [:div
              [:div "Headerissä havaitut haitalliset merkit " (str (:corrupted-headers warning-text))]
              [:div "Yrityslistauksessa havaitut haitalliset merkit "]
              (for [row-map (:corrupted-data warning-text)]
                [:div "Rivi: " (:row row-map) " Virheellinen data: " (:error row-map)])]])]))}))
