(ns ote.services.transport-test
  (:require  [clojure.test :as t :refer [deftest testing is]]
             [ote.test :refer [system-fixture http-get http-post]]
             [clojure.java.jdbc :as jdbc]
             [clojure.test.check :as tc]
             [clojure.test.check.generators :as gen]
             [clojure.test.check.clojure-test :refer [defspec]]
             [clojure.test.check.properties :as prop]
             [ote.components.db :as db]
             [com.stuartsierra.component :as component]
             [ote.components.http :as http]
             [ote.services.transport :as transport-service]
             [ote.db.transport-operator :as t-operator]
             [ote.db.transport-service :as t-service]
             [ote.db.common :as common]
             [clojure.spec.gen.alpha :as sgen]
             [clojure.spec.test.alpha :as stest]
             [clojure.spec.alpha :as s]
             [ote.db.generators :as generators]
             [ote.db.service-generators :as s-generators]
             [clojure.string :as str]
             [clojure.set :as set]))


(t/use-fixtures :each
  (system-fixture
   :transport (component/using
                (transport-service/->Transport
                  (:nap nil))
                [:http :db])))


;; We have a single transport service inserted in the test data,
;; check that its information is fetched ok
(deftest fetch-test-data-transport-service
  (let [ts (http-get "admin" "transport-service/1")
        service (:transit ts)
        ps (::t-service/passenger-transportation service)]
    (is (= (::t-service/contact-address service)
           #::common {:street "Street 1" :postal_code "90100" :post_office "Oulu"}))
    (is (= (::t-service/price-classes ps)
           [#:ote.db.transport-service{:name "starting",
                                       :price-per-unit 5.9M,
                                       :unit "trip"}
            #:ote.db.transport-service{:name "basic fare",
                                       :price-per-unit 4.9M,
                                       :unit "km"}]))

    (is (= (::t-service/homepage service) "www.solita.fi"))
    (is (= (::t-service/contact-phone service) "123456"))))

(defn- non-nil-keys [m]
  (into #{}
        (keep (fn [[k v]]
                (when (and (not (nil? v))
                           (or (not (string? v))
                               (not (str/blank? v))))
                  k)))
        m))

(defn effectively-same-deep
  "Compare that nested structures are effectively the same (insertion vs specql fetch).
  Maps and vectors are compared in a nested way.
  Ignores keys with nil values and empty strings (for composites)."
  [v1 v2]

  (cond
    ;; Both values are maps, check that they have the same non-nil keys
    (and (map? v1) (map? v2))
    (let [keys-v1 (non-nil-keys v1)
          keys-v2 (non-nil-keys v2)]
      (if-not (= keys-v1 keys-v2)
        (do (println "Maps don't have the same non-empty keys"
                     (when-let [not-in-v1 (seq (set/difference keys-v2 keys-v1))]
                       (str ", not in left: " (str/join ", " not-in-v1)))
                     (when-let [not-in-v2 (seq (set/difference keys-v1 keys-v2))]
                       (str ", not in right: " (str/join ", " not-in-v2))))
            false)
        (and (= keys-v1 keys-v2)
             (every? #(let [result (effectively-same-deep (get v1 %)
                                                          (get v2 %))]
                        (when-not result
                          (println "Values for key" % "are not the same!"
                                   "\nV1:" (pr-str v1)
                                   "\nV2:" (pr-str v2)))
                        result)
                     keys-v1))))

    ;; Both values are vectors, check that values at every index are the same
    (and (vector? v1) (vector? v2))
    (if-not (= (count v1) (count v2))
      (do
        (println "Vector lengths differ:" (count v1) "!=" (count v2)
                 "\nV1:" (pr-str v1)
                 "\nV2:" (pr-str v2))
        false)
      (every? true?
              (map (fn [left right]
                     (let [result (effectively-same-deep left right)]
                       (when-not result
                         (println "Vector values are not the same!"
                                  "\nV1:" (pr-str v1)
                                  "\nV2:" (pr-str v2)))
                       result))
                   v1 v2)))

    ;; Other values, just compare
    :default
    (if-not (= v1 v2)
      (do (println "Values are not the same: " v1 " != " v2) false)
      true)))

(defspec save-and-fetch-generated-passenger-transport-service
  50
  (prop/for-all
   [transport-service s-generators/gen-passenger-transportation-service]

   (let [response (http-post "admin" "transport-service"
                             transport-service)
         service (:transit response)
         fetch-response (http-get "admin"
                                  (str "transport-service/" (::t-service/id service)))

         fetched (:transit fetch-response)]

     (and (= (:status response) (:status fetch-response) 200)
          (effectively-same-deep
           (::t-service/passenger-transportation service)
           (::t-service/passenger-transportation fetched))))))

(deftest save-terminal-service-to-wrong-operator
  (let [generated-terminal-service (gen/generate s-generators/gen-terminal-service)
        modified-terminal-service (assoc generated-terminal-service ::t-service/transport-operator-id 2)
        response (http-post "normaluser" "transport-service" modified-terminal-service)
        service (:transit response)
        ;; GET generated service from server
        terminal-service (http-get "normaluser" (str "transport-service/" (::t-service/id service)))
        fetched (:transit terminal-service)
        ;; Change operator id from 2 -> 1 - which will cause error
        problematic-terminal-service (assoc fetched ::t-service/transport-operator-id 1)]

    ;; Gives error, because user doesn't have access rights to operator 1
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo #"status 403"
          (http-post "normaluser" "transport-service" problematic-terminal-service)))))
