(ns ote.time
  "Common utilities for working with date and time information."
  (:require
   #?@(:clj [[specql.impl.composite :as specql-composite]
             [clj-time.format :as format]
             [clj-time.coerce :as coerce]
             [cheshire.generate :as cheshire-generate]
             [clj-time.core :as t]]
       :cljs [[goog.string :as gstr]
              [goog.date.Date]
              [goog.date.DateTime]
              [cljs-time.core :as t]
              [cljs-time.format :as format]
              [cljs-time.local :as local]
              [cljs-time.coerce :as coerce]])
   [specql.data-types :as specql-data-types]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]))

(defprotocol DateFields
  (date-fields [this] "Return date fields as a map of data."))

(defn date-fields-only
  "Return date fields without any time fields."
  [x]
  (select-keys (date-fields x) #{::year ::month ::date}))

;; Record for wall clock time (hours, minutes and seconds)
(defrecord Time [hours minutes seconds])

(s/def ::specql-data-types/time #(instance? Time %))

#?(:cljs
   (defn format-timestamp-for-ui [dt]
     (if (nil? dt)
       " " ;: if nil - print empty string
       (->> dt
         t/to-default-time-zone
         (format/unparse (format/formatter "dd.MM.yyyy HH:mm"))))))

#?(:cljs
   (defn format-timestamp->date-for-ui [dt]
     (if (nil? dt)
       " " ;: if nil - print empty string
       (->> dt
         t/to-default-time-zone
         (format/unparse (format/formatter "dd.MM.yyyy"))))))

#?(:cljs
   (defn date-fields-from-timestamp [dt]
     (if (nil? dt)
       nil
       (->> dt
         t/to-default-time-zone
         (date-fields)))))

#?(:cljs
   (defn format-js-time [time]
     (if  (nil? time)
       "" ;: if nil - print empty string
       (->> time
            t/to-default-time-zone
            (format/unparse (format/formatter "HH:mm:ss"))))))

#?(:cljs
   (defn to-js-time [db-time]
     (let [hours (get db-time :hours)
           minutes (get db-time :minutes)
           seconds (get db-time :seconds)]
       (js/Date.
         (coerce/to-long (local/to-local-date-time
                           (t/today-at hours minutes seconds)))))))


(defn time? [x]
  (instance? Time x))

(defn empty-time?
  "Check if time is empty. Requires both hours and minutes to be set."
  [{:keys [hours minutes]}]
  (or (nil? hours)
      (nil? minutes)))

(def valid-time? (complement empty-time?))

(defn empty-date?
  "Check if date is empty. Requires year, month and date to be set."
  [{::keys [year month date]}]
  (or (nil? year)
      (nil? month)
      (nil? date)))

(def valid-date? (complement empty-date?))

(defn format-time-full [{:keys [hours minutes seconds]}]
  (#?(:clj format
      :cljs gstr/format)
   "%02d:%02d:%02d" hours minutes (or seconds 0)))

(defn format-time [{:keys [hours minutes seconds] :as time}]
  (if (and seconds (not= 0 seconds))
    (format-time-full time)
    (#?(:clj format
        :cljs gstr/format)
     "%02d:%02d" hours minutes)))

(defn parse-time [string]
  (let [[h m s] (map #(#?(:clj Integer/parseInt
                          :cljs js/parseInt) %)
                     (str/split string #":"))]
    (->Time h m s)))

(extend-protocol DateFields
   #?(:cljs js/Date :clj java.util.Date)
   (date-fields [this]
     {::date (.getDate this)
      ::month (inc (.getMonth this))
      ::year (+ 1900 (.getYear this))
      ::hours (.getHours this)
      ::minutes (.getMinutes this)
      ::seconds (.getSeconds this)}))

#?(:cljs
   (extend-protocol DateFields
     goog.date.Date
     (date-fields [this]
       {::date (.getDate this)
        ::month (inc (.getMonth this))
        ::year (.getYear this)}))
   :clj
   (extend-protocol DateFields
     java.time.LocalDate
     (date-fields [this]
       {::date (.getDayOfMonth this)
        ::month (.getMonthValue this)
        ::year (.getYear this)})

     org.joda.time.DateTime
     (date-fields [this]
       {::date (.getDayOfMonth this)
        ::month (.getMonthOfYear this)
        ::year (.getYear this)
        ::hours (.getHourOfDay this)
        ::minutes (.getMinuteOfHour this)
        ::seconds (.getSecondOfMinute this)})

     org.joda.time.LocalDate
     (date-fields [this]
       {::date (.getDayOfMonth this)
        ::month (.getMonthOfYear this)
        ::year (.getYear this)})

     java.sql.Date
     (date-fields [this]
       (date-fields (.toLocalDate this)))))

