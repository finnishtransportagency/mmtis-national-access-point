(ns ote.transit-changes.detection-test-weeks
  (:require [ote.transit-changes.detection :as detection]
            [clojure.test :as t :refer [deftest testing is]]
            [clojure.spec.test.alpha :as spec-test]
            [ote.transit-changes :as transit-changes]
            [ote.transit-changes.detection-test-utilities :as tu]
            [ote.time :as time]
            [ote.config.transit-changes-config :as config-tc]))

;; Dev tip: Put *symname in ns , evaluate, load, run (=define) and inspect in REPL
;; Fetched from routes like below
;; <snippet>
;; (map
;;  #(update % :routes select-keys [tu/route-name])
;;  *res)
;; </snippet>

;;;;;;;; Test Ongoing traffic no changes

(def data-no-changes
  (tu/weeks (tu/to-local-date 2019 5 13) (tu/generate-traffic-week 9 ["A" "A" "A" "A" "A" "A" "A"] tu/route-name))) ;; Last week starts 2019 07 08

(deftest test-no-changes
  (let [all-routes (tu/create-data-all-routes (list '(2019 1 1) '(2019 12 31)))
        analysis-date (tu/to-local-date 2019 5 20)
        result (detection/traffic-week-maps->change-maps analysis-date all-routes data-no-changes)]

    (is (= {:route-key tu/route-name
            :change-type :no-change}
           (select-keys (first result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure ongoing traffic without changes is reported as no changes.")

    (is (= 1 (count result)) "Ensure that the right count of changes are found and there are no extra changes.")))

;;;;;;;; Test change on 2nd to last week

(def data-change-on-2nd-to-last-wk
  (tu/weeks (tu/to-local-date 2019 5 13)
            (concat (tu/generate-traffic-week 5)            ;; Last week starts 2019 06 17
                    [{tu/route-name ["h1" "h2" "h3" "h4" "h5" "!!" "h7"]}
                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" "!!" "h7"]}])))

(deftest test-change-on-2nd-to-last-wk
  (let [all-routes (tu/create-data-all-routes (list '(2019 5 13) '(2019 12 31)))
        analysis-date (tu/to-local-date 2019 5 20)
        result (detection/traffic-week-maps->change-maps analysis-date all-routes data-change-on-2nd-to-last-wk)]

    (is (= {:route-key tu/route-name
            :change-type :no-change}
           (select-keys (first result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure ongoing traffic without changes is reported as no changes.")

    (is (= 1 (count result)) "Ensure that the right count of changes are found and there are no extra changes.")))

;;;;;;;; Test traffic with nil weekend and no changes

(def data-no-changes-weekend-nil
  (tu/weeks (tu/to-local-date 2019 5 13)
            (tu/generate-traffic-week 9 ["h1" "h2" "h3" "h4" "h5" nil nil] tu/route-name))) ;; Last week starts 2019 06 24

(deftest test-no-changes-weekend-nil
  (let [all-routes (tu/create-data-all-routes (list '(2019 5 13) '(2019 12 31)))
        analysis-date (tu/to-local-date 2019 5 20)
        result (detection/traffic-week-maps->change-maps analysis-date all-routes data-no-changes-weekend-nil)]

    (is (= {:route-key tu/route-name
            :change-type :no-change}
           (select-keys (first result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure traffic with nil weekend and no changes, ensure :no-traffic is not reported\"")

    (is (= 1 (count result)) "Ensure that the right count of changes are found and there are no extra changes.")))

;;;;;;;; Test new route

(def data-route-starts-no-end
  (tu/weeks (tu/to-local-date 2019 4 15)
            (concat [{tu/route-name [nil nil nil nil nil nil nil]}
                     {tu/route-name [nil nil nil nil nil nil nil]} ; 2019-04-22
                     {tu/route-name [:holiday-nt :holiday-nt nil nil nil nil nil]}] ;; 2019-04-29
                    (tu/generate-traffic-week 25 [nil nil nil nil nil "h6" "h6"])))) ;; 2019-05-06

(deftest test-route-starts-no-end
  (let [all-routes (tu/create-data-all-routes (list '(2019 5 11) '(2019 12 1)))
        analysis-date (tu/to-local-date 2019 4 22)
        result (detection/traffic-week-maps->change-maps analysis-date all-routes data-route-starts-no-end)]

    (is (= {:change-type :added
            :change-date (tu/to-local-date 2019 5 11)
            :route-key tu/route-name}
           (select-keys (first result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure traffic with nil weekend and no changes, ensure :no-traffic is not reported\"")

    (is (= 1 (count result)) "Ensure that the right count of changes are found and there are no extra changes.")))

;;;;;;;; Test no traffic i.e. pause in traffic which is just above threshold

(def data-no-traffic-run
  (tu/weeks (tu/to-local-date 2018 10 8)
            (list {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
                  {tu/route-name ["h1" "h2" nil nil nil nil nil]} ;; 2018-10-15, 5 day run
                  {tu/route-name [nil nil nil nil nil nil nil]} ;; 2018-10-22, 7 days
                  {tu/route-name [nil nil nil nil nil "h6" "h7"]} ;; 2018-10-29, 5 days => sum 17
                  {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
                  {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
                  {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
                  {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]})))

(deftest test-no-traffic-run
  (let [all-routes (tu/create-data-all-routes (list '(2018 10 8) '(2019 1 30)))
        analysis-date (tu/to-local-date 2018 10 15)
        result (detection/traffic-week-maps->change-maps analysis-date all-routes data-no-traffic-run)]
    (is (= {:change-type :no-traffic
            :change-date (tu/to-local-date 2018 10 17)
            :no-traffic-start-date (tu/to-local-date 2018 10 17)
            :no-traffic-end-date (tu/to-local-date 2018 11 3)
            :route-key tu/route-name}
           (select-keys (first result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure no-traffic starts and ends middle of week and lasts just above threshold")

    (is (= 1 (count result)) "Ensure that the right count of changes are found and there are no extra changes.")))

;;;;;;;; Test two separate no traffic runs i.e. pauses in traffic
(deftest test-run-analysis-when-no-traffic-ongoing
  (let [analysis-date (tu/to-local-date 2018 10 15)
        all-routes (tu/create-data-all-routes (list '(2018 11 5) '(2019 2 28)))
        test-data (tu/weeks (tu/to-local-date 2018 10 8)
                            (concat [{tu/route-name [nil nil nil nil nil nil nil]}
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2018-10-15
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2018-10-22
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2018-10-29
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2018-11-05
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2018-11-23
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2018-11-29
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2018-11-26
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2018-12-03
                                     {tu/route-name [nil nil nil nil nil nil nil]}] ;; 2018-12-10
                                    (tu/generate-traffic-week 6 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"])))
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]

    (is (= {:change-type :added
            :change-date (tu/to-local-date 2018 11 5)
            :route-key tu/route-name}
           (select-keys (first result) tu/select-keys-detect-changes-for-all-routes)))

    (is (= {:change-date (tu/to-local-date 2018 11 26)
            :change-type :no-traffic
            :no-traffic-start-date (tu/to-local-date 2018 11 26)
            :no-traffic-end-date (tu/to-local-date 2018 12 17)
            :route-key tu/route-name}
           (select-keys (second result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure first already ongoing run is NOT reported, second no-traffic run IS reported")

    (is (= 2 (count result))
        "Ensure count of results")))

(deftest test-no-traffic-run-twice-is-detected
  (let [analysis-date (tu/to-local-date 2018 10 15)
        all-routes (tu/create-data-all-routes (list '(2018 10 8) '(2019 1 20)))
        test-data (tu/weeks (tu/to-local-date 2018 10 8)
                            (concat [{tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2018-10-15
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2018-10-22
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2018-10-29
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2018-11-05
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2018-11-12
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2018-11-19
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2018-11-26
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2018-12-03
                                     {tu/route-name [nil nil nil nil nil nil nil]}] ;; 2018-12-10
                                    (tu/generate-traffic-week 5 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]))) ;; 2018-12-17
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]

    (is (= {:change-date (tu/to-local-date 2018 10 15)
            :change-type :no-traffic
            :no-traffic-start-date (tu/to-local-date 2018 10 15)
            :no-traffic-end-date (tu/to-local-date 2018 11 5)
            :route-key tu/route-name}
           (-> (first result)
               (select-keys tu/select-keys-detect-changes-for-all-routes)))
        "Ensure first no-traffic run is reported")

    (is (= {:change-date (tu/to-local-date 2018 11 26)
            :change-type :no-traffic
            :no-traffic-start-date (tu/to-local-date 2018 11 26)
            :no-traffic-end-date (tu/to-local-date 2018 12 17)
            :route-key tu/route-name}
           (-> (second result)
               (select-keys tu/select-keys-detect-changes-for-all-routes))) "Ensure second no-traffic run is reported")

    (is (= 2 (count result))
        "Ensure count of results")))

;;;;;;;; Test traffic change and two patterns of no-traffic which do NOT exceed reporting threshold

(deftest test-no-traffic-two-patterns-consecutive
  (let [analysis-date (tu/to-local-date 2019 5 20)
        all-routes (tu/create-data-all-routes (list '(2019 5 13) '(2019 7 21)))
        test-data (tu/weeks (tu/to-local-date 2019 5 13)    ;; Data adapted from a real-world case
                            (list {tu/route-name ["AA" "AA" "AA" "AA" "AA" "AA" "AA"]} ;; Week 13.5.
                                  {tu/route-name ["AA" "AA" "AA" "AA" "AA" "AA" "AA"]} ;; Week 20.5.
                                  {tu/route-name ["AA" "AA" "AA" "AA" "AA" "AA" "AA"]} ;; Week 27.5.
                                  {tu/route-name ["BB" "CC" nil nil nil nil nil]} ;; Week 3.6.
                                  {tu/route-name ["BB" "CC" nil nil nil nil nil]} ;; Week 10.6.
                                  {tu/route-name ["DD" "DD" "DD" "DD" "DD" nil nil]} ;; Week 17.6.
                                  {tu/route-name ["DD" "DD" "DD" "DD" "DD" nil nil]} ;; 2019-06-24
                                  {tu/route-name ["DD" "DD" "DD" "DD" "DD" nil nil]} ;; 2019-07-01
                                  {tu/route-name ["DD" "DD" "DD" "DD" "DD" nil nil]} ;; 2019-07-08
                                  {tu/route-name ["DD" "DD" "DD" "DD" "DD" nil nil]})) ;; 2019-07-15, end 2019-07-21
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]
    (testing "Ensure first change is reported"
      (is (=
            {:change-type :changed
             :route-key tu/route-name
             :starting-week {:beginning-of-week (tu/to-local-date 2019 5 20)
                             :end-of-week (tu/to-local-date 2019 5 26)}
             :different-week {:beginning-of-week (tu/to-local-date 2019 6 3)
                              :end-of-week (tu/to-local-date 2019 6 9)}}
            (-> result
                first
                (select-keys tu/select-keys-detect-changes-for-all-routes)
                ;; no-traffic keys ignored in analysis because :different-week takes priority over them so let's just not examine them. Update test when logic changes.
                (dissoc :no-traffic-start-date)))))

    (testing "Ensure second change is reported"
      (is (= {:change-type :changed
              :route-key tu/route-name
              :starting-week {:beginning-of-week (tu/to-local-date 2019 6 3)
                              :end-of-week (tu/to-local-date 2019 6 9)}
              :different-week {:beginning-of-week (tu/to-local-date 2019 6 17)
                               :end-of-week (tu/to-local-date 2019 6 23)}}
             (-> result
                 second
                 (select-keys tu/select-keys-detect-changes-for-all-routes)
                 ;; no-traffic keys ignored in analysis because :different-week takes priority over them so let's just not examine them. Update test when logic changes.
                 (dissoc :no-traffic-start-date)))))

    (is (= {:change-type :removed
            :route-key tu/route-name
            :change-date (tu/to-local-date 2019 7 22)}
           (select-keys
             (nth result 2) tu/select-keys-detect-changes-for-all-routes)))

    (testing "Ensure that a right amount of changes are found and there are no extra changes."
      (is (= 3 (count result))))))

;;;;;;;; Test no-traffic run start and end when normal traffic pattern includes also no-traffic days

(deftest test-no-traffic-run-weekdays-and-christmas-newyear
  (let [analysis-date (tu/to-local-date 2018 10 15)
        all-routes (tu/create-data-all-routes (list '(2018 10 8) '(2019 2 3)))
        test-data (tu/weeks (tu/to-local-date 2018 10 8)
                            (concat [{tu/route-name ["h1" "h2" "h3" "h4" "h5" nil nil]} ;; 8.10.
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" nil nil]} ;; 15.10.
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" nil nil]} ;; 22.10.
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" nil nil]} ;; 29.10.
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" nil nil]} ;; 5.11.
                                     {tu/route-name ["h1" nil nil nil nil nil nil]} ;; 2018-11-12. nt Starts 13.11, 6 day run
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2018-11-19. nt 7 days
                                     {tu/route-name [nil nil nil nil "h5" nil nil]} ;; 2018-11-26. nt Ends 30.11, 4 days => sum 17
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" nil nil]} ;; 2018-12-03
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" nil nil]} ;; 2018-12-10
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" nil nil]} ;; 2018-12-17
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2018-12-24
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2018-12-31
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2019-01-07
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" nil nil]} ;; 2019-01-14
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" nil nil]} ;; 2019-01-21
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" nil nil]}])) ;; 2019-01-28, last 2019-02-03
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]
    (is (= {:change-type :no-traffic
            :change-date (tu/to-local-date 2018 11 13)
            :no-traffic-start-date (tu/to-local-date 2018 11 13)
            :no-traffic-end-date (tu/to-local-date 2018 11 30)
            :route-key tu/route-name}
           (select-keys (first result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure no-traffic run is reported and normal no-traffic weekends are not.")

    (is (= {:change-type :no-traffic
            :change-date (tu/to-local-date 2018 12 22)
            :no-traffic-start-date (tu/to-local-date 2018 12 22)
            :no-traffic-end-date (tu/to-local-date 2019 1 14)
            :route-key tu/route-name}
           (select-keys (second result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure christmas time trafficless period is reported right")

    (is (= 2 (count result)) "Ensure that a right amount of changes are found and there are no extra changes.")))

;;;;;;;; Test traffic change window when change does not reach change threshold

(deftest test-traffic-2-different-weeks
  (let [analysis-date (tu/to-local-date 2018 10 22)
        all-routes (tu/create-data-all-routes (list '(2018 10 15) '(2019 2 3)))
        test-data (tu/weeks (tu/to-local-date 2018 10 15)
                            (list {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2018-10-15
                                  {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2018-10-22
                                  {tu/route-name ["h1" "h2" "!!" "h4" "h5" "h6" "h7"]} ;; 2018-10-29
                                  {tu/route-name ["h1" "h2" "h3" "!!" "h5" "h6" "h7"]} ;; 2018-11-05
                                  {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2018-11-12
                                  {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2018-11-19
                                  {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}))
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]

    (is (= {:change-type :no-change
            :route-key tu/route-name}
           (select-keys (first result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure two week change is not reported because it's below threshold")

    (is (= 1 (count result)) "Ensure that a right amount of changes are found and there are no extra changes.")))

(deftest test-traffic-1-different-week-and-new-change
  (let [analysis-date (tu/to-local-date 2019 2 4)
        all-routes (tu/create-data-all-routes (list '(2019 1 28) '(2019 5 5)))
        test-data (tu/weeks (tu/to-local-date 2019 1 28)
                            (list {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2019-01-28
                                  {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2019-02-04
                                  {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2019-02-11
                                  {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2019-02-18
                                  {tu/route-name ["--" "--" "--" "--" "--" "h6" "h7"]} ;; 2019-02-25
                                  {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2019-03-04
                                  {tu/route-name ["h1" "h2" "h3" "h4" "--" "h6" "--"]} ;; 2019-03-11
                                  {tu/route-name ["h1" "h2" "h3" "h4" "--" "h6" "--"]} ;; 2019-03-18
                                  {tu/route-name ["h1" "h2" "h3" "h4" "--" "h6" "--"]} ;; 2019-03-25
                                  {tu/route-name ["h1" "h2" "h3" "h4" "--" "h6" "--"]})) ;; 2019-04-01
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]
    (is (= {:change-type :changed
            :route-key tu/route-name
            :starting-week {:beginning-of-week (tu/to-local-date 2019 2 4)
                            :end-of-week (tu/to-local-date 2019 2 10)}
            :different-week {:beginning-of-week (tu/to-local-date 2019 3 11)
                             :end-of-week (tu/to-local-date 2019 3 17)}}
           (select-keys (first result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure 1-week traffic change is skipped and a real change is detected")

    (is (= 1 (count result)) "Ensure that a right amount of changes are found and there are no extra changes.")))

(deftest test-changes-consecutive-different-traffic-patterns
  (let [analysis-date (tu/to-local-date 2018 10 15)
        all-routes (tu/create-data-all-routes (list '(2018 10 8) '(2019 1 30)))
        test-data (tu/weeks (tu/to-local-date 2018 10 8)
                            (list {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ; 2018-10-08
                                  {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ; 2018-10-15, starting point
                                  {tu/route-name ["h1" "h2" "!!" "h4" "h5" "h6" "h7"]} ; 2018-10-22, wednesday different
                                  {tu/route-name ["h1" "h2" "h3" "!!" "h5" "h6" "h7"]} ; 2018-10-29, thursday different
                                  {tu/route-name ["h1" "h2" "h3" "h4" "!!" "h6" "h7"]} ; friday different
                                  {tu/route-name ["h1" "h2" "h3" "!!" "!!" "h6" "h7"]}))
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]

    (is (= {:change-type :changed
            :starting-week {:beginning-of-week (tu/to-local-date 2018 10 15)
                            :end-of-week (tu/to-local-date 2018 10 21)}
            :different-week {:beginning-of-week (tu/to-local-date 2018 10 22)
                             :end-of-week (tu/to-local-date 2018 10 28)}
            :route-key tu/route-name}
           (select-keys (first result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure that first traffic change is detected")

    (is (= {:change-type :changed
            :different-week {:beginning-of-week (tu/to-local-date 2018 10 29)
                             :end-of-week (tu/to-local-date 2018 11 4)}
            :route-key tu/route-name
            :starting-week {:beginning-of-week (tu/to-local-date 2018 10 22)
                            :end-of-week (tu/to-local-date 2018 10 28)}}
           (select-keys (second result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure that second traffic change is detected")

    (is (= 2 (count result)) "Ensure that a right amount of changes are found and there are no extra changes.")))

(deftest test-changes-overlapping-dfiferent-traffic-patterns
  (spec-test/instrument `detection/route-weeks-with-first-difference)

  (let [analysis-date (tu/to-local-date 2019 2 11)
        all-routes (tu/create-data-all-routes (list '(2019 2 4) '(2019 12 31)))
        test-data (tu/weeks (tu/to-local-date 2019 2 4)
                            (list {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2019-02-04
                                  {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2019-02-11
                                  {tu/route-name ["h1" "h2" "--" "h4" "h5" "h6" "h7"]} ;; 2019-02-18
                                  {tu/route-name ["h1" "h2" "--" "h4" "h5" "h6" "h7"]} ;; 2019-02-25
                                  {tu/route-name ["h1" "--" "--" "h4" "h5" "h6" "h7"]} ;; 2019-03-04
                                  {tu/route-name ["h1" "h2" "h6" "--" "h4" "h5" "h7"]} ;; 2019-03-11
                                  {tu/route-name ["h1" "--" "--" "h4" "h5" "h6" "h7"]} ;; 2019-03-18
                                  {tu/route-name ["h1" "h2" "--" "h4" "h5" "h6" "h7"]})) ;; 2019-03-25
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]

    (is (= {:change-type :changed
            :route-key tu/route-name
            :starting-week {:beginning-of-week (tu/to-local-date 2019 2 11)
                            :end-of-week (tu/to-local-date 2019 2 17)}
            :different-week {:beginning-of-week (tu/to-local-date 2019 2 18)
                             :end-of-week (tu/to-local-date 2019 2 24)}}
           (select-keys (first result) tu/select-keys-detect-changes-for-all-routes)))

    (is (= {:change-type :changed
            :route-key tu/route-name
            :starting-week {:beginning-of-week (tu/to-local-date 2019 2 18)
                            :end-of-week (tu/to-local-date 2019 2 24)}
            :different-week {:beginning-of-week (tu/to-local-date 2019 3 4)
                             :end-of-week (tu/to-local-date 2019 3 10)}}
           (select-keys (second result) tu/select-keys-detect-changes-for-all-routes)))

    (is (= 2 (count result)) "Ensure that a right amount of changes are found and there are no extra changes.")))

(deftest test-traffic-change-middle-of-week
  (let [analysis-date (tu/to-local-date 2019 4 22)
        all-routes (tu/create-data-all-routes (list '(2019 4 15) '(2019 12 31)))
        test-data (tu/weeks (tu/to-local-date 2019 4 15)
                            (list {tu/route-name ["aa" "aa" "aa" "aa" "aa" "aa" "aa"]} ;; Week 2019-04-15
                                  {tu/route-name ["aa" "aa" "aa" "aa" "aa" "aa" "aa"]} ;; Week 2019-04-22
                                  {tu/route-name ["aa" "aa" "aa" "aa" "aa" "aa" "aa"]} ;; Week 2019-04-29
                                  {tu/route-name ["aa" "aa" "aa" "BB" "BB" "BB" "BB"]} ;; Week 2019-05-06
                                  {tu/route-name ["BB" "BB" "BB" "BB" "BB" "BB" "BB"]}
                                  {tu/route-name ["BB" "BB" "BB" "BB" "BB" "BB" "BB"]}
                                  {tu/route-name ["BB" "BB" "BB" "BB" "BB" "BB" "BB"]}
                                  {tu/route-name ["BB" "BB" "BB" "BB" "BB" "BB" "BB"]}))
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]

    (is (= {:change-type :changed
            :route-key tu/route-name
            :starting-week {:beginning-of-week (tu/to-local-date 2019 4 22)
                            :end-of-week (tu/to-local-date 2019 4 28)}
            :different-week {:beginning-of-week (tu/to-local-date 2019 5 6)
                             :end-of-week (tu/to-local-date 2019 5 12)}}
           (select-keys
             (first result)
             tu/select-keys-detect-changes-for-all-routes))
        "Ensure first change middle of week is found, when end of week traffic changes (AAAAAAA=>AAABBBB)")


    (is (= {:change-type :changed
            :route-key tu/route-name
            :starting-week {:beginning-of-week (tu/to-local-date 2019 5 6)
                            :end-of-week (tu/to-local-date 2019 5 12)}
            :different-week {:beginning-of-week (tu/to-local-date 2019 5 13)
                             :end-of-week (tu/to-local-date 2019 5 19)}}
           (select-keys
             (second result)
             tu/select-keys-detect-changes-for-all-routes))
        "Ensure second change is found next week, when also the beginning part of the week changes (AAABBBB=>BBBBBBB)")

    (is (= 2 (count result)) "Ensure that a right amount of changes are found and there are no extra changes.")))

(deftest test-traffic-starting-point-anomalous
  (let [analysis-date (tu/to-local-date 2018 10 15)
        all-routes (tu/create-data-all-routes (list '(2018 10 8) '(2019 12 31)))
        test-data (tu/weeks (tu/to-local-date 2018 10 8)
                            (concat [{tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2018-10-08
                                     {tu/route-name ["h1!" "h2!" "h3!" "h4!" "h5!" "h5!" "h7!"]}] ;; 2018-10-15, starting week is an exception
                                    (tu/generate-traffic-week 4 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"] tu/route-name)))
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]

    (is (= {:change-type :no-change
            :route-key tu/route-name}
           (select-keys (first result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure anomalous week is ignored and analysis starts from following week")

    (is (= 1 (count result)) "Ensure that the right count of changes are found and there are no extra changes."))) ;; gets -22, should be nil

(deftest test-traffic-static-holidays2
  (let [analysis-date (tu/to-local-date 2018 12 10)
        all-routes (tu/create-data-all-routes (list '(2018 12 3) '(2019 6 1)))
        test-data (tu/weeks (tu/to-local-date 2018 12 3)
                            (list {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2018-12-03
                                  {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2018-12-10
                                  {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2018-12-17
                                  {tu/route-name [:holiday :holiday "h3" "h4" "h5" "h6" "h7"]} ;; 2018-12-24
                                  {tu/route-name ["h1" :holiday "h3!" "h4!" "h5!" "h6!" "h7!"]} ;; 2018-12-31
                                  {tu/route-name ["h1!" "h2!" "h3!" "h4!" "h5!" "h6!" "h7!"]} ;; 2019-01-07
                                  {tu/route-name ["h1!" "h2!" "h3!" "h4!" "h5!" "h6!" "h7!"]} ;; 2019-01-14
                                  {tu/route-name ["h1!" "h2!" "h3!" "h4!" "h5!" "h6!" "h7!"]}))
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]

    (is (= {:change-type :changed
            :route-key tu/route-name
            :starting-week {:beginning-of-week (tu/to-local-date 2018 12 10)
                            :end-of-week (tu/to-local-date 2018 12 16)}
            :different-week {:beginning-of-week (tu/to-local-date 2018 12 31)
                             :end-of-week (tu/to-local-date 2019 1 6)}}
           (select-keys (first result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure first change is found, ignoring holidays")

    (is (= {:change-type :changed
            :route-key tu/route-name
            :starting-week {:beginning-of-week (tu/to-local-date 2018 12 31)
                            :end-of-week (tu/to-local-date 2019 1 6)}
            :different-week {:beginning-of-week (tu/to-local-date 2019 1 7)
                             :end-of-week (tu/to-local-date 2019 1 13)}}
           (select-keys (second result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure second change is found, ignoring holidays")

    (is (= 2 (count result)) "Ensure that the right count of changes are found and there are no extra changes.")))

(deftest test-two-week-two-route-change
  (let [all-routes (tu/create-data-all-routes (list '(2019 2 4) '(2019 4 7))
                                              (list '(2019 2 11) '(2019 4 14)))
        analysis-date (tu/to-local-date 2019 2 11)
        test-data (tu/weeks (tu/to-local-date 2019 2 4)
                            (list {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"] tu/route-name-2 [nil nil nil nil nil nil nil]} ; 2019-02-04
                                  {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"] tu/route-name-2 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ; 2019-02-11
                                  {tu/route-name ["h1" "##" "h3" "h4" "h5" "h6" "h7"] tu/route-name-2 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ; 2019-02-18
                                  {tu/route-name ["h1" "##" "h3" "h4" "h5" "h6" "h7"] tu/route-name-2 ["h1" "h2" "##" "h4" "h5" "h6" "h7"]} ; 2019-02-25
                                  {tu/route-name ["h1" "##" "h3" "h4" "h5" "h6" "h7"] tu/route-name-2 ["h1" "h2" "##" "h4" "h5" "h6" "h7"]} ; 2019-03-04
                                  {tu/route-name ["h1" "##" "h3" "h4" "h5" "h6" "h7"] tu/route-name-2 ["h1" "h2" "##" "h4" "h5" "h6" "h7"]} ; 2019-03-11
                                  {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"] tu/route-name-2 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ; 2019-03-18
                                  {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"] tu/route-name-2 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ; 2019-03-25
                                  {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"] tu/route-name-2 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ; 2019-04-01
                                  {tu/route-name [nil nil nil nil nil nil nil] tu/route-name-2 ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]}))
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]

    (is (= {:change-type :changed
            :different-week {:beginning-of-week (tu/to-local-date 2019 2 18)
                             :end-of-week (tu/to-local-date 2019 2 24)}
            :route-key tu/route-name
            :starting-week {:beginning-of-week (tu/to-local-date 2019 2 11)
                            :end-of-week (tu/to-local-date 2019 2 17)}}
           (select-keys (nth result 0) tu/select-keys-detect-changes-for-all-routes))
        "Ensure 1st change on 1st route matches")

    (is (= {:change-type :changed
            :different-week {:beginning-of-week (tu/to-local-date 2019 3 18)
                             :end-of-week (tu/to-local-date 2019 3 24)}
            :route-key tu/route-name
            :starting-week {:beginning-of-week (tu/to-local-date 2019 2 18)
                            :end-of-week (tu/to-local-date 2019 2 24)}}
           (select-keys (nth result 1) tu/select-keys-detect-changes-for-all-routes))
        "Ensure first change matches the right route and date")

    (is (= {:change-type :removed
            :route-key tu/route-name
            :change-date (tu/to-local-date 2019 4 8)}
           (select-keys (nth result 2) tu/select-keys-detect-changes-for-all-routes))
        "Ensure 1st route end is reported")

    (is (= {:change-type :added
            :route-key tu/route-name-2
            :change-date (tu/to-local-date 2019 2 11)}
           (select-keys (nth result 3) tu/select-keys-detect-changes-for-all-routes))
        "Ensure 1st route start is reported")

    (is (= {:change-type :changed
            :different-week {:beginning-of-week (tu/to-local-date 2019 2 25)
                             :end-of-week (tu/to-local-date 2019 3 3)}
            :route-key tu/route-name-2
            :starting-week {:beginning-of-week (tu/to-local-date 2019 2 18)
                            :end-of-week (tu/to-local-date 2019 2 24)}}
           (select-keys (nth result 4) tu/select-keys-detect-changes-for-all-routes))
        "Ensure 1st change on 2nd route matches")

    (is (= {:change-type :changed
            :different-week {:beginning-of-week (tu/to-local-date 2019 3 18)
                             :end-of-week (tu/to-local-date 2019 3 24)}
            :route-key tu/route-name-2
            :starting-week {:beginning-of-week (tu/to-local-date 2019 2 25)
                            :end-of-week (tu/to-local-date 2019 3 3)}}
           (select-keys (nth result 5) tu/select-keys-detect-changes-for-all-routes))
        "Ensure 2nd change on 2nd route matches")

    (is (= {:change-type :removed
            :route-key tu/route-name-2
            :change-date (tu/to-local-date 2019 4 15)}
           (select-keys (nth result 6) tu/select-keys-detect-changes-for-all-routes))
        "Ensure 2nd route end is reported")

    (is (= 7 (count result)) "Ensure that a right amount of changes are found and there are no extra changes.")))

(deftest test-change-to-summer-schedule-nil-weeks-with-midsummer
  (let [analysis-date (tu/to-local-date 2019 5 20)
        all-routes (tu/create-data-all-routes (list '(2019 5 13) '(2019 12 31)))
        test-data (tu/weeks (tu/to-local-date 2019 5 13)    ;; Test data adapted from production case
                            (list {tu/route-name ["1A" "1A" "1A" "1A" "1A" "6A" "7A"]} ;; 2019-05-13
                                  {tu/route-name ["1A" "1A" "1A" "1A" "1A" "6A" "7A"]} ;; 2019-05-20
                                  {tu/route-name ["1A" "1A" "1A" "7A" "5B" "6A" "7B"]} ;; 2019-05-27
                                  {tu/route-name [nil nil nil nil nil "B6" nil]} ;; 2019-06-03
                                  {tu/route-name [nil nil nil nil nil "B6" nil]} ;; 2019-06-10
                                  {tu/route-name [nil nil nil nil "C5" nil nil]} ;; 2019-06-17
                                  {tu/route-name [nil nil nil nil nil "B6" nil]} ;; 2019-06-24
                                  {tu/route-name [nil nil nil nil nil "B6" nil]}
                                  {tu/route-name [nil nil nil nil nil "B6" nil]}
                                  {tu/route-name [nil nil nil nil nil "B6" nil]}
                                  {tu/route-name [nil nil nil nil "C5" "B6" nil]}
                                  {tu/route-name [nil nil nil nil nil "B6" nil]}
                                  {tu/route-name [nil nil nil nil nil "B6" nil]}))
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]

    (is (= {:change-type :changed
            :different-week {:beginning-of-week (tu/to-local-date 2019 5 27) :end-of-week (tu/to-local-date 2019 6 2)}
            :route-key tu/route-name
            :starting-week {:beginning-of-week (tu/to-local-date 2019 5 20) :end-of-week (tu/to-local-date 2019 5 26)}}
           (select-keys
             (first result)
             tu/select-keys-detect-changes-for-all-routes)))

    (is (= {:change-type :changed
            :different-week {:beginning-of-week (tu/to-local-date 2019 6 3) :end-of-week (tu/to-local-date 2019 6 9)}
            :route-key tu/route-name
            :starting-week {:beginning-of-week (tu/to-local-date 2019 5 27) :end-of-week (tu/to-local-date 2019 6 2)}}
           (select-keys
             (second result)
             tu/select-keys-detect-changes-for-all-routes)))

    (is (= 2 (count result)) "Ensure that a right amount of changes are found and there are no extra changes.")))

;;;;;;;;; Test route END reporting

(deftest test-traffic-winter-to-summer-and-end
  (let [analysis-date (tu/to-local-date 2019 5 20)
        all-routes (tu/create-data-all-routes (list '(2019 5 13) '(2019 6 22)))
        test-data (tu/weeks (tu/to-local-date 2019 5 13)    ;; Test data adapted from production case
                            (concat [{tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2019-05-13
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2019-05-20
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2019-05-27
                                     {tu/route-name [nil nil nil nil nil "h6" nil]} ;; 2019-06-10
                                     {tu/route-name [nil nil nil nil nil "h6" nil]} ;; 2019-06-03
                                     {tu/route-name [nil nil nil nil nil "h6" nil]} ;; 2019-06-17, traffic on 22nd
                                     {tu/route-name [nil nil nil nil nil nil nil]}]))
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]

    (testing "Test route END reporting when traffic change to pattern with nils and then only nil."
      (is (= {:change-type :changed
              :route-key tu/route-name
              :different-week {:beginning-of-week (tu/to-local-date 2019 6 3)
                               :end-of-week (tu/to-local-date 2019 6 9)}
              :starting-week {:beginning-of-week (tu/to-local-date 2019 5 20)
                              :end-of-week (tu/to-local-date 2019 5 26)}}
             (select-keys (first result) tu/select-keys-detect-changes-for-all-routes))
          "Expect change to a week with nils")

      (is (= {:change-type :removed
              :route-key tu/route-name
              :change-date (tu/to-local-date 2019 6 23)}
             (select-keys (second result) tu/select-keys-detect-changes-for-all-routes))
          "Expect route end instead of no-traffic, when no-traffic starts within route end threshold"))

    (is (= 2 (count result)) "Ensure that a right amount of changes are found and there are no extra changes.")))

(deftest test-change-nil-and-end
  (let [analysis-date (tu/to-local-date 2019 5 20)
        all-routes (tu/create-data-all-routes (list '(2019 5 13) '(2019 7 22)))
        test-data (tu/weeks (tu/to-local-date 2019 5 13)
                            (concat [{tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2019 05 13
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2019 05 20
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2019 05 27
                                     {tu/route-name ["h1" "h2" nil nil nil nil nil]}] ;; 2019 06 03
                                    (tu/generate-traffic-week 5 [nil nil nil nil nil nil nil])))
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]

    (testing "Test route END reporting when no-traffic starts middle of week"
      (is (= {:change-type :removed
              :change-date (tu/to-local-date 2019 6 5)
              :route-key tu/route-name}
             (select-keys (first result) tu/select-keys-detect-changes-for-all-routes))
          "Ensure route end is reported when no-traffic starts, even if route max-date is later. If that is possible...")

      (is (= 1 (count result)) "Ensure that a right amount of changes are found and there are no extra changes."))))

(deftest test-change-route-end
  (let [analysis-date (tu/to-local-date 2019 5 20)
        all-routes (tu/create-data-all-routes (list '(2019 5 13) '(2019 7 22)))
        test-data (tu/weeks (tu/to-local-date 2019 5 13)
                            (concat [{tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2019 05 13
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2019 05 20
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2019 05 27
                                     {tu/route-name [nil nil nil nil nil nil nil]}] ;; 2019 06 03
                                    (tu/generate-traffic-week 5 [nil nil nil nil nil nil nil])))
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]

    (testing "Test route END reporting when no-traffic starts beginning of week"
      (is (= {:change-type :removed
              :change-date (tu/to-local-date 2019 6 3)
              :route-key tu/route-name}
             (select-keys (first result) tu/select-keys-detect-changes-for-all-routes))
          "Ensure route end is reported when no-traffic starts")

      (is (= 1 (count result)) "Ensure that a right amount of changes are found and there are no extra changes."))))

(deftest test-change-and-ending-route
  (let [analysis-date (tu/to-local-date 2019 5 20)
        all-routes (tu/create-data-all-routes (list '(2019 5 13) '(2019 7 7)))
        test-data (tu/weeks (tu/to-local-date 2019 5 13)
                            (concat [{tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2019-05-13
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2019-05-20
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2019-05-27
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" "h6" "h7"]} ;; 2019-06-03
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" "--" "--"]} ;; 2019-06-10
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" "--" "--"]} ;; 2019-06-17
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" "--" "--"]} ;; 2019-06-24
                                     {tu/route-name ["h1" "h2" "h3" "h4" "h5" "--" "--"]}])) ;; 2019-07-01, last day 7th
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]
    (testing "Test route END and traffic change detection"
      (is (= {:change-type :changed
              :different-week {:beginning-of-week (tu/to-local-date 2019 6 10)
                               :end-of-week (tu/to-local-date 2019 6 16)}
              :route-key tu/route-name
              :starting-week {:beginning-of-week (tu/to-local-date 2019 5 20) :end-of-week (tu/to-local-date 2019 5 26)}}
             (select-keys (first result) tu/select-keys-detect-changes-for-all-routes)))

      (is (= {:change-type :removed
              :change-date (tu/to-local-date 2019 7 8)
              :route-key tu/route-name}
             (select-keys (second result) tu/select-keys-detect-changes-for-all-routes)))
      (is (= 2 (count result)) "Ensure that a right amount of changes are found and there are no extra changes."))))

(deftest test-paused-traffic-with-end
  (let [analysis-date (tu/to-local-date 2019 5 20)
        all-routes (tu/create-data-all-routes (list '(2019 5 13) '(2019 8 4)))
        test-data (tu/weeks (tu/to-local-date 2019 5 13)
                            (concat [{tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019 05 13
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019 05 20
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019 05 27
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019 06 03
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2019 06 10
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2019 06 17
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2019 06 24
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2019 07 01
                                     {tu/route-name [nil nil nil "A" "A" "A" "A"]} ;; 2019 07 08
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019 07 15
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019 07 22
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019 07 29
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2019 08 05
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2019 08 12
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2019 08 19
                                     {tu/route-name [nil nil nil nil nil nil nil]}]))
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]

    (is (= {:change-type :no-traffic
            :change-date (tu/to-local-date 2019 6 10)
            :route-key tu/route-name
            :no-traffic-start-date (tu/to-local-date 2019 6 10)
            :no-traffic-end-date (tu/to-local-date 2019 7 11)}
           (select-keys (first result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure that the pause is detected properly")

    (is (= {:route-key tu/route-name
            :change-type :removed
            :change-date (tu/to-local-date 2019 8 5)}
           (select-keys (second result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure that the route end is detected properly")

    (is (= 2 (count result)) "Ensure that a right amount of changes are found and there are no extra changes.")))

(deftest test-paused-traffic-with-keyword
  (let [analysis-date (tu/to-local-date 2019 5 13)
        all-routes (tu/create-data-all-routes (list '(2019 5 6) '(2019 8 11)))
        test-data (tu/weeks (tu/to-local-date 2019 5 6)
                            (concat [{tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019 05 6
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019 05 13
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019 05 20
                                     {tu/route-name ["A" "A" "A" :keyword "A" nil nil]} ;; 2019 05 27
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2019 06 03
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2019 06 10
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2019 06 17
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2019 06 24
                                     {tu/route-name [nil nil nil "A" "A" "A" "A"]} ;; 2019 07 01
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019 07 08
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019 07 15
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019-07-22
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019-07-29
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]}]))
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]

    (is (= {:change-type :no-traffic
            :change-date (tu/to-local-date 2019 6 1)
            :route-key tu/route-name
            :no-traffic-start-date (tu/to-local-date 2019 6 1)
            :no-traffic-end-date (tu/to-local-date 2019 7 4)}
           (select-keys (first result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure a traffic change and route end within detection window are reported.")

    (is (= 1 (count result)) "Ensure that a right amount of changes are found and there are no extra changes.")))

(deftest test-2-changes-with-nil-days-with-route-end
  (let [analysis-date (tu/to-local-date 2019 5 13)
        all-routes (tu/create-data-all-routes (list '(2019 5 6) '(2019 7 14)))
        test-data (tu/weeks (tu/to-local-date 2019 5 6)
                            (concat [{tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019 05 6
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019 05 13
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019 05 20
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019 05 27
                                     {tu/route-name [nil nil nil nil nil "X" nil]} ;; 2019 06 03
                                     {tu/route-name [nil nil nil nil nil "X" nil]} ;; 2019 06 10
                                     {tu/route-name [nil nil nil nil nil "X" nil]} ;; 2019 06 17
                                     {tu/route-name ["B" "B" "B" "B" "B" "B" "B"]} ;; 2019 06 24
                                     {tu/route-name ["B" "B" "B" "B" "B" "B" "B"]} ;; 2019 07 01
                                     {tu/route-name ["B" "B" "B" "B" "B" "B" "B"]}])) ;; 2019 07 08, last day 14th
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]

    (is (= {:change-type :changed
            :route-key tu/route-name
            :different-week {:beginning-of-week (tu/to-local-date 2019 6 3)
                             :end-of-week (tu/to-local-date 2019 6 9)}
            :starting-week {:beginning-of-week (tu/to-local-date 2019 5 13)
                            :end-of-week (tu/to-local-date 2019 5 19)}}

           (select-keys (first result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure a traffic change is reported.")

    (is (= {:change-type :changed
            :route-key tu/route-name
            :different-week {:beginning-of-week (tu/to-local-date 2019 6 24)
                             :end-of-week (tu/to-local-date 2019 6 30)}
            :starting-week {:beginning-of-week (tu/to-local-date 2019 6 3)
                            :end-of-week (tu/to-local-date 2019 6 9)}}
           (select-keys (second result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure a traffic change is reported.")

    (is (= {:route-key tu/route-name
            :change-type :removed
            :change-date (tu/to-local-date 2019 7 15)}
           (select-keys (nth result 2) tu/select-keys-detect-changes-for-all-routes))
        "Ensure route end is reported.")

    (is (= 3 (count result)) "Ensure that a right amount of changes are found and there are no extra changes.")))

(deftest test-change-after-no-traffic-and-route-end
  (let [analysis-date (tu/to-local-date 2019 5 13)
        all-routes (tu/create-data-all-routes (list '(2019 5 6) '(2019 7 14)))
        test-data (tu/weeks (tu/to-local-date 2019 5 6)
                            (concat [{tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019 05 6
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019 05 13
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019 05 20
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019 05 27
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2019 06 03
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2019 06 10
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2019 06 17
                                     {tu/route-name ["B" "B" "B" "B" "B" "B" "B"]} ;; 2019 06 24
                                     {tu/route-name ["B" "B" "B" "B" "B" "B" "B"]} ;; 2019 07 01
                                     {tu/route-name ["B" "B" "B" "B" "B" "B" "B"]}])) ;; 2019 07 08, last day 14th
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]

    (is (= {:change-type :no-traffic
            :change-date (tu/to-local-date 2019 6 3)
            :route-key tu/route-name
            :no-traffic-start-date (tu/to-local-date 2019 6 3)
            :no-traffic-end-date (tu/to-local-date 2019 6 24)}
           (select-keys (first result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure a traffic change is reported.")

    (is (= {:change-type :changed
            :route-key tu/route-name
            :different-week {:beginning-of-week (tu/to-local-date 2019 6 24)
                             :end-of-week (tu/to-local-date 2019 6 30)}
            :starting-week {:beginning-of-week (tu/to-local-date 2019 5 13)
                            :end-of-week (tu/to-local-date 2019 5 19)}}
           (select-keys (second result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure a traffic change is reported.")

    (is (= {:route-key tu/route-name
            :change-type :removed
            :change-date (tu/to-local-date 2019 7 15)}
           (select-keys (nth result 2) tu/select-keys-detect-changes-for-all-routes))
        "Ensure route end is reported.")

    (is (= 3 (count result)) "Ensure that a right amount of changes are found and there are no extra changes.")))


(deftest test-no-traffic-middle-of-week-and-change
  (let [analysis-date (tu/to-local-date 2019 5 13)
        all-routes (tu/create-data-all-routes (list '(2019 5 6) '(2019 7 14)))
        test-data (tu/weeks (tu/to-local-date 2019 5 6)
                            (concat [{tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019-05-06
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019-05-13
                                     {tu/route-name ["A" "A" "A" "A" "A" nil nil]} ;; 2019-05-20
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2019-05-27
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2019-06-03
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2019-06-10
                                     {tu/route-name [nil nil nil nil nil "B" "B"]} ;; 2019-06-17
                                     {tu/route-name ["B" "B" "B" "B" "B" "B" "B"]} ;; 2019-06-24
                                     {tu/route-name ["B" "B" "B" "B" "B" "B" "B"]} ;; 2019-07-01
                                     {tu/route-name ["B" "B" "B" "B" "B" "B" "B"]}])) ;; 2019-07-08, last day 14th
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]

    (is (= {:change-type :no-traffic
            :change-date (tu/to-local-date 2019 5 25)
            :route-key tu/route-name
            :no-traffic-start-date (tu/to-local-date 2019 5 25)
            :no-traffic-end-date (tu/to-local-date 2019 6 22)}
           (select-keys (first result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure a traffic change is reported.")

    (is (= {:change-type :changed
            :route-key tu/route-name
            :different-week {:beginning-of-week (tu/to-local-date 2019 6 17)
                             :end-of-week (tu/to-local-date 2019 6 23)}
            :starting-week {:beginning-of-week (tu/to-local-date 2019 5 13)
                            :end-of-week (tu/to-local-date 2019 5 19)}}
           (select-keys (second result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure a traffic change is reported.")

    (is (= {:change-type :removed
            :route-key tu/route-name
            :change-date (tu/to-local-date 2019 7 15)}
           (select-keys (nth result 2) tu/select-keys-detect-changes-for-all-routes))
        "Ensure route end is reported.")

    (is (= 3 (count result)) "Ensure that a right amount of changes are found and there are no extra changes.")))

(deftest test-short-no-traffic-and-change-after
  (let [analysis-date (tu/to-local-date 2019 5 13)
        all-routes (tu/create-data-all-routes (list '(2019 5 6) '(2019 7 14)))
        test-data (tu/weeks (tu/to-local-date 2019 5 6)
                            (concat [{tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019-05-06
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019-05-13
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019-05-20
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2019-05-27
                                     {tu/route-name ["B" "B" "B" "B" "B" "B" "B"]} ;; 2019-06-03
                                     {tu/route-name ["B" "B" "B" "B" "B" "B" "B"]} ;; 2019-06-10
                                     {tu/route-name ["B" "B" "B" "B" "B" "B" "B"]} ;; 2019-06-17
                                     {tu/route-name ["B" "B" "B" "B" "B" "B" "B"]} ;; 2019-06-24
                                     {tu/route-name ["B" "B" "B" "B" "B" "B" "B"]} ;; 2019-07-01
                                     {tu/route-name ["B" "B" "B" "B" "B" "B" "B"]}])) ;; 2019-07-08, last day 14th
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]

    (is (= {:change-type :changed
            :route-key tu/route-name
            :different-week {:beginning-of-week (tu/to-local-date 2019 5 27)
                             :end-of-week (tu/to-local-date 2019 6 2)}
            :starting-week {:beginning-of-week (tu/to-local-date 2019 5 13)
                            :end-of-week (tu/to-local-date 2019 5 19)}}
           (select-keys (first result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure a traffic change is reported.")

    (is (= {:change-type :changed
            :route-key tu/route-name
            :different-week {:beginning-of-week (tu/to-local-date 2019 6 3)
                             :end-of-week (tu/to-local-date 2019 6 9)}
            :starting-week {:beginning-of-week (tu/to-local-date 2019 5 27)
                            :end-of-week (tu/to-local-date 2019 6 2)}}
           (select-keys (second result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure a traffic change is reported.")

    (is (= {:change-type :removed
            :route-key tu/route-name
            :change-date (tu/to-local-date 2019 7 15)}
           (select-keys (nth result 2) tu/select-keys-detect-changes-for-all-routes))
        "Ensure route end is reported.")

    (is (= 3 (count result)) "Ensure that a right amount of changes are found and there are no extra changes.")))

(deftest test-short-no-traffic-and-short-change-after
  (let [analysis-date (tu/to-local-date 2019 5 13)
        all-routes (tu/create-data-all-routes (list '(2019 5 6) '(2019 7 14)))
        test-data (tu/weeks (tu/to-local-date 2019 5 6)
                            (concat [{tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019-05-06
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019-05-13
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019-05-20
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2019-05-27
                                     {tu/route-name ["A" "B" "B" "A" "A" "A" "A"]} ;; 2019-06-03
                                     {tu/route-name ["A" "B" "B" "A" "A" "A" "A"]} ;; 2019-06-10
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019-06-17
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019-06-24
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019-07-01
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]}])) ;; 2019-07-08, last day 14th
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]

    (is (= {:change-type :changed
            :route-key tu/route-name
            :different-week {:beginning-of-week (tu/to-local-date 2019 5 27)
                             :end-of-week (tu/to-local-date 2019 6 2)}
            :starting-week {:beginning-of-week (tu/to-local-date 2019 5 13)
                            :end-of-week (tu/to-local-date 2019 5 19)}}
           (select-keys (first result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure a traffic change is reported.")

    (is (= {:change-type :changed
            :route-key tu/route-name
            :different-week {:beginning-of-week (tu/to-local-date 2019 6 3)
                             :end-of-week (tu/to-local-date 2019 6 9)}
            :starting-week {:beginning-of-week (tu/to-local-date 2019 5 27)
                            :end-of-week (tu/to-local-date 2019 6 2)}}
           (select-keys (second result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure a traffic change is reported.")

    (is (= {:change-type :changed
            :route-key tu/route-name
            :different-week {:beginning-of-week (tu/to-local-date 2019 6 17)
                             :end-of-week (tu/to-local-date 2019 6 23)}
            :starting-week {:beginning-of-week (tu/to-local-date 2019 6 3)
                            :end-of-week (tu/to-local-date 2019 6 9)}}
           (select-keys (nth result 2) tu/select-keys-detect-changes-for-all-routes))
        "Ensure a traffic change is reported.")

    (is (= {:change-type :removed
            :route-key tu/route-name
            :change-date (tu/to-local-date 2019 7 15)}
           (select-keys (nth result 3) tu/select-keys-detect-changes-for-all-routes))
        "Ensure route end is reported.")

    (is (= 4 (count result)) "Ensure that a right amount of changes are found and there are no extra changes.")))

;TODO: Current implementation doesn't detect all changes, needs an improvement
(deftest test-no-traffic-middle-of-week-and-changes-before-and-after
  (let [analysis-date (tu/to-local-date 2019 5 13)
        all-routes (tu/create-data-all-routes (list '(2019 5 6) '(2019 7 28)))
        test-data (tu/weeks (tu/to-local-date 2019 5 6)
                            (concat [{tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019-05-06
                                     {tu/route-name ["A" "A" "A" "A" "A" "A" "A"]} ;; 2019-05-13
                                     {tu/route-name ["B" "B" "B" "B" "B" nil nil]} ;; 2019-05-20 TODO: Current implementation doesn't detect this change, needs an improvement
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2019-05-27
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2019-06-03
                                     {tu/route-name [nil nil nil nil nil nil nil]} ;; 2019-06-10
                                     {tu/route-name [nil nil nil nil nil "B" "B"]} ;; 2019-06-17
                                     {tu/route-name ["C" "C" "B" "B" "B" "B" "B"]} ;; 2019-06-24 TODO: Current implementation doesn't detect this change, needs an improvement
                                     {tu/route-name ["C" "C" "B" "B" "B" "B" "B"]} ;; 2019-07-01
                                     {tu/route-name ["D" "D" "B" "B" "B" "B" "B"]} ;; 2019-07-08 TODO: Current implementation doesn't detect this change, needs an improvement
                                     {tu/route-name ["D" "D" "B" "B" "B" "B" "B"]} ;; 2019-07-15
                                     {tu/route-name ["D" "D" "B" "B" "B" "B" "B"]}])) ;;2019-07-22 last day 28th
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]

    (is (= {:change-type :no-traffic
            :change-date (tu/to-local-date 2019 5 25)
            :route-key tu/route-name
            :no-traffic-start-date (tu/to-local-date 2019 5 25)
            :no-traffic-end-date (tu/to-local-date 2019 6 22)}
           (select-keys (first result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure a traffic change is reported.")

    (is (= {:change-type :changed
            :route-key tu/route-name
            :different-week {:beginning-of-week (tu/to-local-date 2019 6 17)
                             :end-of-week (tu/to-local-date 2019 6 23)}
            :starting-week {:beginning-of-week (tu/to-local-date 2019 5 13)
                            :end-of-week (tu/to-local-date 2019 5 19)}}
           (select-keys (second result) tu/select-keys-detect-changes-for-all-routes))
        "Ensure a traffic change is reported.")

    (is (= {:route-key tu/route-name
            :change-type :removed
            :change-date (tu/to-local-date 2019 7 29)}
           (select-keys (nth result 2) tu/select-keys-detect-changes-for-all-routes))
        "Ensure route end is reported.")

    (is (= 3 (count result)) "Ensure that a right amount of changes are found and there are no extra changes.")))

(deftest test-holiday-shouldnt-hide-traffic-days
  (let [analysis-date (tu/to-local-date 2019 11 18)
        all-routes (tu/create-data-all-routes (list '(2019 11 11) '(2020 2 28)))
        test-data (tu/weeks (tu/to-local-date 2019 11 11)
                            (concat [{tu/route-name ["A" nil nil nil nil nil nil]} ;; 2019 11 11
                                     {tu/route-name ["A" nil nil nil nil nil nil]} ;; 2019 11 18
                                     {tu/route-name ["A" nil nil nil nil nil nil]} ;; 2019 11 25
                                     {tu/route-name [:holiday-tr nil nil nil nil nil nil]} ;; 2019 12 02
                                     {tu/route-name [:holiday-tr nil nil nil nil nil nil]} ;; 2019 12 09
                                     {tu/route-name [:holiday-nt nil nil nil nil nil nil]} ;; 2019 12 16
                                     {tu/route-name [:holiday-tr nil nil nil nil nil nil]} ;; 2019 12 23
                                     {tu/route-name [:holiday-tr nil nil nil nil nil nil]} ;; 2019 12 30
                                     {tu/route-name [:holiday-nt nil nil nil nil nil nil]} ;; 2020 01 06
                                     {tu/route-name ["A" nil nil nil nil nil nil]}
                                     {tu/route-name ["A" nil nil nil nil nil nil]}
                                     {tu/route-name ["A" nil nil nil nil nil nil]}]))
        result (detection/traffic-week-maps->change-maps analysis-date all-routes test-data)]
    (testing "Test that holidays hiding route's weekly traffic do NOT cause no-traffic to be reported"
      (is (= {:change-type :no-change
              :route-key tu/route-name}
             (select-keys (first result) tu/select-keys-detect-changes-for-all-routes))
          "Ensure a no traffic changes are reported."))

    (is (= 1 (count result)) "Ensure that a right amount of changes are found and there are no extra changes.")))
