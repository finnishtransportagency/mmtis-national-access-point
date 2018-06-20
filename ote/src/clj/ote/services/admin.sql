-- name: fetch-service-business-ids
   SELECT c.name as "operator", c."business-id" as "business-id",
          s."contact-phone" as "phone", s."contact-gsm" as "gsm", s."contact-email" as "email", CAST('service' AS text) as "source"
     FROM "transport-service" s
          LEFT JOIN service_company sc ON sc."transport-service-id" = s.id
          LEFT JOIN LATERAL unnest(COALESCE(sc.companies, s.companies)) AS c ON TRUE
    WHERE c."business-id" IS NOT NULL
      AND s."published?" = TRUE;

-- name: fetch-operator-business-ids
SELECT o.id, o."name" as "operator", o."business-id" as "business-id",
       o."phone" as "phone", o."gsm" as "gsm", o."email" as "email", CAST('operator' AS text) as "source"
  FROM "transport-operator" o
 WHERE o."business-id" IS NOT NULL;

-- name: delete-transport-operator
-- Delete all operator data except published external interface data from ckan
SELECT del_operator(:operator-group-name);

-- name: search-interfaces
-- Find published interfaces using operator name, service name
SELECT i.id as "interface-id", ts.id as "service-id",
op.id as "operator-id", op.name as "operator-name", op.email as "operator-email", op.phone as "operator-phone", op.gsm as "operator-gsm",
ts.name as "service-name", ts."contact-phone" as "service-phone", ts."contact-email" as "service-email",
i."data-content" as "data-content", (i."external-interface").url as url,
i.format as format, i."gtfs-imported" as imported, i."gtfs-import-error" as "import-error", i."gtfs-db-error" as "db-error"
  FROM "transport-operator" as op, "transport-service" as ts, "external-interface-description" as i
 WHERE (:operator-name::TEXT IS NULL OR op.name ilike :operator-name)
  AND (:service-name::TEXT IS NULL OR ts.name ilike :service-name)
  AND (:interface-url::TEXT IS NULL OR (i."external-interface").url ilike :interface-url)
  AND (:import-error::BOOLEAN IS NULL OR i."gtfs-import-error" IS NOT NULL)
  AND (:db-error::BOOLEAN IS NULL OR i."gtfs-db-error" IS NOT NULL)
  AND (:interface-format::TEXT IS NULL OR :interface-format = ANY(lower(i.format::text)::text[]))
   AND ts."published?" = TRUE
   AND ts."transport-operator-id" = op.id
   AND i."transport-service-id" = ts.id
   AND ('gtfs' = ANY(lower(i.format::text)::text[]) OR 'kalkati.net' = ANY(lower(i.format::text)::text[]))
 GROUP BY ts.id, op.id, i.id
 ORDER BY i.format ASC, i."gtfs-import-error" DESC;
