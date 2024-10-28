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

(defn- create-datasource []
  (let [config {:adapter "postgresql"
                :database-name (:dbname db-spec)
                :server-name (:host db-spec)
                :port-number (:port db-spec)
                :username (:user db-spec)
                :password (:password db-spec)
                :maximum-pool-size 10
                :pool-name "spooky-town-pool"}]
    (hikari/make-datasource config)))

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
        (println "Running database migrations...")
        (migratus/migrate migratus-config)
        (println "Migrations completed successfully")
        (catch Exception e
          (println "Migration error:" (.getMessage e))
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
