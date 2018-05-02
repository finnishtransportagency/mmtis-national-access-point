-- Update gtfs_package to work with foreign keys

ALTER TABLE "gtfs_package"
 DROP CONSTRAINT "gtfs_package_transport-operator-id_fkey",
 DROP CONSTRAINT "gtfs_package_transport-service-id_fkey",
  ADD CONSTRAINT "gtfs_package_transport-operator-id_fkey"
      FOREIGN KEY ("transport-operator-id")
      REFERENCES "transport-operator" (id)
      ON DELETE CASCADE,
  ADD CONSTRAINT "gtfs_package_transport-service-id_fkey"
      FOREIGN KEY ("transport-service-id")
      REFERENCES "transport-service" (id)
      ON DELETE CASCADE;


-- GTFS package as database tables

CREATE TABLE "gtfs-agency"
(
  id                  SERIAL PRIMARY KEY,
  "package-id"		  INTEGER NOT NULL REFERENCES gtfs_package (id), 	
  "agency-id"         text,
  "agency-name"       text NOT NULL,
  "agency-url"        text NOT NULL,
  "agency-timezone"   text NOT NULL,
  "agency-lang"       text,
  "agency-phone"      text
);

CREATE TABLE "gtfs-stop"
(
  id                  SERIAL PRIMARY KEY,
  "package-id"		  INTEGER NOT NULL REFERENCES gtfs_package (id), 	
  "stop-id"           text NOT NULL,
  "stop-code"         text,
  "stop-name"         text NOT NULL,
  "stop-desc"         text,
  "stop-lat"          numeric NOT NULL,
  "stop-lon"          numeric NOT NULL,
  "zone-id"           text,
  "stop-url"          text,
  "location-type"     INTEGER CHECK ("location-type" BETWEEN 0 AND 2), -- 0,1,2
  "parent-station"    text 
);

CREATE TABLE "gtfs-route"
(
  id                  SERIAL PRIMARY KEY,
  "package-id"		  INTEGER NOT NULL REFERENCES gtfs_package (id),  
  "route-id"          text,
  "agency-id"         text,
  "route-short-name"  text,
  "route-long-name"   text,
  "route-desc"        text,
  "route-type"        integer,
  "route-url"         text,
  "route-color"       text,
  "route-text-color"  text
);

CREATE TABLE "gtfs-calendar"
(
  id                  SERIAL PRIMARY KEY,
  "package-id"		  INTEGER NOT NULL REFERENCES gtfs_package (id),  
  "service-id"        text,
  "monday"            boolean NOT NULL,
  "tuesday"           boolean NOT NULL,
  "wednesday"         boolean NOT NULL,
  "thursday"          boolean NOT NULL,
  "friday"            boolean NOT NULL,
  "saturday"          boolean NOT NULL,
  "sunday"            boolean NOT NULL,
  "start-date"        DATE NOT NULL,
  "end-date"          DATE NOT NULL
);

CREATE TABLE "gtfs-calendar-date"
(
  id                  SERIAL PRIMARY KEY,
  "package-id"		  INTEGER NOT NULL REFERENCES gtfs_package (id), 	  
  "service-id" 		  text NOT NULL,
  "date" 			  DATE NOT NULL,
  "exception-type"    integer NOT NULL
);

CREATE TABLE "gtfs-shape"
(
  id                  SERIAL PRIMARY KEY,
  "package-id"		  INTEGER NOT NULL REFERENCES gtfs_package (id), 	  
  "shape-id"          text,
  "shape-pt-lat"      NUMERIC NOT NULL,
  "shape-pt-lon"      NUMERIC NOT NULL,
  "shape-pt-sequence" integer NOT NULL,
  "shape-dist-traveled" numeric
);

CREATE TABLE "gtfs-trip"
(
  id                  SERIAL PRIMARY KEY,
  "package-id"		  INTEGER NOT NULL REFERENCES gtfs_package (id), 	  
  "route-id"          text NOT NULL,
  "service-id"        text NOT NULL,
  "trip-id"           text NOT NULL,
  "trip-headsign"     text,
  "direction-id"      integer CHECK ("direction-id" IN (0,1)),
  "block-id"          text,
  "shape-id"          text
);

CREATE TABLE "gtfs-stop-time"
(
  id                  SERIAL PRIMARY KEY,
  "package-id"		  INTEGER NOT NULL REFERENCES gtfs_package (id), 	  
  "trip-id"           text NOT NULL,
  "arrival-time"      interval NOT NULL,
  "departure-time"    interval NOT NULL,
  "stop-id"           text NOT NULL,
  "stop-sequence"     integer NOT NULL,
  "stop-headsign"     text,
  "pickup-type"       integer NULL CHECK("pickup-type" BETWEEN 0 AND 3),
  "drop-off-type"     integer NULL CHECK("drop-off-type" BETWEEN 0 AND 3),
  "shape-dist-traveled" numeric
);

CREATE TABLE "gtfs-transfer"
(
  id                  SERIAL PRIMARY KEY,
  "package-id"		  INTEGER NOT NULL REFERENCES gtfs_package (id), 	  
  "from-stop-id"  	  text NOT NULL,
  "to-stop-id"        text NOT NULL,
  "transfer-type"     integer NOT NULL,
  "min-transfer-time" integer
);