(ns ote.gtfs.spec
  "Define clojure.spec for GTFS data. All GTFS keys are under :gtfs namespace."
  (:require [clojure.spec.alpha :as s]
            [ote.time :as time :refer [time?]]))

(s/def :gtfs/gtfs (s/keys :req [:gtfs/agency-txt
                                :gtfs/stops-txt
                                :gtfs/routes-txt
                                :gtfs/trips-txt
                                :gtfs/stop-times-txt
                                :gtfs/calendar-txt]
                          :opt [:gtfs/calendar-dates-txt
                                :gtfs/fare-attributes-txt
                                :gtfs/fare-rules-txt
                                :gtfs/shapes-txt
                                :gtfs/frequencies-txt
                                :gtfs/transfers-txt
                                :gtfs/feed-info-txt]))

(def name->keyword
  {"agency.txt" :gtfs/agency-txt
   "stops.txt" :gtfs/stops.txt
   "routes.txt" :gtfs/routes-txt
   "trips.txt" :gtfs/trips.txt
   "stop_times.txt" :gtfs/stop-times-txt
   "calendar.txt" :gtfs/calendar-txt
   "calendar_dates.txt" :gtfs/calendar-dates-txt})

;;;;;;;;;;;;;;;;;;;;;;;
;; Spec for agency.txt

(s/def :gtfs/agency-txt
  (s/coll-of :gtfs/agency))

(s/def :gtfs/agency
  (s/keys :req [:gtfs/agency-name
                :gtfs/agency-url
                :gtfs/agency-timezone]
          :opt [:gtfs/agency-id
                :gtfs/agency-lang
                :gtfs/agency-phone
                :gtfs/agency-fare-url
                :gtfs/agency-email]))

(def ^{:doc "Defines the order of the CSV fields in an agency.txt file"}
  agency-txt-fields
  [:gtfs/agency-id :gtfs/agency-name :gtfs/agency-url :gtfs/agency-timezone
   :gtfs/agency-lang :gtfs/agency-phone :gtfs/agency-fare-url :gtfs/agency-email])

  (def agency-txt-header "agency_id,agency_name,agency_url,agency_timezone,agency_lang,agency_phone,agency_fare_url,agency_email")

;; TODO: specs for individual agency fields

;;;;;;;;;;;;;;;;;;;;;;
;; Spec for stops.txt

(s/def :gtfs/stops-txt
  (s/coll-of :gtfs/stop))

(s/def :gtfs/stop
  (s/keys :req [:gtfs/stop-id
                :gtfs/stop-name
                :gtfs/stop-lat
                :gtfs/stop-lon]
          :opt [:gtfs/stop-code
                :gtfs/stop-desc
                :gtfs/zone-id
                :gtfs/stop-url
                :gtfs/location-type
                :gtfs/parent-station
                :gtfs/stop-timezone
                :gtfs/wheelchair-boarding]))

(def ^{:doc "Defines the order of the CSV fields in a stops.txt file"}
  stops-txt-fields
  [:gtfs/stop-id :gtfs/stop-code :gtfs/stop-name :gtfs/stop-desc
   :gtfs/stop-lat :gtfs/stop-lon :gtfs/zone-id :gtfs/stop-url :gtfs/location-type
   :gtfs/parent-station :gtfs/stop-timezone :gtfs/wheelchair-boarding])

(def stops-txt-header "stop_id,stop_code,stop_name,stop_desc,stop_lat,stop_lon,zone_id,stop_url,location_type,parent_station,stop_timezone,wheelchair_boarding")

