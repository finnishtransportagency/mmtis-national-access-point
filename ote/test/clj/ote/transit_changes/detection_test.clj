(ns ote.transit-changes.detection-test
  (:require [ote.transit-changes.detection :as detection]
            [clojure.test :as t :refer [deftest testing is]]
            [clojure.spec.test.alpha :as spec-test]
            [ote.transit-changes :as transit-changes]))

(defn d [year month day]
  (java.time.LocalDate/of year month day))

(def route-name ["TST" "Testington - Terstersby" "Testersby"])
(def route-name-2 ["KEK" "Keskington - Kerskersby" "Keskersby"])

(defn weeks
  "Give first day of week (monday) as a starting-from."
  [starting-from & route-maps]
  (vec (map-indexed
        (fn [i routes]
          {:beginning-of-week (.plusDays starting-from (* i 7))
           :end-of-week (.plusDays starting-from (+ 6 (* i 7)))
           :routes routes}) route-maps)))


(def test-no-traffic-run
  (weeks (d 2018 10 8)
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name ["h1" "h2" nil nil nil nil nil]} ; 4 day run
         {route-name [nil nil nil nil nil nil nil]} ; 7 days
         {route-name [nil nil nil nil nil "h6" "h7"]} ; 4 days => sum 17
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}))

(deftest no-traffic-run-is-detected
  (is (= {:no-traffic-start-date (d 2018 10 17)
          :no-traffic-end-date (d 2018 11 3)}
         (-> (detection/first-week-difference test-no-traffic-run)
             (get route-name)
             (select-keys [:no-traffic-start-date :no-traffic-end-date])))))

(def test-no-traffic-run-weekdays
  (weeks (d 2018 10 8)
         {route-name ["h1" "h2" "h3" "h4" "h5" nil nil]} ;; 8.10.
         {route-name ["h1" "h2" "h3" "h4" "h5" nil nil]} ;; 15.10.
         {route-name ["h1" "h2" "h3" "h4" "h5" nil nil]} ;; 22.10.
         {route-name ["h1" "h2" "h3" "h4" "h5" nil nil]} ;; 29.10.
         {route-name ["h1" "h2" "h3" "h4" "h5" nil nil]} ;; 5.11.
         {route-name ["h1" nil nil nil nil nil nil]} ; 6 day run
         {route-name [nil nil nil nil nil nil nil]} ; 7 days
         {route-name [nil nil nil nil "h5" nil nil]} ; 4 days => sum 17
         {route-name ["h1" "h2" "h3" "h4" "h5" nil nil]}
         {route-name ["h1" "h2" "h3" "h4" "h5" nil nil]}
         {route-name ["h1" "h2" "h3" "h4" "h5" nil nil]}))

(deftest no-traffic-run-weekdays-is-detected
  ;; Test that traffic that has normal "no-traffic" days (like no traffic on weekends)
  ;; is still detected.
  (is (= {:no-traffic-start-date (d 2018 11 13)
          :no-traffic-end-date (d 2018 11 30)}
         (-> (detection/first-week-difference test-no-traffic-run-weekdays)
             (get route-name)
             (select-keys [:no-traffic-start-date :no-traffic-end-date])))))

(def no-traffic-run-full-detection-window
  (weeks (d 2018 10 8)
         {route-name ["h1" "h2" "h3" "h4" "h5" nil nil]} ;; 8.10.
         {route-name ["h1" "h2" "h3" "h4" "h5" nil nil]} ;; 15.10.
         {route-name ["h1" "h2" "h3" "h4" "h5" nil nil]} ;; 22.10.
         {route-name ["h1" "h2" "h3" "h4" "h5" nil nil]} ;; 29.10.
         {route-name ["h1" "h2" "h3" "h4" "h5" nil nil]} ;; 5.11.
         {route-name ["h1" nil nil nil nil nil nil]} ; 6 day run
         {route-name [nil nil nil nil nil nil nil]} ; 7 days
         {route-name [nil nil nil nil nil nil nil]}
         {route-name [nil nil nil nil nil nil nil]}
         {route-name [nil nil nil nil nil nil nil]}
         {route-name [nil nil nil nil nil nil nil]}
         {route-name [nil nil nil nil nil nil nil]}
         {route-name [nil nil nil nil nil nil nil]}
         {route-name [nil nil nil nil nil nil nil]}
         {route-name [nil nil nil nil nil nil nil]}
         {route-name [nil nil nil nil nil nil nil]}
         {route-name [nil nil nil nil nil nil nil]}
         {route-name [nil nil nil nil nil nil nil]}))

(deftest test-no-traffic-run-full-detection-window
  ;; Test that traffic that has normal "no-traffic" days (like no traffic on weekends)
  ;; is still detected.
  (let [result (-> (detection/first-week-difference no-traffic-run-full-detection-window)
                   (get route-name))]
    (is (= {:no-traffic-start-date (d 2018 11 13)}
           (select-keys result [:no-traffic-start-date :no-traffic-end-date])))))

(def test-traffic-2-different-weeks
  (weeks (d 2018 10 8)
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ; starting point
         {route-name ["h1" "h2" "!!" "h4" "h5" "h6" "h7" ]} ; wednesday different
         {route-name ["h1" "h2" "h3" "!!" "h5" "h6" "h7" ]} ; thursday different
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}  ; back to normal
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}))

(deftest two-week-difference-is-skipped
  (is (nil?
       (get-in (detection/first-week-difference test-traffic-2-different-weeks)
               [route-name :different-week]))))

