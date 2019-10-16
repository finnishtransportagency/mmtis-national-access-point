(ns ote.integration.export.geojson-test
  (:require [ote.integration.export.geojson :as geojson]
            [clojure.test :as t :refer [use-fixtures deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [ote.test :refer [system-fixture http-post http-get sql-execute!]]
            [com.stuartsierra.component :as component]
            [ote.services.transport :as transport-service]
            [ote.db.service-generators :as service-generators]
            [clojure.test.check.generators :as gen]
            [ote.db.transport-service :as t-service]
            [ote.db.places :as places]
            [ote.db.generators :as generators]
            [ote.integration.export.transform :as transform]
            [ote.time :as time]
            [webjure.json-schema.validator.macro :refer [make-validator]]
            [cheshire.core :as cheshire]))

(use-fixtures :each
  (system-fixture
   :transport (component/using (transport-service/->TransportService nil) [:http :db :email])
   :export-geojson (component/using (geojson/->GeoJSONExport (slurp "config.edn")) [:db :http])))

(defn interval-value [{:keys [years months days hours minutes seconds]}]
  [years months
   (+ seconds
      (* 60 minutes)
      (* 60 60 hours)
      (* 60 60 24 days))])

(defn- export-geojson [{::t-service/keys [id transport-operator-id]}]
  (is id "saved service has id")
  (is transport-operator-id "saved service has a transport-operator id")
  ;; Make it public so that GeoJSON export can return it
  (sql-execute! "UPDATE \"transport-service\" SET published = to_timestamp(0) where id = " id)
  (let [geojson-url (str "export/geojson/" transport-operator-id "/" id)
        json-response (http-get (:user-id-admin @ote.test/user-db-ids-atom)
                                geojson-url)]
    (is (= 200 (:status json-response)))
    (is (:json json-response))
    (:json json-response)))

(def test-operation-area [{::places/type "drawn"
                           ::places/namefin "Toriportti"
                           ::places/primary? true
                           :geojson "{\"type\":\"Point\",\"coordinates\":[25.468116,65.012489]}"}])

(deftest export-geojson-with-interval
  (let [service (assoc (gen/generate (service-generators/service-type-generator :parking))
                       ::t-service/operation-area test-operation-area)
        response (http-post (:user-id-admin @ote.test/user-db-ids-atom)
                            "transport-service"
                            service)
        id (get-in response [:transit ::t-service/id])]

    ;; Check that save is ok
    (is (pos? id))

    ;; Fetch exported GeoJSON
    (let [geojson (export-geojson (:transit response))
          maximum-stay (get-in geojson [:features 0 :properties :transport-service
                                        :parking :maximum-stay])]
      ;; Check that maximum stay interval has the same fields in JSON and Clojure interval type
      (is (= (interval-value (time/iso-8601-period->interval maximum-stay))
             (interval-value (get-in service [::t-service/parking ::t-service/maximum-stay])))))))

(deftest service-hours-export
  (testing "Service hours are exported properly and weekdays are sorted"
    (doseq [[type path] [[:passenger-transportation [::t-service/passenger-transportation ::t-service/service-hours]]
                         [:parking [::t-service/parking ::t-service/service-hours]]]
            :let [service (assoc (gen/generate (service-generators/service-type-generator type))
                                 ::t-service/operation-area test-operation-area)
                  saved-service (:transit (http-post (:user-id-admin @ote.test/user-db-ids-atom)
                                                     "transport-service"
                                                     service))
                  geojson (export-geojson saved-service)]]

      (let [generated-values (get-in service path)
            geojson-values (get-in geojson (into [:features 0 :properties :transport-service]
                                                 (map (comp keyword name) path)))]
        (is (= (count generated-values) (count geojson-values))
            "generated and exported has the same amount of service hours entries")
        (doall
         (map (fn [generated-value geojson-value]
                (is (= (into #{} (map name) (::t-service/week-days generated-value))
                       (into #{} (:week-days geojson-value)))
                    "generated and exported have the same week days")
                (is (apply <= (map (comp t-service/week-day-order keyword) (:week-days geojson-value)))
                    "weekdays are in order (monday first, sunday last)"))
              generated-values
              geojson-values))))))

(defspec maximum-stay-iso-8601-transform
  25
  (prop/for-all
   [{:keys [years months days hours minutes seconds] :as maximum-stay} generators/gen-interval]
   (let [transformed (transform/transform-deep {::t-service/maximum-stay (time/->PGInterval maximum-stay)})
         parsed (org.joda.time.Period/parse (::t-service/maximum-stay transformed))]
     ;; Check that the ISO-8601 period strings we generate are parsed back by Joda library and
     ;; contain the same field values
     (and
      (= years (.getYears parsed))
      (= months (.getMonths parsed))
      (= days (.getDays parsed))
      (= hours (.getHours parsed))
      (= minutes (.getMinutes parsed))
      ;; Joda period has separate field for milliseconds, so cast to int
      (= (int seconds) (.getSeconds parsed))))))

(defn- json*
  "Roundtrip data through cheshire to change keyword keys to strings (json-schema works w/ strings)."
  [data]
  (-> data cheshire/encode cheshire/decode))

(def geojson-validator
  (make-validator (json* (geojson/export-geojson-schema)) {}))

(defn valid-geojson? [geojson]
  (let [geojson (json* geojson)
        validation (geojson-validator geojson)]
    (nil? validation)))


(defspec validate-exported-geojson
  25
  (prop/for-all
   [generated-service service-generators/gen-transport-service]
   (let [service (assoc generated-service ::t-service/operation-area test-operation-area)
         response (http-post (:user-id-admin @ote.test/user-db-ids-atom)
                             "transport-service"
                             service)
         id (get-in response [:transit ::t-service/id])]
     (and (pos? id)
          (valid-geojson? (export-geojson (:transit response)))))))
