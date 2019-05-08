-- TODO improvements for later could include parametrization of certain more generic queries.

-- name: fetch-operators-no-services
SELECT op.name, op.id, op.phone, COALESCE(NULLIF(op.email, ''), u.email) AS "email"
  FROM "transport-operator" op
         JOIN "user" u ON u.name = (SELECT author
                                      FROM "revision" r
                                             JOIN "group" g ON op."ckan-group-id" = g.id
                                     WHERE r.id = g."revision_id"
                                     LIMIT 1)
 WHERE (SELECT COUNT(*) FROM "transport-service" ts WHERE ts."transport-operator-id" = op.id) = 0
 ORDER BY op.name ASC;

--name: fetch-all-emails
SELECT u."email" as email
  FROM public.user u
 WHERE email IS NOT NULL
   AND u.state = 'active'

UNION

SELECT t."contact-email" AS email
  FROM "transport-service" t
 WHERE t."contact-email" IS NOT NULL

UNION

SELECT o.email AS email
  FROM "transport-operator" o
 WHERE o."deleted?" = FALSE
   AND o.email IS NOT NULL;

--name: fetch-operators-brokerage
SELECT op.name,
       op."business-id",
       COALESCE(NULLIF(op.phone, ''), NULLIF(ts."contact-phone", ''), ts."contact-gsm") AS phone,
       COALESCE(op.email, ts."contact-email", u.email) AS email,
       ts.name AS "service-name"
  FROM "transport-operator" op
  JOIN "transport-service" ts ON ts."transport-operator-id" = op.id
  JOIN "user" u ON u.id = ts."created-by"
 WHERE ts."brokerage?" IS TRUE
   AND ts.published IS NOT NULL
 ORDER BY op.name ASC;

-- name: fetch-operators-with-sub-contractors
-- In the first selection select operators that do not have any sub companies.
-- Then select all operators that have sub companies in transport-service table, but don't have anything in service_company table.
-- And finally select all operators that have sub companies from external url or from csv upload.
SELECT op.name as "operator", op."business-id" as "business-id", '-' as "sub-company", '-' as "sub-business-id",
       ts.name as "service-name",
        replace(
          replace(
            replace(
             replace(array_to_string(ts."transport-type",','),
             'road' , 'Tieliikenne')
             ,'sea', 'Merenkulku')
             ,'aviation', 'Ilmailu')
             ,'rail', 'Raideliikenne') as "transport-type"

  FROM "transport-operator" op, "transport-service" ts
 WHERE op.id = ts."transport-operator-id"
   AND ts."sub-type" = :subtype::transport_service_subtype
   AND op."deleted?" = FALSE
   AND ts.published IS NOT NULL
   AND (ts.companies = '{}' OR ts.companies = '{"(,)"}' OR ts.companies IS NULL)

UNION

SELECT op.name as "operator", op."business-id" as "business-id", c.name as "sub-company", c."business-id" as "sub-business-id",
       ts.name as "service-name",
        replace(
          replace(
            replace(
             replace(array_to_string(ts."transport-type",','),
             'road' , 'Tieliikenne')
             ,'sea', 'Merenkulku')
             ,'aviation', 'Ilmailu')
             ,'rail', 'Raideliikenne') as "transport-type"

  FROM "transport-operator" op, "transport-service" ts, unnest(ts.companies) with ordinality c
 WHERE op.id = ts."transport-operator-id"
   AND ts."sub-type" = :subtype::transport_service_subtype
   AND op."deleted?" = FALSE
   AND ts.published IS NOT NULL
   AND ts.companies  IS NOT NULL
   AND ts.companies != '{}'
   AND ts.companies != '{"(,)"}'
   AND ts.id NOT IN (select sc."transport-service-id" FROM service_company sc WHERE sc.companies != '{}')

 UNION

 SELECT * FROM (
   SELECT op.name as "operator", op."business-id" as "business-id", c.name as "sub-company", c."business-id" as "sub-business-id",
         ts.name as "service-name",
          replace(
            replace(
              replace(
               replace(array_to_string(ts."transport-type",','),
               'road' , 'Tieliikenne')
               ,'sea', 'Merenkulku')
               ,'aviation', 'Ilmailu')
               ,'rail', 'Raideliikenne') as "transport-type"

     FROM "transport-operator" op, "transport-service" ts, service_company sc, unnest(sc.companies) with ordinality c
    WHERE op.id = ts."transport-operator-id"
      AND ts."sub-type" = :subtype::transport_service_subtype
      AND op."deleted?" = FALSE
      AND ts.published IS NOT NULL
      AND sc."transport-service-id" = ts.id
    ORDER BY op.id ASC)
 AS x;

