(ns ote.integration.import.ytj
  "YTJ (Finnish company register) API client."
  (:require [clj-http.client :as http-client]            
            [taoensso.timbre :as log]
            [clojure.walk :as walk]
            [clj-time.format :as format]
            [clj-time.core :as t]
            [clojure.string :as string]
            [ote.components.http :as http]
            [com.stuartsierra.component :as component]
            [compojure.core :refer [routes GET]]
            [clojure.spec.alpha :as s]
            [ote.util.feature :as feature]))


;; ui needs:
;; - businessId
;; - name
;; - auxiliaryNames
;; - addresses

(defn parse-iso8601-date [s]
  (when (string? s)
    (format/parse (:date format/formatters) s)))

(def http-get http-client/get) ;; redef'd to mock in test

(defn without-expired-items [ytj-map]
  (let [now (t/now)        
        date-in-past? (fn [d]
                        (if (nil? d)
                          false
                          (t/before? d now)))
        end-date-expired? (fn [m]
                            (let [ds (:endDate m)
                                  d (when (string? ds)
                                      (parse-iso8601-date ds))]
                              (date-in-past? d)))
        walk-fn (fn [x]
                  (if (and (vector? x) (map? (first x)))
                     (filterv (complement end-date-expired?) x)
                     x))]
    (walk/postwalk walk-fn ytj-map)))

(defn- compose-result [response]
  (into
    {:status (:status response)}
    (-> response
        :body
        :results
        first
        (select-keys [:name :auxiliaryNames :businessId :addresses :contactDetails])
        without-expired-items)))

(defn fetch-by-company-id [company-id]
  (when (and (string? company-id) (re-matches #"[0-9-]{2,20}" company-id))
    (let [url (str "https://avoindata.prh.fi/bis/v1?totalResults=true&maxResults=10&resultsFrom=0&businessId=" company-id)
          response (try
                     (http-get url {:accept :json
                                    :as :json})
                     (catch clojure.lang.ExceptionInfo e
                       ;; sadly clj-http wants to communicate status as exceptions
                       (-> e ex-data)))]
      ;(log/debug "ytj fetch: got response" (pr-str response))
      (if (or (-> response :status (not= 200))
              (-> response :body :totalResults str (not= "1")))
        (log/debug "YTJ: all is not good, status=" (-> response :status (= 200)) " body=" (-> response :body pr-str))
        )
       (compose-result response))))

(defrecord YTJFetch [config]
  component/Lifecycle
  (start [{db :db http :http :as this}]
    (assoc this ::stop
           (when (feature/feature-enabled? config :open-ytj-integration)
             ;; require authentication because we don't want to be an open proxy for PRH API (and also there may be rate limits)
             (http/publish! http {:authenticated? true}
                            (routes                           
                             (GET "/fetch/ytj" [company-id]
                                  (let [r (fetch-by-company-id company-id)]
                                    ;(println "YTJ: sending as transit:" (pr-str r))
                                    (http/transit-response r))))))))
  (stop [{stop ::stop :as this}]
    (when stop
      (stop))
    (dissoc this ::stop)))
