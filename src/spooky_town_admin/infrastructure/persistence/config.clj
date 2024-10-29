(ns spooky-town-admin.infrastructure.persistence.config
  (:require [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]
            [hikari-cp.core :as hikari]
            [spooky-town-admin.domain.common.result :as r]
            [spooky-town-admin.domain.comic.errors :as errors]
            [migratus.core :as migratus]
            [environ.core :refer [env]])
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

(def migratus-config
  {:store :database
   :migration-dir "db/migrations"  ;; resources는 자동으로 클래스패스에 포함됨
   :db db-spec})


(defn init-db! []
  (when-not @datasource
    (let [ds (connection/->pool HikariDataSource db-spec)]
      (reset! datasource ds)
      ;; 마이그레이션 실행
      (try
        (migratus/migrate migratus-config)
        (catch Exception e
          (.printStackTrace e))))))

(defn get-datasource []
  @datasource)

(defn close-datasource! []
  (when-let [ds @datasource]
    (.close ^HikariDataSource ds)
    (reset! datasource nil)))

(def ^:dynamic *current-tx* nil)

(defn get-current-tx []
  *current-tx*)

(defn run-migrations! [config]
  (try
    (println "Starting database migrations with config:" config)
    (migratus/init config)
    (let [pending (migratus/pending-list config)]
      (if (seq pending)
        (do
          (println "Pending migrations:" pending)
          (migratus/migrate config)
          (r/success {:migrated pending}))
        (do
          (println "No pending migrations")
          (r/success {:migrated []}))))
    (catch Exception e
      (println "Migration failed:" (.getMessage e))
      (.printStackTrace e)
      (r/failure {:error :migration-failed
                  :message (.getMessage e)}))))
