(ns ote.transit-changes.detection-test
  (:require [ote.transit-changes.detection :as detection]
            [clojure.test :as t :refer [deftest testing is]]
            [clojure.spec.test.alpha :as spec-test]
            [ote.transit-changes :as transit-changes]
            [ote.time :as time]))

(defn d [year month day]
  (java.time.LocalDate/of year month day))

(def route-name "Raimola")
(def route-name-2 "Esala")

(defn weeks
  "Give first day of week (monday) as a starting-from."
  [starting-from & route-maps]
  (vec (map-indexed
        (fn [i routes]
          {:beginning-of-week (.plusDays starting-from (* i 7))
           :end-of-week (.plusDays starting-from (+ 6 (* i 7)))
           :routes routes})
        route-maps)))


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
         (-> (detection/route-weeks-with-first-difference-new test-no-traffic-run)
             first
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
         (-> (detection/route-weeks-with-first-difference-new test-no-traffic-run-weekdays)
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
  (let [result (-> (detection/route-weeks-with-first-difference-new no-traffic-run-full-detection-window)
                   first)]
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
       (get-in (detection/route-weeks-with-first-difference-new test-traffic-2-different-weeks)
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
  (let [result (detection/route-weeks-with-first-difference-new normal-to-1-different-to-1-normal-and-rest-are-changed)]
    (is (= {:beginning-of-week (d 2019 3 11)
            :end-of-week (d 2019 3 17)}
           (:different-week (first result))
           ))))


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
                           :end-of-week (d 2018 10 28)}
          :route-key "Raimola"}
         (first (detection/route-weeks-with-first-difference-new test-traffic-normal-difference)))))


(def test-traffic-starting-point-anomalous
  (weeks (d 2018 10 8)
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name ["h1!" "h2!" "h3!" "h4!" "h5!" "h5!" "h7!"]} ; starting week is an exception
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ; next week same as previous
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}))

(deftest anomalous-starting-point-is-ignored
  (let [{:keys [starting-week different-week] :as res}
        (-> test-traffic-starting-point-anomalous
            detection/route-weeks-with-first-difference-new
            first)]
    (is (= (d 2018 10 22) (:beginning-of-week starting-week)))
    (is (= "Raimola" (:route-key res)))
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
        (first (detection/route-weeks-with-first-difference-new test-traffic-static-holidays))]

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


(def test-more-than-one-change-2-routes
  ; Produce change records about individual days -> first change week contains 2 days with differences
  ; In this test case we need to produce 3 rows in the database
  (weeks (d 2019 2 4)
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]
        route-name-2 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 4.2.
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]
        route-name-2 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; first current week (11.2.)
         {route-name ["h1" "!!" "!!" "h4" "h5" "h6" "h7"]
        route-name-2 ["h1" "!!" "!!" "h4" "h5" "h6" "h7"]} ;; 18.2. first change -> only one found currently | needs to detect change also on 13.2. -> this is set to current week
         {route-name ["h1" "!!" "!!" "h4" "h5" "h6" "h7"]
        route-name-2 ["h1" "!!" "!!" "h4" "h5" "h6" "h7"]} ;; no changes here (25.2.)
         {route-name ["h1" "h2" "!!" "h4" "h5" "h6" "h7"]
        route-name-2 ["h1" "h2" "!!" "h4" "h5" "h6" "h7"]} ;; Only tuesday is found (4.3.)
         {route-name ["h1" "h2" "!!" "h4" "h5" "h6" "h7"]
        route-name-2 ["h1" "h2" "!!" "h4" "h5" "h6" "h7"]} ;;
         {route-name ["h1" "h2" "!!" "h4" "h5" "h6" "h7"]
        route-name-2 ["h1" "h2" "!!" "h4" "h5" "h6" "h7"]} ;;
         {route-name ["h1" "h2" "!!" "h4" "h5" "h6" "h7"]
        route-name-2 ["h1" "h2" "!!" "h4" "h5" "h6" "h7"]}))

(def data-two-week-change
  (weeks (d 2019 2 4)
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 4.2.
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; first current week (11.2.)
         {route-name ["h1" "!!" "!!" "h4" "h5" "h6" "h7"]}
         {route-name ["h1" "!!" "!!" "h4" "h5" "h6" "h7"]}
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}))

