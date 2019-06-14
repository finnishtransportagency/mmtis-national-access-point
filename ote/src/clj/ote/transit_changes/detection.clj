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

;; TODO: utilize here prev-change to get :starting-week-hash if it's available, to allow detecting traffic change after no-traffic period. AAAnil16nilBBB
;; :starting-week-hash is lost in earlier loop if no-traffic is reported on :nt-last.
;; Current workaround puts no-traffic reporting to week after :nt-last, which puts it into same map with :different week and has to be split later.
;; Using `prev-change` :starting-week-hash here allows detecting BBB even if state is empty.

(defn detect-change-for-route
  "Reduces [prev curr next1 next2] weeks into a detection state change"
  [{:keys [starting-week-hash] :as state} [prev curr next1 next2 :as wks] route]

  (cond
    ;; If this is the first call and the current week is "anomalous".
    ;; Then start at the next week.
    (and (nil? starting-week-hash)
         (not (week= curr next1))
         (week= prev next1))
    {}                                                      ;; Ignore this week

    ;; No starting week specified yet, use current week
    (nil? starting-week-hash)
    (assoc state :starting-week-hash curr)

    ;; If current week does not equal starting week...
    (and (vnot (week= starting-week-hash curr) (str "curr = start (1) sw:" starting-week-hash " curr:" curr))
         (vnot (week= starting-week-hash next1) "curr = next1 (2)")
         ;; ...and traffic does not revert back to previous in two weeks
         (vnot (week= starting-week-hash next2) "curr = next2 (3)"))
    ;; this is a change
    (assoc state :different-week-hash curr)

    ;; No change found, return state as is
    :default
    state))

(defn week-hash-key-ix
  "Input:
  Output: Returns the day index of :nt-first. Monday = 0, Sunday = 6, nil = not found"
  [weekhash key-to-find]
  (key-to-find (zipmap weekhash (range 0 8))))

(defn add-current-week-hash [to-key if-key state week]
  (if (and (nil? (get state to-key))
           (some? (get state if-key)))
    (assoc state to-key (dissoc week :routes))
    state))

(def add-starting-week
  (partial add-current-week-hash :starting-week :starting-week-hash))

(def add-different-week
  (partial add-current-week-hash :different-week :different-week-hash))

