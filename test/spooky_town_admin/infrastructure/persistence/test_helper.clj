(ns spooky-town-admin.infrastructure.persistence.test-helper
  (:require [clojure.tools.logging :as log]
            [spooky-town-admin.infrastructure.persistence.config :as db-config]
            [ragtime.jdbc :as ragtime-jdbc]
            [ragtime.repl :as ragtime])
  (:import [org.testcontainers.containers PostgreSQLContainer]))

(def ^:dynamic *test-datasource* nil)

(defn create-test-container []
  (doto (PostgreSQLContainer. "postgres:16")
    (.withDatabaseName "test_db")
    (.withUsername "test")
    (.withPassword "test")))

(defn test-fixture [f]
  (let [container (create-test-container)]
    (try
      (.start container)
      (let [db-spec {:dbtype "postgresql"
                     :dbname (.getDatabaseName container)
                     :host (.getHost container)
                     :port (.getMappedPort container 5432)
                     :user (.getUsername container)
                     :password (.getPassword container)}
            ds (db-config/create-datasource db-spec)]
        (log/debug "Created test datasource")
        (db-config/set-datasource! ds)
        (let [config {:datastore (ragtime-jdbc/sql-database db-spec)
                     :migrations (ragtime-jdbc/load-resources "db/migrations/test")}]
          (try
            (log/info "Starting migrations...")
            (ragtime/migrate config)
            (binding [*test-datasource* ds]
              (f))
            (finally
              (doseq [m (reverse (:migrations config))]
                (ragtime/rollback config (:id m)))
              (db-config/set-datasource! nil)))))
      (finally
        (.stop container)))))
