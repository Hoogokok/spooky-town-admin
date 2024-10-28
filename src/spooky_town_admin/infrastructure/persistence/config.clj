(ns spooky-town-admin.infrastructure.persistence.config
  (:require [next.jdbc :as jdbc]
            [migratus.core :as migratus]
            [spooky-town-admin.domain.common.result :as r]
            [spooky-town-admin.domain.comic.errors :as errors])
  (:import [com.zaxxer.hikari HikariConfig HikariDataSource]))

(def db-config
  {:dbtype "postgresql"
   :dbname (or (System/getenv "POSTGRES_DB") "spooky_town")
   :host (or (System/getenv "POSTGRES_HOST") "localhost")
   :port (or (parse-long (or (System/getenv "POSTGRES_PORT") "5432")) 5432)
   :user (or (System/getenv "POSTGRES_USER") "postgres")
   :password (or (System/getenv "POSTGRES_PASSWORD") "postgres")})

(def pool-config
  {:minimum-idle 5
   :maximum-pool-size 10
   :idle-timeout 300000  ; 5 minutes
   :max-lifetime 1200000 ; 20 minutes
   :connection-timeout 30000  ; 30 seconds
   :validation-timeout 5000}) ; 5 seconds

(defn- create-datasource []
  (let [config (doto (HikariConfig.)
                (.setJdbcUrl (str "jdbc:postgresql://"
                                 (:host db-config) ":"
                                 (:port db-config) "/"
                                 (:dbname db-config)))
                (.setUsername (:user db-config))
                (.setPassword (:password db-config))
                (.setMinimumIdle (:minimum-idle pool-config))
                (.setMaximumPoolSize (:maximum-pool-size pool-config))
                (.setIdleTimeout (:idle-timeout pool-config))
                (.setMaxLifetime (:max-lifetime pool-config))
                (.setConnectionTimeout (:connection-timeout pool-config))
                (.setValidationTimeout (:validation-timeout pool-config))
                ;; 연결 테스트 쿼리 설정
                (.setConnectionTestQuery "SELECT 1")
                ;; 풀 이름 설정
                (.setPoolName "spooky-town-pool"))]
    (HikariDataSource. config)))

(def migration-config
  {:store :database
   :migration-dir "migrations/postgresql"
   :db db-config})

(defn init-db! []
  (try
    (let [ds (create-datasource)]
      (migratus/init migration-config)
      (migratus/migrate migration-config)
      (r/success ds))
    (catch Exception e
      (r/failure 
        (errors/system-error 
          :db-init-error
          "데이터베이스 초기화 실패"
          (.getMessage e))))))

(def datasource
  (delay
    (-> (init-db!)
        (r/bind (fn [ds]
                  (if ds
                    (r/success ds)
                    (r/failure 
                      (errors/system-error 
                        :db-connection-error
                        "데이터베이스 연결 실패"
                        "데이터소스 생성 실패"))))))))

;; 애플리케이션 종료 시 연결 풀 정리를 위한 함수
(defn shutdown-datasource! []
  (when-let [ds @datasource]
    (when (instance? HikariDataSource ds)
      (.close ds))))
