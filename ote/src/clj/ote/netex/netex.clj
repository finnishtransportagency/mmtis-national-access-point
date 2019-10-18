(ns ote.netex.netex
  "Scheduled tasks to update gtfs file to s3 and later to database."
  (:require
    [taoensso.timbre :as log]
    [clojure.java.io :as io]
    [cheshire.core :as cheshire]
    [ote.db.netex :as netex]
    [specql.core :as specql]
    [clojure.java.shell :refer [sh with-sh-dir]]
    [clojure.string :as str]
    [amazonica.aws.s3 :as s3]
    [ote.config.netex-config :as config-nt-static]
    [specql.op :as op]))

(defn fetch-conversions [db transport-service-id]
  (specql/fetch db
                ::netex/netex-conversion
                #{::netex/filename ::netex/id ::netex/data-content}
                (op/and
                  {::netex/transport-service-id transport-service-id}
                  {::netex/status :ok}
                  {::netex/filename op/not-null?})))

(defn fetch-conversion [db file-id]
  (first
    (specql/fetch db
                  ::netex/netex-conversion
                  #{::netex/filename ::netex/id ::netex/data-content}
                  (op/and
                    {::netex/id file-id}
                    {::netex/status :ok}
                    {::netex/filename op/not-null?}))))

(defn- path-allowed?
  "Checks if path is in a system directory or similar not allowed place. Returns true if allowed, false if not."
  [^String path]
  (when-let [path-resolved (.getCanonicalPath (clojure.java.io/file path))]
    (or (str/starts-with? path-resolved "/tmp/")
        (str/starts-with? path-resolved "/private/tmp/")))) ; OSX resolves path under /private/

(defn delete-files-recursively! [f1]
  (when (.isDirectory (io/file f1))
    (doseq [f2 (.listFiles (io/file f1))]
      (delete-files-recursively! f2)))
  (io/delete-file f1 true))

(defn cleanup-dir-recursive!
  ;; TODO: replace using fileutils interop etc?
  "Takes an path argument and does a simple sanity check before removing it and child directories recursively"
  [path]
  (if (path-allowed? path)
    (delete-files-recursively! path)
    (log/warn "Directory cleanup skipped, bad path = " path)))

(defn- compose-chouette-import-gtfs-json [operator-name]
  (cheshire/generate-string {:gtfs-import
                             {:user_name "username-1"
                              :name "job 1"
                              :organisation_name operator-name
                              :referential_name "referential-name-1"
                              :object_id_prefix "GTFS"
                              :max_distance_for_connection_link 0
                              :max_distance_for_commercial 0
                              :ignore_end_chars 0
                              :ignoreLastWord false}}
                            {:pretty true}))

(defn- compose-chouette-export-netex-json [operator-name]
  (cheshire/generate-string {:netex-export
                             {:user_name "username-1"
                              :name "job 1"
                              :organisation_name operator-name
                              :referential_name "referential-name-1"
                              :add_metadata true
                              :projection_type "4326"       ; 4326 is WSG86 projection for chouette
                              :add_extension true}}
                            {:pretty true}))

