--- Add Spatial indices for increasing search performance on spatial joins
CREATE INDEX operation_area_gix ON operation_area USING GIST (location);
CREATE INDEX finnish_municipalities_gix ON finnish_municipalities USING GIST (location);
CREATE INDEX finnish_regions_gix ON finnish_regions USING GIST (location);
CREATE INDEX country_gix ON country USING GIST (location);
CREATE INDEX continent_gix ON continent USING GIST (location);
CREATE INDEX finnish_postal_codes_gix ON finnish_postal_codes USING GIST (location);

-- DROP views to allow updating depending columns
DROP VIEW operation_area_geojson RESTRICT;
DROP VIEW places RESTRICT;

--- Update SRIDs to location columns to make Spatial joins more efficient
--- Data is already in this projection
SELECT UpdateGeometrySRID('operation_area','location',4326);
SELECT UpdateGeometrySRID('finnish_regions','location',4326);
SELECT UpdateGeometrySRID('country','location',4326);
SELECT UpdateGeometrySRID('continent','location',4326);

-- Rebuild operation_area_geojson
CREATE VIEW operation_area_geojson AS
 SELECT oa.*, ST_AsGeoJSON(oa.location) AS "location-geojson"
   FROM operation_area oa;

--- Recreate view
CREATE OR REPLACE VIEW places AS
 SELECT CONCAT('finnish-municipality-', natcode) AS id,
        'finnish-municipality' as type,
        namefin,
        nameswe,
        ST_FlipCoordinates(ST_SetSRID(location, 4326)) as location
   FROM finnish_municipalities
UNION ALL
 SELECT CONCAT('finnish-postal-',posti_alue) as id,
        'finnish-postal' as type,
        CONCAT(posti_alue,' ',nimi) AS namefin,
        CONCAT(posti_alue,' ',namn) AS nameswe,
        ST_SetSRID(location, 4326)
   FROM finnish_postal_codes
UNION ALL
 SELECT CONCAT('finnish-region-',numero) as id,
        'finnish-region' AS type,
        nimi AS namefin,
        '' AS nameswe,
        location
   FROM finnish_regions
UNION ALL
 SELECT CONCAT('country-',code) as id,
        'country' AS type,
        namefin,
        nameswe,
        location
   FROM country
UNION ALL
 SELECT CONCAT('continent-',code) as id,
        'continent' AS type,
        namefin,
        nameswe,
        location
   FROM continent;

CREATE TABLE "spatial-search-tree" (
  inside TEXT,
  outside TEXT,
  weight REAL);

CREATE INDEX "spatial-search-inside-idx" on "spatial-search-tree" (inside);
CREATE INDEX "spatial-search-outside-idx" on "spatial-search-tree" (outside);
