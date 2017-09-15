#!/usr/bin/env bash

set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

IMAGE=${1:-solita/napotedb}

if ! docker images | grep $IMAGE >> /dev/null; then
    echo "Imagea" $IMAGE "ei löydetty. Yritetään pullata."
    if ! docker pull $IMAGE; then
        echo $IMAGE "ei ole docker hubissa. Buildataan."
        docker build -t $IMAGE . ;
    fi
    echo ""
fi

docker run -p 5432:5432 --name napotedb -dit $IMAGE 1> /dev/null

echo "Käynnistetään Docker-image" $IMAGE
echo ""
docker images | head -n1
docker images | grep $IMAGE

echo ""
echo "Odotetaan, että PostgreSQL on käynnissä ja vastaa yhteyksiin portissa 5432"
while ! nc -z localhost 5432; do
    sleep 0.5;
done;

bash $DIR/devdb_migrate.sh

echo ""
echo "Napoten tietokanta käynnissä! Imagen tiedot:"
echo ""

docker images | head -n1
docker images | grep $IMAGE
