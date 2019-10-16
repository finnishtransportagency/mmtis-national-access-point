(ns ote.app.controller.transport-service
  "Transport operator controls "                            ;; FIXME: Move transport-service related stuff to other file
  (:require [tuck.core :as tuck :refer-macros [define-event]]
            [ote.communication :as comm]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [testdouble.cljs.csv :as csv]
            [ote.time :as time]
            [ote.util.csv :as csv-util]
            [ote.localization :refer [tr tr-key tr-tree]]
            [ote.db.transport-service :as t-service]
            [ote.db.transport-operator :as t-operator]
            [ote.db.common :as common]
            [ote.ui.form :as form]
            [ote.ui.validation :as validation]
            [ote.app.routes :as routes]
            [ote.app.controller.place-search :as place-search]
            [ote.app.controller.front-page :as front-page]
            [ote.app.controller.common :refer [->ServerError]]))

(defn- pre-set-transport-type [app]
  (let [sub-type (get-in app [:transport-service ::t-service/sub-type])
        set-transport-type (fn [app service-type options]
                                 (assoc-in app [:transport-service service-type ::t-service/transport-type] options))]
    (cond
      (= sub-type :taxi) (set-transport-type app ::t-service/passenger-transportation #{:road})
      (= sub-type :parking) (set-transport-type app ::t-service/parking #{:road})
      :else app)))

(defn service-type-from-sub-type
      "Returns service type keyword based on sub-type."
  [type]
  (case type
    :taxi :passenger-transportation
    :request :passenger-transportation
    :schedule :passenger-transportation
    :terminal :terminal
    :rentals :rentals
    :parking :parking
    :passenger-transportation))

(define-event CreateServiceNavigate [operator-id sub-type]
  {}
  ;; Set transport-operator and sub-type
  (pre-set-transport-type
    (-> app
        (assoc :transport-operator (->> app :transport-operators-with-services
                                        (map :transport-operator)
                                        (filter #(= (::t-operator/id %) operator-id))
                                        first)
               :transport-service (merge (:transport-service app)
                                         (when sub-type
                                           {::t-service/sub-type sub-type
                                            ::t-service/type (service-type-from-sub-type sub-type)}))))))

(define-event ShowBrokeringServiceDialog []
  {}
  (let [brokerage-selected? (get-in app [:transport-service ::t-service/passenger-transportation ::t-service/brokerage?])]
    (if-not brokerage-selected?
      (assoc-in app [:transport-service :show-brokering-service-dialog?] true)
      app)))

(define-event SelectBrokeringService [select-type]
  {}
  (let [type (get-in app [:transport-service ::t-service/type])
        type-key (t-service/service-key-by-type type)]
  (-> app
      (assoc-in [:transport-service :show-brokering-service-dialog?] false )
      (assoc-in [:transport-service type-key ::t-service/brokerage?] select-type))))

;;; Navigation hook events for new service creation and editing

(defmethod routes/on-navigate-event :new-service [{p :params}]
  (->CreateServiceNavigate (js/parseInt (:operator-id p))
                           (keyword (:sub-type p))))

(defmethod routes/on-navigate-event :transport-service [{p :params}]
  (->CreateServiceNavigate (js/parseInt (:operator-id p)) nil))

(declare ->ModifyTransportService)

(defmethod routes/on-navigate-event :edit-service [{p :params}]
  (->ModifyTransportService (:id p)))

(defn new-transport-service [app]
  (pre-set-transport-type
   (update app :transport-service select-keys #{::t-service/type ::t-service/sub-type})))

(def service-level-keys
  #{::t-service/contact-address
    ::t-service/contact-phone
    ::t-service/contact-email
    ::t-service/homepage
    ::t-service/name
    ::t-service/external-interfaces
    ::t-service/operation-area
    ::t-service/companies
    ::t-service/published
    ::t-service/brokerage?
    ::t-service/description
    ::t-service/available-from
    ::t-service/available-to
    ::t-service/notice-external-interfaces?
    ::t-service/companies-csv-url
    ::t-service/company-source
    ::t-service/company-csv-filename
    :csv-count
    ::t-service/transport-type})



(defrecord AddPriceClassRow [])
(defrecord AddServiceHourRow [])
(defrecord RemovePriceClassRow [])
(defrecord NavigateToNewService [])

(defrecord ModifyTransportService [id])
(defrecord ModifyTransportServiceResponse [response])
(defrecord OpenTransportServicePage [id])
(defrecord OpenTransportServiceTypePage [])


(defrecord DeleteTransportService [id])
(defrecord ConfirmDeleteTransportService [id])
(defrecord CancelDeleteTransportService [id])
(defrecord DeleteTransportServiceResponse [response])
(defrecord FailedDeleteTransportServiceResponse [response])

(defrecord PublishTransportService [transport-service-id])
(defrecord PublishTransportServiceResponse [success? transport-service-id])

(defrecord EditTransportService [form-data])
(defrecord SaveTransportService [schemas publish?])
(defrecord SaveTransportServiceResponse [response])
(defrecord FailedTransportServiceResponse [response])
(defrecord CancelTransportServiceForm [])

(defrecord SelectServiceType [data])
(defrecord SetNewServiceType [type])

(defrecord EnsureCsvFile [])
(defrecord EnsureCsvFileResponse [response])
(defrecord FailedCsvFileResponse [response])

(defrecord EnsureExternalInterfaceUrl [url format])
(defrecord EnsureExternalInterfaceUrlResponse [response url format])
(defrecord FailedExternalInterfaceUrlResponse [])

(defrecord AddImportedCompaniesToService [csv filename])

(declare move-service-level-keys-from-form
         move-service-level-keys-to-form)

(defn- update-service-by-id [app id update-fn & args]
  (update app :transport-service-vector
          (fn [services]
            (map #(if (= (::t-service/id %) id)
                    (apply update-fn % args)
                    %)
                 services))))

(defmulti transform-save-by-type
  "Transform transport service before sending it to the server.
  Dispatches on the type. By default, returns service as is."
  ::t-service/type)

(defmethod transform-save-by-type :rentals [service]
  (-> service
      (update-in [::t-service/rentals ::t-service/pick-up-locations]
                 (fn [pick-up-locations]
                   (map (fn [{hours-and-exceptions ::t-service/service-hours-and-exceptions :as pick-up-location}]
                          (as-> pick-up-location loc
                            (if-let [hours (::t-service/service-hours hours-and-exceptions)]
                              (assoc loc ::t-service/service-hours hours)
                              loc)
                            (if-let [exceptions (::t-service/service-exceptions hours-and-exceptions)]
                              (assoc loc ::t-service/service-exceptions exceptions)
                              loc)
                            (if-let [info (::t-service/service-hours-info hours-and-exceptions)]
                              (assoc loc ::t-service/service-hours-info info)
                              loc)
                            (dissoc loc ::t-service/service-hours-and-exceptions)))
                        pick-up-locations)))
      (update-in [::t-service/rentals ::t-service/vehicle-classes]
                 (fn [vehicle-classes]
                   (mapv (fn [{prices-and-units :price-group :as price-group}]
                           (as-> price-group price
                             (if-let [prices (::t-service/price-classes prices-and-units)]
                               (assoc price ::t-service/price-classes prices)
                               price)
                             (dissoc price :price-group)))
                         vehicle-classes)))))

(defmethod transform-save-by-type :default [service] service)

(defmulti transform-edit-by-type
  "Transform transport service for editing after receiving it from the server.
  Dispatches on the type. By default, returns service as is."
  ::t-service/type)

(defmethod transform-edit-by-type :rentals [service]
  (-> service
      (update-in [::t-service/rentals ::t-service/pick-up-locations]
                 (fn [pick-up-locations]
                   (mapv (fn [{hours ::t-service/service-hours
                               exceptions ::t-service/service-exceptions
                               info ::t-service/service-hours-info
                               :as pick-up-location}]
                           (-> pick-up-location
                               (assoc ::t-service/service-hours-and-exceptions
                                      {::t-service/service-hours hours
                                       ::t-service/service-exceptions exceptions
                                       ::t-service/service-hours-info info})
                               (dissoc ::t-service/service-hours
                                       ::t-service/service-exceptions)))
                         pick-up-locations)))
      (update-in [::t-service/rentals ::t-service/vehicle-classes]
                 (fn [vehicle-classes]
                   (mapv (fn [{price-classes ::t-service/price-classes
                               :as vehicle-class}]
                           (-> vehicle-class
                               (assoc :price-group
                                      {::t-service/price-classes price-classes})
                               (dissoc ::t-service/price-classes)))
                         vehicle-classes)))))

(defmethod transform-edit-by-type :default [service] service)

(defn- add-service-for-operator [app service]
  ;; Add service for currently selected transport operator and transport-operator-vector
  (as-> app app
      (update app :transport-operators-with-services
              (fn [operators-with-services]
                (map (fn [operator-with-services]
                       (if (= (get-in operator-with-services [:transport-operator ::t-operator/id])
                              (::t-service/transport-operator-id service))
                         (update operator-with-services :transport-service-vector
                                 (fn [services]
                                   (let [service-idx (first (keep-indexed (fn [i s]
                                                                            (when (= (::t-service/id s)
                                                                                     (::t-service/id service))
                                                                              i)) services))]
                                     (if service-idx
                                       (assoc (vec services) service-idx service)
                                       (conj (vec services) service)))))
                         operator-with-services))
                     operators-with-services)))
      (assoc app :transport-service-vector
                 (some #(when (= (get-in % [:transport-operator ::t-operator/id])
                                 (get-in app [:transport-operator ::t-operator/id]))
                          (:transport-service-vector %))
                       (:transport-operators-with-services app)))))

(defn pul-country->country-code [pick-up-addresses]
  (mapv
    (fn [p]
      (let [pick-up-country (get-in p [::t-service/pick-up-address :country])
            country-code (some #(when (= pick-up-country (second %))
                                  (name (first %)))
                               (tr-tree [:country-list]))]
        (if (some? pick-up-addresses)
          (assoc-in p [::t-service/pick-up-address ::common/country_code] country-code)
          p)))
    pick-up-addresses))

(defn pul-country-code->country [pick-up-addresses]
  (mapv
    (fn [p]
      (let [country-code (get-in p [::t-service/pick-up-address ::common/country_code])
            pick-up-country (some #(when (= country-code (name (first %)))
                                  (second %))
                                  (tr-tree [:country-list]))]
        (if (and (some? country-code) (some? pick-up-country))
          (assoc-in p [::t-service/pick-up-address :country] pick-up-country)
          p)))
    pick-up-addresses))