(s/def :gtfs/wheelchair-boarding #{"0" "1" "2"})

;;;;;;;;;;;;;;;;;;;;;;;
;; Spec for routes.txt

(s/def :gtfs/routes-txt
  (s/coll-of :gtfs/route))

(s/def :gtfs/route
  (s/keys :req [:gtfs/route-id
                :gtfs/route-short-name
                :gtfs/route-long-name
                :gtfs/route-type]
          :opt [:gtfs/agency-id
                :gtfs/route-desc
                :gtfs/route-url
                :gtfs/route-color
                :gtfs/route-text-color
                :gtfs/route-sort-order]))

(def ^{:doc "Defines the order of the CSV fields in a routes.txt file"}
  routes-txt-fields
  [:gtfs/route-id :gtfs/agency-id :gtfs/route-short-name :gtfs/route-long-name
   :gtfs/route-desc :gtfs/route-type :gtfs/route-url :gtfs/route-color
   :gtfs/route-text-color :gtfs/route-sort-order])

(def routes-txt-header "route_id,agency_id,route_short_name,route_long_name,route_desc,route_type,route_url,route_color,route_text_color,route_sort_order")

;;;;;;;;;;;;;;;;;;;;;;
;; Spec for trips.txt

(s/def :gtfs/trips-txt
  (s/coll-of :gtfs/trip))

(s/def :gtfs/trip
  (s/keys :req [:gtfs/route-id
                :gtfs/service-id
                :gtfs/trip-id]
          :opt [:gtfs/trip-headsign
                :gtfs/trip-short-name
                :gtfs/direction-id
                :gtfs/block-id
                :gtfs/shape-id
                :gtfs/wheelchair-accessible
                :gtfs/bikes-allowed]))

(def ^{:doc "Defines the order of the CSV fields in a trips.txt file"}
  trips-txt-fields
  [:gtfs/route-id
   :gtfs/service-id
   :gtfs/trip-id
   :gtfs/trip-headsign
   :gtfs/trip-short-name
   :gtfs/direction-id
   :gtfs/block-id
   :gtfs/shape-id
   :gtfs/wheelchair-accessible
   :gtfs/bikes-allowed])

(def trips-txt-header "route_id,service_id,trip_id,trip_headsign,trip_short_name,direction_id,block_id,shape_id,wheelchair_accessible,bikes_allowed")

;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Spec for stop_times.txt

(s/def :gtfs/stop-times-txt
  (s/coll-of :gtfs/stop-time))

(s/def :gtfs/stop-time
  (s/keys :req  [:gtfs/trip-id
                 :gtfs/arrival-time
                 :gtfs/departure-time
                 :gtfs/stop-id
                 :gtfs/stop-sequence]
          :opt  [:gtfs/stop-headsign
                 :gtfs/pickup-type
                 :gtfs/drop-off-type
                 :gtfs/shape-dist-traveled
                 :gtfs/timepoint]))

(s/def :gtfs/arrival-time time?)
(s/def :gtfs/departure-time time?)

(def ^{:doc "Defines the order of the CSV fields in a stop_times.txt file"}
  stop-times-txt-fields
  [:gtfs/trip-id
   :gtfs/arrival-time
   :gtfs/departure-time
   :gtfs/stop-id
   :gtfs/stop-sequence
   :gtfs/stop-headsign
   :gtfs/pickup-type
   :gtfs/drop-off-type
   :gtfs/shape-dist-traveled
   :gtfs/timepoint])

(def stop-times-txt-header "trip_id,arrival_time,departure_time,stop_id,stop_sequence,stop_headsign,pickup_type,drop_off_type,shape_dist_traveled,timepoint")

;;;;;;;;;;;;;;;;;;;;;;;;;
;; Spec for calendar.txt

(s/def :gtfs/calendar-txt
  (s/coll-of :gtfs/calendar))

(s/def :gtfs/calendar
  (s/keys :req [:gtfs/service-id
                :gtfs/monday
                :gtfs/tuesday
                :gtfs/wednesday
                :gtfs/thursday
                :gtfs/friday
                :gtfs/saturday
                :gtfs/sunday
                :gtfs/start-date
                :gtfs/end-date]))

(def ^{:doc "Defines the order of the CSV fields in a calendar.txt file"}
  calendar-txt-fields
  [:gtfs/service-id
   :gtfs/monday
   :gtfs/tuesday
   :gtfs/wednesday
   :gtfs/thursday
   :gtfs/friday
   :gtfs/saturday
   :gtfs/sunday
   :gtfs/start-date
   :gtfs/end-date])

(def calendar-txt-header "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date")

(s/def :gtfs/monday boolean?)
(s/def :gtfs/tuesday boolean?)
(s/def :gtfs/wednesday boolean?)
(s/def :gtfs/thursday boolean?)
(s/def :gtfs/friday boolean?)
(s/def :gtfs/saturday boolean?)
(s/def :gtfs/sunday boolean?)

(defn date? [dt]
  (satisfies? time/DateFields dt))

(s/def :gtfs/start-date date?)
(s/def :gtfs/end-date date?)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Spec for calendar_dates.txt

(s/def :gtfs/calendar-dates-txt
  (s/coll-of :gtfs/calendar-date))

(s/def :gtfs/calendar-date
  (s/keys :req [:gtfs/service-id
                :gtfs/date
                :gtfs/exception-type]))

(s/def :gtfs/date date?)

(def calendar-dates-txt-fields
  [:gtfs/service-id :gtfs/date :gtfs/exception-type])

(def calendar-dates-txt-header "service_id,date,exception_type")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; FIXME: support optional files as well
