-- name: select-gtfs-urls-update
SELECT eid.id as id, (eid."external-interface").url, eid.format[1], eid.license,
       ts."transport-operator-id" as "operator-id", top."name" as "operator-name", ts.id as "ts-id",
       eid."gtfs-imported" as "last-import-date"
  FROM "external-interface-description" eid
  JOIN "transport-service" ts ON eid."transport-service-id" = ts.id
  JOIN "transport-operator" top ON top.id = ts."transport-operator-id"
 WHERE ts.published IS NOT NULL
   AND top.id NOT IN (:blacklist)
   AND ('GTFS' = ANY(eid.format) OR 'Kalkati.net' = ANY(eid.format))
   AND ( "gtfs-imported" < (current_timestamp - '1 day'::interval) OR "gtfs-imported" IS NULL)
 ORDER BY "gtfs-imported" ASC LIMIT 1
   FOR UPDATE SKIP LOCKED;

-- name: select-gtfs-url-for-service
SELECT eid.id as id, (eid."external-interface").url, eid.format[1], eid.license,
       ts."transport-operator-id" as "operator-id", top."name" as "operator-name", ts.id as "ts-id",
       eid."gtfs-imported" as "last-import-date",
       eid."data-content" as "data-content"
  FROM "external-interface-description" eid
  JOIN "transport-service" ts ON eid."transport-service-id" = ts.id
  JOIN "transport-operator" top ON top.id = ts."transport-operator-id"
 WHERE ts.published IS NOT NULL
   AND ts.id = :service-id
   AND ('GTFS' = ANY(eid.format) OR 'Kalkati.net' = ANY(eid.format));

-- name: services-for-nightly-change-detection
SELECT ts.id
  FROM "transport-service" ts
 WHERE ts."sub-type" = 'schedule'
   AND EXISTS(SELECT id
                FROM "external-interface-description" eid
               WHERE ('GTFS' = ANY(eid.format) OR 'Kalkati.net' = ANY(eid.format))
                 AND 'route-and-schedule' = ANY(eid."data-content")
                 AND eid."transport-service-id" = ts.id)
   AND (:force = TRUE OR gtfs_should_calculate_transit_change(ts.id));
