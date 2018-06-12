#!/bin/sh

### THIS IS MEANT TO BE RUN INSIDE our solita/napote-circleci CONTAINERS ONLY!

P="psql -h localhost -p 5432 -U postgres"
$P -f ../nap/ckan-initial-db.sql napote

mvn flyway:migrate

$P -f testdata-ckan.sql napote
$P -f testdata-ote.sql napote

if [ -f finnish_municipalities.csv ]; then
    echo "Insert Finnish municipalities"
    $P napote -c "TRUNCATE finnish_municipalities;" || true
    $P napote -c "\COPY finnish_municipalities FROM finnish_municipalities.csv CSV HEADER;" || true
fi

if [ -f maakunnat.csv ]; then
    echo "Insert Finnish regions"
    $P napote -c "TRUNCATE finnish_regions;" || true
    $P napote -c "\COPY finnish_regions FROM maakunnat.csv CSV HEADER;" || true
fi


echo "Clean up and free connections"
$P -c "SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity WHERE pg_stat_activity.datname = 'napote' AND pid <> pg_backend_pid();"

echo "Drop napotetest databases if they exists"
$P -c "DROP DATABASE IF EXISTS napotetest_template;"
$P -c "DROP DATABASE IF EXISTS napotetest;"

$P -c "CREATE DATABASE napotetest_template WITH TEMPLATE napote OWNER napotetest;"
$P -c "CREATE DATABASE napotetest WITH TEMPLATE napotetest_template OWNER napotetest;"
