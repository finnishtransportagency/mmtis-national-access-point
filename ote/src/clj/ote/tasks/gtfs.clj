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
            [ote.transit-changes.detection :as detection]
            [ote.config.transit-changes-config :as config-tc]
            [ote.netex.netex :as netex]
            [ote.util.db :refer [PgArray->vec]])
  (:import (org.joda.time DateTimeZone)))

(defqueries "ote/tasks/gtfs.sql")
(defqueries "ote/services/transit_changes.sql")

(def daily-update-time (t/from-time-zone (t/today-at 0 5)
                                         (DateTimeZone/forID "Europe/Helsinki")))

(defn interface-type [format]
  (case format
    "GTFS" :gtfs
    "Kalkati.net" :kalkati
    nil))

(defn mark-gtfs-package-imported! [db interface]
  (specql/update! db ::t-service/external-interface-description
                  {::t-service/gtfs-imported (java.sql.Timestamp. (System/currentTimeMillis))}
                  {::t-service/id (:id interface)})) ;; external-interface-description.id, not service id.

(defn fetch-next-gtfs-interface! [db config]
  (tx/with-transaction
    db
    (let [blacklisted-operators {:blacklist (if (empty? (:no-gtfs-update-for-operators config))
                                              #{-1}         ;; this is needed for postgres NOT IN conditional
                                              (:no-gtfs-update-for-operators config))}
          interface (first (select-gtfs-urls-update db blacklisted-operators))]
      (when interface
       (mark-gtfs-package-imported! db interface))
      interface)))

(defn fetch-given-gtfs-interface!
  "Get gtfs package data from database for given service."
  [db service-id]
  (let [gtfs-data (first (select-gtfs-url-for-service db {:service-id service-id}))]
    (when gtfs-data
      (mark-gtfs-package-imported! db gtfs-data))
    gtfs-data))

(defn update-one-gtfs!
  ([config db upload-s3?]
   (update-one-gtfs! config db upload-s3? nil))
  ([config db upload-s3? service-id]
  ;; Ensure that gtfs-import flag is enabled
   ;; upload-s3? should be false when using local environment
   (let [;; Load next gtfs package or package that is related to given service-id
         interface (if service-id
                     (fetch-given-gtfs-interface! db service-id)
                     (fetch-next-gtfs-interface! db config))
         interface (if (contains? interface :data-content)  ; Avoid creating a coll with empty key when coll doesn't exist
                     (update interface :data-content PgArray->vec)
                     interface)
         force-download? (integer? service-id)]
     (if interface
       (try
         (if-let [conversion-meta (import-gtfs/download-and-store-transit-package
                                   (interface-type (:format interface))
                                   (:gtfs config)
                                   db
                                   interface
                                   upload-s3?
                                   force-download?)]
          (if (netex/gtfs->netex-and-set-status! db (:netex config) conversion-meta)
            nil                                             ; This if & nil used to make success branch more readable
            (log/spy :warn "GTFS: Error on GTFS->NeTEx conversion"))
          (log/spy :warn (str "GTFS: Could not import GTFS file. service-id = " (:ts-id interface))))
        (catch Exception e
          (log/spy :warn (str "GTFS: Error importing, uploading or saving gtfs package to db! Exception=" e))))
      (log/spy :debug (str "GTFS: No gtfs files to upload. service-id = " service-id))))))

(def night-hours #{0 1 2 3 4})

(defn night-time? [dt]
  (-> dt (t/to-time-zone timezone) time/date-fields ::time/hours night-hours boolean))

;; To run change detection for service(s) from REPL, call this with vector of service-ids: `(detect-new-changes-task (:db ote.main/ote) (time/now) true [1289])`
(defn detect-new-changes-task
  ([db detection-date force?]
   (detect-new-changes-task db detection-date force? nil))
  ([db detection-date force? service-ids]
   (let [lock-time-in-seconds (if force?
                                1
                                1800)
         today detection-date                               ;; Today is the default but detection may be run "in the past" if admin wants to
         ;; Start from the beginning of last week
         start-date (time/days-from (time/beginning-of-week detection-date) -7)
         ;; Date in future up to where traffic should be analysed
         end-date (time/days-from start-date (:detection-window-days (config-tc/config)))

         ;; Convert to LocalDate instances
         [start-date end-date today] (map (comp time/date-fields->date time/date-fields)
                                          [start-date end-date today])]
     (lock/try-with-lock
       db "gtfs-nightly-changes" lock-time-in-seconds
       (let [;; run detection only for given services or all
             service-ids (if service-ids
                           service-ids
                           (mapv :id (services-for-nightly-change-detection db {:force force?})))
             service-count (count service-ids)]
         (log/info "Detect transit changes for " (count service-ids) " services.")
         (dotimes [i (count service-ids)]
           (let [service-id (nth service-ids i)]
             (log/info "Detecting next transit changes for service (" (inc i) " / " service-count " ): " service-id)
             (try
               (let [query-params {:service-id service-id
                                   :start-date start-date
                                   :end-date end-date}]
                 (detection/update-transit-changes!
                   db today service-id
                   (detection/service-package-ids-for-date-range db query-params)
                   (detection/detect-route-changes-for-service db query-params)))
               (catch Exception e
                 (log/warn e "Change detection failed for service " service-id)))
             (log/info "Detection completed for service: " service-id))))))))

(defn recalculate-detected-changes-count
"Change detection (detect-new-changes-task) stores detected changes. These changes will expire after some period of time.
All changes occur on a certain day and they will get old. Transit-changes page shows how many changes there are and
if we don't recalculate them every night expired changes will be show also. So in this fn we recalculate change counts again using
different-week-date value and skip all expired changes."
  [db]
  (let [upcoming-changes (upcoming-changes db)
        _ (log/info "Detected change count recalculation started!")]
    ;; Loop upcoming-changes
    (dotimes [i (count upcoming-changes)]
      (let [change-row (nth upcoming-changes i)
            changes (map #(assoc % :change-type (keyword (:change-type %)))
                         (valid-detected-route-changes db
                                                         {:date (:date change-row)
                                                          :service-id (:transport-service-id change-row)}))
            grouped-changes (group-by :change-type changes)]

        (log/info (inc i) "/" (count upcoming-changes) " Recalculating detected change amounts (by change type) for service " (:transport-service-id change-row) " detection date " (:date change-row))
        ;; Update sinle transit-change (change-row)
        (specql/update! db :gtfs/transit-changes
                        {:gtfs/removed-routes (count (group-by :route-hash-id (:removed grouped-changes)))
                         :gtfs/added-routes (count (group-by :route-hash-id (:added grouped-changes)))
                         :gtfs/changed-routes (count (group-by :route-hash-id (:changed grouped-changes)))
                         :gtfs/no-traffic-routes (count (group-by :route-hash-id (:no-traffic grouped-changes)))}
                        {:gtfs/date (:date change-row)
                         :gtfs/transport-service-id (:transport-service-id change-row)})))
    (log/info "Detected change count recalculation ready!")))

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
                     (detect-new-changes-task db (time/now) false)))
         (chime-at (daily-at 0 15)
                   (fn [_]
                     (recalculate-detected-changes-count db)))]
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
