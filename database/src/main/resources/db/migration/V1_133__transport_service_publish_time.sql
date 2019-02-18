ALTER TABLE "transport-service" ADD COLUMN published TIMESTAMP WITH TIME ZONE;

UPDATE "transport-service"
SET published = to_timestamp(0)
WHERE "published?" = true;

DROP VIEW transport_service_search_result;

-- CAN'T ALTER TABLE WHEN WE HAVE PENDING TRIGGER EVENTS SO WE NEED REPLACE THESE FUNCTIONS BEFORE ALTERING TABLE

-- Create trigger function to update facet when service changes
CREATE OR REPLACE FUNCTION transport_service_operation_area_array () RETURNS TRIGGER AS $$
BEGIN

  -- Delete all previous entries for this service
  DELETE
  FROM "operation-area-facet"
  WHERE "transport-service-id" = NEW.id;

  IF NEW.published IS NOT NULL THEN
    -- Insert new values (for published only)
    INSERT
    INTO "operation-area-facet"
    ("transport-service-id", "operation-area")
    SELECT NEW.id, LOWER(oad.text)
    FROM operation_area oa
           JOIN LATERAL unnest(oa.description) AS oad ON TRUE
    WHERE oa."transport-service-id" = NEW.id
      AND oad.text IN (SELECT p.namefin FROM "places" p);
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION store_daily_company_stats () RETURNS VOID AS $$
INSERT INTO company_stats ("date", "count")
VALUES ((SELECT current_date - 1),
        (SELECT SUM(COALESCE(
            (SELECT array_length(companies,1)
             FROM service_company sc
             WHERE sc."transport-service-id" = ts.id),
            array_length(ts.companies,1),
            0))
         FROM "transport-service" ts
         WHERE ts.published IS NOT NULL))
ON CONFLICT ("date") DO
  UPDATE SET "count" =  EXCLUDED."count";
$$ LANGUAGE SQL;

ALTER TABLE "transport-service" DROP COLUMN "published?";

CREATE OR REPLACE VIEW transport_service_search_result AS
SELECT t.*, op.name as "operator-name", op."business-id" as "business-id",
       (SELECT sc."companies"
        FROM "service_company" sc
        WHERE sc."transport-service-id" = t.id) AS "service-companies",
       (SELECT array_agg(oaf."operation-area")
        FROM "operation-area-facet" oaf
        WHERE oaf."transport-service-id" = t.id) AS "operation-area-description",
       (SELECT array_agg(ROW(ei."external-interface", ei.format,
         ei."ckan-resource-id",
         ei.license, ei."data-content",
         ei."gtfs-import-error",
         ei."gtfs-db-error")::external_interface_search_result)
        FROM "external-interface-description" ei
        WHERE ei."transport-service-id" = t.id)::external_interface_search_result[] AS "external-interface-links"
FROM "transport-service" t
       JOIN  "transport-operator" op ON op.id = t."transport-operator-id";