(deftest more-than-one-change-found
  (spec-test/instrument `detection/route-weeks-with-first-difference-old)

  ;; first test that the test data and old change detection code agree
  (testing "single-change detection code agrees with test data"
    (is (= (d 2019 2 18) (-> test-more-than-one-change
                             detection/route-weeks-with-first-difference-new
                             first
                             :different-week
                             :beginning-of-week))))
  
  (let [diff-pairs (-> test-more-than-one-change
                       detection/changes-by-week->changes-by-route
                       detection/detect-changes-for-all-routes)
        ;; diff-pairs (detection/routes-changed-weeks test-more-than-one-change)
        old-diff-pairs (detection/route-weeks-with-first-difference-old test-more-than-one-change)]
    (testing "got two changes"
      (is (= 2 (count diff-pairs))))
    (testing "first change is detected"
      (is (= (d 2019 2 18) (-> diff-pairs
                               first
                               :different-week
                               :beginning-of-week))))

    (testing "second change is detected"
      (is (= (d 2019 3 4) (-> diff-pairs
                              second
                              :different-week
                              :beginning-of-week))))))


(def data-two-week-two-route-change                         ;This is the same format as the (combine-weeks) function
  (weeks (d 2019 2 4)
         {route-name   ["h1" "h2" "h3" "h4" "h5" "h6" "h7"] route-name-2 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name   ["h1" "h2" "h3" "h4" "h5" "h6" "h7"] route-name-2 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ; 11.2. prev week start
         {route-name   ["h1" "##" "h3" "h4" "h5" "h6" "h7"] route-name-2 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ; 18.2. first change in route1
         {route-name   ["h1" "##" "h3" "h4" "h5" "h6" "h7"] route-name-2 ["h1" "h2" "##" "h4" "h5" "h6" "h7"]}
         {route-name   ["h1" "##" "h3" "h4" "h5" "h6" "h7"] route-name-2 ["h1" "h2" "##" "h4" "h5" "h6" "h7"]}
         {route-name   ["h1" "##" "h3" "h4" "h5" "h6" "h7"] route-name-2 ["h1" "h2" "##" "h4" "h5" "h6" "h7"]}
         {route-name   ["h1" "h2" "h3" "h4" "h5" "h6" "h7"] route-name-2 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name   ["h1" "h2" "h3" "h4" "h5" "h6" "h7"] route-name-2 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name   ["h1" "h2" "h3" "h4" "h5" "h6" "h7"] route-name-2 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}))

(def seppo
  (weeks (d 2019 2 4)
         {route-name   ["h1" "h2" "h3" "h4" "h5" "h6" "h7"] route-name-2 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name   ["h1" "h2" "h3" "h4" "h5" "h6" "h7"] route-name-2 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ; 11.2. prev week start
         {route-name   ["h1" "##" "h3" "h4" "h5" "h6" "h7"] route-name-2 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ; 18.2. first change in route1
         {route-name   ["h1" "##" "h3" "h4" "h5" "h6" "h7"] route-name-2 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name   ["h1" "##" "h3" "h4" "h5" "h6" "h7"] route-name-2 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name   ["h1" "h2" "h3" "h4" "h5" "h6" "h7"] route-name-2 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name   ["h1" "h2" "h3" "h4" "h5" "h6" "h7"] route-name-2 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name   ["h1" "h2" "h3" "h4" "h5" "h6" "h7"] route-name-2 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}))


(def data-with-pause
  (weeks (d 2019 2 4)
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 4.2.
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; first current week (11.2.)
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}  ;; 18.2
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}  ;; 11.3.
         {route-name [nil nil nil nil nil nil nil]}         ;; 18.3.
         {route-name [nil nil nil nil nil nil nil]}         ;; 25.3.
         {route-name [nil nil nil nil nil nil nil]}         ;; 1.4.
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}  ;; 8.4.
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}  ;; 15.4.
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]})) ;; 22.4.


(def no-traffic
  (weeks (d 2019 2 4)
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 4.2.
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; first current week (11.2.)
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}))

(def differences
  (weeks (d 2019 2 4)
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 4.2.
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; first current week (11.2.)
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}  ;; 18.2.
         {route-name [nil nil nil nil nil nil nil]}         ;; 25.2.
         {route-name [nil nil nil nil nil nil nil]}         ;; 4.3.
         {route-name [nil nil nil nil nil nil nil]}         ;; 11.3.
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}  ;; 18.3.
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}  ;; 25.3.
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}  ;; 1.4.
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}  ;; 8.4.
         {route-name ["h1" "h2" "!!" "h4" "h5" "h6" "h7"]}  ;; 15.4.
         {route-name ["h1" "h2" "!!" "h4" "h5" "h6" "h7"]}  ;;22.4.
         {route-name ["h1" "h2" "!!" "h4" "h5" "h6" "h7"]}  ;;29.4.
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}  ;;6.5.
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}  ;; 13.5.
         {route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}))

(deftest more-than-one-change-found-w-2-routes
  (let [diff-pairs (-> data-two-week-two-route-change
                       (detection/changes-by-week->changes-by-route)
                       (detection/detect-changes-for-all-routes))

        fwd-difference-old (detection/route-weeks-with-first-difference-old data-two-week-two-route-change)
        fwd-difference-new (detection/route-weeks-with-first-difference-new data-two-week-two-route-change)]
    (testing "first change matches first-week-difference return value"
      (is (= (-> fwd-difference-old (get "Raimola") :different-week) (-> diff-pairs first :different-week)))
      (is (= (-> fwd-difference-new first :different-week) (-> diff-pairs first :different-week))))

    (testing "second route's first change date is ok"
      (is (= (d 2019 2 25)
             (first
              (for [dp diff-pairs
                    :let [d (-> dp :different-week :beginning-of-week)]
                    :when (= "Esala" (:route-key dp))]
                d)))))))


(deftest no-change-found
  (spec-test/instrument `detection/route-weeks-with-first-difference-new)

  (let [diff-pairs (-> data-two-week-change
                       detection/changes-by-week->changes-by-route
                       detection/detect-changes-for-all-routes)
        pairs-with-changes (filterv :different-week diff-pairs)]
    (testing "got no changes"
      (is (= 0 (count pairs-with-changes))))))


