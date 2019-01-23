(ns ote.views.service-search
  "A service search page that allows filtering and listing published services."
  (:require [reagent.core :as r]
            [ote.db.transport-service :as t-service]
            [ote.db.transport-operator :as t-operator]
            [ote.db.common :as common]
            [ote.localization :refer [tr tr-key]]
            [ote.ui.form-fields :as form-fields]
            [ote.ui.buttons :as buttons]
            [ote.ui.form :as form]
            [ote.ui.common :as common-ui]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]
            [ote.ui.icons :as icons]
            [ote.app.controller.service-search :as ss]
            [ote.style.base :as style-base]
            [ote.style.service-search :as style]
            [stylefy.core :as stylefy]
            [clojure.string :as str]
            [ote.views.ckan-service-viewer :as ckan-service-viewer]
            [ote.app.controller.admin :as admin]
            [tuck.core :as tuck]
            [ote.ui.validation :as validation]
            [ote.util.text :as text]
            [ote.ui.page :as page]
            [ote.app.utils :as utils]))

(defn- delete-service-action [e! id name show-delete-modal?]
  [:div {:style {:color "#fff"}}
   [ui/icon-button {:href "#"
                    :style style/delete-button
                    :on-click #(do
                                 (.preventDefault %)
                                 (e! (admin/->DeleteTransportService id)))}
    [ic/action-delete {:class-name (:class (stylefy/use-style style/delete-icon))
                       :style style/partly-visible-delete-icon}]]
   (when show-delete-modal?
     [ui/dialog
      {:open true
       :title (tr [:dialog :delete-transport-service :title])
       :actions [(r/as-element
                   [buttons/cancel
                    {:on-click #(e! (admin/->CancelDeleteTransportService id))}
                    (tr [:buttons :cancel])])
                 (r/as-element
                   [buttons/delete
                    {:on-click #(e! (admin/->ConfirmDeleteTransportService id))}
                    (tr [:buttons :delete])])]}
      (tr [:dialog :delete-transport-service :confirm] {:name name})])])

(defn data-item [icon item]
  [:div (stylefy/use-style style/data-items)
   [:div (stylefy/use-style style-base/item-list-row-margin)
    (when (and icon item)
      [:div (stylefy/use-style style/icon-div) icon])
    (when item
      [:div (stylefy/use-style style-base/item-list-item)
       item])]])

(defn- format-address [{::common/keys [street postal_code post_office]}]
  (let [comma (if (not (empty? street)) ", " " ")]
    (str street comma postal_code " " post_office)))

(defn- gtfs-viewer-link [{interface ::t-service/external-interface [format] ::t-service/format
                          has-errors? :has-errors?}]
  (when format
    (let [format (str/lower-case format)]
      (when (or (= "gtfs" format) (= "kalkati.net" format))
        [:span " | "
         (if-not has-errors?
           (common-ui/linkify
             (str "#/routes/view-gtfs?url=" (.encodeURIComponent js/window
                                                                 (::t-service/url interface))
                  (when (= "kalkati.net" format)
                    "&type=kalkati"))
             [:span
              [ic/action-open-in-new {:style {:position "relative" :top "6px" :color "#06c" :padding-right "3px"}}]
              (tr [:service-search :view-routes])]
             {:target "_blank"})
           [:span
            [ic/alert-warning {:style {:position "relative" :top "6px" :color "#f80" :padding-right "3px"}}]
            (tr [:service-search :view-routes-failure])])]))))

(defn parse-content-value [value-array]
  (let [data-content-value #(tr [:enums ::t-service/interface-data-content %])
        value-str (str/join ", " (map #(data-content-value %) value-array))
        return-value (text/maybe-shorten-text-to 45 value-str)]
    return-value))

(defn- external-interface-links [{::t-service/keys [id external-interface-links transport-operator-id]}]
  (let [nap-url (str js/window.location.origin "/ote/export/geojson/" transport-operator-id "/" id)]
    [:div {:key id}
     [:div.row (stylefy/use-style style/link-result-card-row)
      [common-ui/linkify nap-url "NAP-rajapinta" {:target "_blank"}]
      [:span " | "]
      [:span "GeoJSON"]]
     (when-not (empty? external-interface-links)
       [:div.row
        (doall
          (map-indexed
            (fn [i {::t-service/keys [external-interface format data-content] :as row}]
              (let [data-content (if (nil? data-content)
                                   (::t-service/url external-interface)
                                   (parse-content-value data-content))]
                [:div.row (merge {:key (str i "_" id)}
                                 (stylefy/use-style style/link-result-card-row))
                 [common-ui/linkify (::t-service/url external-interface) data-content {:target "_blank" :style {:color "#06c"}}]
                 [:span " | " (str/join ", " format)]
                 [gtfs-viewer-link row]]))
            external-interface-links))])]))

(defn- list-service-companies [service-companies service-search]
  (when (seq service-companies)
    (let [company-list-max-size 3
          service-company-count (count service-companies)
          searched-business-ids (str/split (get-in service-search [:params :operators]) ",")
          found-business-ids (keep (fn [sc]
                                     (let [s (keep #(when (= (::t-service/business-id sc) %) sc) searched-business-ids)]
                                       (when (not (empty? s)) (first s))))
                                   service-companies)
          presented-companies-count (if (not (empty? found-business-ids))
                                      (count found-business-ids)
                                      company-list-max-size)
          extra-companies (- service-company-count presented-companies-count)]
      [:div
       [:h4 (tr [:service-search :other-involved-companies])]
       ;; Show searched companies or list involved companies
       (if (not (empty? found-business-ids))
         [:div
          (doall (for [c found-business-ids]
                   (when (::t-service/name c)
                     [:div.row (merge {:key (::t-service/business-id c)}
                                      (stylefy/use-style style/simple-result-card-row))
                      (str (::t-service/name c) " (" (::t-service/business-id c) ")")])))
          (when (> extra-companies 0)
            [:div.row (stylefy/use-style style/simple-result-card-row)
             (str " + " extra-companies (tr [:service-search :other-company]))])]
         ;; List only three or show company count
         (if (> service-company-count company-list-max-size)
           [:div.row (stylefy/use-style style/simple-result-card-row)
            (str service-company-count (tr [:service-search :other-company]))]
           (doall
             (for [c (take company-list-max-size service-companies)]
               (when (::t-service/name c)
                 [:div.row (merge {:key (::t-service/business-id c)}
                                  (stylefy/use-style style/simple-result-card-row))
                  (str (::t-service/name c) " (" (::t-service/business-id c) ")")])))))])))

(defn- result-card [e! admin?
                    {::t-service/keys [id name sub-type contact-address
                                       description contact-phone contact-email
                                       operator-name business-id transport-operator-id service-companies companies]
                     :as              service} service-search card-width]
  (let [sub-type-tr (tr-key [:enums ::t-service/sub-type])
        e-links [external-interface-links service]
        service-desc (t-service/localized-text-for "FI" description)
        service-companies (cond
                            (and service-companies companies) (merge service-companies companies)
                            (and service-companies (empty? companies)) service-companies
                            :else companies)
        open-link (fn [content]
                    [:a {:style (merge {:color "#fff"} style/result-card-header-link)
                         :href (str "/#/service/" transport-operator-id "/" id)}
                     content])
        card-padding (if (< card-width 767) "15px" "30px")]
    [:div.result-card (stylefy/use-style style/result-card)
     [:div
      [:div.result-title (stylefy/use-style (merge {:padding-left card-padding} style/result-card-title))
       [:div (stylefy/use-sub-style style/result-card-title :title)
        (open-link name)]
       [:div (stylefy/use-sub-style style/result-card-title :actions)
        (open-link
          [:div
           (tr [:service-search :show-all-information])
           [ic/navigation-chevron-right {:style {:position "relative" :top "6px"} :color "#fff" :height 24}]])
        (when admin?
          [:div {:style {:margin-left "20px"}}
           [delete-service-action e! id name (get service :show-delete-modal?)]])]]]


     [:div.row.result-body (stylefy/use-style style/result-card-body)
      [:div.col-sm-8.col-md-8 {:style {:padding-left card-padding}}
       [:h4 (stylefy/use-style style/result-card-header) (sub-type-tr sub-type)]
       (when-not (empty? service-desc)
         [:div.description {:style {:padding-bottom "20px"}}
          (common-ui/shortened-description service-desc 270)])

       [:div.result-interfaces e-links]]
      [:div.col-sm-3.col-md-3 {:style {:padding-left card-padding}}
       [:h4 (stylefy/use-style style/result-card-header) operator-name]
       [:div (stylefy/use-style style/simple-result-card-row)
        (str (tr [:field-labels :ote.db.transport-operator/business-id]) ": " business-id)]
       [:div (stylefy/use-style style/simple-result-card-row)
        (format-address contact-address)]
       (when contact-phone
         [:div (stylefy/use-style style/simple-result-card-row)
          (str contact-phone)])
       (when contact-email
         [:div (stylefy/use-style style/simple-result-card-row)
          contact-email])
       (list-service-companies service-companies service-search)]]]))

(defn results-listing [e! {service-search :service-search user :user :as app}]
  (let [{:keys [results empty-filters? total-service-count total-company-count
                filter-service-count fetching-more?]} service-search
        operator (:operator (:params app))]
    [:div.container
      [:div.col-xs-12.col-md-12.col-lg-12
       [:span
        (if operator
          (tr (if (zero? filter-service-count)
                [:service-search :operator-no-services]
                [:service-search :operator-services])
              {:name (::t-service/operator-name (first results))
               :count filter-service-count})
          (tr [:service-search (if empty-filters?
                                 :showing-latest-services
                                 :result-count)]
              {:count filter-service-count}))
        " "
        (tr [:service-search :total-services] {:total-service-count total-service-count
                                               :total-company-count total-company-count})]
       (doall
         (for [result results]
           ^{:key (::t-service/id result)}
           [result-card e! (:admin? user) result service-search (:width app)]))

       (if fetching-more?
         [:span (tr [:service-search :fetching-more])]
         (when (> filter-service-count (count results))
           [common-ui/scroll-sensor #(e! (ss/->FetchMore))]))]]))


(defn- capitalize-operation-area-postal-code [sentence]
  (str/replace sentence #"([^A-Öa-ö0-9_])(\w)"
               (fn [[_ before capitalize]]
                 (str before (str/upper-case capitalize)))))

(defn- sort-places [places]
  (when-not (nil? places)
    (let [names (filter #(re-matches #"^\D+$" (:text %)) places)
          names (mapv #(update % :text str/capitalize) names)
          ;; Place names starting with a number, like postal code areas
          numeric (filter #(re-matches #"^\d.*" (:text %)) places)
          numeric (mapv (fn [val]
                          (update val :text capitalize-operation-area-postal-code))
                        numeric)]
      (concat
        (sort-by :text names)
        (sort-by :text numeric)))))

(def transport-types [:road :rail :sea :aviation])

(defn filters-form [e! {filters :filters
                        facets  :facets}]
  (let [sub-types-to-list (fn [data]
                            (keep (fn [val]
                                    (let [subtype (:sub-type val)]
                                      (when-not (= :other subtype)
                                        (into (sorted-map)
                                              (-> val
                                                  (dissoc :sub-type)
                                                  (assoc :value subtype
                                                         :text (tr [:enums ::t-service/sub-type subtype])))))))
                                  data))
        transport-types-to-list (fn [data]
                                  (keep (fn [val]
                                          (into (sorted-map)
                                                (assoc {} :text (tr [:enums ::t-service/transport-type val])
                                                          :value val)))
                                        data))]

    [:div
     [:h2 {:style {:font-weight 500 }} (tr [:service-search :limit-search-results])]
     [form/form {:update! #(e! (ss/->UpdateSearchFilters %))
                 :name->label (tr-key [:service-search]
                                      [:field-labels :transport-service-common]
                                      [:field-labels :transport-service])}
      [(form/group
         {:columns 3
          :layout :raw
          :card? false}

         {:type :component
          :name :operators
          :full-width? true
          :container-class "col-xs-12 col-sm-4 col-md-4"
          :container-style {:padding-right "10px"}
          :component (fn [{data :data}]
                       [form-fields/field
                        {:type :chip-input
                         :label (tr [:service-search :operator-search])
                         :full-width? true
                         :full-width-input? false
                         :hint-text (tr [:service-search :operator-search-placeholder])
                         :hint-style {:top "20px"}
                         ;; No filter, back-end returns what we want
                         :filter (constantly true)
                         :suggestions-config {:text :operator :value :business-id}
                         ;; Filter away transport-operators that have no business-id. (Note: It should be mandatory!)
                         :suggestions (filter :business-id (:results data))
                         :open-on-focus? true
                         :on-update-input (utils/debounce #(e! (ss/->SetOperatorName %)) 300)
                         ;; Select first match from autocomplete filter result list after pressing enter
                         :auto-select? true
                         :on-request-add (fn [chip]
                                           (e! (ss/->AddOperator
                                                 (:business-id chip)
                                                 (:operator chip)))
                                           (e! (ss/->UpdateSearchFilters nil))
                                           chip)
                         :on-request-delete (fn [chip-val]
                                              (e! (ss/->RemoveOperatorById chip-val))
                                              (e! (ss/->UpdateSearchFilters nil)))}
                        (:chip-results data)])}

         {:name :text-search
          :type :string
          :container-class "col-xs-12 col-sm-4 col-md-4"
          :full-width? true
          :hint-text (tr [:service-search :text-search-placeholder])}

         {:id "transport-types"
          :name ::t-service/transport-type
          :type :chip-input
          :container-class "col-xs-12 col-sm-4 col-md-4"
          :full-width? true
          :full-width-input? false
          :suggestions-config {:text :text :value :text}
          :suggestions (transport-types-to-list transport-types)
          :open-on-focus? true})

         (form/group
           {:columns 3
            :layout :raw
            :card? false}

         {:name ::t-service/operation-area
          :type :chip-input
          :container-class "col-xs-12 col-sm-4 col-md-4"
          :hint-text (tr [:service-search :operation-area-search-placeholder])
          :hint-style {:top "20px"}
          :full-width? true
          :full-width-input? false
          :filter (fn [query, key]
                    (let [k (str/lower-case key)
                          q (str/lower-case query)]
                      (when (> (count q) 1)
                        (if (re-matches #"^\D+" q)
                          (str/starts-with?
                            (second (re-matches #"(?:[0-9]+\s*)?(.*)$" k)) q)
                          (str/starts-with? k q)))))
          :open-on-focus? true
          :max-results 10
          :suggestions-config {:text :text :value :text}
          :suggestions (sort-places (::t-service/operation-area facets))
          ;; Select first match from autocomplete filter result list after pressing enter
          :auto-select? true}

         {:id "sub-types"
          :name ::t-service/sub-type
          :label (tr [:service-search :type-search])
          :type :chip-input
          :container-class "col-xs-12 col-sm-4 col-md-4"
          :full-width? true
          :full-width-input? false
          :suggestions-config {:text :text :value :text}
          :suggestions (sub-types-to-list (::t-service/sub-type facets))
          :open-on-focus? true}

         {:name               ::t-service/data-content
          :label              (tr [:service-search :data-content-search-label])
          :type               :chip-input
          :full-width?        true
          :container-class    "col-xs-12 col-sm-4 col-md-4"
          :auto-select?       true
          :open-on-focus?     true
          ;; Translate visible suggestion text, but keep the value intact.
          :suggestions        (mapv (fn [val]
                                      {:text  (tr [:enums ::t-service/interface-data-content val])
                                       :value val})
                                    t-service/interface-data-contents)
          :suggestions-config {:text :text :value :value}
          :is-empty?          validation/empty-enum-dropdown?})]
      filters]]))


(defn service-geojson [e! {:keys [resource geojson loading-geojson?]}]
  [:div.service-geojson
   [common-ui/linkify "/#/services" [:span [icons/arrow-back {:position "relative"
                                                              :top "6px"
                                                              :padding-right "5px"
                                                              :color style-base/link-color}]
                                     (tr [:service-search :back-link])]]
   [:span
    [:h1 (str (get-in resource ["features" 0 "properties" "transport-service" "name"])
              " GeoJSON")]

    (when (false? loading-geojson?)
      [ckan-service-viewer/viewer e! {:resource resource
                                      :geojson geojson}])]])

(defn service-search [e! _]
  (r/create-class
    {:component-will-unmount #(e! (ss/->SaveScrollPosition))
     :component-did-mount    #(e! (ss/->RestoreScrollPosition))
     :reagent-render
       (fn [e! {{results :results :as service-search} :service-search
                params                                :params
                :as                                   app}]
         [:div.service-search
          [page/page-controls
           ""
           (tr [:service-search :label])
           [filters-form e! service-search]]
           (if (nil? results)
             [:div (tr [:service-search :no-filters])]
             [results-listing e! app])])}))