(defn- route-next-different-week
  [{diff :different-week no-traffic-end-date :no-traffic-end-date :as state} route week-maps week-map-current last-analysis-wk]
  (if (or diff no-traffic-end-date)
    ;; change already found, don't try again
    state

    (let [route-week-hashes (mapv (comp #(get % route) :routes) week-maps)
          result (-> state
                     (assoc :route-key route)
                     (detect-change-for-route route-week-hashes route)
                     (add-starting-week week-map-current)
                     (add-different-week week-map-current))]
    result)))

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

(spec/def ::service-route-change-map
  (spec/keys
    :req-un
    [::route-key
     ::starting-week
     ::starting-week-hash]
    :opt-un
    [::changes
     ::different-week
     ::different-week-hash]))

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
  ;(if (= 7  (count route-weeks))
  ;   (def *r7 route-weeks))
  ;; (println "spec for route-weeks:")
  ;; (spec-provider.provider/pprint-specs (spec-provider.provider/infer-specs route-weeks ::route-weeks) 'ote.transit-changes.detection 'spec)
  ;; Take routes from the first week (they are the same in all weeks)
  (let [route-names (into #{}
                          (map first)
                          (:routes (first route-weeks)))
        result (reduce
                 (fn [route-detection-state [_ week-map-current _ _ :as week-maps]]
                   (reduce
                     (fn [route-detection-state route]
                       ;; value under route key in r-d-s map will be updated by
                       ;; (route-next-different-week *value* route week-maps week-map-current)
                       (update route-detection-state route
                               route-next-different-week route week-maps week-map-current (first (take-last 3 route-weeks))))
                     route-detection-state
                     route-names))
                 {}                                         ; initial route detection state is empty
                 (partition 4 1 route-weeks))]
    ;; (println "first-week-difference result: " (pr-str result))
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

(defn- remove-starting-nt-first [hashes]
  (if (not-empty hashes)
    (update-in hashes [0]
               (fn [hash]
                 (if (= :nt-first hash)
                   :nt
                   hash)))
    hashes))

(defn- replace-terminating-nt-last [hashes]
  (if (not-empty hashes)
    (update-in hashes [(dec (count hashes))]
               (fn [hash]
                 (if (= :nt-last hash)
                   :nt
                   hash)))
    hashes))

(defn- hashes-flat->route-wks
  "Input:
    route-hashes = all traffic day hashes in one flat collection
  Output:"
  [route-hashes route-weeks]
  (let [hash-weeks (partition 7 route-hashes)]
    ;(println "hashes-flat->route-wks hash-weeks=" hash-weeks) TODO: remove
    (map (fn [route-week hash-week]
           ;(clojure.pprint/pprint route-week)
           ;(clojure.pprint/pprint hash-week)
           (assoc route-week :routes {(first (keys (:routes route-week)))
                                      (vec hash-week)}))
         route-weeks
         hash-weeks)))

(defn- route-hashes->keyed-notraffic-hashesv
  "Replaces day hashes using a keyword, if the consecutive length of a nil traffic period exceeds a configured
  threshold alue.
  Input:
    route-hashes = a vector of strings
  Output: vector of strings, where each day of a no-traffic qualified period are replaced using a keyword."
  [route-hashes]
  ;(println "route-hashes->keyed-notraffic-hashesv: \n" route-hashes) TODO: remove
  (vec
    (mapcat
      #(if (and (some nil? %) (> (count %) (:detection-threshold-no-traffic-days settings-tc)))
         (seq
           (concat
             [:nt-first]                                 ;; First and last marked to allow detecting start/end dates
             (vec
               (repeat (- (count %) 2) :nt))             ;; Negative shouldn't be possible because of :detection-threshold-no-traffic-days
             [:nt-last]))
         %)
         (partition-by nil? route-hashes))))

(defn- route-wks->hashes-flat
  "Input: collection of maps, each describing a week of traffic (or lack of it) for same one route
  Output: vector where all day hashes are appended as one flat consecutive collection."
  [route-weeks]
  (vec
    (reduce (fn [result {:keys [routes] :as route-week}]
              (concat result (first (vals routes))))
            []
            route-weeks)))

(defn- route-wks->keyed-notraffic-wks
  "Input: route-weeks = collection of maps, each describing a week of traffic (or lack of it) for same one route
  Output: route-weeks where each day hashes replaced by keyword, if the day belongs to a 'no-traffic' period."
  [route-weeks]
  (-> route-weeks
      route-wks->hashes-flat
      route-hashes->keyed-notraffic-hashesv
      replace-terminating-nt-last                           ;; Don't report :no-traffic-end-date if traffic doesn't continue
      remove-starting-nt-first                              ;; Don't report no-traffic which is already ongoing
      (hashes-flat->route-wks route-weeks)))

;; tee testi
;["A" "A" "A" "A" "A" "A" "A"] ;; 2019 05 13
;["A" "A" "A" "A" "A" "A" "A"] ;; 2019 05 20
;["A" "A" "A" "A" "A" "A" "A"] ;; 2019 05 27
;["A" "A" "A" "A" "A" "A" "A"] ;; 2019 06 03
;["B" "B" :nt :nt :nt :nt :nt] ;; 2019 06 10
;[:nt :nt :nt :nt :nt :nt :nt] ;; 2019 06 17
;[:nt :nt :nt :nt :nt :nt :nt] ;; 2019 06 24
;[:nt :nt :nt :nt :nt :nt :nt] ;; 2019 07 01 ; ei poisteta nt viikkoja, tauon jälkeinen muutoksen nosto tärkeämpi kuin taukoa edeltävän muutoksen nosto
;[:nt :nt "B" "B" "B" "B" "B"] ;; 2019 07 08 *a change is detected BUT also as a side effect week considered as anomalous because prev = next1
;["B" "B" "B" "B" "B" "B" "B"] ;; 2019 07 15 NO change detected because will be considered anomalous because prev = next1
;["B" "B" "B" "B" "B" "B" "B"] ;; 2019 07 22 NO change detected because will be considered starting week
;["B" "B" "B" "B" "B" "B" "B"] ;; 2019 07 29
;["B" "B" "B" "B" "B" "B" "B"] ;; 2019 08 05
;["B" "B" "B" "B" "B" "B" "B"] ;; 2019 08 12
;["B" "B" "B" "B" "B" "B" "B"] ;; 2019 08 19
;["B" "B" "B" "B" "B" "B" "B"]
;; => jos täydet NT viikot poistetaan, nousee BB, ei nouse jos ei poisteta
;; => CCCBBBB uusi starting week koska ntntBBBBBB tauon jälkeen anomalous koska edellinen ntntntn todetaan samaksi kuin next CCCBBBB
;; jos tauko loppuu keskellä viikkoa, noussee viikon muutokset, mutta sitä ei oteta starting weekiksi vaan edetään seuraavaan => seuraavalla viikolla ei nouse muutoksia koska se on starting week

;; * :nt sotkee seuraavat vertailut kun piilottaa muutokset
;;

;; Kesken viikon loppuneen no-traffic tauon jälkeen ei tunnisteta minkäään tulevan viikon päivän muutoksia niiden päivien osalta, jotka osuvat samoille päiville kuin taukojäänne
;; esim: ":nt :nt :nt :nt :nt :nt B" aiheuttaa että vain sunnuntaimuutokset tunnistetaan. Ongelma poistuu vasta kun :starting-week ei ole enää :nt:llinen viikko.
;; Option1: korvaa starting week :nt:T oikeilla taukoa edeltävillä hasheilla ==> map raketeeseen myös hashien pvm:t. ==> viikkohashit eri viikoilta
;;   ==>  päivävertailun ja kanta-haun säätämistä
;; Option2: :nt vaihdetaan nil:ksi current weekiltä ==> se viikko olisi anomalous ja seuraava viikko valikoituisi starting-weekiksi
;; Huono puoli: loppumisviikon oikeita muutoksia ei huomata, mutta workaround :nt ignore ongelmalle joka piilottaa kaikki tulevat muutokset
;;
;; Susanna: parempi jättää nostamatta tauon jälkeinen muutos, jos sitä ei voida verrata oikeaan alkuperäiseen liikenteeseen, vaan tulisi verratuksi taukopäivän liikenteettömyyteen.

(defn- append-no-traffic-start-map
  "Input:
    route-week =
      {:beginning-of-week #object[java.time.LocalDate 0x3f51d3c0 \"2019-02-11\"],
       :end-of-week #object[java.time.LocalDate 0x30b5f64f \"2019-02-17\"],
       :routes {\"routename\" [\"h1\" \"h2\" \"h3\" \"h4\" \"h5\" \"h6\" \"h7\"]}}
   Output: If `no-traffic-start-position` exists, creates and appends a no-traffic start map to `change-maps`
  "
  [change-maps {:keys [beginning-of-week routes] :as route-week} no-traffic-start-position prev-wk-traffic?]
  (if (and (number? no-traffic-start-position)
           change-maps
           route-week)
    (conj change-maps
          ;; No :starting-week added because no-traffic week is not compared to any week.
          {:route-key (first (keys routes))
           :no-traffic-start-date (.plusDays beginning-of-week no-traffic-start-position)})
    change-maps))

(defn- append-no-traffic-end-key
  "Input:
    route-week =
    {:beginning-of-week #object[java.time.LocalDate 0x3f51d3c0 \"2019-02-11\"],
     :end-of-week #object[java.time.LocalDate 0x30b5f64f \"2019-02-17\"],
     :routes {\"routename\" [\"h1\" \"h2\" \"h3\" \"h4\" \"h5\" \"h6\" \"h7\"]}}
  Output: If no-traffic-end-position exists, appends :no-traffic-end-date to last object in `change-maps`
"
  [change-maps {:keys [beginning-of-week] :as route-week} no-traffic-end-position]
  (if (and (number? no-traffic-end-position)
           change-maps
           route-week
           (:no-traffic-start-date (last change-maps)))
    (update-in change-maps
               [(dec (count change-maps))]
               (fn [change-map]
                 (assoc change-map :no-traffic-end-date (.plusDays beginning-of-week no-traffic-end-position))))
    change-maps))

(defn- create-no-traffic-change-mapsv
  ""
  [[{routes-prev-wk :routes :as prev-wk} :as route-weeks]]
  (reduce
    (fn [change-maps {:keys [routes] :as route-week}]
      (let [wk-hash (first (vals routes))
            prev-wk-traffic? (some #(and (not= :nt %) (not= :nt-first %))
                                   (first (vals routes-prev-wk)))              ;; Check if previous week has any non-nil i.e. traffic
            ;_ (clojure.pprint/pprint route-weeks)
            ;_ (println "routes-prev-wk= " routes-prev-wk)
            no-traffic-start-position  (week-hash-key-ix wk-hash :nt-first)
            no-traffic-end-position (if-let [ixx (week-hash-key-ix wk-hash :nt-last)]
                                      (inc ixx) ;; inc because no-traffic end to be reported when traffic continues
                                      nil)]
        ;; Threading runs first "end" and then "start" to support case if no-traffic ends and starts on same week
        (-> change-maps
                (append-no-traffic-end-key route-week no-traffic-end-position)
                (append-no-traffic-start-map route-week no-traffic-start-position prev-wk-traffic?))))
    []
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
  (let [val1 (or (get-in m1 [:different-week :beginning-of-week]) (:no-traffic-start-date m1))
        val2 (or (get-in m2 [:different-week :beginning-of-week]) (:no-traffic-start-date m2))]
    (compare val1 val2)))

(defn route-differences
  "
  Takes a vector of weeks for one route and outputs vector of weeks where change or no traffic starts
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
  [route-weeks]
  (let [route-weeks-trafficless (vec (route-wks->keyed-notraffic-wks route-weeks))]
    (loop [route-weeks route-weeks-trafficless
           results (create-no-traffic-change-mapsv route-weeks-trafficless)]
      (let [diff-data (route-weeks-with-first-difference route-weeks)
            ;diff-data (separate-if-trafficless-and-different (first diff-data)) TODO: remove
            filtered-diff-data (filterv
                                 (fn [value]
                                   (or (:no-traffic-start-date value)
                                       (:different-week value)))
                                 diff-data)
            ;sorted-diff-data (sort filtered-diff-data) TODO: does this need to be sorted or not? Couldn't the no-traffics be just in the beginning? Sorting requires using two keys in parallel
            diff-week-beginnings (keep (comp :beginning-of-week :different-week) diff-data)
            no-traffic-end (:no-traffic-end-date (first diff-data))
            diff-week-date (first diff-week-beginnings)
            prev-week-date (when (or diff-week-date no-traffic-end)
                             (.minusWeeks (or diff-week-date no-traffic-end) 1))]
        (if (and (not-empty diff-data) prev-week-date)      ;; end condition: dates returned by f-w-d had nil different-week beginning
          (recur
            ;; Filter out different weeks before current week, because different week is starting week for next change.
            ;; Use the previous week date, because first-week-difference starts comparisons at the second given week
            (filter #(route-starting-week-not-before? % prev-week-date) route-weeks)
            (concat results filtered-diff-data))
          (if (empty? results)
            diff-data                                       ;; No change maps so return a map describing ongoing traffic
            (sort
              change-maps-compare
              (concat results filtered-diff-data))))))))

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
    ;(log/debug "Found changes in trips for route: " route-hash-id ", comparing dates: " starting-week-date " and " different-week-date " route-hash-id " route-hash-id)
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
              routes)
        res (vec (expand-day-changes route-day-changes))]
    res))

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

       ;; If the change is a no-traffic period, the different day is the first day that has no traffic
       :gtfs/current-week-date (time/sql-date
                                 (.plusDays no-traffic-start-date -1))
       ;; If no-traffic starts in future set different-week-day the day when no-traffic segment starts
       ;; if no-traffic segment is in the past (or currenlty on progress) set different-week-date when the traffic starts again
       :gtfs/different-week-date (if (.isBefore (.toLocalDate (time/sql-date no-traffic-start-date)) (.plusDays (java.time.LocalDate/now) 1))
                                   (time/sql-date no-traffic-end-date)
                                   (time/sql-date no-traffic-start-date))
       ;; If no-traffic is identified and no-traffic segment has begun before or at current date, set change date where the traffic starts again.
       :gtfs/change-date (if (.isBefore (.toLocalDate (time/sql-date no-traffic-start-date)) (.plusDays (java.time.LocalDate/now) 1))
                           (time/sql-date no-traffic-end-date) ;; Day when traffic starts again
                           (time/sql-date no-traffic-start-date))}

      :default
      {:gtfs/change-type :no-change})))

