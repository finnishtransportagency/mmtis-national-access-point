(ns ote.services.admin
  "Backend services for admin functionality."
  (:require [ote.components.http :as http]
            [com.stuartsierra.component :as component]
            [specql.core :refer [fetch update! insert! upsert! delete!] :as specql]
            [specql.op :as op]
            [taoensso.timbre :as log]
            [ote.util.csv :as csv-util]
            [ote.db.gtfs :as gtfs]
            [ote.transit :refer [clj->transit]]
            [compojure.core :refer [routes GET POST DELETE]]
            [jeesql.core :refer [defqueries]]
            [ote.nap.users :as nap-users]
            [specql.core :as specql]
            [ote.db.auditlog :as auditlog]
            [ote.db.transport-service :as t-service]
            [ote.db.transport-operator :as t-operator]
            [ote.db.transit :as transit]
            [ote.db.modification :as modification]
            [ote.services.transport :as transport]
            [ote.services.operators :as operators]
            [cheshire.core :as cheshire]
            [ote.authorization :as authorization]
            [ote.util.db :as db-util]
            [clojure.string :as str]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [ring.util.io :as ring-io]
            [ote.components.service :refer [define-service-component]]
            [ote.localization :as localization :refer [tr]]
            [clj-time.core :as t]
            [ote.time :as time]
            [clj-http.client :as http-client]
            [ote.services.external :as external]))

(defqueries "ote/services/admin.sql")
(defqueries "ote/services/reports.sql")

(def service-search-result-columns
  #{::t-service/contact-email
    ::t-service/sub-type
    ::t-service/id
    ::t-service/contact-gsm
    ::t-service/contact-address
    ::t-service/name
    ::t-service/type
    ::modification/created
    ::modification/modified
    ::t-service/published
    ::t-service/transport-operator-id
    ::t-service/contact-phone

    ;; Information JOINed from other tables
    ::t-service/operator-name})

(defn require-admin-user [route user]
  (when-not (:admin? user)
    (throw (SecurityException. "admin only"))))

(defn- admin-service [route {user :user
                             form-data :body :as req} db handler]
  (require-admin-user route (:user user))
  (http/transit-response
   (handler db user (http/transit-request form-data))))

(defn- list-users [db user query]
  (let [users (nap-users/list-users db {:email (str "%" query "%")
                                        :name (str "%" query "%")
                                        :group (str "%" query "%")
                                        :transit-authority? nil})]
    (mapv
      (fn [{groups :groups :as user}]
        (if groups
          (assoc user :groups (cheshire/parse-string (.getValue groups) keyword))
          user))
      users)))

(defn- delete-user [db user query]
  (let [id (:id query)
        timestamp (java.util.Date.)]
    (log/info "Deleting user with id: " (pr-str id))
    (nap-users/delete-user! db {:id id :name timestamp})
    id))

