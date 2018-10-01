(ns ote.integration.import.gtfs
  "GTFS file import functionality."
  (:require [amazonica.aws.s3 :as s3]
            [ote.components.http :as http]
            [com.stuartsierra.component :as component]
            [compojure.core :refer [routes GET]]
            [clj-http.client :as http-client]
            [ote.util.zip :refer [read-zip read-zip-with]]
            [ote.gtfs.spec :as gtfs-spec]
            [ote.gtfs.parse :as gtfs-parse]
            [specql.core :as specql]
            [ote.db.gtfs :as gtfs]
            [ote.db.transport-service :as t-service]
            [clojure.java.io :as io]
            [digest]
            [specql.op :as op]
            [taoensso.timbre :as log]
            [specql.impl.composite :as specql-composite]
            [specql.impl.registry :refer [table-info-registry]]
            [jeesql.core :refer [defqueries]]
            [ote.gtfs.kalkati-to-gtfs :as kalkati-to-gtfs])
  (:import (java.io File)))

(defqueries "ote/integration/import/stop_times.sql")


(defn load-zip-from-url [url]
  (with-open [in (:body (http-client/get url {:as :stream}))]
    (read-zip in)))

(defn load-file-from-url [db interface-id url last-import-date etag]
  (let [query-headers {:headers (merge
                                  (if (not (nil? etag))
                                    {"If-None-Match" etag}
                                    (when-not (nil? last-import-date)
                                      {"If-Modified-Since" last-import-date})))
                       :as :byte-array}
        response (http-client/get url query-headers)]
    (if (= 304 (:status response))
      ;; Not modified
      (do
        ;; Remove old import errors, because the package can be loaded, but we already have the newest version.
        (specql/update! db ::t-service/external-interface-description
                        {::t-service/gtfs-import-error nil}
                        {::t-service/id interface-id})
        nil)
      response)))

(defn load-gtfs [url-or-response]
  (http/transit-response
    (into {}
          (keep (fn [{:keys [name data]}]
                  (when-let [gtfs-file-type (gtfs-spec/name->keyword name)]
                    [gtfs-file-type (gtfs-parse/parse-gtfs-file gtfs-file-type data)])))
          (if (and (map? url-or-response)
                   (contains? url-or-response :body))
            ;; This is an HTTP response, read body input stream
            (read-zip (:body url-or-response))

            ;; This is an URL, fetch and read it
            (load-zip-from-url url-or-response)))))

(defn gtfs-file-name [operator-id ts-id]
  (let [new-date (java.util.Date.)
        date (.format (java.text.SimpleDateFormat. "yyyy-MM-dd") new-date)]
    (str date "_" operator-id "_" ts-id "_gtfs.zip")))

(defn gtfs-hash [file]
  (digest/sha-256 file))

(defn db-table-name [file-name]
  (case file-name
    "agency.txt" :gtfs/agency
    "stops.txt" :gtfs/stop
    "routes.txt" :gtfs/route
    "calendar.txt" :gtfs/calendar
    "calendar_dates.txt" :gtfs/calendar-date
    "shapes.txt" :gtfs/shape
    "stop_times.txt" :gtfs/stop-time
    "trips.txt" :gtfs/trip
    "transfers.txt" nil
    nil))

(defmulti process-rows (fn [file rows] file))

