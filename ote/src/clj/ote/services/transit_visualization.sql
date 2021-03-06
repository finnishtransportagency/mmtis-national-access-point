-- name: fetch-operator-date-hashes
SELECT date, hash::text
  FROM "gtfs-date-hash"
 WHERE hash IS NOT NULL
   AND "package-id" IN (SELECT id FROM gtfs_package WHERE "transport-operator-id" = :operator-id AND p."deleted?" = FALSE)
   -- Take dates from two months ago two 1 year in the future (but always full years)
   AND date >= make_date(EXTRACT(YEAR FROM (current_date - '2 months'::interval)::date)::integer, 1, 1)
   AND date <= make_date(EXTRACT(YEAR FROM (current_date + '1 year'::interval)::date)::integer, 12, 31);

-- name: fetch-routes-for-dates
-- Given a package id and two dates, fetch the routes operating on those days with
-- the amount of trips on each day.
WITH
date1_trips AS (
SELECT r."route-short-name", r."route-long-name", trip."trip-headsign",
       COUNT(trip."trip-id") AS trips,
       string_agg(t.trips::TEXT,',') as tripdata
  FROM "gtfs-route" r
  JOIN "gtfs-trip" t ON (t."package-id" = r."package-id" AND r."route-id" = t."route-id")
  JOIN LATERAL unnest(t.trips) trip ON true
 WHERE t."service-id" IN (SELECT gtfs_services_for_date(
                           (SELECT gtfs_latest_package_for_date(:operator-id::INTEGER, :date1::date)),
                           :date1::date))
   AND r."package-id" = (SELECT gtfs_latest_package_for_date(:operator-id::INTEGER, :date1::date))
 GROUP BY r."route-short-name", r."route-long-name", trip."trip-headsign"
),
date2_trips AS (
SELECT r."route-short-name", r."route-long-name", trip."trip-headsign",
       COUNT(trip."trip-id") AS trips,
       string_agg(t.trips::TEXT,',') as tripdata
  FROM "gtfs-route" r
  JOIN "gtfs-trip" t ON (t."package-id" = r."package-id" AND r."route-id" = t."route-id")
  JOIN LATERAL unnest(t.trips) trip ON true
 WHERE t."service-id" IN (SELECT gtfs_services_for_date(
                           (SELECT gtfs_latest_package_for_date(:operator-id::INTEGER, :date2::date)),
                           :date2::date))
   AND r."package-id" = (SELECT gtfs_latest_package_for_date(:operator-id::INTEGER, :date2::date))
 GROUP BY r."route-short-name", r."route-long-name", trip."trip-headsign"
)
SELECT x.* FROM (
 SELECT COALESCE(d1."route-short-name",d2."route-short-name") AS "route-short-name",
        COALESCE(d1."route-long-name",d2."route-long-name") AS "route-long-name",
        COALESCE(d1."trip-headsign",d2."trip-headsign") AS "trip-headsign",
        d1.trips as "date1-trips", d2.trips as "date2-trips",
        CASE
          WHEN d1.tripdata = d2.tripdata THEN false
          ELSE true
        END as "different?"
   FROM date1_trips d1 FULL OUTER JOIN date2_trips d2
        ON (COALESCE(d1."route-short-name",'') = COALESCE(d2."route-short-name", '') AND
            COALESCE(d1."route-long-name",'') = COALESCE(d2."route-long-name", '') AND
            COALESCE(d1."trip-headsign",'') = COALESCE(d2."trip-headsign",''))) x
ORDER BY x."route-short-name";

-- name: fetch-trip-stops-for-route-by-name-and-date
-- Fetch all trips with stop sequence
SELECT x."trip-id", x."trip-headsign",
       string_agg(concat(x."stop-name",'@',x."departure-time"), '->' ORDER BY x."trip-id", x."departure-time") as stops
  FROM (
SELECT trip."trip-id", trip."trip-headsign", stop."stop-name", stoptime."departure-time"
  FROM "gtfs-route" r
  JOIN "gtfs-trip" t ON (r."package-id" = t."package-id" AND r."route-id" = t."route-id")
  JOIN LATERAL unnest(t.trips) trip ON TRUE
  JOIN LATERAL unnest(trip."stop-times") stoptime ON TRUE
  JOIN "gtfs-stop" stop ON (r."package-id" = stop."package-id" AND stoptime."stop-id" = stop."stop-id")
 WHERE COALESCE(:route-short-name::VARCHAR,'') = COALESCE(r."route-short-name",'')
   AND COALESCE(:route-long-name::VARCHAR,'') = COALESCE(r."route-long-name",'')
   AND COALESCE(:headsign::VARCHAR,'') = COALESCE(trip."trip-headsign",'')
   AND t."service-id" IN (SELECT gtfs_services_for_date(
                                (SELECT gtfs_latest_package_for_date(:operator-id::INTEGER, :date::DATE)),
                                :date::date))
   AND r."package-id" = (SELECT gtfs_latest_package_for_date(:operator-id::INTEGER, :date::DATE))
 ORDER BY stoptime."stop-sequence") x
 GROUP BY x."trip-id", x."trip-headsign";