; Dev tip: Put *symname in ns , evaluate, load, run (=define) and inspect in REPL
; Fetched from routes like below
; <snippet>
; (map
;  #(update % :routes select-keys [route-name])
;  *res)
; </snippet>

(def data-realworld-two-change-case
  [{:beginning-of-week (java.time.LocalDate/parse "2019-02-18"),
    :end-of-week (java.time.LocalDate/parse "2019-02-24"),
    :routes {route-name [nil nil nil nil nil nil "hkolmas"]}}
   {:beginning-of-week (java.time.LocalDate/parse "2019-02-25"),
    :end-of-week (java.time.LocalDate/parse "2019-03-03"),
    :routes {route-name ["heka" "heka" "heka" "heka" "heka" "htoka" "hkolmas"]}}
   {:beginning-of-week (java.time.LocalDate/parse "2019-03-04")
    :end-of-week (java.time.LocalDate/parse "2019-03-10"),
    :routes {route-name ["heka" "heka" "heka" "heka" "heka" "htoka" "hkolmas"]}}
   {:beginning-of-week (java.time.LocalDate/parse "2019-03-11"),
    :end-of-week (java.time.LocalDate/parse "2019-03-17"),
    :routes {route-name ["heka" "heka" "heka" "heka" "heka" "htoka" "hkolmas"]}}
   {:beginning-of-week (java.time.LocalDate/parse "2019-03-18"),
    :end-of-week (java.time.LocalDate/parse "2019-03-24"),
    :routes {route-name ["heka" "heka" "heka" "heka" "heka" "htoka" "hkolmas"]}}
   {:beginning-of-week (java.time.LocalDate/parse "2019-03-25"),
    :end-of-week (java.time.LocalDate/parse "2019-03-31"),
    :routes {route-name ["heka" "heka" "heka" "heka" "heka" "htoka" "hkolmas"]}}
   {:beginning-of-week (java.time.LocalDate/parse "2019-04-01"),
    :end-of-week (java.time.LocalDate/parse "2019-04-07"),
    :routes {route-name ["heka" "heka" "heka" "heka" "heka" "htoka" "hkolmas"]}}
   {:beginning-of-week (java.time.LocalDate/parse "2019-04-08"),
    :end-of-week (java.time.LocalDate/parse "2019-04-14"),
    :routes {route-name ["heka" "heka" "heka" "heka" "heka" "htoka" "hkolmas"]}}
   {:beginning-of-week (java.time.LocalDate/parse "2019-04-15"),
    :end-of-week (java.time.LocalDate/parse "2019-04-21"),
    :routes {route-name ["heka" "heka" "heka" "heka" "hkolmas" "htoka" "hkolmas"]}}
   {:beginning-of-week (java.time.LocalDate/parse "2019-04-22"),
    :end-of-week (java.time.LocalDate/parse "2019-04-28"),
    :routes {route-name ["hkolmas" "heka" "heka" "heka" "heka" "htoka" "hkolmas"]}}
   {:beginning-of-week (java.time.LocalDate/parse "2019-04-29"),
    :end-of-week (java.time.LocalDate/parse "2019-05-05"),
    :routes {route-name ["heka" "heka" "heka" "heka" "heka" "htoka" "hkolmas"]}} ;; In data third value was :first-of-may
   {:beginning-of-week (java.time.LocalDate/parse "2019-05-06"),
    :end-of-week (java.time.LocalDate/parse "2019-05-12"),
    :routes {route-name ["heka" "heka" "heka" "heka" "heka" "htoka" "hkolmas"]}}
   {:beginning-of-week (java.time.LocalDate/parse "2019-05-13"),
    :end-of-week (java.time.LocalDate/parse "2019-05-19"),
    :routes {route-name ["heka" "heka" "heka" "heka" "heka" "htoka" "hkolmas"]}}
   {:beginning-of-week (java.time.LocalDate/parse "2019-05-20"),
    :end-of-week (java.time.LocalDate/parse "2019-05-26"),
    :routes {route-name ["heka" "heka" "heka" "heka" "heka" "htoka" "hkolmas"]}}
   {:beginning-of-week (java.time.LocalDate/parse "2019-05-27"), ;; first change 
    :end-of-week (java.time.LocalDate/parse "2019-06-02"),
    :routes {route-name ["heka" "hkolmas" nil nil nil nil nil]}} 
   {:beginning-of-week (java.time.LocalDate/parse "2019-06-03"),
    :end-of-week (java.time.LocalDate/parse "2019-06-09"),
    :routes {route-name ["hneljas" "hneljas" "hneljas" "hneljas" "hneljas" nil nil]}}
   {:beginning-of-week (java.time.LocalDate/parse "2019-06-10"),
    :end-of-week (java.time.LocalDate/parse "2019-06-16"),
    :routes {route-name ["hneljas" "hneljas" "hneljas" "hneljas" "hneljas" nil nil]}}
   {:beginning-of-week (java.time.LocalDate/parse "2019-06-17"),
    :end-of-week (java.time.LocalDate/parse "2019-06-23"),
    :routes {route-name ["hneljas" "hneljas" "hneljas" "hneljas" nil nil nil]}}


   ;; padding added
   {:beginning-of-week (java.time.LocalDate/parse "2019-06-24"),
    :end-of-week (java.time.LocalDate/parse "2019-06-30"),
    :routes {route-name ["hneljas" "hneljas" "hneljas" "hneljas" nil nil nil]}}

   {:beginning-of-week (java.time.LocalDate/parse "2019-07-01"),
    :end-of-week (java.time.LocalDate/parse "2019-07-01"),
    :routes {route-name ["hneljas" "hneljas" "hneljas" "hneljas" nil nil nil]}}

   {:beginning-of-week (java.time.LocalDate/parse "2019-07-08"),
    :end-of-week (java.time.LocalDate/parse "2019-07-14"),
    :routes {route-name ["hneljas" "hneljas" "hneljas" "hneljas" nil nil nil]}}])

