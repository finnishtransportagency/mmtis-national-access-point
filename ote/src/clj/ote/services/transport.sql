-- name: fetch-transport-operator-ckan-description
-- single?: true
SELECT description FROM "group" WHERE id = :id;

-- name: fetch-transport-services
SELECT ts.id,
    ts."transport-operator-id",
    ts."name",
    ts."type",
    ts."sub-type",
    ts."published?",
    ts."created",
    ts."modified"
FROM "transport-service" ts
WHERE ts."transport-operator-id" in (:operator-ids)
ORDER BY ts."type" DESC, ts.modified DESC NULLS LAST;
