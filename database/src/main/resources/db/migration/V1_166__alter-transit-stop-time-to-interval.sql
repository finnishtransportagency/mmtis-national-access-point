-- Normalize transit_trip and transit_trip into new tables
CREATE TABLE transit_route_trip
(
    id                     SERIAL PRIMARY KEY,
    "transit-route-id"     INTEGER REFERENCES "transit_route" (id) ON DELETE CASCADE,
    "service-calendar-idx" INTEGER,
    "stop-times"           transit_stop_time[] -- temporary column for to allow flattening data to transit_stop_time on migration
);
CREATE TABLE transit_route_stop_time
(
    id                            SERIAL PRIMARY KEY,
    "transit-trip-id"             INTEGER REFERENCES "transit_route_trip" (id) ON DELETE CASCADE,
    "stop-idx"                    INTEGER,
    "arrival-time"                INTERVAL, --INTERVAL, -- converted to INTERVAL to support 24h+ trips
    "departure-time"              INTERVAL, -- INTERVAL, -- converted to INTERVAL to support 24h+ trips
    "arrival-time-24h-deleteme"   TIME,     -- Temporary column, to be removed on future migration
    "departure-time-24h-deleteme" TIME,     -- Temporary column, to be removed on future migration
    "pickup-type"                 transit_stopping_type,
    "drop-off-type"               transit_stopping_type
);

INSERT INTO "transit_route_trip" ("transit-route-id",
                                  "service-calendar-idx",
                                  "stop-times")
    (SELECT r.id, t."service-calendar-idx", t."stop-times"
     FROM "transit_route" AS r,
          UNNEST(r.trips) AS t);

--
INSERT INTO transit_route_stop_time ("transit-trip-id",
                                     "stop-idx",
                                     "pickup-type",
                                     "drop-off-type",
                                     "arrival-time",
                                     "departure-time",
                                     "arrival-time-24h-deleteme",
                                     "departure-time-24h-deleteme")
    (SELECT t.id,
            s."stop-idx",
            s."pickup-type",
            s."drop-off-type",
            s."arrival-time"::INTERVAL,
            s."departure-time"::INTERVAL,
            s."arrival-time"::INTERVAL,
            s."departure-time"::INTERVAL
     FROM transit_route_trip AS t,
          UNNEST(t."stop-times") AS s);

-- Drop old replaced data structures
ALTER TABLE transit_route
    DROP COLUMN "trips";
ALTER TABLE transit_route_trip
    DROP COLUMN "stop-times";
DROP TYPE transit_trip CASCADE;
DROP TYPE transit_stop_time CASCADE;
