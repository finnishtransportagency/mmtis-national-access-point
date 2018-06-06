(ns ote.app.controller.admin
  (:require [tuck.core :as tuck]
            [ote.db.transport-service :as t-service]
            [ote.db.transport-operator :as t-operator]
            [ote.localization :refer [tr tr-key]]
            [ote.communication :as comm]))

(defn- update-service-by-id [app id update-fn & args]
  (update-in app [:service-search :results]
          (fn [results]
            (map #(if (= (::t-service/id %) id)
                    (apply update-fn % args)
                    %)
                 results))))

(defrecord UpdateUserFilter [user-filter])
(defrecord UpdateServiceFilter [service-filter])
(defrecord UpdateServiceOperatorFilter [operator-filter])
(defrecord UpdateOperatorFilter [operator-filter])
(defrecord UpdatePublishedFilter [published-filter])
(defrecord SearchUsers [])
(defrecord SearchServices [])
(defrecord SearchServicesByOperator [])
(defrecord SearchOperators [])
(defrecord SearchUsersResponse [response])
(defrecord SearchServicesResponse [response])
(defrecord SearchOperatorResponse [response])
(defrecord GetBusinessIdReport [])
(defrecord GetBusinessIdReportResponse [response])
(defrecord UpdateBusinessIdFilter [business-id-filter])
(defrecord DeleteTransportService [id])
(defrecord CancelDeleteTransportService [id])
(defrecord ConfirmDeleteTransportService [id])
(defrecord DeleteTransportServiceResponse [response])
(defrecord FailedDeleteTransportServiceResponse [response])
(defrecord ChangeAdminTab [tab])

;; Interface tab
(defrecord UpdateInterfaceOperatorFilter [interface-filter])
(defrecord UpdateInterfaceServiceFilter [interface-filter])
(defrecord SearchInterfacesByOperator [])
(defrecord SearchInterfacesByOperatorResponse [response])
(defrecord SearchInterfacesByService [])
(defrecord SearchInterfacesByServiceResponse [response])
(defrecord OpenInterfaceErrorModal [id])
(defrecord CloseInterfaceErrorModal [id])
(defrecord OpenOperatorModal [id])
(defrecord CloseOperatorModal [id])

;; Delete Transport Operator
(defrecord OpenDeleteOperatorModal [id])
(defrecord CancelDeleteOperator [id])
(defrecord ConfirmDeleteOperator [id])
(defrecord DeleteOperatorResponse [response])
(defrecord DeleteOperatorResponseFailed [response])
(defrecord EnsureServiceOperatorId [id ensured-id])

(defrecord ToggleAddMemberDialog [id])

(defn- update-operator-by-id [app id update-fn & args]
  (update-in app [:admin :operator-list :results]
             (fn [operators]
               (map #(if (= (::t-operator/id %) id)
                       (apply update-fn % args)
                       %)
                    operators))))

(defn- update-interface-by-id [app id update-fn & args]
  (update-in app [:admin :interface-list :results]
             (fn [operators]
               (map #(if (= (:interface-id %) id)
                       (apply update-fn % args)
                       %)
                    operators))))


(defn- get-search-result-operator-by-id [app id]
  (some
    #(when (= (::t-operator/id %) id) %)
    (get-in app [:admin :operator-list :results])))

