(ns ote.db.tx
  "Helper for transactions"
  (:require [clojure.java.jdbc :as jdbc]
            [jeesql.core :refer [defqueries]]))

(defqueries "ote/db/transaction_lock.sql")

(defmacro with-transaction
  "Start or join current db transaction.
  Runs `body` with `db-sym` bound to a connection that is in transaction."
  [db-sym & body]
  `(jdbc/with-db-transaction [~db-sym ~db-sym]
                             ~@body))
