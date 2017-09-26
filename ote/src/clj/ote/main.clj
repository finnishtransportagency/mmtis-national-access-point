(ns ote.main
  "Liikenneviraston OTE: Olennaisten Tietojen Editori -sovellus.
  Järjestelmän käynnistys."
  (:require [com.stuartsierra.component :as component]
            [ote.komponentit.http :as http]
            [ote.komponentit.db :as db]
            [ote.palvelut.lokalisaatio :as lokalisaatio-palvelu]
            [ote.palvelut.transport :as transport-service]
            ))

(def ^{:doc "Ajossa olevan OTE-järjestelmän kahva"}
  ote nil)

(defn ote-system [asetukset]
  (component/system-map
   ;; Peruskomponentit
   :db (db/tietokanta (:db asetukset))
   :http (http/http-palvelin (:http asetukset))

   ;; Frontille tarjottavat palvelut
   :transport (component/using (transport-service/->Transport) [:http :db])

   ;; Käännöstiedostojen haku
   :lokalisaatio (component/using (lokalisaatio-palvelu/->Lokalisaatio) [:http])))

(defn kaynnista []
  (alter-var-root
   #'ote
   (constantly
    (-> "asetukset.edn" slurp read-string ; lue asetukset
        ote-system ; luo järjestelmä
        component/start-system))))

(defn sammuta []
  (component/stop-system ote)
  (alter-var-root #'ote (constantly nil)))

(defn restart []
  (when ote
    (sammuta))
  (kaynnista))

(defn -main [& args]
  (kaynnista))