(defn- user-operator-members [db user query]
  (let [user-id (:id query)
        members (vec (nap-users/search-user-operators-and-members db {:user-id user-id}))]
    (mapv (fn [x]
            (update x :members #(db-util/PgArray->vec %))) members)))

(defn- published-search-param [query]
  (case (:published-type query)
    nil? nil
    :ALL nil
    :YES {::t-service/published op/not-null?}
    :NO {::t-service/published op/null?}
    nil))

(defn- list-services
  "Returns list of transport-services. Query parameters aren't mandatory, but it can be used to filter results."
  [db user query]
  (let [q (when-not (nil? (:query query))
            {::t-service/name (op/ilike (str "%" (:query query) "%"))})
        search-params (merge q (published-search-param query))]
    (fetch db ::t-service/transport-service-search-result
         service-search-result-columns
         search-params
         {:specql.core/order-by ::t-service/name})))

(defn- list-operators
  "Returns list of transport-operators. Query parameters aren't mandatory, but it can be used to filter results."
  [db user query]
  (let [q (when-not (nil? (:query query))
            {::t-operator/deleted? false
             ::t-operator/name (op/ilike (str "%" (:query query) "%"))})]
    (fetch db ::t-operator/transport-operator
           (specql/columns ::t-operator/transport-operator)
           q
           {:specql.core/order-by ::t-operator/name})))

(defn- list-services-by-operator [db user query]
  (let [q (when-not (nil? (:query query))
            {::t-service/operator-name (op/ilike (str "%" (:query query) "%"))})
        search-params (merge q (published-search-param query))]
    (fetch db ::t-service/transport-service-search-result
         service-search-result-columns
         search-params
         {:specql.core/order-by ::t-service/operator-name})))

(defn- interfaces-array->vec [db-interfaces]
  (mapv (fn [d] (-> d
                    (update :format #(db-util/PgArray->vec %))
                    (update :data-content #(db-util/PgArray->vec %)))) db-interfaces))

(defn- list-interfaces [db user query]
  (let [service-name (:service-name query)
        operator-name (:operator-name query)
        import-error (:import-error query)
        db-error (:db-error query)
        interface-format (:interface-format query)
        interface-url (:interface-url query)
        ;; Get services that doesn't have any issues or services with errors.
        services-with-interface (when (not (:no-interface query))
                                  (interfaces-array->vec
                                    (search-services-with-interfaces db {:service-name (when service-name (str "%" service-name "%"))
                                                                         :operator-name (when operator-name (str "%" operator-name "%"))
                                                                         :interface-url (when interface-url (str "%" interface-url "%"))
                                                                         :import-error (when import-error true)
                                                                         :db-error (when db-error true)
                                                                         :interface-format (when (and interface-format (not= :ALL interface-format)) (str/lower-case (name interface-format)))})))
        ;; Get only services that do not have interface.
        services-without-interface (when (:no-interface query)
                                     (search-services-wihtout-interface db {:service-name (when service-name (str "%" service-name "%"))
                                                                            :operator-name (when operator-name (str "%" operator-name "%"))}))]
    (concat services-with-interface services-without-interface)))

(defn- list-sea-routes [db user query]
  (specql/fetch db ::transit/route
                #{::transit/id ::transit/transport-operator-id ::transit/name ::transit/published?
                  [::transit/operator #{::t-operator/name ::t-operator/id}]}
                {::transit/operator {::t-operator/name (op/ilike (str "%" query "%"))}}))

(defn distinct-by [f coll]
  (let [groups (group-by f coll)]
    (map #(first (groups %)) (distinct (map f coll)))))

(defn- PGobj->clj
  [data]
  (mapv
   (fn [{services :services :as row}]
     (if services
       (assoc row :services (cheshire/parse-string (.getValue  services) keyword))
       row))
   data))

(defn- business-id-report [db user query]
  (let [services (when
                     (or
                      (nil? (:business-id-filter query))
                      (= :ALL (:business-id-filter query))
                      (= :services (:business-id-filter query)))
                   (PGobj->clj (fetch-service-business-ids db)))
        operators (when
                      (or
                       (nil? (:business-id-filter query))
                       (= :ALL (:business-id-filter query))
                       (= :operators (:business-id-filter query)))
                    (PGobj->clj (fetch-operator-business-ids db)))

        report (concat services operators)]

    (sort-by :operator (distinct-by :business-id report))))

(defn- admin-delete-transport-service!
  "Allow admin to delete single transport service by id"
  [nap-config db user {id :id}]
  (let [deleted-service (transport/all-data-transport-service db id)
        return (transport/delete-transport-service! nap-config db user id)
        auditlog {::auditlog/event-type :delete-service
                  ::auditlog/event-attributes
                  [{::auditlog/name "transport-service-id", ::auditlog/value (str id)},
                   {::auditlog/name "transport-service-name", ::auditlog/value (::t-service/name deleted-service)}]
                  ::auditlog/event-timestamp (java.sql.Timestamp. (System/currentTimeMillis))
                  ::auditlog/created-by (get-in user [:user :id])}]
    (upsert! db ::auditlog/auditlog auditlog)
    return))

(defn delete-transport-operator!
  "Delete single transport operator by id. Delete services and all other datas as well."
  [db user transport-operator-id]
  (let [auditlog {::auditlog/event-type :delete-operator
                  ::auditlog/event-attributes
                                        [{::auditlog/name "transport-operator-id"
                                          ::auditlog/value (str transport-operator-id)}]
                  ::auditlog/event-timestamp (java.sql.Timestamp. (System/currentTimeMillis))
                  ::auditlog/created-by (get-in user [:user :id])}]
  (authorization/with-transport-operator-check
    db user transport-operator-id
    #(do
       (operators/delete-transport-operator db {:operator-group-name (str "transport-operator-" transport-operator-id)})
       (upsert! db ::auditlog/auditlog auditlog)
       transport-operator-id))))

(defn summary-types-for-monitor-report [db summary-type]
  (let [type-count-table (if (= :month summary-type)
                           (monthly-producer-types-and-counts db)
                           (tertile-producer-types-and-counts db))
        summary (distinct (keep summary-type type-count-table)) ;; order is important
        subtypes (distinct (keep :sub-type type-count-table))
        by-subtype (group-by :sub-type type-count-table)
        ;; the following yields a sequence map like {"taxi" (7M 12M 41M 44M 55M 59M 61M 70M 79M 85M 86M 89M 92M), ... }
        running-totals (into {}
                             (vec (for [[st ms] by-subtype]
                                    [st (reductions + (map :sum (sort-by (if (= :month summary-type)
                                                                           summary-type
                                                                           {:year :nro}) ms)))])))
        by-subtype-w-totals (into {} (for [subtype subtypes]
                                       [subtype (mapv assoc (get by-subtype subtype) (repeat :running-sum) (get running-totals subtype))]))
        by-summary (group-by summary-type (apply concat (vals by-subtype-w-totals)))
        type-colors {"taxi" "rgb(0,170,187)"
                     "request" "rgb(102,204,102)"
                     "schedule" "rgb(153,204,0)"
                     "terminal" "rgb(221,204,0)"
                     "rentals" "rgb(255,136,0)"
                     "parking"  "rgb(255,102,153)"
                     "brokerage" "rgb(153,0,221)"}
        find-sum-backwards (fn [month subtype]
                             ;; the twist in this function is that it looks up the sum from the most recent
                             ;; previous month, if the given month doesn't have the sum.
                             ;; this is because we are presenting cumulative sums.
                             (let [monthlies-for-subtype (get by-subtype-w-totals subtype)
                                   without-future-summary (filter #(<= 0 (compare month (summary-type %))) monthlies-for-subtype)
                                   highest-summary (last without-future-summary)]
                               (if highest-summary
                                 (:running-sum highest-summary)
                                 ;; else
                                 0M)))
        monthly-series-for-type (fn [t]
                                  (vec
                                    (for [x summary]
                                      (let [tc (filter #(= t (:sub-type %)) (get summary-type x))]
                                        ;(println "month/tertile" x "hs" (find-sum-backwards x t))
                                        (find-sum-backwards x t)))))
        type-dataset (fn [t]
                       {:label (tr [:enums :ote.db.transport-service/sub-type (keyword t)])
                        :data (monthly-series-for-type t)
                        :backgroundColor (get type-colors t)})]

    {:labels (vec summary)
     :datasets (mapv type-dataset subtypes)}))


(defn monitor-report [db type]
  {:monthly-companies  (monthly-registered-companies db)
   :tertile-companies  (tertile-registered-companies db)
   :companies-by-service-type (operator-type-distribution db)
   :monthly-types (summary-types-for-monitor-report db :month)
   :tertile-types (summary-types-for-monitor-report db :tertile)})

(defn- csv-data [header rows]
  (concat [header] rows))

(defn monitor-csv-report [db report-type]
  (case report-type
    "monthly-companies"
    (csv-data ["kuukausi" "tuottaja-ytunnus-lkm" "tuottavien_lkm" "osallistuvien_lkm"]
              (map (juxt :month :sum :sum-providing :sum-participating) (monthly-registered-companies db)))

    "tertile-companies"
    (csv-data ["tertiili" "tuottaja-ytunnus-lkm" "tuottavien_lkm" "osallistuvien_lkm"]
              (map (juxt :tertile :sum :sum-providing :sum-participating) (tertile-registered-companies db)))

    "company-service-types"
    (csv-data ["tuottaja-tyyppi" "tuottaja-ytunnus-lkm"]
              (map (juxt :sub-type :count) (operator-type-distribution db)))

    "monthly-companies-by-service-type"
    (csv-data ["kuukausi" "tuottaja-tyyppi" "lkm"]
              ;; the copious vec calls are here because sort blows up on lazyseqs,
              ;; and we end up with nested lazyseqs here despite using mapv etc.
              (let [r (summary-types-for-monitor-report db :month)
                    months (:labels r)
                    ds (:datasets r)
                    sums-by-type (into {}  (map (juxt :label :data) ds))
                    type-month-tmp-table (vec (for [[t vs] sums-by-type] (interleave months (repeat t) vs)))
                    final-table (mapv #(partition 3 (mapv str %)) type-month-tmp-table)]
                (sort (vec (map vec (apply concat final-table))))))

    "tertile-companies-by-service-type"
    (csv-data ["tertiili" "tuottaja-tyyppi" "lkm"]
              ;; the copious vec calls are here because sort blows up on lazyseqs,
              ;; and we end up with nested lazyseqs here despite using mapv etc.
              (let [r (summary-types-for-monitor-report db :tertile)
                    months (:labels r)
                    ds (:datasets r)
                    sums-by-type (into {}  (map (juxt :label :data) ds))
                    type-month-tmp-table (vec (for [[t vs] sums-by-type] (interleave months (repeat t) vs)))
                    final-table (mapv #(partition 3 (mapv str %)) type-month-tmp-table)]
                (sort (vec (map vec (apply concat final-table))))))))

(defn- transport-operator-report [db type]
  (case type
    "all-emails"
    (csv-data ["email"]
              (map (juxt :email)
                   (fetch-all-emails db)))

    "no-services"
    (csv-data ["nimi" "id" "email"]
              (map (juxt :name :id :email)
                   (fetch-operators-no-services db)))

    "brokerage"
    (csv-data ["nimi" "y-tunnus" "puhelin" "email" "palvelun nimi"]
              (map (juxt :name :business-id :phone :email :service-name)
                   (fetch-operators-brokerage db)))

    "taxi-operators"
    (csv-data ["Palveluntuottaja" "Y-tunnus" "Alla oleva yritys" "Y-tunnus" "Palvelu" "Liikennemuoto"]
              (map (juxt :operator :business-id :sub-company :sub-business-id :service-name :transport-type)
                   (fetch-operators-with-sub-contractors db {:subtype "taxi"})))

    "request-operators"
    (csv-data ["Palveluntuottaja" "Y-tunnus" "Alla oleva yritys" "Y-tunnus" "Palvelu" "Liikennemuoto"]
              (map (juxt :operator :business-id :sub-company :sub-business-id :service-name :transport-type)
                   (fetch-operators-with-sub-contractors db {:subtype "request"})))

    "schedule-operators"
    (csv-data ["Palveluntuottaja" "Y-tunnus" "Alla oleva yritys" "Y-tunnus" "Palvelu" "Liikennemuoto"]
              (map (juxt :operator :business-id :sub-company :sub-business-id :service-name :transport-type)
                   (fetch-operators-with-sub-contractors db {:subtype "schedule"})))

    "unpublished-services"
    (csv-data ["nimi" "puhelin" "email" "julkaisemattomia palveluita" "palvelut"]
              (map (juxt :name :phone :email :unpublished-services-count :services)
                   (fetch-operators-unpublished-services db)))

    "payment-interfaces"
    (csv-data ["Palveluntuottaja", "Y-tunnus" "Palvelu" "Palvelun osoite" "Palvelun tyyppi" "Rajapinnan osoite" "Formaatti" "Lisenssi"]
              (map (juxt :operator :business-id :service-name :service-address :service-type :url :format :licence)
                   (fetch-operators-with-payment-services db)))))

(defn- get-user-operators-by-business-id [db business-id]
  (let [result (specql/fetch db ::t-operator/transport-operator
                             (specql/columns ::t-operator/transport-operator)
                             {::t-operator/business-id business-id})]
    (map #(assoc {} :transport-operator %) result)))

(defn- get-commercial-scheduled-services [db]
  (fetch-commercial-services db))

(defn- toggle-commercial-service [db {id :id commercial? :commercial? :as form-data}]
  (specql/update! db ::t-service/transport-service
                  {::t-service/commercial-traffic? commercial?}
                  {::t-service/id id}))

(defn- read-csv
  "Read CSV from input stream. Guesses the separator from the first line."
  [input]
  (let [separator (csv-util/csv-separator input)]
    (csv/read-csv input :separator separator)))

(defn parse-exception-holidays-csv
  [holidays-string]
  (let [data (next (read-csv holidays-string))]
    (map
      (fn [holiday]
        (let [timestamp (first holiday)
              parsed-time (ote.time/parse-date-iso-8601 (first (str/split timestamp #" ")))
              third-arg (second (next holiday))
              holiday (if (nil? third-arg)
                        "holiday"
                        third-arg)]
          {:date parsed-time
           :name holiday}))
      data)))

(defn empty-old-exceptions
  [db]
  (specql/delete! db :gtfs/detection-holidays
                  {:gtfs/date op/not-null?}))

(defn save-exception-days-to-db
  [db exceptions]
  (let [results (for [exception exceptions]
                  (specql/insert! db :gtfs/detection-holidays
                                  {:gtfs/date (:date exception)
                                   :gtfs/reason (:name exception)}))]
    results))

(defn- fetch-new-exception-csv
  "Fetches csv and produces a list of exceptions"
  [db req]
  (try
    (let [url "http://traffic.navici.com/tiedostot/poikkeavat_ajopaivat.csv"
          response (try
                     (http-client/get url {:as "UTF-8"
                                           :socket-timeout 30000
                                           :conn-timeout 10000})
                     (catch clojure.lang.ExceptionInfo e
                       ;; sadly clj-http wants to communicate status as exceptions
                       (-> e ex-data)))]
      (if (= 200 (:status response))
        (let [response-csv (parse-exception-holidays-csv (:body response))]
          (when (not-empty response-csv)
            (empty-old-exceptions db)
            (http/transit-response (save-exception-days-to-db db response-csv))))
        {:status 500
         :headers {"Content-Type" "application/json+transit"}
         :body (clj->transit {:status (:status response)
                              :response (str response)})}))
    (catch Exception e
      (log/warn "Exception csv error: " e)
      {:status 500
       :headers {"Content-Type" "application/json+transit"}
       :body (clj->transit {:error (str e)})})))

(defn- admin-routes [db http nap-config]
  (routes
    (POST "/admin/users" req (admin-service "users" req db #'list-users))

    (POST "/admin/delete-user" req (admin-service "delete-user" req db #'delete-user))

    (POST "/admin/user-operator-members" req (admin-service "user-operators" req db #'user-operator-members))

    (POST "/admin/transport-services" req (admin-service "services" req db #'list-services))

    (POST "/admin/transport-operators" req (admin-service "operators" req db #'list-operators))

    (POST "/admin/transport-services-by-operator" req (admin-service "services" req db #'list-services-by-operator))

    (POST "/admin/interfaces" req (admin-service "interfaces" req db #'list-interfaces))

    (POST "/admin/sea-routes" req (admin-service "sea-routes" req db #'list-sea-routes))

    (POST "/admin/transport-service/delete" req
      (admin-service "transport-service/delete" req db
                     (partial admin-delete-transport-service! nap-config)))

    (POST "/admin/business-id-report" req (admin-service "business-id-report" req db #'business-id-report))

    (POST "/admin/transport-operator/delete" {form-data :body user :user}
      (http/transit-response
        (delete-transport-operator! db user
                                    (:id (http/transit-request form-data)))))
    (GET "/admin/user-operators-by-business-id/:business-id" [business-id]
      (http/transit-response
        (get-user-operators-by-business-id db business-id)))

    (GET "/admin/commercial-services" req
         (http/transit-response (get-commercial-scheduled-services db )))

    (GET "/admin/csv-fetch" req
      (require-admin-user "csv" (:user (:user req)))
      (fetch-new-exception-csv db req))

    (POST "/admin/toggle-commercial-services" {form-data :body user :user}
        (toggle-commercial-service db (http/transit-request form-data))
        (http/transit-response "OK"))))


(define-service-component CSVAdminReports
  {}
  ^{:format :csv
    :filename (str "raportti-" (time/format-date-iso-8601 (time/now)) ".csv")}
  (GET "/admin/reports/transport-operator/:type"
       {{:keys [type]} :params
        user :user}
    (require-admin-user "reports/transport-operator" (:user user))
    (transport-operator-report db type)))

(define-service-component MonitorReport []
  {}
  (GET "/admin/reports/monitor-report"
       {user :user}
    (require-admin-user "reports/monitor" (:user user))
    (http/transit-response (monitor-report db "all"))))

(define-service-component MonitorReportCSV
  {}
  ^{:format :csv
    :filename (str "raportti-" (time/format-date-iso-8601 (time/now)) ".csv")}
  (GET "/admin/reports/monitor/csv/:type"
       {{:keys [type]} :params
        user :user}
       (require-admin-user "reports/monitor" (:user user))
       (monitor-csv-report db type)))

(defrecord Admin [nap-config]
  component/Lifecycle
  (start [{db :db http :http :as this}]
    (assoc this ::stop
           (http/publish! http (admin-routes db http nap-config))))

  (stop [{stop ::stop :as this}]
    (stop)
    (dissoc this ::stop)))


