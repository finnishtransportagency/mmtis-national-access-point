(ns ote.services.transit-visualization
  (:require [compojure.core :refer [GET]]
            [jeesql.core :refer [defqueries]]
            [ote.time :as time]
            [cheshire.core :as cheshire]
            [ote.components.http :as http]
            [ote.components.service :refer [define-service-component]]
            [specql.core :as specql]
            [clojure.string :as str]
            [specql.impl.composite :as composite]
            [specql.impl.registry :as specql-registry]
            [ote.util.fn :refer [flip]]
            [ote.util.db :as util-db]
            [ote.transit-changes.detection :as detection]
            [clojure.set :as set]
            [digest]
            [ote.authorization :as authorization]))

(defqueries "ote/services/transit_visualization.sql")
;; Testing to declare jeesql functions to make clj-kondo work better
(declare fetch-date-hashes-for-route-with-route-hash-id)
(declare fetch-route-trips-by-hash-and-date)
(declare fetch-service-info)
(declare detected-service-change-by-date)
(declare detected-route-changes-by-date)
(declare fetch-gtfs-packages-for-service)
(declare fetch-route-trip-info-by-name-and-date)

(defn- parse-stops [stops]
  (mapv (fn [stop]
          (let [[lat lon stop-name trip-id headsign] (str/split stop #";")]
            {:lat (Double/parseDouble lat)
             :lon (Double/parseDouble lon)
             :stop-name stop-name
             :trip-id trip-id
             :headsign headsign}))
        (str/split stops #"\|\|")))

(defn- format-trip-data-to-geojson
  "Formatting trips route-line to line geometry that indicates where busses drive. Stops are formatted to Points with
  location coordinates and properties like stop name."
  [{:keys [route-line departures stops trip-id] :as foo}]
  (let [all-stops (parse-stops stops)
        first-stop (first all-stops)
        last-stop (last all-stops)
        stop-location-hash (digest/sha-256 (str/join "-" (map (juxt :lat :lon) all-stops)))]
    {:stop-location-hash stop-location-hash
     :route-line {:type "Feature"
                  :properties {:departures (mapv time/format-interval-as-time (.getArray departures))
                               :routename (str (:stop-name first-stop) " \u2192 " (:stop-name last-stop) "|| (" stop-location-hash ")")}
                  :geometry (cheshire/decode route-line keyword)

                  :stops (map
                           (fn [stop]
                             (let [[lon lat name trip-id] (str/split stop #";")]
                               {:type "Point"
                                :coordinates [(Double/parseDouble lon)
                                              (Double/parseDouble lat)]
                                :properties {"stopname" name
                                             "trip-name" (str (:stop-name first-stop) " \u2192 " (:stop-name last-stop) "|| (" stop-location-hash ")")}}))
                           (when-not (str/blank? stops)
                             (str/split stops #"\|\|")))}}))

(defn trip-lines
  "Modify trip list (trips) to distinct routes using :stop-location-hash"
  [trips]
  (mapv
    #(first (second %)) ; group by returns list of objects with the same key. Take first element of every list.
    (group-by
      :stop-location-hash ;; stop-location-hash contains digest of all stop locations = identify different trip lines
      (mapv
        #(format-trip-data-to-geojson %)
        trips))))

(defn service-changes-for-date [db service-id date]
  (first
   (specql/fetch db :gtfs/transit-changes
                 (specql/columns :gtfs/transit-changes)
                 {:gtfs/transport-service-id service-id
                  :gtfs/date date})))

(defn service-calendar-for-route [db service-id route-hash-id detection-date]
  (if (and (integer? service-id) (string? route-hash-id) (string? detection-date))
    (into {}
          (map (juxt :date :hash))
          (fetch-date-hashes-for-route-with-route-hash-id db {:service-id service-id
                                                              :route-hash-id route-hash-id
                                                              :detection-date detection-date}))
    nil))

(defn parse-gtfs-stoptimes [pg-array]
  (let [string (str pg-array)]
    (when-not (str/blank? string)
      (composite/parse @specql-registry/table-info-registry
                       {:category "A"
                        :element-type :gtfs/stoptime-display}
                       string))))

(defn trip-differences-for-dates [db service-id date1 date2 route-hash-id detection-date]
  "Get trip differences for dates using detection date. Detection-date is essential, because it affects
  the packages where trip data is fetched"
  (let [date1-trips (detection/route-trips-for-date db service-id route-hash-id (time/parse-date-iso-8601 date1) detection-date false)
        date2-trips (detection/route-trips-for-date db service-id route-hash-id (time/parse-date-iso-8601 date2) detection-date false)
        result (detection/compare-selected-trips date1-trips date2-trips
                                                   (time/parse-date-iso-8601 date1)
                                                   (time/parse-date-iso-8601 date2))
        stop-changes (reduce detection/update-min-max-range nil
                             (map :stop-time-changes (:trip-changes result)))
        sequence-changes (reduce detection/update-min-max-range nil
                                 (map :stop-seq-changes (:trip-changes result)))]

    (-> result
        (assoc :trip-stop-sequence-changes-lower (:lower sequence-changes))
        (assoc :trip-stop-sequence-changes-upper (:upper sequence-changes))
        (assoc :trip-stop-time-changes-lower (:lower stop-changes))
        (assoc :trip-stop-time-changes-upper (:upper stop-changes))
        (dissoc :starting-week-date :different-week-date :trip-changes)
        (set/rename-keys {:added-trips :gtfs/added-trips :removed-trips :gtfs/removed-trips}))))

(define-service-component TransitVisualization {}

  ;; Get transit changes, service info and package info for given date and service
  ^{:unauthenticated false :format :transit}
  (GET "/transit-visualization/:service-id/:date{[0-9\\-]+}"
       {{:keys [service-id date]} :params
        user :user}
    (let [service-id (Long/parseLong service-id)]
      ;; Is transit authority
      (or (authorization/transit-authority-authorization-response user)

          ;; Return result
          {:service-info (first (fetch-service-info db {:service-id service-id}))
           :changes (first (detected-service-change-by-date db
                                                            {:service-id service-id
                                                             :date (time/iso-8601-date->sql-date date)}))
           :route-changes (map #(assoc % :change-type (keyword (:change-type %)))
                               (detected-route-changes-by-date db
                                                               {:date (time/iso-8601-date->sql-date date)
                                                                :service-id service-id}))
           :route-hash-id-type (first (specql/fetch db :gtfs/detection-service-route-type
                                                    #{:gtfs/route-hash-id-type}
                                                    {:gtfs/transport-service-id service-id}))
           :gtfs-package-info (fetch-gtfs-packages-for-service db {:service-id service-id})
           :transit-changes (specql/fetch db :gtfs/transit-changes
                                          (specql/columns :gtfs/transit-changes)
                                          {:gtfs/transport-service-id service-id}
                                          {:specql.core/order-by :gtfs/date
                                           :specql.core/order-direction :desc
                                           :specql.core/limit 50})
           :used-packages (:gtfs/package-ids (first (specql/fetch db :gtfs/transit-changes
                                                                  #{:gtfs/package-ids}
                                                                  {:gtfs/transport-service-id service-id
                                                                   :gtfs/date (time/iso-8601-date->sql-date date)}
                                                                  {:specql.core/order-by :gtfs/date
                                                                   :specql.core/order-direction :desc
                                                                   :specql.core/limit 1})))})))


  ^{:unauthenticated false :format :transit}
  (GET "/transit-visualization/:service-id/route"
       {{:keys [service-id]} :params
        {:strs [route-hash-id detection-date]} :query-params
        user :user}
    (or (authorization/transit-authority-authorization-response user)
        {:calendar (service-calendar-for-route db (Long/parseLong service-id) route-hash-id detection-date)}))


  ^{:unauthenticated false}
  (GET "/transit-visualization/:service-id/route-lines-for-date"
       {{service-id :service-id} :params
        {:strs [date used-packages route-hash-id]} :query-params
        user :user}
    (or (authorization/transit-authority-authorization-response user)
        (http/geojson-response
          (cheshire/encode
            {:type "FeatureCollection"
             :features (trip-lines
                         (fetch-route-trips-by-hash-and-date
                           db
                           {:service-id (Long/parseLong service-id)
                            :date (time/parse-date-iso-8601 date)
                            :used-packages (util-db/str-vec->str used-packages)
                            :route-hash-id route-hash-id}))}
            {:key-fn name}))))

  ^{:unauthenticated false :format :transit}
  (GET "/transit-visualization/:service-id/route-trips-for-date"
       {{service-id :service-id} :params
        {:strs [date used-packages route-hash-id]} :query-params
        user :user}
    (or (authorization/transit-authority-authorization-response user)
        (into []
              (map #(update % :stoptimes parse-gtfs-stoptimes))
              (fetch-route-trip-info-by-name-and-date
                db
                {:service-id (Long/parseLong service-id)
                 :date (time/parse-date-iso-8601 date)
                 :used-packages (util-db/str-vec->str used-packages)
                 :route-hash-id route-hash-id}))))

  ^{:unauthenticated false :format :transit}
  (GET "/transit-visualization/:service-id/route-differences"
       {{service-id :service-id} :params
        {:strs [date1 date2 used-packages detection-date route-hash-id]} :query-params
        user :user}
    (or (authorization/transit-authority-authorization-response user)
        (trip-differences-for-dates db service-id date1 date2 route-hash-id detection-date))))