-- name: fetch-route-trips-by-hash-and-date
-- Fetch geometries of route trips for given date by route hash-id
SELECT ST_AsGeoJSON(COALESCE(
          -- If there exists a "gtfs-shape" row for this package and trip shape id,
          -- use that to generate the detailed route line
          (SELECT ST_MakeLine(ST_MakePoint(rs."shape-pt-lon", rs."shape-pt-lat") ORDER BY rs."shape-pt-sequence") as routeline
             FROM "gtfs-shape" gs
             JOIN LATERAL unnest(gs."route-shape") rs ON TRUE
            WHERE gs."shape-id" = x."shape-id"
              AND gs."package-id" = x."package-id"),
          -- Otherwise use line generated from the stop sequence
          x."route-line")) as "route-line",
       array_agg(departure) as departures,
       string_agg(stops, '||') as stops,
       x."trip-id", x.headsign
  FROM (SELECT (array_agg(stoptime."departure-time"))[1] as "departure",
               ST_MakeLine(ST_MakePoint(stop."stop-lon", stop."stop-lat") ORDER BY stoptime."stop-sequence") as "route-line",
               string_agg(CONCAT(stop."stop-lon", ';', stop."stop-lat", ';', stop."stop-name", ';', stoptime."trip-id"), '||' ORDER BY stoptime."stop-sequence") as stops,
               trip."shape-id", r."package-id", trip."trip-id" as "trip-id", trip."trip-headsign" as headsign
          FROM "detection-route" r
          JOIN "gtfs-trip" t ON (r."package-id" = t."package-id" AND r."route-id" = t."route-id")
          JOIN LATERAL unnest(t.trips) trip ON TRUE
          JOIN LATERAL unnest(trip."stop-times") stoptime ON stoptime."trip-id" = trip."trip-id"
          JOIN "gtfs-stop" stop ON (r."package-id" = stop."package-id" AND stoptime."stop-id" = stop."stop-id" AND stoptime."stop-id" = stop."stop-id")
         WHERE COALESCE(:route-hash-id::VARCHAR,'') = COALESCE(r."route-hash-id",'')
           AND ROW(r."package-id", t."service-id")::service_ref IN
               (SELECT * FROM gtfs_services_for_date(:used-packages::INT[],:date::DATE))
           AND r."package-id" = ANY(:used-packages::INT[])
         GROUP BY trip."shape-id", r."package-id", trip."trip-id", trip."trip-headsign") x
 -- Group same route lines to single row (aggregate departures to array)
 GROUP BY "route-line", "shape-id", "package-id", "trip-id", headsign;

-- name: fetch-route-trip-info-by-name-and-date
-- Fetch listing of all trips by route name and date
WITH route_stops as (
 SELECT trip."package-id" as "package-id", (trip.trip)."trip-id" as "trip-id",
        (trip.trip)."trip-headsign" as headsign, stoptime."stop-id" as "stop-id",
        stoptime."arrival-time" as "arrival-time", stoptime."departure-time" as "departure-time",
        stoptime."stop-sequence" as "stop-sequence"
  FROM gtfs_route_trips_for_date(:route-hash-id,gtfs_service_packages_for_detection_date(:service-id::INTEGER, :date::date, :detection-date::DATE), :date::DATE) rt
       JOIN LATERAL unnest(rt.tripdata) trip ON TRUE
       JOIN LATERAL unnest((trip.trip)."stop-times") stoptime ON TRUE
 WHERE rt."route-hash-id" = :route-hash-id
 GROUP BY stoptime."departure-time", stoptime."arrival-time", stoptime."stop-sequence", stoptime."stop-id", trip."package-id", trip.trip
)
SELECT rs."package-id", rs."trip-id", rs."headsign" as headsign,
       array_agg(ROW(rs."stop-sequence",
                   s."stop-name",
                   rs."arrival-time",
                   rs."departure-time",
                   s."stop-lat", s."stop-lon",s."stop-fuzzy-lat", s."stop-fuzzy-lon")::gtfs_stoptime_display
                 ORDER BY rs."stop-sequence") AS "stoptimes"
  FROM route_stops as rs,
       (SELECT * FROM "gtfs-stop" s WHERE s."package-id" IN (select distinct rs."package-id" FROM route_stops rs)) s
 WHERE s."package-id" = rs."package-id"
   AND s."stop-id" = rs."stop-id"
 GROUP BY rs."package-id", rs."trip-id", rs.headsign;

