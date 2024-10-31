(ns spooky-town-admin.infrastructure.persistence.config
  (:require [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]
            [hikari-cp.core :as hikari]
            [spooky-town-admin.core.result :as r]
            [spooky-town-admin.domain.comic.errors :as errors]
            [ragtime.jdbc :as ragtime-jdbc]
            [ragtime.repl :as ragtime]
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

(defn create-ragtime-config [env db-spec]
  {:datastore (ragtime-jdbc/sql-database db-spec)
   :migrations (case env
                :test (ragtime-jdbc/load-resources "db/migrations/test")
                :dev (ragtime-jdbc/load-resources "db/migrations/dev")
                (ragtime-jdbc/load-resources "db/migrations/production"))})

(defn run-migrations! [{:keys [db env] :as config}]
  (try
    (log/info "Starting database migrations for environment:" env)
    (let [ragtime-config (create-ragtime-config env db)]
      (log/debug "Running migrations with config:" ragtime-config)
      (ragtime/migrate ragtime-config)
      (r/success {:migrated true}))
    (catch Exception e
      (log/error e "Migration failed")
      (r/failure (errors/system-error 
                  :migration-failed 
                  "Migration failed" 
                  (.getMessage e))))))

(defn rollback-migrations! [{:keys [db env]}]
  (try
    (log/info "Rolling back migrations for environment:" env)
    (let [ragtime-config (create-ragtime-config env db)]
      (ragtime/rollback ragtime-config)
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
        (let [config (create-ragtime-config :dev db-spec)]
          (ragtime/migrate config))
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
