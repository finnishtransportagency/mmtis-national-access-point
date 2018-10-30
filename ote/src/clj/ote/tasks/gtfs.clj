(ns ote.tasks.gtfs
  "Scheduled tasks to update gtfs file to s3 and later to database."
  (:require [chime :refer [chime-at]]
            [clj-time.core :as t]
            [clj-time.periodic :refer [periodic-seq]]
            [ote.util.feature :as feature]
            [com.stuartsierra.component :as component]
            [jeesql.core :refer [defqueries]]
            [ote.db.tx :as tx]
            [ote.db.transport-service :as t-service]
            [ote.integration.import.gtfs :as import-gtfs]
            [taoensso.timbre :as log]
            [specql.core :as specql]
            [ote.time :as time]
            [ote.tasks.util :refer [daily-at timezone]]
            [ote.db.lock :as lock]
            [clojure.string :as str]
            [ote.util.functor :refer [fmap]]
            [ote.util.collections :refer [map-by]]
            [ote.transit-changes.detection :as detection])
  (:import (org.joda.time DateTimeZone)))

(defqueries "ote/tasks/gtfs.sql")

(def daily-update-time (t/from-time-zone (t/today-at 0 5)
                                         (DateTimeZone/forID "Europe/Helsinki")))

(defn interface-type [format]
  (case format
    "GTFS" :gtfs
    "Kalkati.net" :kalkati))

(defn fetch-and-mark-gtfs-interface! [config db]
  (tx/with-transaction db
    (let [blacklisted-operators {:blacklist (if (empty? (:no-gtfs-update-for-operators config))
                                              #{-1} ;; this is needed for postgres NOT IN conditional
                                              (:no-gtfs-update-for-operators config))}
          gtfs-data (first (select-gtfs-urls-update db blacklisted-operators))]
      (when gtfs-data
        (specql/update! db ::t-service/external-interface-description
                        {::t-service/gtfs-imported (java.sql.Timestamp. (System/currentTimeMillis))}
                        {::t-service/id (:id gtfs-data)}))
      gtfs-data)))

(defn update-one-gtfs! [config db upload-s3?]
  ;; Ensure that gtfs-import flag is enabled
  ;; upload-s3? should be false when using local environment
  (let [{:keys [id url operator-id ts-id last-import-date format license] :as gtfs-data}
        (fetch-and-mark-gtfs-interface! config db)]
    (if (nil? gtfs-data)
      (log/debug "No gtfs files to upload.")
      (try
        (log/debug "GTFS File url found. " (pr-str gtfs-data))
        (import-gtfs/download-and-store-transit-package
          (interface-type format) (:gtfs config) db url operator-id ts-id last-import-date license id upload-s3?)
        (catch Exception e
          (log/warn e "Error when importing, uploading or saving gtfs package to db!"))
        (finally
          (log/debug "GTFS file imported and uploaded successfully!"))))))

(def night-hours #{0 1 2 3 4})

(defn night-time? [dt]
  (-> dt (t/to-time-zone timezone) time/date-fields ::time/hours night-hours boolean))


(defn detect-new-changes-task
  ([db]
   (detect-new-changes-task db false))
  ([db force?]
   (let [;; Start from the beginning of last week
         start-date (time/days-from (time/beginning-of-week (time/now)) -7)

         ;; Continue 15 weeks from the current week
         end-date (time/days-from start-date (dec (* 7 16)))]
     (lock/try-with-lock
      db "gtfs-nightly-changes" 1800
      (let [service-ids (map :id (services-for-nightly-change-detection db {:force force?}))]
        (log/info "Detect transit changes for " (count service-ids) " services.")
        (doseq [service-id service-ids]
          (log/info "Detecting next transit changes for service: " service-id)
          (try
            (->> {:service-id service-id
                  :start-date start-date
                  :end-date end-date}
                 (detection/route-changes db)
                 (detection/store-transit-changes! db service-id))
            (catch Exception e
              (log/warn e "Change detection failed for service " service-id)))))))))

(defrecord GtfsTasks [at config]
  component/Lifecycle
  (start [{db :db :as this}]
    (assoc this
           ::stop-tasks
           (if (feature/feature-enabled? config :gtfs-import)
             [(chime-at
               (filter night-time?
                       (drop 1 (periodic-seq (t/now) (t/minutes 1))))
               (fn [_]
                 (#'update-one-gtfs! config db true)))
              (chime-at (daily-at 5 15)
                        (fn [_]
                          (detect-new-changes-task db)))]
             (do
               (log/debug "GTFS IMPORT IS NOT ENABLED!")
               nil))))
  (stop [{stop-tasks ::stop-tasks :as this}]
    (for [stop-task stop-tasks]
      (stop-task))
    (dissoc this ::stop-tasks)))

(defn gtfs-tasks
  ([config] (gtfs-tasks daily-update-time config))
  ([at config]
   (->GtfsTasks at config)))
