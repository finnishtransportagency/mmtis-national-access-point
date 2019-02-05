ALTER TYPE "gtfs-route-change-info"
  ADD ATTRIBUTE "trip-stop-sequence-changes-lower" INTEGER;

ALTER TYPE "gtfs-route-change-info"
  ADD ATTRIBUTE "trip-stop-sequence-changes-upper" INTEGER;

ALTER TYPE "gtfs-route-change-info"
  DROP ATTRIBUTE "trip-stop-sequence-changes";

ALTER TYPE "gtfs-route-change-info"
  ADD ATTRIBUTE "trip-stop-time-changes-lower" INTEGER;

ALTER TYPE "gtfs-route-change-info"
  ADD ATTRIBUTE "trip-stop-time-changes-upper" INTEGER;

ALTER TYPE "gtfs-route-change-info"
  DROP ATTRIBUTE "trip-stop-time-changes";

CREATE TABLE "detected-route-change" (
 "transit-change-date"        DATE NOT NULL,
 "transit-service-id"         INTEGER NOT NULL references "transport-service" (id),
 "route-short-name"           TEXT,                   -- "15"
 "route-long-name"            TEXT NOT NULL,          -- "Helsinki-Oulu"
 "trip-headsign"              TEXT,
 "change-type"                "gtfs-route-change-type", 
 "added-trips"                INTEGER DEFAULT 0,
 "removed-trips"              INTEGER DEFAULT 0,
 "trip-stop-sequence-changes-lower"   INTEGER DEFAULT 0 NOT NULL, -- Range of change counts in trips (0 - 2), low limit
 "trip-stop-sequence-changes-upper"   INTEGER DEFAULT 0 NOT NULL, -- Range of change counts in trips (0 - 2), high limit
 "trip-stop-time-changes-lower"       INTEGER DEFAULT 0 NOT NULL, -- Range of stop time changes in trips (0 - 200), low limit
 "trip-stop-time-changes-upper"       INTEGER DEFAULT 0 NOT NULL, -- Range of stop time changes in trips (0 - 200), high limit
 "current-week-date"          DATE,                   -- Day of detection run
 "different-week-date"        DATE,                   -- The date when the change happens. Exception when we are in the middle no-traffic, date is the first day of traffic
 "change-date"                DATE,                   -- Date for next the detection run
 "route-hash-id"              TEXT NOT NULL,          -- routes key between gtfs packages ("route-short-name" - "route-long-name" - "trip-headsign")
 "created-date"               DATE,
 FOREIGN KEY ("transit-change-date", "transit-service-id") REFERENCES "gtfs-transit-changes" ("date", "transport-service-id"));

DO $$
  DECLARE
    r RECORD;
    c "gtfs-route-change-info";
  BEGIN
    FOR r IN SELECT date AS d, "transport-service-id" AS tsid, "route-changes" AS rc FROM "gtfs-transit-changes"
      LOOP
        FOREACH c IN ARRAY r.rc
          LOOP
            INSERT INTO "detected-route-change"
            VALUES (r.d, r.tsid,
                    c."route-short-name",
                    c."route-long-name",
                    c."trip-headsign",
                    c."change-type",
                    c."added-trips",
                    c."removed-trips",
                    COALESCE(lower(c."trip-stop-sequence-changes"), 0),
                    COALESCE(upper(c."trip-stop-sequence-changes"), 0), -- Live data has values like '[%,%)', upper returns value minus one which gives the right value
                    COALESCE(lower(c."trip-stop-time-changes"), 0),
                    COALESCE(upper(c."trip-stop-time-changes"), 0), -- Live data has values like '[%,%)', upper returns value minus one which gives the right value
                    c."current-week-date",
                    c."different-week-date",
                    c."change-date",
                    COALESCE(c."route-hash-id",
                             c."route-short-name" || '-' || c."route-long-name" || '-' || c."trip-headsign"),
                    NULL);
            -- raise notice 'd % c hs %', r.d, c."trip-headsign";
          END LOOP;
      END LOOP;
  END
$$ LANGUAGE plpgsql;

-- TODO: ALTER TABLE "gtfs-transit-changes"
--         DROP COLUMN "route-changes";

-- TODO: Add an index to this table to date and service-id
