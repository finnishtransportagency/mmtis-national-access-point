(ns ote.db.common
  "Database configurations for common Types"
  (:require [clojure.spec.alpha :as s]
    #?(:clj [ote.tietokanta.specql-db :refer [define-tables]])
            [specql.rel :as rel]
            [specql.transform :as xf]
            [specql.impl.registry]
            [specql.data-types])
  #?(:cljs
     (:require-macros [ote.tietokanta.specql-db :refer [define-tables]])))

(define-tables
  ;; Common UDT types
  ["address" ::address])