(defn- chouette-report-ok? [chouette-report-filepath]
  (if (and (.exists (io/file chouette-report-filepath))
           (.isFile (io/file chouette-report-filepath)))
    (let [action_report (:action_report (cheshire/parse-string (slurp (str chouette-report-filepath)) keyword))
          result (:result action_report)
          error-files (->> action_report
                           :files
                           (filter #(= "NOK" (:status %)))
                           (map :name))]

      (if (and (= "OK" result) (empty? error-files))
        true
        (do (log/warn "NeTEx conversion chouette report NOK: result = " result
                      ", GTFS error files = '" error-files "'"
                      ", chouette-report-filepath = " chouette-report-filepath)
            false)))
    (do
      (log/warn "NeTEx conversion chouette report missing. filepath = " chouette-report-filepath)
      false)))

(defn- chouette-output-valid?
  "Takes chouette process exit info and output path and evaluates if conversion was a success or failure.
  Return: On success string defining filesystem path to output file, on failure nil"
  [{:keys [exit err] :as ex-info}                           ; Conversion command exit info
   {:keys [conversion-work-path]}                           ; Ote netex config
   {:keys [work-dir input-report-file output-report-file]}  ; Ote chouette config
   output-filepath
   chouette-cmd]
  (if (and (= 0 exit)
           (str/blank? err)
           (chouette-report-ok? (str conversion-work-path work-dir input-report-file))
           (chouette-report-ok? (str conversion-work-path work-dir output-report-file))
           (.exists (io/file output-filepath)))
    output-filepath
    (do (log/warn "Netex conversion chouette error, command exit info = " ex-info ", tried = " chouette-cmd)
        nil)))

(defn gtfs->netex!
  "Return: On success string defining filesystem path to output netex archive, on failure nil"
  [{:keys [gtfs-file gtfs-filename gtfs-basename operator-name]} {:keys [chouette-path conversion-work-path] :as config-netex}]
  {:pre [(and (< 1 (count conversion-work-path))
              (not (clojure.string/blank? conversion-work-path)))
         (not (clojure.string/blank? gtfs-filename))
         (seq gtfs-file)]}
  (let [netex-config-static (config-nt-static/config)
        import-config-filepath (str conversion-work-path (get-in netex-config-static [:chouette :input-config-file]))
        export-config-filepath (str conversion-work-path (get-in netex-config-static [:chouette :export-config-file]))
        gtfs-filepath (str conversion-work-path gtfs-filename)
        gtfs-name-suffix "_gtfs"
        netex-filepath (str conversion-work-path
                            (if (str/ends-with? gtfs-basename gtfs-name-suffix)
                              (subs gtfs-basename 0 (- (count gtfs-basename) (count gtfs-name-suffix)))
                              gtfs-basename)
                            "_netex.zip")
        chouette-cmd ["./chouette.sh"                       ; Vector used to allow logging shell invocation on error
                      "-i " import-config-filepath
                      "-o " export-config-filepath
                      "-f " netex-filepath
                      ;; Set chouette's internal work dir under ote work dir so it gets deleted as part of task cleanup
                      "-d " (str conversion-work-path (get-in netex-config-static [:chouette :work-dir]))
                      gtfs-filepath]]

    (if (and (path-allowed? gtfs-filepath)
             (path-allowed? conversion-work-path))
      (do
        ;; Setup input files for chouette command line tool conversion call.
        ;; No return condition checks because calls throw and exception on failure
        ;(cleanup-dir-recursive! conversion-work-path)
        (.mkdirs (clojure.java.io/file conversion-work-path)) ; Returns true when created, false otherwise
        (spit import-config-filepath (compose-chouette-import-gtfs-json operator-name)) ; Returns nil on success or throws an exception
        (spit export-config-filepath (compose-chouette-export-netex-json operator-name))
        (io/copy                                            ; Returns nil on success or throws an exception
          (java.io.ByteArrayInputStream. gtfs-file)
          (io/file gtfs-filepath))
        ;; Do GTFS to NeTEx conversion, previous lines
        (log/info (str "GTFS->NeTEx invokes: " chouette-cmd))
        (->
          (with-sh-dir chouette-path
                       (apply sh chouette-cmd))             ; TODO: use a dedicated user with limited access rights
          (chouette-output-valid? config-netex
                                  (:chouette (config-nt-static/config))
                                  netex-filepath
                                  chouette-cmd)))
      (do
        (log/error (str "Bad path argument(s) " config-netex))
        nil))))

(defn- upload-s3
  "Takes path to file and map with bucket name and puts file into bucket.
  Returns: On success true,
  on failure amazonica throws an exception, on other failures like missing argument this returns false"
  [filepath {:keys [bucket]}]
  (when (and bucket filepath)
    (let [filename (.getName (io/file filepath))]
      (log/debug "Putting to S3 bucket = " bucket ", file = " filepath ", filename = " filename)
      ;; No need to set content-length?
      ;; Access denied or nonexistent bucket will throw an exception
      (s3/put-object bucket filename (io/file filepath))
      filename)))

(defn set-conversion-status!
  "Resolves operation result based on input args and updates status to db.
  Return: On successful conversion true, on failure false"
  [{:keys [netex-filepath s3-filename]}
   db
   {:keys [service-id external-interface-description-id external-interface-data-content] :as conversion-meta}]
  (let [result (if (clojure.string/blank? netex-filepath)
                 :error
                 :ok)]
    (log/info (str "GTFS->NeTEx result to db: service-id = " service-id
                   " result = " result
                   ", s3-filename = " s3-filename
                   ", conversion-meta=" conversion-meta))
    (specql/upsert! db ::netex/netex-conversion
                    #{::netex/transport-service-id ::netex/external-interface-description-id}
                    {::netex/transport-service-id service-id
                     ::netex/external-interface-description-id external-interface-description-id
                     ::netex/filename (or s3-filename "")
                     ::netex/modified (java.util.Date.)
                     ::netex/status result
                     ::netex/data-content (set (mapv keyword external-interface-data-content))})
    (= :ok result)))

(defn gtfs->netex-and-set-status!
  "Deletes `conversion-work-path` and runs gtfs->netex conversion to gtfs file in `conversion-meta`
  Return: True on success, false on failure"
  [db {:keys [conversion-work-path] :as config-netex} conversion-meta]
  (cleanup-dir-recursive! conversion-work-path)
  ;; If s3 bucket is not defined conversion result is set to db as success in order to reflect conversion tool
  ;; invocation result. Even though created netex file is lost if s3 upload is skipped.
  (let [meta (try
               (as-> (assoc conversion-meta
                       :netex-filepath (gtfs->netex! conversion-meta config-netex)) meta
                     (assoc meta
                       :s3-filename (upload-s3 (:netex-filepath meta) config-netex)))
               (catch Exception e
                 (log/warn (str "GTFS->NeTEx Conversion failed, service-id = " (:service-id conversion-meta)
                                ", external-interface-description-id = " (:external-interface-description-id conversion-meta
                                                                           )
                                ", filename = " (:filename conversion-meta)
                                ", Exception = \n" (pr-str e)))
                 conversion-meta))]
    (cleanup-dir-recursive! conversion-work-path)
    (set-conversion-status! meta db conversion-meta)))
