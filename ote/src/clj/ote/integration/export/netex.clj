(ns ote.integration.export.netex
  "Exporting service for traffic data in NeTEx format"
  (:require [amazonica.aws.s3 :as s3]
            [ote.netex.netex :as netex]
            [ring.util.io :as ring-io]
            [com.stuartsierra.component :as component]
            [ote.components.http :as http]
            [compojure.core :refer [GET]]
            [clojure.java.io :as io]))

(defn file-download-url [base-url transport-service-id file-id]
  (format "%sexport/netex/%d/%d" base-url transport-service-id file-id))

(defn fetch-netex-response [db {:keys [bucket]} file-id]
  (let [filename (:ote.db.netex/filename (netex/fetch-conversion db file-id))
        file (when (and bucket
                        (not (clojure.string/blank? filename)))
               (s3/get-object bucket filename))]
    (if file
      {:status 200
       :headers {"Content-Type" "application/zip"
                 "Content-Disposition" (str "attachment; filename=" filename)}
       :body (ring-io/piped-input-stream (fn [out]
                                           (io/copy (:input-stream file) out)))}
      {:status 404
       :body "File not found"})))

(defrecord NeTExExport [config]
  component/Lifecycle
  (start [{http :http
           db :db :as this}]
    (assoc this
      ::stop (http/publish! http
                            {:authenticated? false}
                            (GET "/export/netex/:transport-service-id{[0-9]+}/:file-id{[0-9]+}"
                              {{:keys [file-id]} :params}
                              (fetch-netex-response db
                                                    (:netex config)
                                                    (Long/parseLong file-id))))))
  (stop [{stop ::stop :as this}]
    (stop)
    (dissoc this ::stop)))