(extend-protocol tuck/Event

  UpdateUserFilter
  (process-event [{f :user-filter} app]
    (update-in app [:admin :user-listing] assoc :user-filter f))

  UpdateServiceFilter
  (process-event [{f :service-filter} app]
    (update-in app [:admin :service-listing] assoc :service-filter f))

  UpdateServiceOperatorFilter
  (process-event [{f :operator-filter} app]
    (update-in app [:admin :service-listing] assoc :operator-filter f))

  UpdateOperatorFilter
  (process-event [{f :operator-filter} app]
    (update-in app [:admin :operator-list] assoc :operator-filter f))

  UpdateBusinessIdFilter
  (process-event [{f :business-id-filter} app]
    (update-in app [:admin :business-id-report] assoc :business-id-filter f))

  UpdatePublishedFilter
  (process-event [{f :published-filter} app]
    (update-in app [:admin :service-listing] assoc :published-filter f))

  UpdateInterfaceOperatorFilter
  (process-event [{f :interface-filter} app]
    (update-in app [:admin :interface-list] assoc :interface-operator-filter f))

  UpdateInterfaceServiceFilter
  (process-event [{f :interface-filter} app]
    (update-in app [:admin :interface-list] assoc :interface-service-filter f))

  SearchUsers
  (process-event [_ app]
    (comm/post! "admin/users" (get-in app [:admin :user-listing :user-filter])
                {:on-success (tuck/send-async! ->SearchUsersResponse)})
    (assoc-in app [:admin :user-listing :loading?] true))

  SearchUsersResponse
  (process-event [{response :response} app]
    (update-in app [:admin :user-listing] assoc
               :loading? false
               :results response))

  GetBusinessIdReport
  (process-event [_ app]
    (comm/post! "admin/business-id-report"  {:business-id-filter (get-in app [:admin :business-id-report :business-id-filter])}
                {:on-success (tuck/send-async! ->GetBusinessIdReportResponse)})
    (assoc-in app [:admin :business-id-report :loading?] true))

  GetBusinessIdReportResponse
  (process-event [{response :response} app]
    (update-in app [:admin :business-id-report] assoc
               :loading? false
               :results response))

  SearchServices
  (process-event [_ app]
    (comm/post! "admin/transport-services"
                {:query (get-in app [:admin :service-listing :service-filter])
                :published-type (get-in app [:admin :service-listing :published-filter])}
                {:on-success (tuck/send-async! ->SearchServicesResponse)})
    (assoc-in app [:admin :service-listing :loading?] true))

  SearchServicesByOperator
  (process-event [_ app]
    (comm/post! "admin/transport-services-by-operator"
                {:query (get-in app [:admin :service-listing :operator-filter])
                 :published-type (get-in app [:admin :service-listing :published-filter])}
                {:on-success (tuck/send-async! ->SearchServicesResponse)})
    (assoc-in app [:admin :service-listing :loading?] true))

  SearchServicesResponse
  (process-event [{response :response} app]
    (update-in app [:admin :service-listing] assoc
               :loading? false
               :results response))

  SearchOperators
  (process-event [_ app]
    (comm/post! "admin/transport-operators"
                {:query (get-in app [:admin :operator-list :operator-filter])}
                {:on-success (tuck/send-async! ->SearchOperatorResponse)})
    (assoc-in app [:admin :operator-list :loading?] true))

  SearchOperatorResponse
  (process-event [{response :response} app]
    (update-in app [:admin :operator-list] assoc
               :loading? false
               :results response))


  SearchInterfacesByOperator
  (process-event [_ app]
    (comm/post! "admin/interfaces-by-operator" (get-in app [:admin :interface-list :interface-operator-filter])
                {:on-success (tuck/send-async! ->SearchInterfacesByOperatorResponse)})
    (assoc-in app [:admin :interface-list :loading?] true))

  SearchInterfacesByOperatorResponse
  (process-event [{response :response} app]
    (update-in app [:admin :interface-list] assoc
               :loading? false
               :results response))

  SearchInterfacesByService
  (process-event [_ app]
    (comm/post! "admin/interfaces-by-service" (get-in app [:admin :interface-list :interface-service-filter])
                {:on-success (tuck/send-async! ->SearchInterfacesByOperatorResponse)})
    (assoc-in app [:admin :interface-list :loading?] true))

  SearchInterfacesByServiceResponse
  (process-event [{response :response} app]
    (update-in app [:admin :interface-list] assoc
               :loading? false
               :results response))

  OpenInterfaceErrorModal
  (process-event [{id :id} app]
    (update-interface-by-id
      app id
      assoc :show-error-modal? true))

  CloseInterfaceErrorModal
  (process-event [{id :id} app]
    (update-interface-by-id
      app id
      dissoc :show-error-modal?))

  OpenOperatorModal
  (process-event [{id :id} app]
    (update-interface-by-id
      app id
      assoc :show-operator-modal? true))

  CloseOperatorModal
  (process-event [{id :id} app]
    (update-interface-by-id
      app id
      dissoc :show-operator-modal?))

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
    (comm/post! "admin/transport-service/delete"
                {:id id}
                {:on-success (tuck/send-async! ->DeleteTransportServiceResponse)
                 :on-failure (tuck/send-async! ->FailedDeleteTransportServiceResponse)})
    app)


  DeleteTransportServiceResponse
  (process-event [{response :response} app]
    (let [filtered-map (filter #(not= (:ote.db.transport-service/id %) (int response)) (get-in app [:service-search :results]))]
      (-> app
          (assoc-in [:service-search :results] filtered-map)
          (assoc :page :services
                 :flash-message (tr [:common-texts :delete-service-success])
                 :services-changed? true))))

  FailedDeleteTransportServiceResponse
  (process-event [{response :response} app]
    (assoc app :flash-message-error (tr [:common-texts :delete-service-error])))

  ChangeAdminTab
  (process-event [{tab :tab} app]
    (assoc-in app [:admin :tab :admin-page] tab))

  OpenDeleteOperatorModal
  (process-event [{id :id} app]
    (update-operator-by-id
      app id
      assoc :show-delete-modal? true
      :ensure-id nil))

  CancelDeleteOperator
  (process-event [{id :id} app]
    (update-operator-by-id
      app id
      dissoc :show-delete-modal?))

  ConfirmDeleteOperator
  (process-event [{id :id} app]
    (when (= id (int (:ensured-id (get-search-result-operator-by-id app id))))
      (comm/post! "admin/transport-operator/delete" {:id id}
                  {:on-success (tuck/send-async! ->DeleteOperatorResponse)
                   :on-failure (tuck/send-async! ->DeleteOperatorResponseFailed)}))
    app)

  DeleteOperatorResponse
  (process-event [{response :response} app]
    (let [filtered-map (filter #(not= (::t-operator/id %) (int response)) (get-in app [:admin :operator-list :results]))]
      (-> app
          (assoc-in [:admin :operator-list :results] filtered-map)
          (assoc :flash-message "Palveluntuottaja poistettu onnistuneesti."
                 :operators-changed? true))))

  DeleteOperatorResponseFailed
  (process-event [{response :response} app]
    (assoc app :flash-message-error "Palveluntuottajan poistaminen epäonnistui"))

  EnsureServiceOperatorId
  (process-event [{id :id ensured-id :ensured-id} app]
    (update-operator-by-id
      app id
      assoc :ensured-id ensured-id))

  ToggleAddMemberDialog
  (process-event [{id :id} app]
    (let [show? (:show-add-member-dialog? (get-search-result-operator-by-id app id))]
      (update-operator-by-id
        app id
        assoc :show-add-member-dialog? (not show?)))))