(spec/fdef transform-route-change
           :args (spec/cat :all-routes vector? :route-change ::service-route-change-map :route-changes-all ::detected-route-changes-for-services-coll))
(defn transform-route-change
  "Transform a detected route change into a database 'gtfs-route-change-info' type."
  [all-routes
   {:keys [no-traffic-start-date no-traffic-end-date
           starting-week different-week route-key no-traffic-run
           changes] :as route-change} route-changes-all]
  (spec/assert ::detected-route-changes-for-services-coll route-changes-all)
  (let [route-map (map second all-routes)
        route (first (filter #(= route-key (:route-hash-id %)) route-map))
        route-changes-for-key (filter #(= route-key (:route-key %)) route-changes-all)
        first-route-change? (= route-change (first route-changes-for-key))
        ;; Overwrite only first change type to "added" for a new route. Otherwise also changes after route start would be marked as "added".
        added? (and first-route-change? (min-date-in-the-future? route))
        removed-date (:route-end-date route-change)
        no-traffic? (and no-traffic-start-date
                         (or no-traffic-end-date
                             (and (pos? no-traffic-run)
                                  (.isBefore no-traffic-start-date (.toLocalDate (:max-date route))))))
        max-date-in-past? (.isBefore (.toLocalDate (:max-date route)) (java.time.LocalDate/now))
        {:keys [starting-week-date different-week-date
                added-trips removed-trips trip-changes]} changes
        changed? (and starting-week-date different-week-date)
        trip-stop-seq-changes (reduce update-min-max-range
                                      {}
                                      (map :stop-seq-changes trip-changes))
        trip-stop-time-changes (reduce update-min-max-range
                                       {}
                                       (map :stop-time-changes trip-changes))
        change (route-change-type max-date-in-past? added? removed-date changed? no-traffic? starting-week-date different-week-date
                                  no-traffic-start-date no-traffic-end-date route)
        change-key (change-history/create-change-key-from-change-data (merge route
                                                                             {:gtfs/route-hash-id (:route-hash-id route)
                                                                              :gtfs/change-type (:gtfs/change-type change)
                                                                              :gtfs/different-week-date (:gtfs/different-week-date change)
                                                                              :gtfs/added-trips added-trips
                                                                              :gtfs/removed-trips removed-trips
                                                                              :gtfs/trip-stop-sequence-changes-lower (:lower trip-stop-seq-changes)
                                                                              :gtfs/trip-stop-sequence-changes-upper (:upper trip-stop-seq-changes)
                                                                              :gtfs/trip-stop-time-changes-lower (:lower trip-stop-time-changes)
                                                                              :gtfs/trip-stop-time-changes-upper (:upper trip-stop-time-changes)}))]
    (merge
      {;; Route identification
       :gtfs/route-short-name (:route-short-name route)
       :gtfs/route-long-name (:route-long-name route)
       :gtfs/trip-headsign (:trip-headsign route)
       :gtfs/route-hash-id (:route-hash-id route)

       ;; Trip change counts
       :gtfs/added-trips added-trips
       :gtfs/removed-trips removed-trips
       :gtfs/trip-stop-sequence-changes-lower (:lower trip-stop-seq-changes)
       :gtfs/trip-stop-sequence-changes-upper (:upper trip-stop-seq-changes)
       :gtfs/trip-stop-time-changes-lower (:lower trip-stop-time-changes)
       :gtfs/trip-stop-time-changes-upper (:upper trip-stop-time-changes)

       :gtfs/change-key (:gtfs/change-key change-key)}
      change)))


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
    (specql/insert! db :gtfs/detected-route-change
                    (merge {:gtfs/transit-change-date analysis-date
                            :gtfs/transit-service-id service-id
                            :gtfs/created-date (java.util.Date.)}
                           r))))

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
             (assoc row :hash holiday-id)
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
       [[{:beginning-of-week 1.1, :end-of-week 7.1, :routes {\"route1\" [\"h1\" \"h1\"]}}
        {:beginning-of-week 8.1, :end-of-week 15.1, :routes {\"route1\" [\"h1\" \"h1\"]}}
        {:beginning-of-week 16.1, :end-of-week 23.1, :routes {\"route1\" [\"h1\" \"h1\"]}}]
        [{:beginning-of-week 1.1, :end-of-week 7.1, :routes {\"route2\" [\"h1\" \"h1\"]}}
         {:beginning-of-week 8.1, :end-of-week 15.1, :routes {\"route2\" [\"h1\" \"h1\"]}}
         {:beginning-of-week 16.1, :end-of-week 23.1, :routes {\"route2\" [\"h1\" \"h1\"]}}]]"
  [weeks]
  (vals (group-by (fn [d]
                    (keys (:routes d)))
                  (reduce
                    (fn [result week]
                      (let [routes (:routes week)
                            r-weeks
                            (map (fn [route]
                                   (assoc week :routes (conj {} route)))
                                 routes)]
                        (concat result r-weeks)))
                    []
                    weeks))))

(defn detect-changes-for-all-routes
  "Input: takes a vector or routes with their traffic weeks
  Invokes a function in a loop for each route to detect any changes for each route.
  Output: Vector of routes enriched by details if there are changes in traffic for a route on a week."
  [route-list-with-week-hashes]
  (vec (mapcat route-differences route-list-with-week-hashes)))

(defn- route-ends?
  "Input: date=analysis date, max-date=last day with traffic for route, traffic-threshold-d=route end threshold of days in future for reporting route end
  Output: returns true if max-date is below traffic-threshold-d"
  [^LocalDate date max-date ^Integer traffic-threshold-d]
  (and max-date
       (.isBefore (.toLocalDate max-date) (.plusDays date traffic-threshold-d))
       (.isAfter (.toLocalDate max-date) (.minusDays date 1)))) ; minus 1 day so we are sure the current day is still calculated


(spec/fdef add-ending-route-change
           :args (spec/cat :all-route-changes coll? :all-routes coll?)
           :ret ::detected-route-changes-for-services-coll)
(defn add-ending-route-change
  "Takes a collection of route changes and adds a \"route ending\" change if max-date is before
  a traffic threshold days value
  See spec definition for argument validity.
  Input:
        date: Analysis date when detection routine is run
        all-routes format:
                    ([\"-Vihtjärvi - Loppi-\"
                    {:route-short-name \"\",
                    :route-long-name \"Vihtjärvi - Loppi\",
                    :trip-headsign \"\",
                    :min-date #inst \"2019-03-13T22:00:00.000-00:00\",
                    :max-date #inst \"2019-08-29T21:00:00.000-00:00\",
                    :route-hash-id \"-Vihtjärvi - Loppi-\"}]
                    [...])
        all-route-changes format:
                    [{:route-key \"-Vihtjärvi - Loppi-\",
                     :no-traffic-run 78,
                     :starting-week-hash  [\"\\\\x09cc9ea6bb7da31623b5393efc6f5cadddb9b8825e0199237775dbab247c82d5\"   \"\\\\x09cc9ea6bb7da31623b5393efc6f5cadddb9b8825e0199237775dbab247c82d5\"   \"\\\\x09cc9ea6bb7da31623b5393efc6f5cadddb9b8825e0199237775dbab247c82d5\"   \"\\\\x09cc9ea6bb7da31623b5393efc6f5cadddb9b8825e0199237775dbab247c82d5\"   \"\\\\x09cc9ea6bb7da31623b5393efc6f5cadddb9b8825e0199237775dbab247c82d5\"   \"\\\\x795102e43a28a709b622b373880dafc7fb842850f13c3af2403f6bb3b2b32ee3\"   nil],
                     :starting-week  {:beginning-of-week   #object[java.time.LocalDate 0x2af682f8 \"2019-03-25\"], :end-of-week #object[java.time.LocalDate 0x68572428 \"2019-03-31\"]},
                     :no-traffic-start-date  #object[java.time.LocalDate 0x59e94954 \"2019-06-02\"]
                     {...}]
   Output:
        [{:route-key \"-Vihtjärvi - Loppi-\",
        :no-traffic-run 78,
        :starting-week-hash  [\"\\\\x09cc9ea6bb7da31623b5393efc6f5cadddb9b8825e0199237775dbab247c82d5\"   \"\\\\x09cc9ea6bb7da31623b5393efc6f5cadddb9b8825e0199237775dbab247c82d5\"   \"\\\\x09cc9ea6bb7da31623b5393efc6f5cadddb9b8825e0199237775dbab247c82d5\"   \"\\\\x09cc9ea6bb7da31623b5393efc6f5cadddb9b8825e0199237775dbab247c82d5\"   \"\\\\x09cc9ea6bb7da31623b5393efc6f5cadddb9b8825e0199237775dbab247c82d5\"   \"\\\\x795102e43a28a709b622b373880dafc7fb842850f13c3af2403f6bb3b2b32ee3\"   nil],
        :starting-week  {:beginning-of-week   #object[java.time.LocalDate 0x2af682f8 \"2019-03-25\"],
        :end-of-week #object[java.time.LocalDate 0x68572428 \"2019-03-31\"]},
        :no-traffic-start-date  #object[java.time.LocalDate 0x59e94954 \"2019-06-02\"]
        REMOVE THIS: :max-date #inst \"2019-08-29T21:00:00.000-00:00\"
        }
        {...}]
  "
  [date all-routes all-changes]
  (let [route-max-date (fn [route-hash-id all-routes]
                         (:max-date (some
                                      #(when (= route-hash-id (:route-hash-id (second %))) (second %))
                                      all-routes)))
        create-end-change (fn [last-chg max-date ^LocalDate date]
                            (when (route-ends? date max-date (:detection-threshold-route-end-days settings-tc))
                              (merge {:route-end-date (or
                                                        (and
                                                          (nil? (:no-traffic-end-date last-chg))
                                                          ;; If last change starts a no-traffic earlier than route max-date, use start of no-traffic. Not sure if this is possible.
                                                          ;; +1 NOT added because :no-traffic-start-date defines the first no-traffic day, i.e. traffic end
                                                          (:no-traffic-start-date last-chg))
                                                        ;; +1 because max-date defines the LAST day with traffic, hence no-traffic starts on the next day
                                                        (.plusDays (.toLocalDate max-date) 1))}
                                     (select-keys last-chg [:route-key :starting-week :starting-week-hash]))))
        remove-ongoing-or-break (fn [route-chg-group]
                                  (if (or (and (= 1 (count route-chg-group))
                                               (empty? (select-keys (last route-chg-group) [:different-week ;; If map is a traffic change map, don't discard
                                                                                            ;; If map is an ending no-traffic map, don't discard
                                                                                            :no-traffic-end-date])))
                                          (and
                                            (:no-traffic-start-date (last route-chg-group))
                                            (nil? (:no-traffic-end-date (last route-chg-group)))))
                                    (vec (take (dec (count route-chg-group)) route-chg-group)) ;; Discard content because end-change map should replace the sole normal "traffic ongoing" map
                                    route-chg-group))
        chg (doall (vec (mapcat
                          (fn [[route-key route-chg-group]]
                            (let [last-change (last route-chg-group)
                                  max-date (route-max-date (:route-key last-change) all-routes)
                                  end-change (create-end-change last-change max-date date)]
                              (if end-change
                                (conj (remove-ongoing-or-break route-chg-group) end-change)
                                route-chg-group)))
                          (group-by :route-key all-changes))))
        res (or chg [])]
    res))

(spec/fdef detect-route-changes-for-service
           :ret ::detected-route-changes-for-services-coll)
(defn detect-route-changes-for-service [db {:keys [start-date service-id] :as route-query-params}]
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
        routes-by-date (routes-by-date route-hashes-with-holidays all-route-keys)] ;; Format: ({:date routes(=hashes)})
    (try
      {:all-routes all-routes
       :route-changes
       (let [new-data (->> routes-by-date
                           ;; Create week hashes so we can find out the differences between weeks
                           (combine-weeks)
                           (changes-by-week->changes-by-route)
                           (detect-changes-for-all-routes)
                           (add-ending-route-change (java.time.LocalDate/now) all-routes)
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


