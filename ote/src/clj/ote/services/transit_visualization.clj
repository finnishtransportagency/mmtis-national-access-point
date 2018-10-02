(ns ote.services.transit-visualization
  (:require [compojure.core :refer [GET]]
            [jeesql.core :refer [defqueries]]
            [ote.time :as time]
            [cheshire.core :as cheshire]
            [ote.components.http :as http]
            [ote.components.service :refer [define-service-component]]
            [ote.db.transport-operator :as t-operator]
            [specql.core :as specql]
            [specql.op :as op]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [specql.impl.composite :as composite]
            [specql.impl.registry :as specql-registry]
            [ote.util.fn :refer [flip]]
            [ote.db.transport-service :as t-service]))

(defqueries "ote/services/transit_visualization.sql")

(defn route-line-features [rows]
  (mapcat (fn [{:keys [route-line departures stops] :as foo}]
            (vec (into
                  #{{:type "Feature"
                     :properties {:departures (mapv time/format-interval-as-time (.getArray departures))}
                     :geometry (cheshire/decode route-line keyword)}}
                  (map (fn [stop]
                         (let [[lon lat name] (str/split stop #",")]
                           {:type "Point"
                            :coordinates [(Double/parseDouble lon)
                                          (Double/parseDouble lat)]
                            :properties {"name" name}})))
                  (when (not (str/blank? stops))
                    (str/split stops #"\|\|")))))
          rows))

(defn service-changes-for-date [db service-id date]
  (first
   (specql/fetch db :gtfs/transit-changes
                 (specql/columns :gtfs/transit-changes)
                 {:gtfs/transport-service-id service-id
                  :gtfs/date date})))

(defn service-calendar-for-route [db service-id route-short-name route-long-name trip-headsign]
  (into {}
        (map (juxt :date :hash))
        (fetch-date-hashes-for-route db {:service-id service-id
                                         :route-short-name route-short-name
                                         :route-long-name route-long-name
                                         :trip-headsign trip-headsign})))

(defn parse-gtfs-stoptimes [pg-array]
  (let [string (str pg-array)]
    (if (str/blank? string)
      nil
      (composite/parse @specql-registry/table-info-registry
                       {:category "A"
                        :element-type :gtfs/stoptime-display}
                       string))))

(defn- fetch-gtfs-package-info [db where-clause]
  (specql/fetch db :gtfs/package
                #{:gtfs/created
                  [:gtfs/external-interface-description #{::t-service/external-interface}]}
                where-clause
                {::specql/order-by :gtfs/created
                 ::specql/order-direction :descending}))

(defn gtfs-package-info [db transport-service-id package-ids]
  (let [current-packages (fetch-gtfs-package-info db {:gtfs/transport-service-id transport-service-id
                                                      :gtfs/id (op/in package-ids)})
        previous-packages (fetch-gtfs-package-info db {:gtfs/transport-service-id transport-service-id
                                                       :gtfs/id (op/not (op/in package-ids))})]
    {:current-packages current-packages
     :previous-packages previous-packages}))

(define-service-component TransitVisualization {}

  ^{:unauthenticated true :format :transit}
  (GET "/transit-visualization/:service-id/:date{[0-9\\-]+}"
       {{:keys [service-id date]} :params}
       (let [service-id (Long/parseLong service-id)
             changes (service-changes-for-date db
                                               service-id
                                               (-> date
                                                   time/parse-date-iso-8601
                                                   java.sql.Date/valueOf))]
         {:service-info (first (fetch-service-info db {:service-id service-id}))
          :changes changes
          :gtfs-package-info (gtfs-package-info db service-id (:gtfs/package-ids changes))}))

  ^{:unauthenticated true :format :transit}
  (GET "/transit-visualization/:service-id/route"
       {{:keys [service-id]} :params
        {:strs [short-name long-name headsign]} :query-params}
       {:calendar (service-calendar-for-route db (Long/parseLong service-id)
                                              short-name long-name headsign)})


  ^:unauthenticated
  (GET "/transit-visualization/:service-id/route-lines-for-date"
       {{service-id :service-id} :params
        {:strs [date short-name long-name headsign]} :query-params}
       (http/geojson-response
        (cheshire/encode
         {:type "FeatureCollection"
          :features (route-line-features
                     (fetch-route-trips-by-name-and-date
                      db
                      {:service-id (Long/parseLong service-id)
                       :date (time/parse-date-iso-8601 date)
                       :route-short-name short-name
                       :route-long-name long-name
                       :trip-headsign headsign}))}
         {:key-fn name})))

  ^{:unauthenticated true :format :transit}
  (GET "/transit-visualization/:service-id/route-trips-for-date"
       {{service-id :service-id} :params
        {:strs [date short-name long-name headsign]} :query-params}
       (into []
             (map #(update % :stoptimes parse-gtfs-stoptimes))
             (fetch-route-trip-info-by-name-and-date
              db
              {:service-id (Long/parseLong service-id)
               :date (time/parse-date-iso-8601 date)
               :route-short-name short-name
               :route-long-name long-name
               :trip-headsign headsign})))

  ^{:unauthenticated true :format :transit}
  (GET "/transit-visualization/:service-id/route-differences"
       {{service-id :service-id} :params
        {:strs [date1 date2 short-name long-name headsign]} :query-params}
       (composite/parse @specql-registry/table-info-registry
                        {:type "gtfs-route-change-info"}
                        (fetch-route-differences
                         db
                         {:service-id (Long/parseLong service-id)
                          :date1 (time/parse-date-iso-8601 date1)
                          :date2 (time/parse-date-iso-8601 date2)
                          :route-short-name short-name
                          :route-long-name long-name
                          :trip-headsign headsign}))))
