(ns ote.db.netex
  "Database configurations for Netex Conversions"
  (:require [clojure.spec.alpha :as s]
            #?(:clj [ote.db.specql-db :refer [define-tables]])
            [specql.rel :as rel]
            [specql.transform :as xf]
            [specql.impl.registry]
            [ote.db.common :as common]
            [specql.data-types])
  #?(:cljs
     (:require-macros [ote.db.specql-db :refer [define-tables]])))

(define-tables
  ;; Tables
  ["netex-conversion" ::netex-conversion]

  ;; Enums
  ["netex_conversion_status" ::netex_conversion_status (specql.transform/transform (specql.transform/to-keyword))]
  ["interface_data_content" ::interface-data-content (specql.transform/transform (specql.transform/to-keyword))])