-- name: fetch-date-hashes-for-route-with-route-hash-id
-- Fetch the date/hash pairs for a given route using detection-date, service-id and route-hash-id
WITH dates AS (
  -- Calculate a series of dates from beginning of last year
  -- to the end of the next year.
  SELECT ts::date AS date
    FROM generate_series(
            (date_trunc('year', CURRENT_DATE) - '1 year'::interval)::date,
            (date_trunc('year', CURRENT_DATE) + '2 years'::interval)::date,
            '1 day'::interval) AS g(ts)
)
SELECT x.date::text, string_agg(x.hash,' ' ORDER BY x.e_id asc) as hash
  FROM (SELECT DISTINCT ON (concat(d.date, rh.hash)) concat(d.date, rh.hash) as ddd ,
      d.date, dh."package-id", rh.hash::text, p."external-interface-description-id" as e_id
          FROM dates d
               -- Join all date hashes to packages
               JOIN "gtfs-date-hash" dh ON (dh."package-id" = ANY(gtfs_service_packages_for_detection_date(:service-id::INTEGER, d.date, :detection-date::DATE))
                                                AND dh.date = d.date
                                                AND dh."transport-service-id" = :service-id)
               -- Join gtfs_package to get external-interface-description-id
               JOIN gtfs_package p ON p.id = dh."package-id"
               -- Join unnested per route hashes
               JOIN LATERAL unnest(dh."route-hashes") rh ON TRUE
         WHERE rh."route-hash-id" = :route-hash-id::VARCHAR
           -- Get traffic (date-hashes) dates for the package download date and after that
           AND dh.date >= p.created::DATE) x
 GROUP BY x.date;


-- name: fetch-service-info
-- Fetch service info for display in the UI
SELECT ts.name AS "transport-service-name",
       ts.id AS "transport-service-id",
       op.name AS "transport-operator-name",
       op.id AS "transport-operator-id"
  FROM "transport-service" ts
  JOIN "transport-operator" op ON ts."transport-operator-id" = op.id
 WHERE ts.published IS NOT NULL
   AND ts.id = :service-id;

-- name: fetch-gtfs-packages-for-service
SELECT p.id, p.created,
       to_char(lower(dr.daterange), 'dd.mm.yyyy') as "min-date",
       to_char(upper(dr.daterange), 'dd.mm.yyyy') as "max-date",
       id.url as "interface-url",
       id."download-status",
       concat(id."db-error", id."download-error") as error
  FROM gtfs_package p
  JOIN LATERAL gtfs_package_date_range(p.id) as dr (daterange) ON TRUE
  LEFT JOIN "external-interface-download-status" id ON id."transport-service-id" = :service-id AND id."package-id" = p.id
 WHERE p."transport-service-id" = :service-id AND p."deleted?" = FALSE
 ORDER BY p.created DESC;

-- name: detected-service-change-by-date
SELECT c.date, c."added-routes", c."removed-routes", c."changed-routes", c."no-traffic-routes", c."current-week-date",
       c."different-week-date", c."change-date", c.created, c."transport-service-id"
FROM "gtfs-transit-changes" c
WHERE c."transport-service-id" = :service-id
  AND c.date = :date::DATE;

-- name: detected-route-changes-by-date
-- Fetch changes for service joining detected-route-change table and detected-change-history table.
-- Take same change only once (distinct) due to some change detection issues there might be no-change type changes multiple times in database.
SELECT distinct h."change-detected", c."route-short-name", c."route-long-name", c."trip-headsign", c."route-hash-id", c."change-type", c."added-trips",
       c."removed-trips", c."trip-stop-sequence-changes-lower", c."trip-stop-sequence-changes-upper",
       c."trip-stop-time-changes-lower", c."trip-stop-time-changes-upper", c."current-week-date",
       c."different-week-date", c."change-date", c."created-date",
       h."email-sent" >= (SELECT MAX("email-sent") FROM "detected-change-history") AS "recent-change?"
  FROM "detected-route-change" c
       LEFT JOIN "detected-change-history" h ON h."transport-service-id" = c."transit-service-id" AND h."change-key" = c."change-key"
 WHERE c."transit-change-date" = :date
   AND c."transit-service-id" = :service-id;

-- name: latest-transit-changes-for-visualization
SELECT tc.date, tc."added-routes", tc."removed-routes", tc."changed-routes", tc."no-traffic-routes",
       string_agg(concat(p.id::TEXT,',', p.created::TEXT,',', p."deleted?"::TEXT,',', p."interface-deleted?"::TEXT,',',ds.url::TEXT), ';') AS "package-info"
  FROM "gtfs-transit-changes" tc
        JOIN gtfs_package p ON p.id = ANY(tc."package-ids") AND p."transport-service-id" = :service-id
        LEFT JOIN "external-interface-download-status" ds ON ds."external-interface-description-id" = p."external-interface-description-id"
 WHERE tc."transport-service-id" = :service-id
 GROUP BY tc.date,tc."added-routes", tc."removed-routes", tc."changed-routes", tc."no-traffic-routes"
 ORDER BY tc.date desc
 LIMIT 50;