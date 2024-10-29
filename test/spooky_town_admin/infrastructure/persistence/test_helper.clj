(ns spooky-town-admin.infrastructure.persistence.test-helper
  (:require [next.jdbc :as jdbc]
            [spooky-town-admin.infrastructure.persistence.config :as config]
            [spooky-town-admin.core.result :as r]
            [clojure.tools.logging :as log]
            )
  (:import [org.testcontainers.containers PostgreSQLContainer]
           [com.zaxxer.hikari HikariConfig HikariDataSource]))

(def ^:dynamic *test-datasource* nil)

(defn create-test-container []
  (let [container (PostgreSQLContainer. "postgres:16")]
    (doto container
      (.withDatabaseName "test_db")
      (.withUsername "test")
      (.withPassword "test")
      (.withExposedPorts (into-array Integer [(Integer. 5432)]))
      (.withReuse true)
      (.withTmpFs {"/var/lib/postgresql/data" "rw,noexec,nosuid,size=1024m"})
      (.withEnv "POSTGRES_DB" "test_db")
      (.withEnv "POSTGRES_USER" "test")
      (.withEnv "POSTGRES_PASSWORD" "test"))))

(defn create-test-datasource [container]
  (let [config (doto (HikariConfig.)
                (.setJdbcUrl (.getJdbcUrl container))
                (.setUsername (.getUsername container))
                (.setPassword (.getPassword container))
                (.setMaximumPoolSize 2))]
    (HikariDataSource. config)))

(defn test-fixture [f]
  (let [container (create-test-container)]
    (try
      (log/info "Starting container...")
      (.start container)
      (log/info "Container started. JDBC URL:" (.getJdbcUrl container))
      (let [ds (create-test-datasource container)]
        (log/info "Created datasource")
        (let [migration-config {:store :database
                              :migration-dir "db/migrations"
                              :db {:dbtype "postgresql"
                                  :dbname (.getDatabaseName container)
                                  :host "localhost"
                                  :port (.getMappedPort container 5432)
                                  :user (.getUsername container)
                                  :password (.getPassword container)}}]
          (log/info "Running migrations with config:" (dissoc migration-config :db))
          (let [migration-result (config/run-migrations! migration-config)]
            (if (r/success? migration-result)
              (do
                (log/info "Migrations successful")
                (binding [*test-datasource* ds]
                  (f)))
              (log/error "Migration failed:" migration-result)))))
      (catch Exception e
        (log/error "Error during test setup:" (.getMessage e))
        (throw e))
      (finally
        (log/info "Stopping container...")
        (.stop container)))))
