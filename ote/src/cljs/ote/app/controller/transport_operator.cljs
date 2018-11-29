(ns ote.app.controller.transport-operator
  "Transport operator controls "                            ;; FIXME: Move transport-service related stuff to other file
  (:require [ote.communication :as comm]
            [ote.ui.form :as form]
            [ote.localization :refer [tr tr-key]]
            [ote.db.transport-operator :as t-operator]
            [ote.app.routes :as routes]
            [tuck.core :refer [define-event send-async! Event]]
            [ote.app.controller.common :refer [->ServerError]]
            [ote.db.common :as common]))

(define-event ToggleTransportOperatorDeleteDialog []
  {:path [:transport-operator :show-delete-dialog?]
   :app show?}
  (not show?))

(define-event DeleteTransportOperatorResponse [response]
  {}
  (routes/navigate! :own-services)
  (-> app
    (assoc-in [:transport-operator :show-delete-dialog?] false)
    (assoc :flash-message (tr [:common-texts :delete-operator-success])
           :services-changed? true)))

(define-event DeleteTransportOperator [id]
  {}
  (comm/post! "transport-operator/delete"  {:id id}
            {:on-success (send-async! ->DeleteTransportOperatorResponse)
             :on-failure (send-async! ->ServerError)})
  app)

(defn- address-of-type [type addresses]
  "Returns from a vector the first map whose type key matches to YTJ address type."
  (let [item (first (filter #(= type (:type %)) addresses))]
    {
     ::common/post_office (:city item)
     ::common/postal_code (:postCode item)
     ::common/street      (:street item)
     }
    )
  )

(defn- filter-coll-type [type collection]
  "Returns a filtered a collection of maps based on :type key"
  (first (filter #(some (fn [pred] (pred %))
                        [(comp #{type} :type)])
                 collection)))

(defn- preferred-contacts [types contacts]
  "Returns a collection of contact maps, filtered by types defined in vector 'types'. Result is sorted according to order of the types"
  (loop [[type & remaining] types
         result []]
    (if type
      (let [match (:value (filter-coll-type type contacts))]
        ;(.debug js/console "Contact match=" (clj->js match))
        (recur remaining (if match (conj result match) result)))
      (do
        ;(.debug js/console "Contact result=" (clj->js result))
        result)))
  )

(define-event FetchOperatorResponse [response]
              {}
              (let [address-billing (address-of-type 1 (:addresses response))
                    address-visiting (address-of-type 2 (:addresses response))
                    ytj-contact-phone (first (preferred-contacts ["Puhelin" "Telefon" "Telephone"] (:contactDetails response)))
                    ytj-contact-gsm (first (preferred-contacts ["Matkapuhelin" "Mobiltelefon" "Mobile phone"] (:contactDetails response)))
                    ;ytj-contact-email (first (preferred-contacts ["Matkapuhelin" "Mobiltelefon" "Mobile phone"] (:contactDetails response))) ;TODO: check ytj field types, does it return email?
                    ytj-contact-web (first (preferred-contacts ["Kotisivun www-osoite" "www-adress" "Website address"] (:contactDetails response)))
                    ]
                (cond-> app
                        true (assoc
                               :ytj-response response
                               :ytj-response-loading false
                               :transport-operator-loaded? true
                               :ytj-business-names (into [{:name (:name response)}] ; Company name first in list
                                                         (:auxiliaryNames response))) ; Auxiliary names after company name)
                        true (assoc-in [:transport-operator ::t-operator/billing-address] address-billing)
                        true (assoc-in [:transport-operator ::t-operator/visiting-address] address-visiting)
                        (and (not-empty ytj-contact-phone) (empty? (::t-operator/phone app))) (assoc-in [:transport-operator ::t-operator/phone] ytj-contact-phone)
                        (and (not-empty ytj-contact-gsm) (empty? (::t-operator/gsm app))) (assoc-in [:transport-operator ::t-operator/gsm] ytj-contact-gsm)
                        ;(and (not-empty ytj-contact-email) (empty? (::t-operator/email app))) (assoc-in [:transport-operator ::t-operator/email] ytj-contact-email)
                        (and (not-empty ytj-contact-web) (empty? (::t-operator/homepage app))) (assoc-in [:transport-operator ::t-operator/homepage] ytj-contact-web)
                )))

(defn- send-fetch-ytj [app-state id]
  (if id
    (do
      (comm/get! (str "fetch/ytj?company-id=" id)
                 {:on-success (send-async! ->FetchOperatorResponse)
                  :on-failure (send-async! ->FetchOperatorResponse)
                  })
      (assoc app-state :transport-operator-loaded? false
                       :ytj-response-loading true))
    app-state))

(define-event FetchOperator [id]
              {}
              (->
                app
                (dissoc :ytj-response)
                (send-fetch-ytj id)
                ))

(defrecord SelectOperator [data])
(defrecord SelectOperatorForTransit [data])
(defrecord EditTransportOperator [id])
(defrecord EditTransportOperatorResponse [response])
(defrecord EditTransportOperatorState [data])
(defrecord SaveTransportOperator [])
(defrecord SaveTransportOperatorResponse [data])
(defrecord FailedTransportOperatorResponse [response])

(defrecord TransportOperatorResponse [response])
(defrecord CreateTransportOperator [])


(defn transport-operator-by-ckan-group-id[id]
  (comm/get! (str "transport-operator/" id) {:on-success (send-async! ->TransportOperatorResponse)}))

(extend-protocol Event

  CreateTransportOperator
  (process-event [_ app]
    (routes/navigate! :transport-operator)
    (assoc app
           :transport-operator {:new? true}
           :services-changed? true))

  SelectOperator
  (process-event [{data :data} app]
    (let [id (get data ::t-operator/id)
          service-operator (some #(when (= id (get-in % [:transport-operator ::t-operator/id]))
                                     %)
                                  (:transport-operators-with-services app))
          route-operator (some #(when (= id (get-in % [:transport-operator ::t-operator/id]))
                                     %)
                                  (:route-list app))]
      (assoc app
        :transport-operator (:transport-operator service-operator)
        :transport-service-vector (:transport-service-vector service-operator)
        :routes-vector (:routes route-operator))))

  SelectOperatorForTransit
  (process-event [{data :data} app]
    (let [id (get data ::t-operator/id)
          selected-operator (some #(when (= id (get-in % [:transport-operator ::t-operator/id]))
                                     %)
                                  (:route-list app))]
      (assoc app
        :transport-operator (:transport-operator selected-operator)
        :routes-vector (:routes selected-operator))))

  EditTransportOperator
  (process-event [{id :id} app]
    (if id
      (do
        (comm/get! (str "t-operator/" id)
                   {:on-success (send-async! ->EditTransportOperatorResponse)})
        (assoc app
               :transport-operator-loaded? false))
      (do
        (assoc app
               :transport-operator-loaded? true))))

  EditTransportOperatorResponse
  (process-event [{response :response} app]
       (assoc app
              :transport-operator-loaded? true
              :transport-operator response))

  EditTransportOperatorState
  (process-event [{data :data} app]
    (update app :transport-operator merge data))

  SaveTransportOperator
  (process-event [_ app]
    (let [operator-data (-> app
                            :transport-operator
                            form/without-form-metadata)]

      (comm/post! "transport-operator" operator-data {:on-success (send-async! ->SaveTransportOperatorResponse)
                                                      :on-failure (send-async! ->FailedTransportOperatorResponse)})
      app))

  FailedTransportOperatorResponse
  (process-event [{response :response} app]
    (assoc app :flash-message-error (tr [:common-texts :save-failed])))

  SaveTransportOperatorResponse
  (process-event [{data :data} app]
    (routes/navigate! :own-services)
    (assoc app
           :flash-message (tr [:common-texts :transport-operator-saved ])
           :transport-operator data
           :transport-operators-with-services (map (fn [{:keys [transport-operator] :as operator-with-services}]
                                                     (if (= (::t-operator/id data)
                                                            (::t-operator/id transport-operator))
                                                       (assoc operator-with-services
                                                              :transport-operator data)
                                                       operator-with-services))
                                                   (:transport-operators-with-services app))
           :services-changed? true))

  TransportOperatorResponse
  (process-event [{response :response} app]
    (assoc app
      :transport-operator (assoc response
                            :loading? false))))