;; Combine trips into an array by route and service ids
(defmethod process-rows :gtfs/trips-txt [_ trips]
  (for [[[route-id service-id] trips] (group-by (juxt :gtfs/route-id :gtfs/service-id) trips)]
    {:gtfs/route-id route-id
     :gtfs/service-id service-id
     :gtfs/trips (map #(dissoc % :gtfs/route-id :gtfs/service-id) trips)}))

;; Combine into an array by shape id
(defmethod process-rows :gtfs/shapes-txt [_ shapes]
  (for [[shape-id shapes] (group-by :gtfs/shape-id shapes)]
    {:gtfs/shape-id shape-id
     :gtfs/route-shape (map #(select-keys % #{:gtfs/shape-pt-lat :gtfs/shape-pt-lon
                                              :gtfs/shape-pt-sequence :gtfs/shape-dist-traveled})
                            shapes)}))

(defmethod process-rows :default [_ rows] rows)

(defn import-stop-times [db package-id stop-times-file]
  (log/debug "Importing stop times from " stop-times-file
             " (" (int (/ (.length stop-times-file) (* 1024 1024))) "mb)")
  (let [;; Read all trips into memory (mapping from trip id to the row and index for update)
        trip-id->update-info (into {}
                                   (map (juxt :trip-id identity))
                                   (gtfs-trip-id-and-index db {:package-id package-id}))]
    (loop [i 0

           ;; Stop times file should have stops with the same trip id on consecutive lines
           ;; Partition returns a lazy sequence of groups of consecutive lines that have
           ;; the same trip id.
           [p & ps] (partition-by
                     :gtfs/trip-id
                     (gtfs-parse/parse-gtfs-file :gtfs/stop-times-txt
                                                 (io/reader stop-times-file)))]
      (when p
        (when (zero? (mod i 1000))
          (log/debug "Trip partitions stored: " i))

        (let [;; Use specql internal stringify to turn sequence of stop times
              ;; to a string in PostgreSQL composite array format
              stop-times (specql-composite/stringify @table-info-registry
                                                     {:category "A"
                                                      :element-type :gtfs/stop-time-info}
                                                     p true)
              {:keys [trip-row-id index] :as found} (trip-id->update-info (:gtfs/trip-id (first p)))]
          (when found
            (update-stop-times! db {:package-id package-id
                                    :trip-row-id trip-row-id
                                    :index index
                                    :stop-times stop-times}))
          (recur (inc i) ps))))))

(defn save-gtfs-to-db [db gtfs-file package-id interface-id]
  (log/debug "Save-gtfs-to-db - package-id: " package-id " interface-id " interface-id)
  (let [stop-times-file (File/createTempFile (str "stop-times-" package-id "-") ".txt")]
    (try
      (read-zip-with
       (java.io.ByteArrayInputStream. gtfs-file)
       (fn [{:keys [name input]}]
         (if (= name "stop_times.txt")
           ;; Copy stop times to a temp file, we need to process it last
           (with-open [output (io/output-stream stop-times-file)]
             (io/copy input output))
           (when-let [db-table-name (db-table-name name)]
             (let [file-type (gtfs-spec/name->keyword name)
                   file-data (gtfs-parse/parse-gtfs-file file-type (io/reader input))]
               (log/debug file-type " file: " name " PARSED.")
               (when (= file-type :gtfs/calendar-txt)
                 (def debug-calendar file-data))
               (doseq [fk (process-rows file-type file-data)]
                 (when (and db-table-name (seq fk))
                   (specql/insert! db db-table-name (assoc fk :gtfs/package-id package-id)))))))))

      ;; Handle stop times
      (import-stop-times db package-id stop-times-file)

      (log/info "Generating date hashes for package " package-id)
      (generate-package-hashes db {:package-id package-id})

      (log/info "Generating finnish regions and envelope for package " package-id)
      (gtfs-set-package-geometry db {:package-id package-id})

      ;; IF handling was ok, remove all errors from the interface table
      (specql/update! db ::t-service/external-interface-description
                      {::t-service/gtfs-db-error nil ::t-service/gtfs-import-error nil}
                      {::t-service/id interface-id})

      (catch Exception e
        (.printStackTrace e)
        (specql/update! db ::t-service/external-interface-description
                        {::t-service/gtfs-imported (java.sql.Timestamp. (System/currentTimeMillis))
                         ::t-service/gtfs-db-error (str (.getName (class e)) ": " (.getMessage e))}
                        {::t-service/id interface-id})
        (log/warn "Error in save-gtfs-to-db" e))

      (finally
        (.delete stop-times-file)))))

;; PENDING: this is for local testing, truncates *ALL* GTFS data from the database
;;          and reads in a local GTFS zip file
;; Create Transport Service with :sheduled sub_tybe
;; Add gtfs url for
;; Keep this method commented away and when you want to manually use this, start REPL first, and then run this in REPL only.
;; Also! Change from gtfs-package table columns transport-service-id, transport-operator-id and external-interface-description-id to what they should be.
;; Also! Change from gtfs_package column created to be earlier than today.
;; And then run SELECT gtfs_upsert_service_transit_changes(<service_id>);
#_(defn test-hsl-gtfs []
  (let [db (:db ote.main/ote)]
    (clojure.java.jdbc/execute! db ["TRUNCATE TABLE gtfs_package RESTART IDENTITY CASCADE"])
    (clojure.java.jdbc/execute! db ["INSERT INTO gtfs_package (id) VALUES (1)"])
    (let [bytes (with-open [in (io/input-stream "/Users/markusva/Downloads/google_transit (1).zip" #_"hsl_gtfs.zip")]
                  (let [out (java.io.ByteArrayOutputStream.)]
                    (io/copy in out)
                    (.toByteArray out)))]
      (println "**************************** START test-hsl-gtfs *********************")
      (println "GTFS zip has " (int (/ (count bytes) (* 1024 1024))) " megabytes")
      (save-gtfs-to-db db bytes 1 1)
      (println "******************* test-hsl-gtfs end *********************"))))

(defmulti load-transit-interface-url
  "Load transit interface from URL. Dispatches on type.
  Returns a response map or nil if it has not been modified."
  (fn [type db interface-id url last-import-date saved-etag] type))


(defn- load-interface-url [db interface-id url last-import-date saved-etag]
  (try
    (load-file-from-url db interface-id url last-import-date saved-etag)
    (catch Exception e
      (log/warn "Error when loading gtfs package from url " url ": " (.getMessage e))
      (specql/update! db ::t-service/external-interface-description
                      ;; Note that the gtfs-imported field tells us when the interface was last checked.
                      {::t-service/gtfs-imported (java.sql.Timestamp. (System/currentTimeMillis))
                       ::t-service/gtfs-import-error (.getMessage e)}
                      {::t-service/id interface-id})
      nil)))

(defmethod load-transit-interface-url :gtfs [_ db interface-id url last-import-date saved-etag]
  (load-interface-url db interface-id url last-import-date saved-etag))

(defmethod load-transit-interface-url :kalkati [_ db interface-id url last-import-date saved-etag]
  (let [response (load-interface-url db interface-id url last-import-date saved-etag)]
    (when response
      (update response :body kalkati-to-gtfs/convert-bytes))))

(defn download-and-store-transit-package
  "Download GTFS (later kalkati files also) file, upload to s3, parse and store to database.
  Requires s3 bucket config, database settings, operator-id and transport-service-id."
  [interface-type gtfs-config db url operator-id ts-id last-import-date license interface-id]
  (let [filename (gtfs-file-name operator-id ts-id)
        saved-etag (:gtfs/etag (last (specql/fetch db :gtfs/package
                                                   #{:gtfs/etag}
                                                   {:gtfs/transport-operator-id operator-id
                                                    :gtfs/transport-service-id  ts-id})))
        response (load-transit-interface-url interface-type db interface-id url last-import-date saved-etag)
        new-etag (get-in response [:headers :etag])
        gtfs-file (:body response)]

    (when-not (nil? response)
      (if (nil? gtfs-file)
        (do
          (log/warn "Got empty body as response when loading gtfs from: " url)
          (specql/update! db ::t-service/external-interface-description
                          {::t-service/gtfs-imported (java.sql.Timestamp. (System/currentTimeMillis))
                           ::t-service/gtfs-import-error (str "Virhe ladatatessa pakettia: " (pr-str response))}
                          {::t-service/id interface-id}))
        (let [new-gtfs-hash (gtfs-hash gtfs-file)
              old-gtfs-hash (specql/fetch db :gtfs/package
                                          #{:gtfs/sha256}
                                          {:gtfs/transport-operator-id operator-id
                                           :gtfs/transport-service-id ts-id})]

          ;; No gtfs import errors catched. Remove old import errors.
          (specql/update! db ::t-service/external-interface-description
                          {::t-service/gtfs-import-error nil}
                          {::t-service/id interface-id})

          ;; IF hash doesn't match, save new and upload file to s3
          (if (or (nil? old-gtfs-hash) (not= old-gtfs-hash new-gtfs-hash))
            (do
              (let [package (specql/insert! db :gtfs/package {:gtfs/sha256 new-gtfs-hash
                                                              :gtfs/transport-operator-id operator-id
                                                              :gtfs/transport-service-id ts-id
                                                              :gtfs/created (java.sql.Timestamp. (System/currentTimeMillis))
                                                              :gtfs/etag new-etag
                                                              :gtfs/license license
                                                              :gtfs/external-interface-description-id interface-id})]

                (s3/put-object (:bucket gtfs-config) filename (java.io.ByteArrayInputStream. gtfs-file) {:content-length (count gtfs-file)})
                (log/debug "File: " filename " was uploaded to S3 successfully.")

                ;; Parse gtfs package and save it to database.
                (save-gtfs-to-db db gtfs-file (:gtfs/id package) interface-id)))
            (log/debug "File " filename " was found from S3, no need to upload. Thank you for trying.")))))))

(defrecord GTFSImport [config]
  component/Lifecycle
  (start [{db :db http :http :as this}]
    (assoc this ::stop
      (http/publish! http {:authenticated? false}
                     (routes
                       (GET "/import/gtfs" {params :query-params}
                         (load-gtfs (get params "url")))))))
  (stop [{stop ::stop :as this}]
    (stop)
    (dissoc this ::stop)))
