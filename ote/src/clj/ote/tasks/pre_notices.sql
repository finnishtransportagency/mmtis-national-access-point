-- name: fetch-pre-notices-by-interval
SELECT id, "pre-notice-type", "route-description", created, modified,
  (SELECT array_agg(fr.nimi)
    FROM "finnish_regions" fr
   WHERE n.regions IS NOT NULL AND fr.numero = ANY(n.regions)) as "regions",
  (SELECT op.name
     FROM "transport-operator" op
    WHERE op.id = n."transport-operator-id") as "operator-name"
  FROM "pre_notice" n
 WHERE "pre-notice-state" = 'sent' AND
       (created > (current_timestamp - :interval::interval) OR modified > (current_timestamp - :interval::interval));