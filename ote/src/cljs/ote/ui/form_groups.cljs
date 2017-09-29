(ns ote.ui.form-groups
  "Contains reusable form elements."
  (:require [ote.ui.form :as form]
            [ote.db.common :as common]))


(defn address
  "Creates a form group for address that creates three form elements street, post-office and postal-code."
  [label address-field]
  (form/group label
              {:name ::common/street
               :type :string
               :read (comp ::common/street address-field)
               :write (fn [data street]
                        (assoc-in data [address-field ::common/street] street))}

              {:name ::common/post-office
               :type :string
               :read (comp ::common/postal_office address-field)
               :write (fn [data post-office]
                        (assoc-in data [address-field ::common/post-office] post-office))}

              {:name ::common/postal-code
               :type :string
               :read (comp ::common/postal_code address-field)
               :write (fn [data postal-code]
                        (assoc-in data [address-field ::common/postal-code] postal-code))}))