--name: fetch-operators-unpublished-services
SELECT x.*,
       (SELECT string_agg(CONCAT("name", ' (Muokattu: ',
                                 to_char(COALESCE("modified", "created"), 'mm.dd.yyyy hh24\:mi\:ss')::VARCHAR, ')'),
                          E',\n' ORDER BY name, modified)
          FROM "transport-service"
         WHERE "transport-operator-id" = x."op-id"
           AND published IS NULL) AS services

  FROM (SELECT op.name,
               op.id AS "op-id",
               (SELECT COALESCE(NULLIF(op.phone, ''), NULLIF(ts."contact-phone", ''), ts."contact-gsm") AS phone
                  FROM "transport-service" ts
                 WHERE "transport-operator-id" = op.id
                 LIMIT 1),
               COALESCE(NULLIF(op.email, ''), u.email) email,
               (SELECT COUNT(*)
                  FROM "transport-service" ts
                 WHERE ts."transport-operator-id" = op.id
                   AND ts.published IS NULL) AS "unpublished-services-count"

          FROM "transport-operator" op
          JOIN "user" u ON u.name = (SELECT author
                                       FROM "revision" r
                                              JOIN "group" g ON op."ckan-group-id" = g.id
                                      WHERE r.id = g."revision_id"
                                      LIMIT 1)
         ORDER BY op.name ASC) x
 WHERE "unpublished-services-count" > 0;

-- name: fetch-operators-with-payment-services
SELECT top.name AS "operator", top."business-id" AS "business-id",
ts.name AS "service-name", ts."contact-address" AS "service-address",
CASE ts."sub-type"::TEXT
	WHEN 'terminal' THEN  'Asemat, satamat ja muut terminaalit'
	WHEN 'taxi' THEN  'Taksiliikenne (tieliikenne)'
	WHEN 'request' THEN  'Tilausliikenne ja muu kutsuun perustuva liikenne'
	WHEN 'rentals' THEN  'Liikennevälineiden vuokrauspalvelut ja kaupalliset yhteiskäyttöpalvelut'
	WHEN 'schedule' THEN  'Säännöllinen aikataulun mukainen liikenne'
	WHEN 'parking' THEN  'Yleiset kaupalliset pysäköintipalvelut'
	WHEN 'brokerage' THEN  'Välityspalvelut'
	ELSE 'Tuntematon'
END	AS "service-type",
(eid."external-interface").url AS "url", eid.format AS "format", eid.license AS licence
  FROM "transport-operator" top
  JOIN "transport-service" ts ON top.id = ts."transport-operator-id" AND ts.published IS NOT NULL
  JOIN "external-interface-description" eid ON ts.id = eid."transport-service-id"
 WHERE 'payment-interface' = ANY(eid."data-content");

-- name: monthly-registered-companies
-- returns a cumulative sum of companies, defined as distinct business-id's of "all-companies", created up until the row's month.
SELECT to_char(ac.created, 'YYYY-MM') AS month,
       sum(count(ac."business-id")) OVER (ORDER BY to_char(ac.created, 'YYYY-MM')) AS sum
FROM
  (SELECT DISTINCT ON ("business-id") "business-id", created FROM "all-companies") ac
GROUP BY month
ORDER BY month;

-- name: tertile-registered-companies
-- returns a cumulative sum of companies, defined as distinct business-id's of operators, created up until the row's tertile.
SELECT
       concat(to_char(date_trunc('year', ac.created), 'YYYY'), ' ',floor(extract(month FROM ac.created)::int / 4.00001) + 1 , '/3 ') as tertile,
       sum(count(ac."business-id")) over (ORDER BY  concat(to_char(date_trunc('year', ac.created), 'YYYY'), ' ',floor(extract(month FROM ac.created)::int / 4.00001) + 1 , '/3 '))
   FROM
     (select DISTINCT ON ("business-id") "business-id", created FROM "all-companies") ac
group by tertile
order by tertile;

-- name: operator-type-distribution
-- returns a distrubution of transport-service sub-types among all transport services
SELECT ac."sub-type" as "sub-type", count(ac."business-id") as count
  FROM "all-companies" ac
 GROUP BY ac."sub-type"
 ORDER BY ac."sub-type" ASC;

-- subquery name legend is: stq = subtype grouping query, biq = business-id grouping query
-- 
-- name: monthly-producer-types-and-counts
 SELECT to_char(ac.created, 'YYYY-MM') as month,
        count(ac."sub-type") as sum,
        ac."sub-type"
   FROM
        "all-companies" ac
 GROUP BY month, ac."sub-type"
 ORDER BY month, ac."sub-type";

-- name: tertile-producer-types-and-counts
SELECT count(ac."sub-type") as sum,
       ac."sub-type" as "sub-type",
       concat(to_char(date_trunc('year', ac.created), 'YYYY'), ' ', (floor(extract(month FROM ac.created)::int / 4.00001)) + 1 , '/3 ') as tertile
  FROM
       "all-companies" ac
 GROUP BY tertile, ac."sub-type"
 ORDER BY tertile, ac."sub-type";