(defn country->country-code [app service]
  (let [key (t-service/service-key-by-type (::t-service/type service))
        country (get-in service [key ::t-service/contact-address :country])
        country-code (some #(when (= country (second %))
                         (name (first %)))
                           (tr-tree [:country-list]))
       app (if (= :rentals (::t-service/type service))
              (update-in app [:transport-service key ::t-service/pick-up-locations]
                         #(pul-country->country-code %))
              app)]
    (if (some? service)
      (assoc-in app [:transport-service key ::t-service/contact-address ::common/country_code] country-code)
      app)))

(defn country-code->country [app service]
  (let [key (t-service/service-key-by-type (::t-service/type service))
        country-code (get-in service [key ::t-service/contact-address ::common/country_code])
        country (some #(when (= country-code (name (first %)))
                         (second %))
                      (tr-tree [:country-list]))
        app (if (= :rentals (::t-service/type service))
              (update-in app [:transport-service key ::t-service/pick-up-locations]
                         #(pul-country-code->country %))
              app)]
    (if (and (some? country-code) (some? country))
      (assoc-in app [:transport-service key ::t-service/contact-address :country] country)
      app)))

(extend-protocol tuck/Event

  AddPriceClassRow
  (process-event [_ app]
    (update-in app [:transport-service ::t-service/passenger-transportation ::t-service/price-classes]
               #(conj (or % []) {::t-service/currency "EUR"})))

  AddServiceHourRow
  (process-event [_ app]
    (update-in app [:transport-service ::t-service/passenger-transportation ::t-service/service-hours]
               #(conj (or % []) {::t-service/from (time/parse-time "08:00")})))

  RemovePriceClassRow
  (process-event [_ app]
    (assoc-in app [:t-service :price-class-open] false))

  SelectServiceType
  ;; Set only service type and sub-type
  (process-event [{data :data} app]
    (let [type (service-type-from-sub-type data)
          app (assoc-in app [:transport-service ::t-service/sub-type] data)
          app (assoc-in app [:transport-service ::t-service/type] type)
          ]
      app))

  NavigateToNewService
  ;; Redirect to add service page
  (process-event [_ app]
    (let [app (new-transport-service app)
          sub-type (get-in app [:transport-service ::t-service/sub-type])]
      (routes/navigate! :new-service {:operator-id (-> app :transport-operator ::t-operator/id str)
                                      :sub-type (name sub-type)})
      app))

  OpenTransportServiceTypePage
  ;; :transport-service :<transport-service-type> needs to be cleaned up before creating a new one
  (process-event [_ app]
    (let [app (new-transport-service app)]
      (routes/navigate! :transport-service {:operator-id (-> app :transport-operator ::t-operator/id str)})
      app))

  ModifyTransportService
  (process-event [{id :id} app]
    (comm/get! (str "transport-service/" id)
               {:on-success (tuck/send-async! ->ModifyTransportServiceResponse)})
    (assoc app :transport-service-loaded? false))

  ModifyTransportServiceResponse
  (process-event [{response :response} app]
    (let [type (::t-service/type response)
          app (assoc app
                :transport-service-loaded? true
                :transport-service (-> response
                                       (update ::t-service/operation-area place-search/operation-area-to-places)
                                       (move-service-level-keys-to-form (t-service/service-key-by-type type))
                                       transform-edit-by-type)
                :transport-operator (->> app :transport-operators-with-services
                                         (map :transport-operator)
                                         (filter #(= (::t-operator/id %)
                                                     (::t-service/transport-operator-id response)))
                                         first))
          app (country-code->country app (:transport-service app))]
      app))

  EnsureCsvFile
  (process-event [_ app]
    (let [url (get-in app [:transport-service ::t-service/passenger-transportation ::t-service/companies-csv-url])]
      (when (and url (not (empty? url)))
        (comm/post! (str "check-company-csv")
                    {:url url}
                    {:on-success (tuck/send-async! ->EnsureCsvFileResponse)
                     :on-failure (tuck/send-async! ->FailedCsvFileResponse)}))
    (update-in app [:transport-service ::t-service/passenger-transportation] dissoc :csv-count)))

  EnsureCsvFileResponse
  (process-event [{response :response} app]
    (assoc-in app [:transport-service ::t-service/passenger-transportation :csv-count]  response))

  FailedCsvFileResponse
  (process-event [{response :response} app]
    (assoc-in app [:transport-service ::t-service/passenger-transportation :csv-count] response))

  EnsureExternalInterfaceUrl
  (process-event [{url :url format :format} app]
    (let [on-success (tuck/send-async! ->EnsureExternalInterfaceUrlResponse url format)
          on-failure (tuck/send-async! ->FailedExternalInterfaceUrlResponse)]
      (update-in app [:transport-service (t-service/service-key-by-type (::t-service/type (:transport-service app)))
                      ::t-service/external-interfaces]
                 (fn [external-interfaces]
                   (mapv (fn [{eif ::t-service/external-interface eif-format ::t-service/format :as row}]
                           (if (and (= (::t-service/url eif) url) (= (first eif-format) format))
                             (do
                               (when-let [validation-timeout (:eif-validation-timeout row)]
                                 (.clearTimeout js/window validation-timeout))

                               ;; Debounce the validation to prevent unnecessary validation requests as they
                               ;; can hog server resources.
                               (assoc row :eif-validation-timeout
                                          (.setTimeout
                                            js/window
                                            #(comm/post! (str "check-external-api") {:url url :format format}
                                                         {:on-success on-success
                                                          :on-failure on-failure})
                                            1000)))
                             row))
                         external-interfaces)))))

  EnsureExternalInterfaceUrlResponse
  (process-event [{url :url format :format response :response :as e} app]
    (update-in app [:transport-service (t-service/service-key-by-type (::t-service/type (:transport-service app)))
                    ::t-service/external-interfaces]
               (fn [external-interfaces]
                 (mapv (fn [{eif ::t-service/external-interface eif-format ::t-service/format :as row}]
                         (if (and (= (::t-service/url eif) url) (= (first eif-format) format))
                           (-> row
                               (assoc ::t-service/external-interface (assoc eif :url-status response))
                               (dissoc :eif-validation-timeout))
                           row))
                       external-interfaces))))

  FailedExternalInterfaceUrlResponse
  (process-event [{response :response} app]
    app)

  AddImportedCompaniesToService
  (process-event [{csv :csv filename :filename} app]
    (let [valid? (not (some #(or (empty? (::t-service/name %))
                                 (or (empty? (::t-service/business-id %))
                                     (validation/validate-rule :business-id nil (::t-service/business-id %))))
                            csv))]

      (if valid?
        (update-in app [:transport-service ::t-service/passenger-transportation] assoc
                   ::t-service/companies csv
                   ::t-service/company-csv-filename filename
                   :csv-imported? true
                   :csv-valid? true)
        (update-in app [:transport-service ::t-service/passenger-transportation] assoc
                   ::t-service/company-csv-filename filename
                   :csv-imported? true
                   :csv-valid? false))))

  ;; Use this when navigating outside of OTE. Above methods won't work from NAP.
  OpenTransportServicePage
  (process-event [{id :id} app]
    (set! (.-location js/window) (str "/ote/#/edit-service/" id))
    app)

  PublishTransportService
  (process-event [{:keys [transport-service-id]} app]
    (comm/post! (str "transport-service/publish")
                {:transport-service-id transport-service-id}
                {:on-success (tuck/send-async! ->PublishTransportServiceResponse transport-service-id)})
    app)

  PublishTransportServiceResponse
  (process-event [{success? :success? transport-service-id :transport-service-id :as e} app]

    (if success?
      (update app :transport-services
              (fn [services]
                (map (fn [{id ::t-service/id :as service}]
                       (if (= id transport-service-id)
                         (assoc service ::t-service/published? true)
                         service))
                     services)))
      app))

  DeleteTransportService
  (process-event [{id :id} app]
    (update-service-by-id
     app id
     assoc :show-delete-modal? true))

  CancelDeleteTransportService
  (process-event [{id :id} app]
    (update-service-by-id
     app id
     dissoc :show-delete-modal?))

  ConfirmDeleteTransportService
  (process-event [{id :id} app]
    (comm/post! "transport-service/delete" {:id id}
                {:on-success (tuck/send-async! ->DeleteTransportServiceResponse)
                 :on-failure (tuck/send-async! ->FailedDeleteTransportServiceResponse)})
    app)

  DeleteTransportServiceResponse
  (process-event [{response :response} app]
    (let [filtered-map (filter #(not= (:ote.db.transport-service/id %) (int response)) (get app :transport-service-vector))
          app (assoc app :transport-service-vector filtered-map
                         :flash-message (tr [:common-texts :delete-service-success])
                         :services-changed? true)]
      (front-page/get-transport-operator-data app)
      app))

  FailedDeleteTransportServiceResponse
  (process-event [{response :response} app]
    (assoc app :flash-message-error (tr [:common-texts :delete-service-error])))

  SaveTransportService
  (process-event [{:keys [schemas publish?]} {service :transport-service
                                              operator :transport-operator :as app}]
    (let [key (t-service/service-key-by-type (::t-service/type service))
          operator-id (if (nil? (::t-service/transport-operator-id service))
                        (::t-operator/id operator)
                        (::t-service/transport-operator-id service))
          service-data
          (-> service
              (update key (comp (partial form/prepare-for-save schemas)
                                form/without-form-metadata))
              (dissoc :transport-service-type-subtype
                      :select-transport-operator
                      :show-brokering-service-dialog?)
              (move-service-level-keys-from-form key)
              (assoc ::t-service/published? publish?
                     ::t-service/transport-operator-id operator-id)
              (update ::t-service/operation-area place-search/place-references)
              (update ::t-service/external-interfaces
                       (fn [d]
                         (mapv #(dissoc % :eif-validation-timeout) d)))
              transform-save-by-type)]
      ;; Disable post if concurrent save event is in progress
      (if (not (:service-save-in-progress app))
        (do
          (comm/post! "transport-service" service-data
                      {:on-success (tuck/send-async! ->SaveTransportServiceResponse)
                       :on-failure (tuck/send-async! ->FailedTransportServiceResponse)})
          (-> app
              (assoc :service-save-in-progress true)))
        app)))

  SaveTransportServiceResponse
  (process-event [{response :response} app]
    (let [app (add-service-for-operator
                (assoc app :flash-message (tr [:common-texts :transport-service-saved]))
                response)]
    (routes/navigate! :own-services)
    (-> app
        (assoc :service-save-in-progress false
               :services-changed? true)
        (dissoc :transport-service
                ;; Remove navigation prompt message only if save was successful.
                :before-unload-message))))

  FailedTransportServiceResponse
  (process-event [{response :response} app]
    (assoc app :service-save-in-progress false
               :flash-message-error (tr [:common-texts :save-failed])))

  EditTransportService
  (process-event [{form-data :form-data} {ts :transport-service :as app}]
    (let [key (t-service/service-key-by-type (::t-service/type ts))]
      (-> app
          (update-in [:transport-service key] merge form-data)
          (country->country-code ts)
          (assoc :before-unload-message [:dialog :navigation-prompt :unsaved-data]))))

  CancelTransportServiceForm
  (process-event [_ app]
    (routes/navigate! :own-services)
    app)

  SetNewServiceType
  (process-event [_ app]
    ;; This is needed when directly loading a new service URL to set the type
    (let [sub-type (keyword (get-in app [:params :sub-type]))]
      (-> app
        (assoc-in [:transport-service ::t-service/sub-type] sub-type)
        (assoc-in [:transport-service ::t-service/type] (service-type-from-sub-type sub-type))
        (pre-set-transport-type)))))

(defn move-service-level-keys-from-form
  "The form only sees the type specific level, move keys that are stored in the
  transport-service level there."
  [service from]
  (reduce (fn [service key]
            (as-> service s
              (if (contains? (get service from) key)
                (assoc s key (get-in service [from key]))
                s)
              (update s from dissoc key)))
          service
          service-level-keys))

(defn move-service-level-keys-to-form
  "Reverse of `move-service-level-keys-from-form`."
  [service to]
  (reduce (fn [service key]
            (as-> service s
              (if (contains? service key)
                (assoc-in s [to key] (get service key))
                s)
              (dissoc s key)))
          service
          service-level-keys))

(defn is-service-owner?
  "Admin can see services that they don't own. So we need to know, if user is a service owner"
  [app]
  (let [service-operator-id (get-in app [:transport-service ::t-service/transport-operator-id])
        first-matching-item (some
                              #(= service-operator-id (get-in % [:transport-operator ::t-operator/id]))
                              (get app :transport-operators-with-services))]
    (if (not (nil? first-matching-item))
      true
      false)))

(defn clean-up-csv-value [value]
  (when-not (nil? value)
    (str/trim
      (str/replace value "\"" ""))))

(defn parse-csv-response->company-map
  "Convert given vector to maps of business-id and company names."
  [csv-data]
  (let [headers (first csv-data)
        companies (map (fn [[business-id name]]
           {::t-service/business-id (clean-up-csv-value business-id)
            ::t-service/name        (clean-up-csv-value name)})
         (rest csv-data))]
    companies))

(defn read-companies-csv! [e! file-input filename]
  (let [fr (js/FileReader.)]
    (set! (.-onload fr)
          (fn [e]
            (let [txt (-> e .-target .-result)
                  separator (csv-util/csv-separator txt)
                  csv (parse-csv-response->company-map (csv/read-csv txt :newline :lf :separator separator))]
              (e! (->AddImportedCompaniesToService csv filename)))))
    (.readAsText fr (aget (.-files file-input) 0) "UTF-8")))
