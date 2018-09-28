(ns ote.gtfs.kalkati-to-gtfs
  "Convert a Kalkati.net XML transit description to GTFS package"
  (:require [clojure.xml :as xml]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [clojure.java.io :as io]
            [ote.geo :as geo]
            [ote.time :as time]
            [ote.util.zip :as zip-file]
            [ring.util.io :as ring-io]
            [ote.gtfs.parse :as gtfs-parse]
            [ote.gtfs.spec :as gtfs-spec]
            [taoensso.timbre :as log]))

(defn kalkati-zipper [input]
  (xml-zip
   (xml/parse input)))

(def
  ^{:doc
    "Convert a Kalkat.net transport mode to GTFS mode.
GTFS does not support all the same modes, so not all modes
can be mapped to GTFS.

Kalkati Transport modes
 1 - air
 2 - train
 21 - long/mid distance train
 22 - local train
 23 - rapid transit
 3 - metro
 4 - tramway
 5 - bus, coach
 6 - ferry
 7 - waterborne
 8 - private vehicle
 9 - walk
 10 - other

 GTFS Transport Modes
 0 - Tram, Streetcar, Light rail.
 1 - Subway, Metro.
 2 - Rail.
 3 - Bus.
 4 - Ferry.
 5 - Cable car.
 6 - Gondola, Suspended cable car.
 7 - Funicular."}
  kalkati-mode->gtfs-mode
  {"2" "2"
   "21" "2"
   "22" "0"
   "23" "2"
   "3" "1"
   "4" "0"
   "5" "3"
   "6" "4"
   "7" "4"})

(defn- double-attr [loc name]
  (Double/parseDouble (z/attr loc name)))

(defn- int-attr [loc name]
  (Integer/parseInt (z/attr loc name)))

(defn trans-modes-by-id [kz]
  (into {}
        (map (juxt :id identity))
        (z/xml-> kz :Trnsmode
                 (fn [m]
                   {:id (z/attr m :TrnsmodeId)
                    :name (z/attr m :Name)
                    :type (z/attr m :ModeType)}))))

(defn stations-by-id
  "Extract Kalkati stations and map them by id"
  [root]
  (into {}
        (map (juxt :id identity))
        (z/xml-> root :Station
                 (fn [station]
                   {:id (z/xml1-> station (z/attr :StationId))
                    :name (z/xml1-> station (z/attr :Name))
                    :coordinate (geo/kkj->wgs84
                                 {:x (double-attr station :X)
                                  :y (double-attr station :Y)})}))))

(defn routes
  "Return all routes defined by the Kalkati file"
  [root]
  (z/xml-> root :Timetbls :Service
           (fn [service]
             {:service-id (z/attr service :ServiceId)
              :company-id (z/xml1-> service :ServiceNbr (z/attr :CompanyId))
              :number (z/xml1-> service :ServiceNbr (z/attr :ServiceNbr))
              :name (z/xml1-> service :ServiceNbr (z/attr :Name))
              :variant (z/xml1-> service :ServiceNbr (z/attr :Variant))
              :mode-id (z/xml1-> service :ServiceTrnsmode (z/attr :TrnsmodeId))
              :validity-footnote-id (z/xml1-> service :ServiceValidity (z/attr :FootnoteId))
              :stop-sequence (z/xml-> service :Stop
                                      (fn [stop]
                                        {:stop-sequence (int-attr stop :Ix)
                                         :station-id (z/attr stop :StationId)
                                         :departure (z/attr stop :Departure)}))})))

(defn calendars
  "Return all calendars mapped by footnote id.
  For some reason Kalkati service calendars are called 'footnotes'."
  [root]
  (into {}
        (map (juxt :id identity))
        (z/xml-> root :Footnote
                 (fn [c]
                   (let [first-date (z/xml1-> c (z/attr :Firstdate) time/parse-date-iso-8601)
                         date-vector (z/attr c :Vector)]
                     (merge
                      {:id (z/attr c :FootnoteId)
                       :first-date first-date
                       :vector date-vector}
                      (when (and first-date date-vector)
                        {:dates (into #{}
                                      (remove
                                       nil?
                                       (map (fn [i valid?]
                                              (when (= \1 valid?)
                                                (.plusDays first-date i)))
                                            (range) date-vector)))})))))))

(defn companies-by-id [kz]
  (into {}
        (map (juxt :id identity))
        (z/xml-> kz :Company
                 (fn [c]
                   {:id (z/attr c :CompanyId)
                    :name (z/attr c :Name)}))))

(defn agency-txt [companies]
  (map (fn [{:keys [id name]}]
         {:gtfs/agency-id id
          :gtfs/agency-name name
          :gtfs/agency-url "http://example.com"
          :gtfs/agency-timezone "Europe/Helsinki"})
       (vals companies)))

(defn routes-txt [trans-modes routes]
  (map
   (fn [{:keys [id name mode-id company-id]}]
     (let [mode (trans-modes mode-id)]
       {:gtfs/route-id id
        :gtfs/agency-id company-id
        :gtfs/route-short-name ""
        :gtfs/route-long-name (str name
                                   (when (not= "N/A" (:name mode))
                                     (str " (" (:name mode) ")")))
        :gtfs/route-type (or (kalkati-mode->gtfs-mode (:type mode)) 3)}))
   routes))

(defn trips-txt [routes-with-trips]
  (mapcat
   (fn [{:keys [id trips]}]
     (mapcat
      (fn [[footnote-id trips]]
        (for [{:keys [service-id] :as trip} trips]
          {:gtfs/route-id id
           :gtfs/service-id footnote-id
           ;; Trip id is "t_<service-id>_<validity-footnote-id>"
           :gtfs/trip-id (str "t_" (:service-id trip) "_" footnote-id)}))
      (group-by :validity-footnote-id trips)))
   routes-with-trips))

(defn stops-txt [stations]
  (map (fn [{:keys [id name coordinate]}]
         {:gtfs/stop-id id
          :gtfs/stop-name name
          :gtfs/stop-lat (:y coordinate)
          :gtfs/stop-lon (:x coordinate)}) (vals stations)))

(defn routes-with-trips
  "Takes a list of kalkati routes and returns routes with ids and trips."
  [routes]
  (map
   (fn [i trips]
     (merge (select-keys (first trips) #{:company-id :number :name :mode-id})
            {:id (str "r_" i)
             :trips trips}))

   (drop 1 (range)) ; increasing id
   (vals (group-by (juxt :name :mode-id) routes))))

(defn calendar-dates-txt [calendars]
  (mapcat
   (fn [{:keys [id dates]}]
     (for [d dates]
       {:gtfs/service-id id
        :gtfs/date d
        :gtfs/exception-type "1"}))
   (vals calendars)))

(defn- gtfs-time [kalkati-time]
  (str (subs kalkati-time 0 2) ":" (subs kalkati-time 2)))

(defn stop-times-txt [routes-with-trips]
  (mapcat
   (fn [{:keys [id trips]}]
     (mapcat
      (fn [[footnote-id trips]]
        (mapcat
         (fn [{:keys [service-id stop-sequence] :as trip}]
           (for [{:keys [stop-sequence arrival departure station-id] :as stop} stop-sequence
                 :let [arr (when arrival (gtfs-time arrival))
                       dep (when departure (gtfs-time departure))]]
             {:gtfs/trip-id (str "t_" (:service-id trip) "_" footnote-id)
              :gtfs/arrival-time (or arr dep)
              :gtfs/departure-time (or dep arr)
              :gtfs/stop-sequence stop-sequence
              :gtfs/stop-id station-id}))
         trips))
      (group-by :validity-footnote-id trips)))
   routes-with-trips))

(defn kalkati->gtfs
  "Takes a parsed zipper of Kalkati.net XML and returns the equivalent
  contents as GTFS CSV-files. Returns a sequence of GTFS files as maps
  containing :name and :data keys."
  [kz]
  (let [trans-modes (trans-modes-by-id kz)
        routes (routes-with-trips (routes kz))

        stations (stations-by-id kz)

        calendars (calendars kz)]

    [{:name "agency.txt"
      :data (gtfs-parse/unparse-gtfs-file :gtfs/agency-txt (agency-txt (companies-by-id kz)))}
     {:name "routes.txt"
      :data (gtfs-parse/unparse-gtfs-file :gtfs/routes-txt (routes-txt trans-modes routes))}
     {:name "stops.txt"
      :data (gtfs-parse/unparse-gtfs-file :gtfs/stops-txt (stops-txt stations))}

     {:name "trips.txt"
      :data (gtfs-parse/unparse-gtfs-file :gtfs/trips-txt (trips-txt routes))}

     {:name "stop_times.txt"
      :data (gtfs-parse/unparse-gtfs-file :gtfs/stop-times-txt (stop-times-txt routes))}
     {:name "calendar_dates.txt"
      :data (gtfs-parse/unparse-gtfs-file :gtfs/calendar-dates-txt (calendar-dates-txt calendars))}]))

(defn kalkati->gtfs-zip-file
  "Takes a parsed zipper of Kalkati.net XML and an output stream.
  Writes the equivalent GTFS zip file to the output stream."
  [kz output]
  (try
    (zip-file/write-zip (kalkati->gtfs kz) output)
    (catch Exception e
      (log/error e "Kalkati.net to GTFS conversion failed."))))

(defn convert
  "Convert Kalkati.net zipped XML to GTFS zip file.
  Takes an input stream where the Kalkati.net zip can be read.
  Returns a piped input stream where the GTFS zip file can be read."
  [kalkati-zip-input]
  (let [kalkati-input-files (zip-file/read-zip kalkati-zip-input)
        lvm-xml (some #(when (= (:name %) "LVM.xml") %) kalkati-input-files)]
    (when-not lvm-xml
      (throw (ex-info "Unable to find LVM.xml file in zip input"
                      {:file-names (mapv :name kalkati-input-files)})))

    ;; Load the XML into a zip tree for easy parsing
    (let [kz (kalkati-zipper (java.io.ByteArrayInputStream. (.getBytes (:data lvm-xml) "UTF-8")))]
      (ring-io/piped-input-stream
       #(kalkati->gtfs-zip-file kz %)))))
