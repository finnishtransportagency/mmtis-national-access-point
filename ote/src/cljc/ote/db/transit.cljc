(ns ote.db.transit
  "Datamodel for route based transit"
  (:require [clojure.spec.alpha :as s]
            #?(:clj [ote.tietokanta.specql-db :refer [define-tables]])
            #?(:clj [specql.postgis])
            [specql.rel :as rel]
            [specql.transform :as xf]
            [specql.impl.registry]
            [specql.data-types]
            [ote.db.common]
            [ote.db.modification]
            [ote.db.transport-service]
            [ote.time :as time]
            [ote.util.fn :refer [flip]]
            [ote.db.transport-operator]
            [ote.db.user])
  #?(:cljs
     (:require-macros [ote.tietokanta.specql-db :refer [define-tables]])))

(define-tables
  ["localized_text" :ote.db.transport-service/localized_text]
  ["transit_agency" ::agency
   {"name" ::agency-name}]
  ["transit_stop_type" ::stop-type-enum (specql.transform/transform (specql.transform/to-keyword))]
  ["transit_route_type" ::route-type-enum (specql.transform/transform (specql.transform/to-keyword))]
  ["transit_stop" ::stop]
  ["transit_service_rule" ::service-rule]
  ["transit_service_calendar" ::service-calendar]
  ["transit_stopping_type" ::stopping-type (specql.transform/transform (specql.transform/to-keyword))]
  ["transit_stop_time" ::stop-time]
  ["transit_trip" ::trip]
  ["transit_route" ::route
   ote.db.modification/modification-fields]

  ["finnish_ports" ::finnish-ports
   ote.db.modification/modification-fields]
  ["pre_notice_type" ::pre_notice_type (specql.transform/transform (specql.transform/to-keyword))]
  ["notice_effective_date" ::notice-effective-date]
  ["pre_notice_comment" ::pre-notice-comment
   ote.db.modification/modification-fields
   {::author (specql.rel/has-one :ote.db.modification/created-by
                                 :ote.db.user/user
                                 :ote.db.user/id)}]
  ["pre_notice" ::pre-notice
   ote.db.modification/modification-fields
   {"transport-operator-id" :ote.db.transport-operator/id
    ::attachments (specql.rel/has-many ::id
                                       ::pre-notice-attachment
                                       ::pre-notice-id)
    ::comments (specql.rel/has-many ::id
                                    ::pre-notice-comment
                                    ::pre-notice-id)
    :ote.db.transport-operator/transport-operator (specql.rel/has-one :ote.db.transport-operator/id
                                                                      :ote.db.transport-operator/transport-operator
                                                                      :ote.db.transport-operator/id)}]
  ["pre_notice_attachment" ::pre-notice-attachment
   ote.db.modification/modification-fields])

(def rule-week-days [::monday ::tuesday ::wednesday ::thursday
                     ::friday ::saturday ::sunday])

(defn rule-dates
  "Evaluate a recurring schedule rule. Returns a sequence of dates."
  [{::keys [from-date to-date] :as rule}]
  (let [week-days (into #{}
                        (keep #(when (get rule (keyword "ote.db.transit" (name %))) %))
                        time/week-days)]
    (when (and from-date to-date (not (empty? week-days)))
      (for [d (time/date-range (time/native->date-time from-date)
                               (time/native->date-time to-date))
            :when (week-days (time/day-of-week d))]
        (select-keys
         (time/date-fields d)
         #{::time/year ::time/month ::time/date})))))

(defn time-to-24h
  "Convert a time entry to stay within 24h (stop times may be greater)."
  [time]
  (update time :hours mod 24))

(defn trip-stop-times-to-24h
  "Convert all stop times to be within 24h hours. Trips that span multiple days
  may have stop times greater than 24h but they must be stored in the database
  in 24h format."
  [trip]
  (update trip ::stop-times (flip mapv)
          (fn [st]
            (-> st
                (update ::arrival-time #(when % (time-to-24h %)))
                (update ::departure-time #(when % (time-to-24h %)))))))

(defn trip-stop-times-from-24h
  "Convert trip stop times from 24h to continuous hours.
  A trip that spans multiple days will have stop times greater than 24h."
  [trip]
  (update trip ::stop-times
          (fn [stop-times]
            (loop [hours-to-add 0
                   acc []
                   previous-departure nil
                   [st & stop-times] stop-times]
              (if (nil? st)
                acc
                (let [hours-to-add (if (and previous-departure
                                            (< (time/minutes-from-midnight (::arrival-time st))
                                               (time/minutes-from-midnight previous-departure)))
                                     (+ hours-to-add 24)
                                     0)]
                  (recur hours-to-add
                         (conj acc (-> st
                                       (update ::arrival-time #(when % (update % :hours + hours-to-add)))
                                       (update ::departure-time #(when % (update % :hours + hours-to-add)))))
                         (::departure-time st)
                         stop-times)))))))
