(ns ote.tasks.company
  "Scheduled tasks to update company CSVs and stats."
  (:require [chime :refer [chime-at]]
            [clj-time.core :as t]
            [clj-time.periodic :refer [periodic-seq]]
            [com.stuartsierra.component :as component]
            [jeesql.core :refer [defqueries]]
            [ote.db.tx :as tx]
            [ote.services.transport :as transport]
            [ote.db.transport-service :as t-service]
            [taoensso.timbre :as log])
  (:import (org.joda.time DateTimeZone)))

(defqueries "ote/tasks/company.sql")


(def daily-update-time (t/from-time-zone (t/today-at 0 5)
                                         (DateTimeZone/forID "Europe/Helsinki")))

(defn update-one-csv! [db]
  (try
    (tx/with-transaction db
      (when-let [ts (first (select-company-csv-for-update db))]
        (let [url (fetch-company-csv-url db ts)]
          (log/debug "Update CSV for " (:transport-service-id ts) " at URL: " url)
          (transport/save-external-companies
           db {::t-service/id (:transport-service-id ts)
               ::t-service/companies-csv-url url})
          (update-done! db ts))))
    (catch Exception e
      (log/warn "Error in updating company CSV file" e))))

(defrecord CompanyTasks [at]
  component/Lifecycle
  (start [{db :db :as this}]
    (assoc this
           ::stop-tasks [(chime-at (drop 1 (periodic-seq at (t/days 1)))
                                   (fn [_]
                                     (store-daily-company-stats db)))
                         (chime-at (drop 1 (periodic-seq (t/now) (t/minutes 1)))
                                   (fn [_]
                                     (#'update-one-csv! db)))]))
  (stop [{stop-tasks ::stop-tasks :as this}]
    (doseq [stop stop-tasks]
      (stop))
    (dissoc this ::stop-tasks)))

(defn company-tasks
  ([] (company-tasks daily-update-time))
  ([at]
   (->CompanyTasks at)))