(def midnight {::hours 0 ::minutes 0 ::seconds 0})

(defn date-fields->date-time [{::keys [year date month hours minutes seconds]}]
  (t/date-time year month date hours minutes seconds))

(defn date-fields->date [{::keys [year date month]}]
  #?(:clj (java.time.LocalDate/of year month date)
     :cljs (goog.date.Date. year (dec month) date)))

(defn year [dt]
  (::year (date-fields dt)))


(defn format-date
  "Format given date in human readable format."
  [date]
  (let [{::keys [date month year]} (date-fields date)]
    (#?(:cljs gstr/format :clj format)
     "%02d.%02d.%d" date month year)))

(defn format-date-iso-8601
  "Format given date in ISO-8601 format."
  [date]
  (let [{::keys [date month year]} (date-fields date)]
    (#?(:cljs gstr/format :clj format)
     "%d-%02d-%02d" year month date)))

(defn format-date-opt
  "Format given date in human readable format or empty string if nil."
  [date]
  (if date
    (format-date date)
    ""))

#?(:clj
   (defn parse-date-iso-8601
     "Parse a date in ISO-8601 format."
     [date]
     (let [[year month date]
           (map #(Integer/parseInt %)
                (str/split date #"-"))]
       (java.time.LocalDate/of year month date)))
   :cljs
   (defn parse-date-iso-8601
     "Parse a date in ISO-8601 format."
     [date]
     (let [[year month date]
           (map #(js/parseInt %)
                (str/split date #"-"))]
       (t/date-time year month date))))

(defn parse-date-eu
  "Parse a EU formatted date (dd.MM.yyyy) to a local date"
  [date]
  (let [[date month year] (map #?(:clj #(Integer/parseInt %)
                                  :cljs js/parseInt)
                               (str/split date #"\."))]
    (date-fields->date {::year year ::month month ::date date})))

;; Hook into specql to allow us to read/write the time
#?(:clj
   (defmethod specql-composite/parse-value "time" [_ string]
     (parse-time string)))

#?(:clj
   (defmethod specql-composite/stringify-value "time" [_ time]
     (when time
       (format-time time))))

;; Define a record that models a Postgres interval type on the frontend
;; Units less than second are expressed as fractional seconds (as in postgres)

(defrecord Interval [years months days hours minutes seconds])

(def empty-interval {:years 0
                     :months 0
                     :days 0
                     :hours 0
                     :minutes 0
                     :seconds 0.0})
#?(:clj
(defn iso-8601-date->sql-date [date-iso]
  (-> date-iso
      parse-date-iso-8601
      java.sql.Date/valueOf)))

(defn interval
  "Returns an interval of the given amount and unit.
  Example:
  `(interval 2 :hours)` returns an Interval record with
  hours set to 2 and all other fields set to zero."
  [amount unit]
  (map->Interval (merge empty-interval
                        {unit amount})))

#?(:clj
   (defn ->PGInterval [interval]
     (if (instance? org.postgresql.util.PGInterval interval)
       interval
       (let [{:keys [years months days hours minutes seconds]} interval]
         (org.postgresql.util.PGInterval.
          (int years) (int months) (int days) (int hours)
          (int minutes) (double seconds))))))

#?(:clj
   (defn pginterval->interval [^org.postgresql.util.PGInterval pg-interval]
     (map->Interval {:years (.getYears pg-interval)
                     :months (.getMonths pg-interval)
                     :days (.getDays pg-interval)
                     :hours (.getHours pg-interval)
                     :minutes (.getMinutes pg-interval)
                     :seconds (.getSeconds pg-interval)})))

(s/def :specql.data-types/interval
   (partial instance? #?(:clj org.postgresql.util.PGInterval
                         :cljs Interval)))

#?(:clj (def interval-fields-regex #"(\d+(\.\d+)?) (\w+)"))

#?(:clj (defn parse-interval [string]
          (map->Interval
            (merge
              {:years 0 :months 0 :days 0 :hours 0 :minutes 0 :seconds 0.0}
              (when-let [field-matches (re-seq interval-fields-regex string)]
                (reduce (fn [interval [_ amount _ unit]]
                          (assoc interval
                            (case unit
                              ("year" "years") :years
                              ("mon" "mons") :months
                              ("day" "days") :days
                              "hours" :hours
                              "mins" :minutes
                              "secs" :seconds)
                            (if (= unit "secs")
                              (Double/parseDouble amount)
                              (Integer/parseInt amount))))
                        {} field-matches))
              (when-let [match (re-matches #"(.* )?(\d+):(\d+):(\d+)" string)]
                (let [[_ _ h m s] match]
                  {:hours   (Integer/parseInt h)
                   :minutes (Integer/parseInt m)
                   :seconds (Double/parseDouble s)}))))))

(defn time->interval [time]
  (as-> time t
       (merge empty-interval t)
       (update t :seconds (fnil double 0))
        (map->Interval t)))

#?(:clj
   (defn time->pginterval [time]
     (->PGInterval (time->interval time))))