(def normal-to-1-different-to-1-normal-and-rest-are-changed
  (weeks (d 2019 1 28)
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ; prev week
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ; starting week
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ; normal
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ; normal
         {route-name ["!!" "!!" "!!" "!!" "!!" "h6" "h7"]} ; first different week - should be skipper
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ; back to normal
         {route-name ["h1" "h2" "h3" "h4" "!!" "h6" "!!"]} ; new schedule - should be found as different week
         {route-name ["h1" "h2" "h3" "h4" "!!" "h6" "!!"]} ; new schedule
         {route-name ["h1" "h2" "h3" "h4" "!!" "h6" "!!"]} ; New schedule
         {route-name ["h1" "h2" "h3" "h4" "!!" "h6" "!!"]})); New schedule

(deftest one-week-difference-is-skipped
  (let [result (detection/first-week-difference normal-to-1-different-to-1-normal-and-rest-are-changed)]
    (is (= {:beginning-of-week (d 2019 3 11)
            :end-of-week (d 2019 3 17)}
           (get-in result [route-name :different-week])))))


(def test-traffic-normal-difference
  (weeks (d 2018 10 8)
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ; starting point
         {route-name ["h1" "h2" "!!" "h4" "h5" "h6" "h7" ]} ; wednesday different
         {route-name ["h1" "h2" "h3" "!!" "h5" "h6" "h7" ]} ; thursday different
         {route-name ["h1" "h2" "h3" "h4" "!!" "h6" "h7"]} ; friday different
         {route-name ["h1" "h2" "h3" "!!" "!!" "h6" "h7"]})) ;; thu and fri different

(deftest normal-difference
  (is (= {:starting-week-hash ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]
          :starting-week {:beginning-of-week (d 2018 10 15)
                          :end-of-week (d 2018 10 21)}
          :different-week-hash  ["h1" "h2" "!!" "h4" "h5" "h6" "h7"]
          :different-week {:beginning-of-week (d 2018 10 22)
                           :end-of-week (d 2018 10 28)}}
         (get (detection/first-week-difference test-traffic-normal-difference) route-name))))


(def test-traffic-starting-point-anomalous
  (weeks (d 2018 10 8)
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name ["h1!" "h2!" "h3!" "h4!" "h5!" "h5!" "h7!"]} ; starting week is an exception
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ; next week same as previous
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}))

(deftest anomalous-starting-point-is-ignore
  (let [{:keys [starting-week different-week] :as res}
        (get (detection/first-week-difference test-traffic-starting-point-anomalous) route-name)]
    (is (= (d 2018 10 22) (:beginning-of-week starting-week)))
    (is (nil? different-week))))

(def test-traffic-static-holidays
  (weeks (d 2018 12 3)
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 3.12
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 10.12
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 17.12
         {route-name [:xmas-eve :xmas-day "h3" "h4" "h5" "h6" "h7"]} ; Week having static-holidays - 24.12
         {route-name ["h1" :new-year "h3!" "h4!" "h5!" "h6!" "h7!"]} ; Week having static-holidays (1.1.) 31.12
         {route-name ["h1!" "h2!" "h3!" "h4!" "h5!" "h6!" "h7!"]} ;; 7.1
         {route-name ["h1!" "h2!" "h3!" "h4!" "h5!" "h6!" "h7!"]} ;; 14.1
         {route-name ["h1!" "h2!" "h3!" "h4!" "h5!" "h6!" "h7!"]})) ;; 21.1

(deftest static-holidays-are-skipped
  (let [{:keys [starting-week different-week] :as res}
        (get (detection/first-week-difference test-traffic-static-holidays) route-name)]

    (testing "detection skipped christmas week"
      (is (= (d 2018 12 31) (:beginning-of-week different-week))))

    (testing "first different day is wednesday because tuesday is new year"
      (is (= 2 (transit-changes/first-different-day (:starting-week-hash res) (:different-week-hash res)))))))


(def test-more-than-one-change  
  ; Produce change records about individual days -> first change week contains 2 days with differences
  ; In this test case we need to produce 3 rows in the database
  (weeks (d 2019 2 4)
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 4.2.
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; first current week (11.2.)
         {route-name ["h1" "!!" "!!" "h4" "h5" "h6" "h7"]} ;; 18.2. first change -> only one found currently | needs to detect change also on 13.2. -> this is set to current week
         {route-name ["h1" "!!" "!!" "h4" "h5" "h6" "h7"]} ;; no changes here (25.2.)
         {route-name ["h1" "h2" "!!" "h4" "h5" "h6" "h7"]} ;; Only tuesday is found (4.3.)
         {route-name ["h1" "h2" "!!" "h4" "h5" "h6" "h7"]} ;;
         {route-name ["h1" "h2" "!!" "h4" "h5" "h6" "h7"]} ;;
         {route-name ["h1" "h2" "!!" "h4" "h5" "h6" "h7"]}))

(deftest more-than-one-change-found
  (spec-test/instrument `detection/first-week-difference)

  ;; first test that the test data and old change detection code agree
  (testing "single-change detection code agrees with test data"
    (is (= (d 2019 2 18) (-> test-more-than-one-change
                             detection/first-week-difference
                             (get route-name)
                             :different-week
                             :beginning-of-week))))
  
  (let [diff-pairs (detection/week-difference-pairs test-more-than-one-change)]
    (testing "got two changes"
      (is (= 2 (count diff-pairs))))
    (testing "first change is detected"
      (is (= (d 2019 2 18) (-> diff-pairs
                            first
                            (get route-name)
                            :different-week
                            :beginning-of-week))))

    (testing "second change is detected"
      (is (= (d 2019 2 25) (-> diff-pairs
                               second
                               (get route-name)
                               :different-week
                               :beginning-of-week))))))

