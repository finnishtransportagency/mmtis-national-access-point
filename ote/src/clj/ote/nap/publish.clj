(ns ote.nap.publish
  "Publish transport-service as CKAN dataset."
  (:require [ote.nap.ckan :as ckan]
            [ote.db.transport-operator :as t-operator]
            [ote.db.transport-service :as t-service]
            [ote.db.operation-area :as operation-area]
            [specql.core :refer [fetch] :as specql]
            [clojure.string :as str]
            [taoensso.timbre :as log]))

(defn- fetch-service-operation-area-description
  "Fetch the operation area as a comma separated list (for SOLR facet search).
  Takes all operation areas for the transport service and joins their description
  texts into a single string."
  [db service-id]
  (->> (fetch db ::operation-area/operation-area
              #{::operation-area/description}
              {::operation-area/transport-service-id service-id})
       (mapcat ::operation-area/description)
       (map ::t-service/text)
       (str/join ", ")))

(defn- ckan-dataset-description
  "Create a CKAN dataset description that can be used with the CKAN API to
  create a dataset. The owner organization is the user's organization."
  [db user {ds-id ::t-service/ckan-dataset-id service-id ::t-service/id :as ts}]
  (merge
   {:ckan/name (str "org-" (::t-service/transport-operator-id ts)
                    "-service-" service-id)
    :ckan/title (::t-service/name ts)
    :ckan/owner-org (get-in user [:group :name])
    :ckan/transport-service-type (name (::t-service/type ts))
    :ckan/operation-area (fetch-service-operation-area-description db service-id)}

   ;; If dataset has already been created, add its id
   (when ds-id
     {:ckan/id ds-id})))

(defmulti interface-description ::t-service/type)

(defmethod interface-description :default [ts]
  {:ckan/url (str "/ote/export/geojson/"
                  (::t-service/transport-operator-id ts) "/"
                  (::t-service/id ts))
   :ckan/name (str (::t-service/name ts) " GeoJSON")
   :ckan/format "GeoJSON"})


(defn- ckan-resource-description
  "Create a CKAN resource description that can be used with the CKAN API to
  create a resource."
  [export-base-url ts {:ckan/keys [id name] :as ckan-dataset}]
  (merge {:ckan/package-id id}
         (update (interface-description ts)
                 :ckan/url #(str export-base-url %))))

(defn- verify-ckan-response
  "Check that CKAN API call response was successful and return the result.
  If the API call failed, log an error and throw an exception."
  [{:ckan/keys [success result] :as response}]
  (if-not success
    (do
      (log/error "CKAN API call did not succeed: " response)
      (throw (ex-info "CKAN API call failed" response)))
    result))

(def transport-operator-descriptor-columns
  "Columns we need to fetch for a transport service when creating the dataset
  descriptor."
  #{::t-service/id ::t-service/transport-operator-id
    ::t-service/name ::t-service/type
    ::t-service/ckan-dataset-id ::t-service/ckan-resource-id})

(defn- fetch-transport-service [db id]
  (first
   (fetch db ::t-service/transport-service transport-operator-descriptor-columns
          {::t-service/id id})))

(defn- fetch-transport-service-external-interfaces [db id]
  (fetch db ::t-service/external-interface-description
         #{::t-service/external-interface ::t-service/format
           ::t-service/ckan-resource-id ::t-service/id}
         {::t-service/transport-service-id id}))

(defn publish-service-to-ckan!
  "Use CKAN API to creata a dataset (package) and resource for the given transport service id."
  [{:keys [api export-base-url] :as nap-config} db user transport-service-id]
  (let [c (ckan/->CKAN api (get-in user [:user :apikey]))
        ts (fetch-transport-service db transport-service-id)
        dataset (->> ts
                     (ckan-dataset-description db user)
                     (ckan/create-or-update-dataset! c)
                     verify-ckan-response)
        resource (->> dataset
                      (ckan-resource-description export-base-url ts)
                      (ckan/add-or-update-dataset-resource! c)
                      verify-ckan-response)

        external-interfaces (fetch-transport-service-external-interfaces db transport-service-id)
        external-resources
        (mapv (fn [{ei ::t-service/external-interface fmt ::t-service/format}]
                (verify-ckan-response
                 (ckan/add-or-update-dataset-resource!
                  c (merge
                     {:ckan/package-id (:ckan/id dataset)
                      :ckan/name (-> ei ::t-service/description first ::t-service/text)
                      :ckan/url (::t-service/url ei)
                      :ckan/format fmt}))))
              external-interfaces)]

    ;; Update CKAN resource ids for all external interfaces
    (doall
     (map (fn [{id ::t-service/id} {ckan-resource-id :ckan/id}]
            (specql/update! db ::t-service/external-interface-description
                            {::t-service/ckan-resource-id ckan-resource-id}
                            {::t-service/id id}))
          external-interfaces external-resources))

    ;; Update CKAN dataset and resource ids
    (specql/update! db ::t-service/transport-service
                    {::t-service/ckan-dataset-id (:ckan/id dataset)
                     ::t-service/ckan-resource-id (:ckan/id resource)}
                    {::t-service/id transport-service-id})

    {:dataset dataset
     :resource resource
     :external-resources external-resources}))
