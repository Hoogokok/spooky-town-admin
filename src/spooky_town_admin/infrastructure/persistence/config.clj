(ns spooky-town-admin.infrastructure.persistence.config
  (:require [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]
            [hikari-cp.core :as hikari]
            [spooky-town-admin.core.result :as r]
            [spooky-town-admin.domain.comic.errors :as errors]
            [migratus.core :as migratus]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log])
  (:import (com.zaxxer.hikari HikariDataSource)))

(def ^:private datasource (atom nil))

(def db-spec
  {:dbtype "postgresql"
   :dbname (or (env :postgres-db) "postgres")
   :host (or (env :postgres-host) "localhost")
   :port (or (env :postgres-port) 5432)
   :user (or (env :postgres-user) "postgres")
   :password (or (env :postgres-password) "postgres")})

(defn create-datasource
  "데이터베이스 연결을 위한 DataSource 생성"
  ([db-spec]
   (if (:jdbcUrl db-spec)
     (jdbc/get-datasource db-spec)  ; 기존 jdbcUrl 형식 지원
     (create-datasource 
       (:dbtype db-spec) 
       (:dbname db-spec) 
       (:host db-spec) 
       (:port db-spec) 
       (:user db-spec) 
       (:password db-spec))))
  ([dbtype dbname host port user password]
   (let [jdbc-url (format "jdbc:%s://%s:%d/%s?user=%s&password=%s"
                         dbtype host port dbname user password)]
     (jdbc/get-datasource {:jdbcUrl jdbc-url}))))

(defn create-migratus-config [datasource env]
  {:store :database
   :migration-dir (case env
                   :test "db/migrations/test"
                   :dev "db/migrations/dev"
                   "db/migrations/productions")
   :db {:connection-uri (format "jdbc:postgresql://%s:%d/%s?user=%s&password=%s"
                              (or (env :postgres-host) "localhost")
                              (or (env :postgres-port) 5432)
                              (or (env :postgres-db) "postgres")
                              (or (env :postgres-user) "postgres")
                              (or (env :postgres-password) "postgres"))}})

(defn run-migrations! [{:keys [db env] :as config}]
  (try
    (log/info "Starting database migrations for environment:" env)
    (let [ds (create-datasource db)
          migration-config (create-migratus-config ds env)]
      (log/debug "Running migrations with config:" migration-config)
      (migratus/init migration-config)
      (let [pending (migratus/pending-list migration-config)]
        (if (seq pending)
          (do
            (log/info "Pending migrations:" pending)
            (migratus/migrate migration-config)
            (r/success {:migrated pending}))
          (do
            (log/info "No pending migrations")
            (r/success {:migrated []})))))
    (catch Exception e
      (log/error e "Migration failed")
      (r/failure (errors/system-error 
                  :migration-failed 
                  "Migration failed" 
                  (.getMessage e))))))

(defn rollback-migrations! [{:keys [db env]}]
  (try
    (log/info "Rolling back migrations for environment:" env)
    (let [ds (create-datasource db)
          migration-config (create-migratus-config ds env)]
      (migratus/rollback migration-config)
      (r/success true))
    (catch Exception e
      (log/error e "Rollback failed")
      (r/failure (errors/system-error 
                  :rollback-failed 
                  "Rollback failed" 
                  (.getMessage e))))))

(defn init-db! []
  (when-not @datasource
    (let [ds (connection/->pool HikariDataSource db-spec)]
      (reset! datasource ds)
      (try
        (run-migrations! {:db db-spec :env :dev})
        (catch Exception e
          (log/error e "Failed to initialize database")
          (.printStackTrace e))))))

(defn set-datasource! 
  "데이터소스를 설정합니다. nil을 전달하면 기존 데이터소스를 제거합니다."
  [ds]
  (when-let [old-ds @datasource]
    (when (instance? HikariDataSource old-ds)
      (.close ^HikariDataSource old-ds)))
  (reset! datasource ds))

(defn get-datasource []
  @datasource)

(defn close-datasource! []
  (when-let [ds @datasource]
    (.close ^HikariDataSource ds)
    (reset! datasource nil)))

(def ^:dynamic *current-tx* nil)

(defn get-current-tx []
  *current-tx*)