;; this doesn't compile or work in normal "lein test" run due to the ote.main/ote reference, uncomment for repl use
#_(deftest test-with-gtfs-package-of-a-service
  (let [db (:db ote.main/ote)
        route-query-params {:service-id 5 :start-date (time/parse-date-eu "18.02.2019") :end-date (time/parse-date-eu "06.07.2019")}
        new-diff (detection/detect-route-changes-for-service-new db route-query-params)
        old-diff (detection/detect-route-changes-for-service-old db route-query-params)]
    ;; new-diff structure:
    ;; :route-changes [ ( [ routeid {dada} ]) etc
    (println (:start-date route-query-params))
    (testing "differences between new and old"
      (is (= old-diff new-diff)))))

(deftest more-than-one-change-found-case-2
  (spec-test/instrument `detection/route-weeks-with-first-difference-new)

  ;; first test that the test data and old change detection code agree
  (testing "single-change detection code agrees with test data"
    (is (= (d 2019 5 27) (-> data-realworld-two-change-case
                             detection/route-weeks-with-first-difference-new
                             first
                             :different-week
                             :beginning-of-week))))

  (let [diff-pairs (-> data-realworld-two-change-case
                       detection/changes-by-week->changes-by-route
                       detection/detect-changes-for-all-routes)
        old-diff-pair (-> data-realworld-two-change-case
                          detection/route-weeks-with-first-difference-old)]
    (testing "got two changes"
      (is (= 2 (count diff-pairs))))
    
    (testing "first change is detected"
      (is (= (d 2019 5 27) (-> diff-pairs first :different-week :beginning-of-week))))

    (testing "second change date is correct"
      (is (= (d 2019 6 3) (-> diff-pairs second :different-week :beginning-of-week))))))

