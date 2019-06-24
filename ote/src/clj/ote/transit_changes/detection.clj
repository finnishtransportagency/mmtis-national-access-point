(ns ote.transit-changes.detection
  "Detect changes in transit traffic patterns.
  Interfaces with stored GTFS transit data."
  (:require [ote.transit-changes :as transit-changes :refer [week=]]
            [ote.time :as time]
            [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [specql.core :as specql]
            [clojure.spec.alpha :as spec]
            [specql.op :as op]
            [ote.db.user :as user]
            [ote.util.collections :refer [map-by count-matching]]
            [ote.tasks.util :as task-util]
            [ote.db.tx :as tx]
            [ote.transit-changes.change-history :as change-history]
            [ote.config.transit-changes-config :as config-tc])
  (:import (java.time LocalDate DayOfWeek)))

(def settings-tc (config-tc/config))

(defqueries "ote/transit_changes/detection.sql")
(defqueries "ote/services/transit_changes.sql")

(defn get-gtfs-packages [db service-id package-count]
  (let [id-map (map :gtfs/id (specql/fetch db :gtfs/package
                                           #{:gtfs/id}
                                           {:gtfs/transport-service-id service-id
                                            :gtfs/deleted? false}
                                           {:specql.core/order-by :gtfs/id
                                            :specql.core/order-direction :desc
                                            :specql.core/limit package-count}))
        id-vector (vec (sort < id-map))]
    id-vector))

(defn hash-recalculations
  "List currently running hash-recalculations"
  [db]
  (let [calculations (specql/fetch db :gtfs/hash-recalculation
                                   (specql/columns :gtfs/hash-recalculation)
                                   {:gtfs/completed op/null?})]
    (when (pos-int? (count calculations))
      {:calculations calculations})))

(defn reset-last-hash-recalculations
  "Reset currently running hash-recalculations. Should only be used if caluclation is stuck."
  [db]
  (let [last-calculation (first (specql/fetch db :gtfs/hash-recalculation
                                              (specql/columns :gtfs/hash-recalculation)
                                              {:gtfs/completed op/null?}
                                              {:specql.core/order-by :gtfs/recalculation-id
                                               :specql.core/order-direction :desc
                                               :specql.core/limit 1}))]
    (when last-calculation
      (specql/delete! db :gtfs/hash-recalculation {:gtfs/recalculation-id (:gtfs/recalculation-id last-calculation)}))))

(defn- start-hash-recalculation [db packets-total user]
  (let [id (specql/insert! db :gtfs/hash-recalculation
                           {:gtfs/started (java.sql.Timestamp. (System/currentTimeMillis))
                            :gtfs/packets-ready 0
                            :gtfs/packets-total packets-total
                            :gtfs/completed nil
                            :gtfs/created-by (:id user)})]
    id))

(defn- update-hash-recalculation [db packages-ready id]
  (specql/update! db :gtfs/hash-recalculation
                  {:gtfs/packets-ready packages-ready}
                  {:gtfs/recalculation-id id}))

(defn- stop-hash-recalculation [db id]
  (specql/update! db :gtfs/hash-recalculation
                  {:gtfs/completed (java.sql.Timestamp. (System/currentTimeMillis))}
                  {:gtfs/recalculation-id id}))

(defn calculate-package-hashes-for-service [db service-id package-count user]
  (let [package-ids (get-gtfs-packages db service-id package-count)
        ;; When given service has packages, mark calculation started
        recalculation-id (when package-ids
                           (:gtfs/recalculation-id (start-hash-recalculation db package-count user)))]
    (log/info "Found " (count package-ids) " For service " service-id)

    (dotimes [i (count package-ids)]
      (let [package-id (nth package-ids i)]
        (log/info "Generating hashes for package " package-id "  (service " service-id ")")
        (generate-date-hashes db {:package-id package-id})
        (update-hash-recalculation db (inc i) recalculation-id)
        (log/info "Generation ready! (package " package-id " service " service-id ")")))
    (stop-hash-recalculation db recalculation-id)))

(defn db-route-detection-type [db service-id]
  (let [type (first (specql/fetch db :gtfs/detection-service-route-type
                                  #{:gtfs/route-hash-id-type}
                                  {:gtfs/transport-service-id service-id}))]
    ;; If type is saved to database return it. If not, return default "short-long-headsign"
    (if type
      (:gtfs/route-hash-id-type type)
      "short-long-headsign")))

(defn calculate-route-hash-id-for-service
  "We support only few different hash calculation types. [short-long-headsign, short-long, route-id]"
  [db service-id package-count type]
  (let [package-ids (get-gtfs-packages db service-id package-count)]
    ;; Delete / insert type to db
    (specql/delete! db :gtfs/detection-service-route-type {:gtfs/transport-service-id service-id})
    (specql/insert! db :gtfs/detection-service-route-type
                    {:gtfs/transport-service-id service-id
                     :gtfs/route-hash-id-type type})
    (doall
      ;; Calculate route-hash-id:s again to detection-route table for given service.
      (for [package-id package-ids]
        (cond
          (= type "short-long-headsign")
          (calculate-routes-route-hashes-using-headsign db {:package-id package-id})
          (= type "short-long")
          (calculate-routes-route-hashes-using-short-and-long db {:package-id package-id})
          (= type "route-id")
          (calculate-routes-route-hashes-using-route-id db {:package-id package-id})
          (= type "long-headsign")
          (calculate-routes-route-hashes-using-long-headsign db {:package-id package-id})
          (= type "long")
          (calculate-routes-route-hashes-using-long db {:package-id package-id})
          :else
          (calculate-routes-route-hashes-using-headsign db {:package-id package-id}))))))

(defn routes-by-date [date-route-hashes all-routes]
  ;; date-route-hashes contains all hashes for date range and is sorted
  ;; by date so we can partition by :date to get each date's hashes
  (for [hashes-for-date (partition-by :date date-route-hashes)
        :let [date (:date (first hashes-for-date))
              route-hashes (into (zipmap all-routes (repeat nil))
                                 (map (juxt :route-hash-id :hash))
                                 hashes-for-date)
              cleaned-route-hashes (dissoc route-hashes
                                           ;; If there is no traffic for the service on a given date, the
                                           ;; result set will contain a single row with nil values for route.
                                           ;; Remove the empty-route-key so we don't get an extra route.
                                           "" nil)]]
    {:date (.toLocalDate date)
     :routes cleaned-route-hashes}))

(defn merge-week-hash
  "Merges multiple maps containing route day hashes.
  Returns a single map with each routes hashes in a vector."
  [route-day-hashes]
  (apply merge-with (fn [v1 v2]
                      (if (vector? v1)
                        (conj v1 v2)
                        [v1 v2]))
         route-day-hashes))

(defn combine-weeks
  "Combine list of date based hashes into weeks."
  [routes-by-date]
  (for [days (partition 7 routes-by-date)
        :let [bow (:date (first days))
              eow (:date (last days))]]
    {:beginning-of-week bow
     :end-of-week eow
     :routes (merge-week-hash (map :routes days))}))

(defn- vnot [cond msg]
  #_(when cond (println "debug: not a change because" msg))
  (not cond))

(defn week-hash-key-ix
  "Input: weekhash = sequence of string hashes
    key-to-find = keyword to find from weekhashes
  Output: Returns the index of first occurrence of `key-to-find`. Monday = 0, Sunday = 6, nil = not found"
  [weekhash key-to-find]
  (key-to-find (zipmap weekhash (range 8))))

(defn add-current-week-hash [to-key if-key state week]
  (if (and (nil? (get state to-key))
           (some? (get state if-key)))
    (assoc state to-key (dissoc week :routes))
    state))

(def add-starting-week
  (partial add-current-week-hash :starting-week :starting-week-hash))

(def add-different-week
  (partial add-current-week-hash :different-week :different-week-hash))

(defn detect-change-for-route
  "Reduces [prev curr next1 next2] weeks into a detection state change
  Returns a map with :change-type and details, or a temporary state map for next iteration to continue"
  [{:keys [starting-week-hash] :as state} [prev curr next1 next2] route week-map-current]
  (cond
    ;; If this is the first call and the current week is "anomalous".
    ;; Then start at the next week.
    (and (nil? starting-week-hash)
         (not (week= curr next1))
         (week= prev next1))
    {}                                                      ;; Ignore this week

    ;; No starting week specified yet, use current week
    (nil? starting-week-hash)
    (-> state
        (assoc :starting-week-hash curr)
        (add-starting-week week-map-current))

    ;; If current week does not equal starting week...
    (and (vnot (week= starting-week-hash curr) (str "curr = start (1) sw:" starting-week-hash " curr:" curr))
         (vnot (week= starting-week-hash next1) "curr = next1 (2)")
         ;; ...and traffic does not revert back to previous in two weeks
         (vnot (week= starting-week-hash next2) "curr = next2 (3)"))
    ;; this is a change
    (-> state
        (add-starting-week week-map-current)
        (assoc :different-week-hash curr
               ;; Change-date set later by different week's day comparison. Here only week start date is known.
               :change-type :changed
               :route-key route)
        (add-different-week week-map-current))

    ;; No change found
    :default
    state))

(defn- route-next-different-week
  "Returns a map describing a change week via :change-type or a temporary state map without :change-type."
  [{diff :different-week no-traffic-end-date :no-traffic-end-date :as state} route week-maps week-map-current]
  (if (or diff no-traffic-end-date)
    state                                                   ;; change already found, don't try again
    (detect-change-for-route state
                             (mapv (comp #(get % route) :routes) week-maps) ;; Pick week hashes from window's week maps
                             route week-map-current)))

(spec/def
  ::routes
  (spec/map-of
    string?
    (spec/coll-of (spec/or :keyword keyword? :string (spec/nilable string?)))))
(spec/def ::local-date #(instance? java.time.LocalDate %))
(spec/def ::end-of-week ::local-date)
(spec/def ::beginning-of-week ::local-date)

(spec/def
  ::route-week
  (spec/keys :req-un [::beginning-of-week ::end-of-week ::routes]))
(spec/def
  ::route-weeks-vec
  (spec/coll-of ::route-week))

(spec/def ::bow-eow-map (spec/keys :req-un [::beginning-of-week ::end-of-week]))
(spec/def
  ::different-week
  ::bow-eow-map)
(spec/def
  ::week-hash-vec
  (spec/coll-of (spec/or :keyword keyword? :string string? :nil nil?)))
(spec/def
  ::different-week-hash
  ::week-hash-vec)
(spec/def
  ::starting-week
  ::bow-eow-map)
(spec/def ::starting-week-hash ::week-hash-vec)

(spec/def ::route-change-map
  (spec/keys
    :req-un
    [::different-week
     ::different-week-hash
     ::starting-week
     ::starting-week-hash]))

(spec/def ::route-key (spec/every string? :count 3))

(spec/def
  ::single-route-change
  (spec/coll-of (spec/tuple ::route-key ::route-change-map) :kind map?))

(spec/fdef route-weeks-with-first-difference
           :args (spec/cat :rw ::route-weeks-vec)
           :ret ::single-route-change)

(spec/def ::route-key string?)

(spec/def ::change-type keyword?)

(spec/def ::service-route-change-map
  (spec/keys
    :req-un
    [::route-key
     ::change-type]
    :opt-un
    [::changes
     ::different-week
     ::different-week-hash
     ::starting-week
     ::starting-week-hash]))

(spec/def
  ::detected-route-changes-for-services-coll
  (spec/coll-of ::service-route-change-map :kind vector?))

(defn route-weeks-with-first-difference
  "Detect the next different week in each route.
  NOTE! starting from the second week in the given route-weeks, the first given week is considered the \"prev\" week.
  Takes a list of weeks that have week hashes for each route.
  Returns map from route [short long headsign] to next different week info.
  The route-weeks maps have keys :beginning-of-week, :end-of-week and :routes, under :routes there is a map with route-name -> 7-vector with day hashes of the week"
  [route-weeks]
  ;; Take routes from the first week (they are the same in all weeks)
  (let [route-name (first (first (:routes (first route-weeks))))
        result (reduce
                 (fn [route-detection-state [_ week-map-current _ _ :as week-maps]]
                   (update route-detection-state route-name
                           route-next-different-week route-name week-maps week-map-current))
                 {}                                         ; initial route detection state is empty
                 (partition 4 1 route-weeks))]
    ;; (spec-provider.provider/pprint-specs (spec-provider.provider/infer-specs result ::route-differences-pair) 'ote.transit-changes.detection 'spec)
    (vals result)))


(defn local-date-before? [d1 d2]
  (.isBefore d1 d2))

(defn local-date-after? [d1 d2]
  (clj-time.core/after? (time/native->date-time d1) (time/native->date-time d2)))

(defn route-starting-week-past-date? [rw date]
  (assert (some? date))
  (assert (some? rw))
  (assert (some? (:beginning-of-week rw)) rw)
  (local-date-after? (:beginning-of-week rw) date))

(defn route-starting-week-not-before?
  "rw: route week, date: localdate
  Returns true if `rw`s key is before `date`."
  [rw date]
  (assert (some? date))
  (assert (some? rw))
  (assert (some? (:beginning-of-week rw)) rw)
  (not (local-date-before? (:beginning-of-week rw) date)))

(defn- replace-starting-nt-first
  "Input: hashes = Sequence of values
  Output: Replaces first value if it marks start of no-traffic period."
  [hashes]
  (if (not-empty hashes)
    (update-in hashes [0]
               (fn [hash]
                 (if (= :nt-first hash)
                   :nt
                   hash)))
    hashes))

(defn- replace-terminating-nt-last
  "Input: hashes = Sequence of values
  Output: Replaces last value if it marks end of no-traffic period."
  [hashes]
  (if (not-empty hashes)
    (update-in hashes [(dec (count hashes))]
               (fn [hash]
                 (if (= :nt-last hash)
                   :nt
                   hash)))
    hashes))

(defn- hashes-flat->route-wksv
  "Input: route-hashes = Sequence of values
    route-weeks = sequence of maps each describing traffic for one week of one route
  Output: route-weeks where week traffic hashes are replaced using values from route-hashes in running order"
  [route-hashes route-weeks]
  (mapv (fn [route-week hash-week]
          (assoc route-week :routes {(first (keys (:routes route-week)))
                                     (vec hash-week)}))
        route-weeks
        (partition 7 route-hashes)))

(defn- hash-without-traffic? [hash]
  (or (nil? hash)                                           ;; Business day without traffic
      (= :holiday-nt hash)))                                ;; Holiday without traffic

(defn- route-hashes->keyed-notraffic-hashesv
  "Replaces day hashes using a keyword, if the consecutive length of a nil traffic period exceeds a configured
  threshold alue.
  Input: route-hashes = Sequence of values
  Output: Sequence of values from `route-hashes`,
  where day values of a no-traffic qualified period are replaced using a keyword."
  [route-hashes]
  (vec
    (mapcat
      (fn [group]
        ;; If a grouped sequence does not have any string values, consider it a group without traffic.
        ;; Evalue if trafficless groups meet reporting criteria and if so, replace values using a specific keyword.
        ;; Replace also holiday keywords for the sake of uniformity.
        (if (and (some hash-without-traffic? group)
                 (> (count group) (:detection-threshold-no-traffic-days settings-tc)))
          (seq
            (concat
              [:nt-first]                                   ;; First and last marked to allow detecting start/end dates
              (vec
                ;; repeat handles negative amounts and those not likely because of :detection-threshold-no-traffic-days
                (repeat (- (count group) 2) :nt))
              [:nt-last]))
          group))
      (partition-by                                         ;; Group hashes of no traffic to own groups to count lengths
        hash-without-traffic?
        route-hashes))))

(defn- route-wks->hashes-flat
  "Input: route-weeks = Sequence of maps, each describing traffic for one week of one route.
  Output: Result where day hash strings from `route-weeks` are combined as one flat sequence."
  [route-weeks]
  (vec
    (reduce (fn [result {:keys [routes] :as route-week}]
              (concat result (first (vals routes))))
            []
            route-weeks)))

(defn- route-wks->keyed-notraffic-wksv
  "Input: route-weeks = Sequence of maps, each describing traffic for one week of one route.
  Output: Sequence where day hashes belonging to a 'no-traffic' period are replaced using a keyword."
  [route-weeks]
  (-> route-weeks
      route-wks->hashes-flat
      route-hashes->keyed-notraffic-hashesv
      replace-terminating-nt-last                           ;; Don't report :no-traffic-end-date if traffic doesn't continue
      replace-starting-nt-first                              ;; Don't report no-traffic which is already ongoing
      (hashes-flat->route-wksv route-weeks)))

(defn- append-no-traffic-start-map
  "Input: route-week = a map describing traffic for one week of one route
     no-traffic-start-position = index of first day of no-traffic period, or nil.
   Output: Appends a map with :no-traffic-start-date to `change-maps` if no-traffic-start-position is not nil."
  [change-maps {:keys [beginning-of-week routes] :as route-week} no-traffic-start-position]
  (if (and (number? no-traffic-start-position)
           change-maps
           route-week)
    (concat change-maps
            ;; No :starting-week added because no-traffic week is not compared to any week.
            [{:route-key (first (keys routes))
              :change-type :no-traffic
              :change-date (.plusDays beginning-of-week no-traffic-start-position)
              :no-traffic-start-date (.plusDays beginning-of-week no-traffic-start-position)}])
    change-maps))

(defn- append-no-traffic-end-key
  "Input: route-week = a map describing traffic for one week of one route
     no-traffic-end-position = index of last day of no-traffic period, or nil.
   Output: `change-maps`, where last object has :no-traffic-end-date appended if `no-traffic-end-position` exists."
  [change-maps {:keys [beginning-of-week] :as route-week} no-traffic-end-position]
  (if (and (number? no-traffic-end-position)
           change-maps
           route-week
           (= (:change-type (last change-maps)) :no-traffic))
    ;; Replace last item with an item with :no-traffic-end-date
    (concat
      (pop (vec change-maps))
      [(assoc (last change-maps)
         :no-traffic-end-date (.plusDays beginning-of-week no-traffic-end-position))])
    change-maps))

(defn- create-changes-no-traffic
  "Input: change-maps = sequence where results shall be appended
    prev-wk =  a map describing traffic for one week of one route that is _previous_ to the analysed week.
    route-week = a map describing traffic for the week to be analysed of one route.
  Output: change-maps sequence where objects representing no-traffic periods are appended."
  [change-maps [{routes-prev-wk :routes :as prev-wk} :as route-weeks]]
  (reduce
    (fn [change-maps {:keys [routes] :as route-week}]
      (let [wk-hash (first (vals routes))
            no-traffic-start-position (week-hash-key-ix wk-hash :nt-first)
            no-traffic-end-position (when-let [ix (week-hash-key-ix wk-hash :nt-last)]
                                      (inc ix))]            ;; inc because no-traffic end is to be reported when traffic continues.
        ;;  Run first "end" and then "start" creation in case old no-traffic ends and new one starts on same week.
        (-> change-maps
            (append-no-traffic-end-key route-week no-traffic-end-position)
            (append-no-traffic-start-map route-week no-traffic-start-position))))
    change-maps
    (filterv
      (fn [{:keys [routes]}]
        (let [wk-hash (first (vals routes))]
          (or (week-hash-key-ix wk-hash :nt-first)
              (week-hash-key-ix wk-hash :nt-last))))
      route-weeks)))

(defn change-maps-compare
  "Compares maps m1 and m2 two key values where based on which exists.
  [:different-week :beginning-of-week]` has higher preference."
  [m1 m2]
  (let [val1 (or (get-in m1 [:different-week :beginning-of-week]) (:change-date m1))
        val2 (or (get-in m2 [:different-week :beginning-of-week]) (:change-date m2))]
    (compare val1 val2)))

(defn- create-change-route-added
  "Input: route-key = route hash id
    analysis-date = date of analysis run
    all-routes = sequence of maps describing :min-date per route
    change-maps = sequence where new change maps shall be appended
  Output: sequence of change-maps where :added change-map is prepended for route, if necessary."
  [change-maps route-key ^LocalDate analysis-date all-routes]
  (let [route-min-date (some (fn [[_ route-info]]
                               (when (= route-key (:route-hash-id route-info))
                                 (:min-date route-info)))
                             all-routes)]
    ;; Report new route if min-date is today or later
    (if (and route-min-date analysis-date
             (not (.isBefore (.toLocalDate route-min-date) analysis-date))) ;; Report also routes which start on analysis date
      (concat [{:route-key route-key
                :change-date (.toLocalDate route-min-date)
                :change-type :added}]
              change-maps)
      change-maps)))

(defn- route-ends?
  "Input: date = analysis date,
    max-date = last day with traffic for route,
    traffic-threshold-d = Number of days from analysis date for which route should have traffic
  Output: true if `max-date` is below date plus `traffic-threshold-d`"
  [^LocalDate date max-date ^Integer traffic-threshold-d]
  (and max-date
       (.isBefore (.toLocalDate max-date) (.plusDays date traffic-threshold-d))
       (.isAfter (.toLocalDate max-date) (.minusDays date 1)))) ; minus 1 day so we are sure the current day is still calculated

(defn remove-no-traffic-append-route-removed-change
  [change-maps route-key analysis-date all-routes]
  (let [route-max-date (fn [route-hash-id all-routes]
                         (:max-date (some
                                      #(when (= route-hash-id (:route-hash-id (second %))) (second %))
                                      all-routes)))
        create-change-map-removed (fn [route-key last-chg max-date ^LocalDate date]
                                    (when (route-ends? date max-date (:detection-threshold-route-end-days settings-tc))
                                      {:change-type :removed
                                       :change-date (if (and (= (:change-type last-chg) :no-traffic)
                                                             (nil? (:no-traffic-end-date last-chg)))
                                                      ;; If last change starts a no-traffic earlier than route max-date, use start of no-traffic.
                                                      ;; +1 NOT added because :no-traffic-start-date defines the first no-traffic day, i.e. traffic end
                                                      (:change-date last-chg)
                                                      ;; +1 because max-date defines the LAST day with traffic, hence no-traffic starts on the next day
                                                      (.plusDays (.toLocalDate max-date) 1))
                                       :route-key route-key}))
        no-traffic-ongoing? #(and (= (:change-type %) :no-traffic)
                                  (nil? (:no-traffic-end-date %)))
        change-map-removed (create-change-map-removed route-key
                                                      (last change-maps)
                                                      (route-max-date route-key all-routes)
                                                      analysis-date)]

    (if change-map-removed
      ;; Remove last no-traffic ongoing and set route ending from first day of no traffic
      ;; because for now route ending shall be reported from first day without traffic
      (concat
        (if (no-traffic-ongoing? (last change-maps))
          (pop (vec change-maps))
          change-maps)
        [change-map-removed])
      change-maps)))

(defn- route-differences
  " Takes a vector of weeks for ONE ROUTE and outputs vector of weeks where change or no traffic starts
  (Or if neither is found, returns the starting week of analysis)
  Input: [{:beginning-of-week #object[java.time.LocalDate 0x3f51d3c0 \"2019-02-11\"],
          :end-of-week #object[java.time.LocalDate 0x30b5f64f \"2019-02-17\"],
          :routes {\"routename\" [\"h1\" \"h2\" \"h3\" \"h4\" \"h5\" \"h6\" \"h7\"]}}
          {...}]
  Output: [{:different-week
            {:beginning-of-week [\"2019-02-25\"]
            :end-of-week #object[java.time.LocalDate 0x5a900751 \"2019-03-03\"]}
           :route-key \"routename\"
           :different-week-hash [\"h1\" \"!!\" \"h3\" \"h4\" \"h5\" \"h6\" \"h7\"]\n
           :starting-week {:beginning-of-week #object[java.time.LocalDate   \"2019-02-11\"]
                            :end-of-week #object[java.time.LocalDate \"2019-02-17\"]}
           :starting-week-hash [\"h1\" \"h2\" \"h3\" \"h4\" \"h5\" \"h6\" \"h7\"]}]
           {...}"
  [route-weeks all-routes ^LocalDate analysis-date]
  ;; First pre-process input data and mark "no-traffic" periods
  (let [route-weeks-nt-keyed (route-wks->keyed-notraffic-wksv route-weeks)
        route-key (first (keys (:routes (first route-weeks))))]

    ;; Iterate all traffic weeks of one route and create traffic change maps
    (loop [route-weeks route-weeks-nt-keyed
           ;; Create route added, removed and no-traffic change maps before first loop iteration
           change-maps (-> []
                           (create-change-route-added route-key analysis-date all-routes)
                           (create-changes-no-traffic route-weeks-nt-keyed)
                           (remove-no-traffic-append-route-removed-change route-key analysis-date all-routes))]

      ;; Create change-map for next traffic change
      (let [temp-change-map (first (route-weeks-with-first-difference route-weeks))
            change-map (when (contains? temp-change-map :different-week)
                         temp-change-map)
            ;; Filter from previous week date because route-weeks-with-first-difference starts from
            ;; the second given week (curr): [prev curr next1 next2]
            week-filter-date (when-let [change-week-date (get-in change-map [:different-week :beginning-of-week])]
                               (.minusWeeks change-week-date 1))
            ;; Compose result for this round in one place to avoid forgetting something in different loop exit conditions
            results-iteration (if change-map
                                (concat change-maps [change-map])
                                change-maps)
            ;; Filter out different weeks before current week, because different week is starting week for next change.
            weeks-remaining (when week-filter-date
                              (filter #(route-starting-week-not-before? % week-filter-date)
                                      route-weeks))]
        (if (not-empty weeks-remaining)
          (recur
            weeks-remaining
            results-iteration)
          (if (empty? results-iteration)
            [{:change-type :no-change
              :route-key route-key}]
            (sort change-maps-compare results-iteration)))))))

(defn route-trips-for-date [db service-id route-hash-id date]
  (vec
    (for [trip-stops (partition-by (juxt :package-id :trip-id)
                                   (fetch-route-trips-for-date db {:service-id service-id
                                                                   :route-hash-id route-hash-id
                                                                   :date date}))
          :let [package-id (:package-id (first trip-stops))
                trip-id (:trip-id (first trip-stops))]]
      {:gtfs/package-id package-id
       :gtfs/trip-id trip-id
       :stoptimes (mapv (fn [{:keys [stop-id stop-name departure-time stop-sequence stop-lat stop-lon stop-fuzzy-lat stop-fuzzy-lon]}]
                          {:gtfs/stop-id stop-id
                           :gtfs/stop-name stop-name
                           :gtfs/stop-lat stop-lat
                           :gtfs/stop-lon stop-lon
                           :gtfs/stop-fuzzy-lat stop-fuzzy-lat
                           :gtfs/stop-fuzzy-lon stop-fuzzy-lon
                           :gtfs/stop-sequence stop-sequence
                           :gtfs/departure-time (time/pginterval->interval departure-time)})
                        trip-stops)})))

(defn compare-selected-trips [date1-trips date2-trips starting-week-date different-week-date]
  (let [combined-trips (transit-changes/combine-trips date1-trips date2-trips)
        {:keys [added removed changed]}
        (group-by (fn [[l r]]
                    (cond
                      (nil? l) :added
                      (nil? r) :removed
                      :default :changed))
                  combined-trips)
        ;; When dealing with new routes there aren't traffic at date1-trips because traffic is starting
        ;; So calculate only new trips, no other changes or stops
        added-trip-count (if (and (nil? combined-trips) (pos-int? (count date2-trips)))
                           (count date2-trips)
                           0)
        ;; When traffic is ending there isn't traffic at date2-trips vector. So calculate only ending trips.
        removed-trip-count (if (and (nil? combined-trips) (pos-int? (count date1-trips)))
                             (count date1-trips)
                             0)]
    {:starting-week-date starting-week-date
     :different-week-date different-week-date
     :added-trips (if combined-trips (count added) added-trip-count)
     :removed-trips (if combined-trips (count removed) removed-trip-count)
     :trip-changes (map (fn [[l r]]
                          (transit-changes/trip-stop-differences l r))
                        changed)}))

(defn compare-route-days [db service-id route-hash-id
                          {:keys [starting-week starting-week-hash
                                  different-week different-week-hash] :as r}]
  (let [first-different-day (transit-changes/first-different-day starting-week-hash
                                                                 different-week-hash)
        starting-week-date (.plusDays (:beginning-of-week starting-week) first-different-day)
        different-week-date (.plusDays (:beginning-of-week different-week) first-different-day)
        date1-trips (route-trips-for-date db service-id route-hash-id starting-week-date)
        date2-trips (route-trips-for-date db service-id route-hash-id different-week-date)]
    (compare-selected-trips date1-trips date2-trips starting-week-date different-week-date)))

(defn compare-route-days-all-changes-for-week [db service-id route-hash-id
                                               {:keys [starting-week starting-week-hash
                                                       different-week different-week-hash] :as r}]
  (let [changed-days (transit-changes/changed-days-of-week starting-week-hash different-week-hash)]
    (for [ix changed-days
          :let [starting-week-date (.plusDays (:beginning-of-week starting-week) ix)
                different-week-date (.plusDays (:beginning-of-week different-week) ix)
                date1-trips (route-trips-for-date db service-id route-hash-id starting-week-date)
                date2-trips (route-trips-for-date db service-id route-hash-id different-week-date)]
          :when (number? ix)]
      (compare-selected-trips date1-trips date2-trips starting-week-date different-week-date))))

(defn route-day-changes
  "Takes in routes with possible different weeks and adds day change comparison."
  [db service-id routes]
  (let [route-day-changes
        (into {}
              (map (fn [[route {diff :different-week :as detection-result}]]
                     (if diff                               ;; If a different week was found, do detailed trip analysis
                       [route (assoc detection-result
                                :changes (compare-route-days db service-id route detection-result))]
                       [route detection-result])))
              routes)]
    route-day-changes))

(defn- expand-day-changes
  "Input: coll of maps each describing a week with traffic changes.
  Takes :changes coll from each map, removes it, and creates a new map to contain each of the elements in :changes coll.
  Output: returns a coll of maps, each describing one single changed day on a week.
  There may be multiple maps per one week if there are multiple changed days on the week."
  [detection-results]
  (reduce
    (fn [result detection]
      (if-let [changes (:changes detection)]
        (vec (concat result (for [chg changes]
                              (assoc detection :changes chg))))
        (conj result detection)))
    []
    detection-results))

(defn route-day-changes
  "Takes a collection of routes and adds day change comparison details for those weeks which have :different-week"
  [db service-id routes]
  (let [route-day-changes
        (mapv (fn [{diff :different-week route-key :route-key :as detection-result}]
                (if diff                                    ;; If a different week was found, do detailed trip analysis
                  (assoc detection-result
                    :changes (compare-route-days-all-changes-for-week db service-id route-key detection-result))
                  detection-result))
              routes)]
    (vec (expand-day-changes route-day-changes))))

(defn- date-in-the-past? [^LocalDate date]
  (and date
       (.isBefore date (java.time.LocalDate/now))))

(defn- min-date-in-the-future? [{min-date :min-date}]
  (and min-date
       (.isAfter (.toLocalDate min-date) (java.time.LocalDate/now))))

(defn update-min-max-range [range val]
  (-> range
      (update :lower #(if (or (nil? %) (< val %)) val %))
      (update :upper #(if (or (nil? %) (> val %)) val %))))

(defn- week-day-in-week [date week-day]
  (.plusDays date (- (.getValue week-day)
                     (.getValue (.getDayOfWeek date)))))

(defn discard-past-changes
  "Discard past changes by returning a :no-change"
  [{type :gtfs/change-type change-date :gtfs/change-date :as change}]
  (if (and
        change-date
        (.isBefore (.toLocalDate change-date) (java.time.LocalDate/now))
        (not= :removed type))
    {:gtfs/change-type :no-change
     :gtfs/change-date nil}
    change))

(defn- route-change-type [max-date-in-past? added? removed-date changed? no-traffic? starting-week-date different-week-date
                          no-traffic-start-date no-traffic-end-date route]
  ;; Change type and type specific dates
  (discard-past-changes
    (cond

      max-date-in-past?                                     ; Done because some of the changes listed in transit changes pages are actually in the past
      {:gtfs/change-type :no-change}

      added?
      {:gtfs/change-type :added

       ;; For an added route, the change-date is the date when traffic starts
       :gtfs/different-week-date (:min-date route)
       :gtfs/change-date (:min-date route)
       :gtfs/current-week-date (time/sql-date (.plusDays (.toLocalDate (:min-date route)) -1))}

      removed-date
      {:gtfs/change-type :removed
       ;; For a removed route, the change-date is the day after traffic stops
       ;; BUT: If removed? is identified and route ends before current date, set change date as nil so we won't analyze this anymore.
       :gtfs/change-date (if (.isBefore removed-date (java.time.LocalDate/now))
                           nil
                           (time/sql-date removed-date))

       :gtfs/different-week-date (time/sql-date (.plusDays (.toLocalDate (:max-date route)) 1))
       :gtfs/current-week-date (:max-date route)}

      changed?
      {:gtfs/change-type :changed
       :gtfs/current-week-date (time/sql-date starting-week-date)
       :gtfs/different-week-date (time/sql-date different-week-date)
       :gtfs/change-date (time/sql-date different-week-date)}

      no-traffic?
      {:gtfs/change-type :no-traffic
       :gtfs/current-week-date (time/sql-date
                                 (.plusDays no-traffic-start-date -1))
       :gtfs/different-week-date (time/sql-date no-traffic-start-date)

       :gtfs/change-date (time/sql-date no-traffic-start-date)}

      :default
      {:gtfs/change-type :no-change})))

(spec/fdef transform-route-change
           :args (spec/cat :all-routes vector? :route-change ::service-route-change-map :route-changes-all ::detected-route-changes-for-services-coll))
(defn transform-route-change
  "Transform a detected route change into a database 'gtfs-route-change-info' type."
  [all-routes
   {:keys [route-key change-date change-type changes] :as route-change} route-changes-all]
  (spec/assert ::detected-route-changes-for-services-coll route-changes-all)
  (let [route-info (first (filter #(= route-key (:route-hash-id %))
                                  (map second all-routes)))
        {:keys [added-trips different-week-date removed-trips
                starting-week-date trip-changes]} changes
        trip-stop-seq-changes (reduce update-min-max-range
                                      {}
                                      (map :stop-seq-changes trip-changes))
        trip-stop-time-changes (reduce update-min-max-range
                                       {}
                                       (map :stop-time-changes trip-changes))
        route-change-for-db (merge
                              ;; :current-week-date = baseline day i.e. "before change"
                              ;; :different-week-date = when change takes place
                              ;; :change-date = when to run transit detection again?

                              {;; Route identification
                               :gtfs/route-short-name (:route-short-name route-info)
                               :gtfs/route-long-name (:route-long-name route-info)
                               :gtfs/trip-headsign (:trip-headsign route-info)
                               :gtfs/route-hash-id (:route-hash-id route-info)
                               ;; Trip change counts
                               :gtfs/added-trips added-trips
                               :gtfs/removed-trips removed-trips
                               :gtfs/trip-stop-sequence-changes-lower (:lower trip-stop-seq-changes)
                               :gtfs/trip-stop-sequence-changes-upper (:upper trip-stop-seq-changes)
                               :gtfs/trip-stop-time-changes-lower (:lower trip-stop-time-changes)
                               :gtfs/trip-stop-time-changes-upper (:upper trip-stop-time-changes)
                               ;; History table change-key shall be updated a bit later
                               :gtfs/change-key nil}

                              (cond                         ;; This used to be filtered via discard-past-changes
                                (= change-type :no-change)
                                {:gtfs/change-type :no-change}

                                (= change-type :added)
                                {:gtfs/change-type :added
                                 :gtfs/different-week-date (time/sql-date change-date)
                                 :gtfs/change-date (time/sql-date change-date)
                                 :gtfs/current-week-date (time/sql-date (.plusDays change-date -1))}

                                (= change-type :removed)
                                {:gtfs/change-type :removed
                                 ;; For a removed route, the change-date is the day after traffic stops
                                 ;; BUT: If removed? is identified and route ends before current date, set change date as nil so we won't analyze this anymore.
                                 :gtfs/change-date (if (.isBefore change-date (java.time.LocalDate/now))
                                                     nil
                                                     (time/sql-date change-date))
                                 :gtfs/different-week-date (time/sql-date change-date)
                                 :gtfs/current-week-date (time/sql-date change-date)}

                                (= change-type :changed)
                                {:gtfs/change-type :changed
                                 :gtfs/current-week-date (time/sql-date starting-week-date)
                                 :gtfs/different-week-date (time/sql-date different-week-date)
                                 :gtfs/change-date (time/sql-date different-week-date)}

                                (= change-type :no-traffic)
                                {:gtfs/change-type :no-traffic
                                 :gtfs/current-week-date (time/sql-date (.plusDays change-date -1))
                                 :gtfs/different-week-date (time/sql-date change-date)
                                 :gtfs/change-date (time/sql-date change-date)}

                                :default
                                (do
                                  (log/warn "transform-route-change: default, Should not reach here! Shall mark this a :no-change")
                                  (clojure.spec.alpha/assert (constantly false) "Should not reach here!")
                                  {:gtfs/change-type :no-change})))]

    (change-history/append-change-key route-change-for-db)))

; Development-time utility
;(defn- debug-print-change-stats [all-routes route-changes type]
;  (doseq [r all-routes
;          :let [key (:route-hash-id r)
;                {:keys [changes no-traffic-start-date no-traffic-end-date]
;                 :as route} (route-changes key)]]
;    #_(println key " has traffic " (:min-date r) " - " (:max-date r)
;               (when no-traffic-end-date
;                 (str " no traffic between: " no-traffic-start-date " - " no-traffic-end-date))
;               (when changes
;                 (str " has changes")))))

(defn- update-route-changes! [db analysis-date service-id route-change-infos]
  {:pre [(some? analysis-date)
         (pos-int? service-id)]}
  ;; Previous detected route change rows deleted because design is, there can be one analysis run per day per service
  ;; and count or details of route change rows per analysis round might differ.
  (specql/delete! db :gtfs/detected-route-change
                  {:gtfs/transit-change-date analysis-date
                   :gtfs/transit-service-id service-id})
  (doseq [r route-change-infos]
    (when r
      (specql/insert! db :gtfs/detected-route-change
                      (merge {:gtfs/transit-change-date analysis-date
                              :gtfs/transit-service-id service-id
                              :gtfs/created-date (java.util.Date.)}
                             r)))))

(defn update-transit-changes! [db analysis-date service-id package-ids {:keys [all-routes route-changes]}]
  {:pre [(some? analysis-date)
         (or (zero? service-id)
             (pos? service-id))]}
  (tx/with-transaction
    db
    (let [route-change-infos (map (fn [detection-result]
                                    (transform-route-change all-routes detection-result route-changes))
                                  route-changes)
          change-infos-group (group-by :gtfs/change-type route-change-infos)
          earliest-route-change (first (drop-while (fn [{:gtfs/keys [change-date]}]
                                                     ;; Remove change-date from the route-changes-infos list if it is nil or it is in the past
                                                     (or (nil? change-date)
                                                         (date-in-the-past? (.toLocalDate change-date))))
                                                   (sort-by :gtfs/change-date route-change-infos)))
          ;; Set change date to future (every 2 weeks at monday) - This is the day when changes are detected for next time
          new-change-date (time/sql-date (time/native->date (.plusDays (time/beginning-of-week (.toLocalDate (time/now))) (:detection-interval-service-days settings-tc))))
          transit-chg-res (specql/upsert! db :gtfs/transit-changes
                                          #{:gtfs/transport-service-id :gtfs/date}
                                          {:gtfs/transport-service-id service-id
                                           :gtfs/date analysis-date
                                           :gtfs/change-date new-change-date
                                           :gtfs/different-week-date (:gtfs/different-week-date earliest-route-change)
                                           :gtfs/current-week-date (:gtfs/current-week-date earliest-route-change)

                                           :gtfs/removed-routes (count (group-by :gtfs/route-hash-id (:removed change-infos-group)))
                                           :gtfs/added-routes (count (group-by :gtfs/route-hash-id (:added change-infos-group)))
                                           :gtfs/changed-routes (count (group-by :gtfs/route-hash-id (:changed change-infos-group)))
                                           :gtfs/no-traffic-routes (count (group-by :gtfs/route-hash-id (:no-traffic change-infos-group)))

                                           :gtfs/package-ids package-ids
                                           :gtfs/created (java.util.Date.)})]
      (update-route-changes! db (time/sql-date analysis-date) service-id route-change-infos)
      (change-history/update-change-history db (time/sql-date analysis-date) service-id package-ids route-change-infos))))

(defn override-holidays [db date-route-hashes]
  (map (fn [row]
         (let [date (:date row)
               holiday-id (when date
                            (transit-changes/is-holiday? db date))]
           (if holiday-id
             (assoc row :hash
                        ;; Mark holiday using a keyword which no-traffic detection know if day has traffic or not.
                        (if (string? (:hash row))
                          :holiday-tr                       ;; holiday with traffic
                          :holiday-nt))                     ;; holiday without traffic
             row)))
       date-route-hashes))

(defn- set-route-hash-id [_ route type]
  (let [short (:route-short-name route)
        long (:route-long-name route)
        headsign (:trip-headsign route)]
    (cond
      (= type "short-long-headsign")
      (str short "-" long "-" headsign)
      (= type "short-long")
      (str short "-" long)
      (= type "long-headsign")
      (str long "-" headsign)
      (= type "long")
      (str long)
      :else
      (str short "-" long "-" headsign))))

(defn add-route-hash-id-as-a-map-key
  "Add default route-hash-id (long-short-headsign) to routes"
  [routes route-hash-id-type]
  (map
    (fn [x]
      (update x :route-hash-id #(set-route-hash-id % x route-hash-id-type)))
    routes))

(defn map-by-route-key [service-routes route-hash-id-type]
  (let [service-routes (if (empty? (:route-hash-id (first service-routes)))
                         (add-route-hash-id-as-a-map-key service-routes route-hash-id-type)
                         service-routes)]
    (sort-by :route-hash-id (map-by :route-hash-id service-routes))))

(defn changed-day-from-changed-week
  [db service-id route-list-with-changed-weeks]
  (mapv #(route-day-changes db service-id %) route-list-with-changed-weeks))

(defn remove-outscoped-weeks
  "Input: all-routes = sequence of vectors (template: `[ ['' {}] ['' {}] ]` ). Each vector describes a route.
    routes-weeks = sequence of vectors. A vector contains maps of a route, a map describes a week of traffic.
  Output: Removes from routes-weeks weeks which fall out from route's min and max date of traffic."
  [all-routes routes-weeks]
  (mapv
    (fn [route-wks]
      ;; Take route-info from first object with matching route key. Same in all because a vector contains one route.
      (let [route-info (some #(when (= (first (keys (:routes (first route-wks))))
                                       (first %))
                                (second %))
                             all-routes)
            route-min-date-local (when route-info
                                   (.toLocalDate (:min-date route-info)))
            route-max-date-local (when route-info
                                   (.toLocalDate (:max-date route-info)))]

        (if (and route-min-date-local route-max-date-local)
          (filterv #(and (.isAfter (:end-of-week %) route-min-date-local)
                         (.isBefore (.minusDays (:beginning-of-week %) 1) route-max-date-local))
                   route-wks)
          route-wks)))
    routes-weeks))

(defn changes-by-week->changes-by-route
  "Input: Takes collection of maps (weeks), each map contains all routes of the service for the week.
    Input format:
        {:beginning-of-week 1.1 :end-of-week 7.1 :routes { \"route1\" [\"h1\" \"h1\"}
                                                          \"route2\" [\"h1\" \"h1\"]
                                                          \"route3\" [\"h1\" \"h1\"]}}
        {:beginning-of-week 8.1 :end-of-week 15.1 :routes { \"route1\" [\"h1\" \"h1\"}
                                                          \"route2\" [\"h1\" \"h1\"]
                                                          \"route3\" [\"h1\" \"h1\"]}}
  Function splits the input maps so that routes are not grouped together, instead each route will be in its own collection.
  Output: A vector of 'route-vectors'. Each route-vector contains maps, each representing a week for the specific route.
    Output format:
       [[{:beginning-of-week 1.1, :end-of-week 7.1, :route-key \"name\" :routes {\"route1\" [\"h1\" \"h1\"]}}
        {:beginning-of-week 8.1, :end-of-week 15.1, :route-key \"name\" :routes {\"route1\" [\"h1\" \"h1\"]}}
        {:beginning-of-week 16.1, :end-of-week 23.1, :route-key \"name\" :routes {\"route1\" [\"h1\" \"h1\"]}}]
        [{:beginning-of-week 1.1, :end-of-week 7.1, :route-key \"name\" :routes {\"route2\" [\"h1\" \"h1\"]}}
         {:beginning-of-week 8.1, :end-of-week 15.1, :route-key \"name\" :routes {\"route2\" [\"h1\" \"h1\"]}}
         {:beginning-of-week 16.1, :end-of-week 23.1, :route-key \"name\" :routes {\"route2\" [\"h1\" \"h1\"]}}]]"
  [weeks-routes]
  (->> weeks-routes
       ;; Take each route object from a week object and duplicate them so route weeks are independent week objects
       (reduce
         (fn [result week-routes]
           (let [routes (:routes week-routes)
                 r-weeks
                 (map (fn [route]
                        (assoc week-routes :routes (conj {} route)))
                      routes)]
             (concat result r-weeks)))
         [])
       ;; Group week objects of same routes into same vectors
       (group-by (fn [d]
                   (keys (:routes d))))
       ;; Remove useless route key, it's contained in the val objects
       (vals)))

(defn detect-changes-for-all-routes
  "Input: route-list-with-week-hashes = sequence of routes with their traffic weeks
  Output: Sequence of change-maps, each describing a traffic change of a route or ongoing traffic without changes."
  [^LocalDate analysis-date all-routes route-list-with-week-hashes]
  (vec (mapcat
         #(route-differences % all-routes analysis-date)
         route-list-with-week-hashes)))


(defn traffic-week-maps->change-maps
  "Input: analysis-date = date when analysis is run
    all-routes = sequence of vectors. Each vector describes a route
  Output: Sequence of maps, each describing a single change in traffic for a route"
  [analysis-date all-routes week-maps]
  (->> week-maps
       (changes-by-week->changes-by-route)
       (remove-outscoped-weeks all-routes)
       (detect-changes-for-all-routes analysis-date all-routes)))

(spec/fdef detect-route-changes-for-service
           :ret ::detected-route-changes-for-services-coll)
(defn detect-route-changes-for-service [db {:keys [service-id] :as route-query-params}]
  "Input: Takes service-id,
  fetches and analyzes packages for the service and produces a collection of structures, each of which describes
  if a route has traffic or changes/no-traffic/ending-traffic, during a time period defined in the analysis logic.
  Output: ::detected-route-changes-for-services-coll"
  (let [route-hash-id-type (db-route-detection-type db service-id)
        ;; Generate "key" for all routes. By default it will be a vector ["<route-short-name>" "<route-long-name" "trip-headsign"]
        service-routes (sort-by :route-hash-id (service-routes-with-date-range db {:service-id service-id}))
        all-routes (map-by-route-key service-routes route-hash-id-type)
        all-route-keys (set (keys all-routes))
        route-hashes (sort-by :date
                              (apply concat
                                     (mapv (fn [route-key]
                                             (let [query-params (merge {:route-hash-id route-key} route-query-params)]
                                               (service-route-hashes-for-date-range db query-params)))
                                           all-route-keys)))
        ;; Change hashes that are at static holiday to a keyword
        route-hashes-with-holidays (override-holidays db route-hashes)
        routes-by-date (routes-by-date route-hashes-with-holidays all-route-keys) ;; Format: ({:date routes(=hashes)})
        analysis-date (java.time.LocalDate/now)]
    (try
      {:all-routes all-routes
       :route-changes
       (let [new-data (->> routes-by-date
                           ;; Create week hashes so we can find out the differences between weeks
                           (combine-weeks)
                           (traffic-week-maps->change-maps analysis-date all-routes)
                           ; Fetch detailed day details
                           (route-day-changes db service-id))]
         (spec/assert ::detected-route-changes-for-services-coll new-data)
         new-data)}
      (catch Exception e
        (log/warn e "Error when detecting route changes using route-query-params: " route-query-params " service-id:" service-id)))))

(defn- update-hash [old x]
  (let [short (:gtfs/route-short-name x)
        long (:gtfs/route-long-name x)
        headsign (:gtfs/trip-headsign x)]
    (str short "-" long "-" headsign)))

(defn service-package-ids-for-date-range [db query-params]
  (mapv :id (service-packages-for-date-range db query-params)))

;; This is only for local development
;; Add route-hash-id for all routes in gtfs-transit-changes table in column route-hashes.
(defn update-date-hash-with-null-route-hash-id [db service-id]
  (let [transit-changes (specql/fetch db :gtfs/transit-changes
                                      (specql/columns :gtfs/transit-changes)
                                      {:gtfs/transport-service-id service-id})]
    (for [t transit-changes]
      (let [route-changes (specql/fetch db :gtfs/detected-route-change
                                        (specql/columns :gts/detected-route-change)
                                        {:gtfs/transit-change-date (:gtfs/date transit-changes)
                                         :gtfs/transit-service-id service-id})
            chg-routes (map (fn [x]
                              (update x :gtfs/route-hash-id #(update-hash % x))) route-changes)]
        (specql/update! db :gtfs/transit-changes
                        {:gtfs/transport-service-id service-id
                         :gtfs/date (:gtfs/date t)}
                        ;; where
                        {:gtfs/transport-service-id service-id
                         :gtfs/date (:gtfs/date t)})
        (doseq [r route-changes]
          (specql/update! db :gtfs/detected-route-change
                          r
                          {:gtfs/transport-service-id service-id
                           :gtfs/transit-change-date (:gtfs/date t)
                           :gtfs/route-short-name (:gtfs/route-short-name r)
                           :gtfs/route-long-name (:gtfs/route-long-name r)
                           :gtfs/trip-headsign (:gtfs/trip-headsign r)}))))))

;;Use only in local environment and for debugging purposes!
(defn update-route-hashes [db]
  (let [service-ids (fetch-distinct-services-from-transit-changes db)]
    (doall
      (for [id service-ids]
        (update-date-hash-with-null-route-hash-id db (:id id))))))

(defn- call-generate-date-hash [db packages user future]
  (let [package-count (count packages)
        recalculation-id (when packages
                           (:gtfs/recalculation-id (start-hash-recalculation db package-count user)))]
    (dotimes [i (count packages)]
      (let [package-id (nth packages i)]
        #_(println "Generating" (inc i) "/" package-count " - " package-id)
        (if future
          (generate-date-hashes-for-future db {:package-id (:package-id package-id)})
          (generate-date-hashes db {:package-id (:package-id package-id)}))
        (update-hash-recalculation db (inc i) recalculation-id))
      (log/info "Generation ready!"))
    (stop-hash-recalculation db recalculation-id)))

(defn calculate-monthly-date-hashes-for-packages [db user future]
  (let [monthly-packages (fetch-monthly-packages db)]
    (log/info "Generating monthly date hashes. Package count" (count monthly-packages))
    (call-generate-date-hash db monthly-packages user future)
    monthly-packages))

(defn calculate-date-hashes-for-all-packages [db user future]
  (let [all-packages (fetch-all-packages db)]
    (log/info "Generating all date hashes. Package count" (count all-packages))
    (call-generate-date-hash db all-packages user future)
    all-packages))

(defn calculate-date-hashes-for-contract-traffic [db user future]
  (let [all-packages (fetch-contract-packages db)]
    (log/info "Generating contract date hashes. Package count" (count all-packages))
    (call-generate-date-hash db all-packages user future)
    all-packages))


;; Do not use this if you don't need to.
;; This is helper function for local development. It will calculate route-hash-id to gtfs-date-hash table for the given
;; package-id. Running time of this function is quite long. (3-10minutes)
;; TODO: Create function that takes service-id as parameter and calls this function with package-id's that belongs to given service-id
(defn generate-gtfs-date-hash-for-package [db package-id]
  (let [hashes (specql/fetch db :gtfs/date-hash
                             (specql/columns :gtfs/date-hash)
                             {:gtfs/package-id package-id})]
    (doall
      (for [h hashes]
        (let [route-hashes (:gtfs/route-hashes h)
              chg-hashes (map (fn [x]
                                (update x :gtfs/route-hash-id #(update-hash % x))) route-hashes)]
          (when (and route-hashes (:gtfs/hash h))
            (specql/update! db :gtfs/date-hash
                            {:gtfs/date (:gtfs/date h)
                             :gtfs/package-id package-id
                             :gtfs/route-hashes chg-hashes
                             :gtfs/modified (java.util.Date.)}
                            ; where
                            {:gtfs/package-id package-id
                             :gtfs/date (:gtfs/date h)})
            (println "package-id " package-id "date " (:gtfs/date h))))))))
