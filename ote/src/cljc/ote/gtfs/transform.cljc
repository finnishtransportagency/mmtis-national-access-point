(ns ote.gtfs.transform
  "Transform ote.db.transit datamodel maps to GTFS data"
  (:require [ote.db.transit :as transit]
            [ote.gtfs.spec :as gtfs-spec]
            [ote.gtfs.parse :as gtfs-parse]
            [ote.db.transport-operator :as t-operator]
            [ote.db.transport-service :as t-service]
            [ote.localization :as localization]
            [taoensso.timbre :as log]
            [ote.time :as time]))

(defn- agency-txt [{::t-operator/keys [id name homepage phone email] :as transport-operator}]
  [{:gtfs/agency-id id
    :gtfs/agency-name name
    :gtfs/agency-url homepage
    :gtfs/agency-timezone "Europe/Helsinki"
    :gtfs/agency-lang "FI"
    :gtfs/agency-phone phone
    :gtfs/agency-email email}])

(defn- stops-txt [stops]
  (for [[id {::transit/keys [name location stop-type]}] stops]
    {:gtfs/stop-id id
     :gtfs/stop-name (t-service/localized-text-with-fallback #?(:cljs @localization/selected-language
                                                      :clj localization/*language*) name)
     :gtfs/stop-lat (.-x (.getGeometry location))
     :gtfs/stop-lon (.-y (.getGeometry location))}))

(defn- routes-txt [transport-operator-id routes]
  (for [{::transit/keys [id name route-type]} routes]
    {:gtfs/route-id id
     :gtfs/route-short-name ""
     :gtfs/route-long-name name
     :gtfs/route-type (case route-type
                        :light-rail "0"
                        :subway "1"
                        :rail "2"
                        :bus "3"
                        :ferry "4"
                        :cable-car "5"
                        :gondola "6"
                        :funicular "7")
     :gtfs/agency-id transport-operator-id}))

(defn- index-key
  "Add a key to all items of collection based on index position."
  [key val-fn coll]
  (map-indexed
   (fn [i item]
     (assoc item key (val-fn i)))
   coll))

(defn- index-of [pred vec]
  (first (keep-indexed
          (fn [i item]
            (when (pred item)
              i))
          vec)))

(defn- services-with-removed-dates
  "Returns GTFS services with removed dates"
  [services dates]
  (reduce
   (fn [services date]
     (let [df (select-keys (time/date-fields date) #{::time/year ::time/month ::time/date})
           removed-date (time/date-fields->date df)]
       (mapv
        (fn [{rule-dates :rule-dates :as service}]
          (if (rule-dates df)
            (update service :removed-dates (fnil conj #{}) removed-date)
            service))
        services)))
   services dates))

(defn- services-with-added-dates
  "Returns GTFS services with added dates"
  [services dates]
  (reduce
   (fn [services date]
     (let [df (select-keys (time/date-fields date) #{::time/year ::time/month ::time/date})
           service-idx (or (index-of #(not ((:rule-dates %) df)) services) 0)]
       (update-in services [service-idx :added-dates] (fnil conj #{}) (time/date-fields->date df))))
   services dates))

(defn route-services
  "Generate GTFS services from service calendars. One service calendar can be
  expanded to multiple services."
  [{::transit/keys [service-calendars id]}]
  (for [{::transit/keys [service-rules service-added-dates service-removed-dates]
         service-calendar-id :service-calendar-id}
        (index-key :service-calendar-id str service-calendars)]
    ;; GTFS has 1 rule per service id, we need to create as many
    ;; services as there are rules, and distribute added/removed dates between them
    (-> (for [{::transit/keys [monday tuesday wednesday thursday
                               friday saturday sunday]
               :as service} (index-key :gtfs/service-id
                                       #(str id "_" service-calendar-id "_" %)
                                       service-rules)]
          {:rule-dates (into #{} (transit/rule-dates service))
           :gtfs/service-id (:gtfs/service-id service)
           :gtfs/monday (boolean monday)
           :gtfs/tuesday (boolean tuesday)
           :gtfs/wednesday (boolean wednesday)
           :gtfs/thursday (boolean thursday)
           :gtfs/friday (boolean friday)
           :gtfs/saturday (boolean saturday)
           :gtfs/sunday (boolean sunday)
           :gtfs/start-date (::transit/from-date service)
           :gtfs/end-date (::transit/to-date service)})
        vec
        (services-with-removed-dates service-removed-dates)
        (services-with-added-dates service-added-dates))))

(defn- trips-txt [routes]
  (mapcat
   (fn [{::transit/keys [id trips]
         services :services :as route}]
     (reduce concat
             (map-indexed
              (fn [i {::transit/keys [service-calendar-idx]}]
                (for [{service-id :gtfs/service-id} (nth services service-calendar-idx)]
                  {:gtfs/route-id id
                   :gtfs/trip-id (str id "_" i)
                   :gtfs/service-id service-id}))
              trips)))
   routes))

(defn- calendar-txt [routes]
  (mapcat
   (fn [{services :services}]
     (mapcat #(for [service %]
                (select-keys service gtfs-spec/calendar-txt-fields)) services))
   routes))

(defn- calendar-dates-txt [routes]
  (mapcat
   (fn [{services :services}]
     (mapcat
      (fn [services]
        (mapcat (fn [{service-id :gtfs/service-id
                      :keys [added-dates removed-dates]
                      :as s}]
                  (concat
                   (for [d added-dates]
                     {:gtfs/service-id service-id
                      :gtfs/date d
                      :gtfs/exception-type "1"})
                   (for [d removed-dates]
                     {:gtfs/service-id service-id
                      :gtfs/date d
                      :gtfs/exception-type "2"})))
                services))
      services))
   routes))

(defn- stopping-type
  "GTFS pickup/drop off type for a database stopping-type enum value."
  [t]
  (case t
    :regular "0"
    :not-available "1"
    :phone-agency "2"
    :coordinate-with-driver "3"
    ;; Defaults to regular
    "0"))

#?(:clj
   (defn- stop-times-txt [routes]
     (mapcat
       (fn [{::transit/keys [id trips stops]}]
         (reduce
           concat
           (map-indexed
             (fn [i {stop-times ::transit/stop-times :as trip}]
               (for [{::transit/keys [stop-idx arrival-time departure-time
                                      pickup-type drop-off-type]
                      idx            :idx} (index-key :idx identity stop-times)]
                 {:gtfs/trip-id        (str id "_" i)
                  :gtfs/stop-id        (::transit/code (nth stops stop-idx))
                  :gtfs/arrival-time   (time/time->pginterval (or arrival-time departure-time))
                  :gtfs/departure-time (time/time->pginterval (or departure-time arrival-time))
                  :gtfs/pickup-type    (stopping-type pickup-type)
                  :gtfs/drop-off-type  (stopping-type drop-off-type)
                  :gtfs/stop-sequence  idx}))
             trips)))
       routes)))

#?(:clj
   (defn routes-gtfs
     "Generate all supported GTFS files form given transport operator and route list"
     [transport-operator routes]
     (let [routes (mapv #(assoc % :services (route-services %)) routes)
           stops-by-code (into {}
                               (comp (mapcat ::transit/stops)
                                     (map (juxt ::transit/code identity)))
                               routes)]
       (try
         [{:name "agency.txt"
           :data (gtfs-parse/unparse-gtfs-file :gtfs/agency-txt (agency-txt transport-operator))}
          {:name "stops.txt"
           :data (gtfs-parse/unparse-gtfs-file :gtfs/stops-txt (stops-txt stops-by-code))}
          {:name "stop_times.txt"
           :data (gtfs-parse/unparse-gtfs-file :gtfs/stop-times-txt
                                               (stop-times-txt routes))}
          {:name "routes.txt"
           :data (gtfs-parse/unparse-gtfs-file
                   :gtfs/routes-txt
                   (routes-txt (::t-operator/id transport-operator) routes))}
          {:name "trips.txt"
           :data (gtfs-parse/unparse-gtfs-file
                   :gtfs/trips-txt
                   (trips-txt routes))}
          {:name "calendar.txt"
           :data (gtfs-parse/unparse-gtfs-file
                   :gtfs/calendar-txt
                   (calendar-txt routes))}

          {:name "calendar_dates.txt"
           :data (gtfs-parse/unparse-gtfs-file
                   :gtfs/calendar-dates-txt
                   (calendar-dates-txt routes))}]
         (catch #?(:cljs js/Object :clj Exception) e
           (.printStackTrace e)
           (log/warn "Error generating GTFS file content" e))))))
