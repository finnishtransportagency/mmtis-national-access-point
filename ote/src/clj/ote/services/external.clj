(ns ote.services.external
  "CKAN-integration tasks. Functions to invoke CKAN API calls from OTE."
  (:require [com.stuartsierra.component :as component]
            [ote.components.http :as http]
            [ote.util.csv :as csv-util]
            [ote.db.transport-service :as t-service]
            [ote.db.modification :as modification]
            [clj-http.client :as http-client]
            [compojure.core :refer [routes GET POST DELETE]]
            [taoensso.timbre :as log]
            [ote.authorization :as authorization]
            [clojure.string :as str]
            [clojure.data.csv :as csv]
            [clojure.string :as s]
            [specql.core :as specql]
            [clojure.java.io :as io]))

(defn ensure-url
  "Add http:// to the beginning of the given url if it doesn't exist."
  [url]
  (str/replace (if (not (s/includes? url "http"))
                  (str "http://" url)
                  url)
               #" " "%20"))

(defn parse-response->csv
  "Convert given vector to map where map key is given in the first line of csv file."
  [csv-data]
  (let [headers (first csv-data)
        valid-header? (csv-util/valid-csv-header? headers)
        parsed-data (when valid-header?
                      (map (fn [cells]
                             (let [[business-id name] (map str/trim cells)]
                               {::t-service/business-id business-id
                                ::t-service/name name}))
                            (rest csv-data)))
        validated-data (filter #(csv-util/valid-business-id? (::t-service/business-id %)) parsed-data)]
    {:result validated-data
     :failed-count (- (count parsed-data) (count validated-data))}))

(defn save-companies
  "Save business-ids, company names to db"
  [db data]
   (let [data (modification/with-modification-fields data ::t-service/id)]
    (specql/upsert! db ::t-service/service-company data)))

(defn- read-csv
  "Read CSV from input stream. Guesses the separator from the first line."
  [input]
  (let [separator (csv-util/csv-separator input)]
    (csv/read-csv input :separator separator)))

(defn check-csv
  "Fetch csv file from given url, parse it and return status map that contains information about the count of companies
  in the file."
  [url-data]
  (try
    (let [url (ensure-url (get url-data :url))
          response (http-client/get url {:as "UTF-8"
                                         :socket-timeout 30000
                                         :conn-timeout 10000})]
      (if (= 200 (:status response))
        (try
          (let [data (when (= 200 (:status response))
                       (read-csv (:body response)))
                parsed-data (parse-response->csv data)]
            ;; response to client application
            {:status :success
             :count (count (:result parsed-data))
             :failed-count (:failed-count parsed-data)
             :companies (:result parsed-data)})
          (catch Exception e
            (log/warn "CSV check failed due to Exception in parsing: " e)
            {:status :failed
             :error :csv-parse-failed}))
        {:status :failed
         :error :url-parse-failed
         :http-status (:status response)}))
    (catch Exception e
      (log/warn "CSV check failed due to Exception in HTTP connection" e)
      {:status :failed
       :error :url-parse-failed})))

(defn- check-external-api
  [url-data]
  (try
    (let [url (ensure-url (get url-data :url))
          response (http-client/get url {:as "UTF-8"
                                         :socket-timeout 30000
                                         :conn-timeout 10000})]
      (if (= 200 (:status response))
        {:status :success}
        {:status :failed}))
    (catch Exception e
      {:status :failed})))

(defn- external-routes-auth
  "Routes that require authentication"
  [db nap-config]
  (routes
   (POST  "/check-company-csv" {url-data :body}
          (http/transit-response
           (check-csv (http/transit-request url-data ))))
   (POST  "/check-external-api" {url-data :body}
          (http/transit-response
           (check-external-api
            (http/transit-request url-data ))))))

(defrecord External [nap-config]
  component/Lifecycle
  (start [{:keys [db http] :as this}]
    (assoc
      this ::stop
           [(http/publish! http (external-routes-auth db nap-config))]))
  (stop [{stop ::stop :as this}]
    (doseq [s stop]
      (s))
    (dissoc this ::stop)))