(defn format-interval-as-time [interval]
  (let [interval #?(:cljs interval
                    :clj (if (instance? org.postgresql.util.PGInterval interval)
                           (pginterval->interval interval)
                           interval))]
    (format-time-full
     (update (select-keys interval [:hours :minutes :seconds])
             :seconds int))))

#?(:clj
   (defmethod specql-composite/parse-value "interval" [_ string]
     (->PGInterval (parse-interval string))))

(def iso-8601-period-pattern #"^P(\d+Y)?(\d+M)?(\d+D)?(T(\d+H)?(\d+M)?(\d+(\.\d+)?S)?)?$")

(defn interval->iso-8601-period [{:keys [years months days
                                         hours minutes seconds]}]
  (let [defined? #(and % (not (zero? %)))]
    (str "P"
         (when (defined? years) (str years "Y"))
         (when (defined? months) (str months "Y"))
         (when (defined? days) (str days "D"))
         (when (some defined? [hours minutes seconds])
           (str "T"
                (when (defined? hours) (str hours "H"))
                (when (defined? minutes) (str minutes "M"))
                (when (defined? seconds) (str seconds "M")))))))

(defn iso-8601-period->interval [period]
  (let [[_ years months days _ hours minutes seconds] (re-matches iso-8601-period-pattern period)]
    (map->Interval
     (merge empty-interval
            (into {}
                  (remove nil?)
                  (for [[value key] [[years :years] [months :months] [days :days]
                                     [hours :hours] [minutes :minutes] [seconds :seconds]]
                        :let [value (when value
                                      (subs value 0 (dec (count value))))]]
                    (when value
                      [key (if (= key :seconds)
                             (#?(:clj Double/parseDouble :cljs js/parseFloat) value)
                             (#?(:clj Integer/parseInt :cljs js/parseInt) value))])))))))

#?(:clj
   (defn pgtimestamp->ckan-timestring
     "CKAN uses unstandard format of ISO8601 called CKAN8601. It is in format 'yyyy-MM-ddTHH:mm:ss'.
     This format is required for e.g. last_updated or created fields in CKAN api payloads."
     [timestamp]
     (format/unparse (format/formatters :date-hour-minute-second)
                     (coerce/from-sql-time timestamp))))

#?(:clj
   (cheshire-generate/add-encoder
    org.postgresql.util.PGInterval
    (fn [interval json-generator]
      (cheshire-generate/encode-map (pginterval->interval interval) json-generator))))

(defn minutes-from-midnight [{:keys [minutes hours seconds] :as time}]
  (+ (* 60 hours) minutes
     (if seconds
       (/ seconds 60.0)
       0)))

(defn minutes-from-midnight->time [minutes]
  (let [hours (int (/ minutes 60))
        seconds (int (* 60.0 (- minutes (int minutes))))
        minutes (int (- (int minutes) (* 60 hours)))]
    (->Time hours minutes seconds)))

(defn minutes-elapsed [t1 t2]
  (- (minutes-from-midnight t2)
     (minutes-from-midnight t1)))

(defn format-minutes-elapsed
  "Format how long a time is elapsed in minutes and seconds."
  [minutes-elapsed]
  (let [seconds (int (* 60.0 (- minutes-elapsed (int minutes-elapsed))))
        minutes (int minutes-elapsed)]
    (#?(:clj format
        :cljs gstr/format)
     "%02d:%02d" minutes (Math/abs seconds))))

(defn date-range [start end]
  (lazy-seq
   (cons start
         (when (t/before? start end)
           (date-range (t/plus start (t/days 1)) end)))))


(defn java-localdate->inst [ld]
  (date-fields->native
   (merge {::hours 0 ::minutes 0 ::seconds 0}
          (date-fields ld))))

(defn java-localdate->joda-date-time [ld]
  (native->date-time (java-localdate->inst ld)))

(def week-days [:monday :tuesday :wednesday :thursday :friday :saturday :sunday])
(def week-day-order {:monday 0 :tuesday 1 :wednesday 2 :thursday 3 :friday 4 :saturday 5 :sunday 6})

(defn day-of-week [dt]
  (case (t/day-of-week dt)
    1 :monday
    2 :tuesday
    3 :wednesday
    4 :thursday
    5 :friday
    6 :saturday
    7 :sunday))

(defn joda-datetime->java-localdate [joda-dt]
  (let [str-dt (format-date-iso-8601 joda-dt)
        java-ld (java.time.LocalDate/parse str-dt)]
    java-ld))

(defn native->date-time
  "Convert a platform native Date object to clj(s)-time.
  Takes into account that the JS date objects for timezones added
  to UTC (like Finnish UTC+2/+3) the date part is the previous day.

  For example: midnight 27 Feb 2018 => 26 Feb 2018 22:00"
  [native-date]
  (date-fields->date-time (date-fields native-date)))

(defn date-fields->native
  "Convert date fields ma pto native Date object"
  [{::keys [year month date hours minutes seconds]}]
  #?(:cljs (js/Date. year (dec month) date hours minutes seconds)
     :clj (let [gc (java.util.GregorianCalendar. year (dec month) date hours minutes seconds)]
            (.getTime gc))))

(defn time-difference [time1 time2]
  (Math/abs (- (minutes-from-midnight time1) (minutes-from-midnight time2))))

(defn day-difference
  "How many days from date1 to date2."
  [date1 date2]
  (if (t/before? date1 date2)
    (t/in-days (t/interval date1 date2))
    (- (t/in-days (t/interval date2 date1)))))


(defn date-string->date-time [date-string]
  (let [df (date-fields-only (parse-date-iso-8601 date-string))
        year (:ote.time/year df)
        month (:ote.time/month df)
        day (:ote.time/date df)]
    (t/date-time year month day)))

#?(:cljs
;; TBD: rename away from "local" so not to confuse with ambiguous Java LocalTime, and consider using goog.date.Date
(defn to-local-js-date [date]
  (let [d (date-fields-from-timestamp date)]
    (new js/goog.date.DateTime (:ote.time/year d) (dec (:ote.time/month d)) (:ote.time/date d) 0 0 0))))

#?(:cljs
   (defn days-until [date]
     (let [date-fields-now (date-fields-from-timestamp (t/now))
           date-fields (date-fields-from-timestamp date)
           now-date-time (date-string->date-time (str (:ote.time/year date-fields-now) "-" (:ote.time/month date-fields-now) "-" (:ote.time/date date-fields-now)))
           date-time (date-string->date-time (str (:ote.time/year date-fields) "-" (:ote.time/month date-fields) "-" (:ote.time/date date-fields)))]
       (if date
           (day-difference now-date-time date-time)
         0))))

#?(:cljs
   (defn date-to-str-date [date]
     (let [f (date-fields-only date)
           year (:ote.time/year f)
           month (:ote.time/month f)
           month (if (= 1 (count (str month)))
                   (str "0" month)
                   month)
           day (:ote.time/date f)
           day (if (= 1 (count (str day)))
                 (str "0" day)
                 day)
           date-str (str year "-" month "-" day)]
       date-str)))

(defn now []
  (t/now))

;; Please! find out what type on date and days these parameters are!
;; Return goog.date.UTCDateTime object to to front end and Joda DateTime object to backend
(defn days-from [date days]
  (t/plus date (t/days days)))

(defn beginning-of-week [d]
  (if (= :monday (day-of-week d))
    d
    (recur (days-from d -1))))

#?(:clj
(defn date-string->inst-date [date-string]
  (let [df (date-fields-only (parse-date-iso-8601 date-string))
        date1 (-> df
                  date-fields->date
                  (.atStartOfDay (java.time.ZoneId/of "Europe/Helsinki"))
                  .toInstant
                  java.util.Date/from)]
    date1)))



