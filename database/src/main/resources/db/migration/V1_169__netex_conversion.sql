CREATE TYPE netex_conversion_status AS ENUM ('ok', 'error');

CREATE TABLE "netex-conversion" (
    id                                  SERIAL PRIMARY KEY,
    "external-interface-description-id" INTEGER NOT NULL references "external-interface-description" (id) ON DELETE CASCADE,
    "transport-service-id"              INTEGER NOT NULL references "transport-service" (id) ON DELETE CASCADE,
    "url"                               TEXT NOT NULL,
    "status"                            netex_conversion_status,
    "modified"                          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    "created"                           TIMESTAMP WITH TIME ZONE DEFAULT NOW());
