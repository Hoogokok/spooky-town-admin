(ns spooky-town-admin.infrastructure.persistence.config
  (:require [next.jdbc :as jdbc]
            [migratus.core :as migratus]
            [spooky-town-admin.domain.common.result :as r]
            [spooky-town-admin.domain.comic.errors :as errors]))

(def db-config
  {:dbtype "postgresql"
   :dbname (or (System/getenv "POSTGRES_DB") "spooky_town")
   :host (or (System/getenv "POSTGRES_HOST") "localhost")
   :port (or (parse-long (or (System/getenv "POSTGRES_PORT") "5432")) 5432)
   :user (or (System/getenv "POSTGRES_USER") "postgres")
   :password (or (System/getenv "POSTGRES_PASSWORD") "postgres")})

(def migration-config
  {:store :database
   :migration-dir "migrations/postgresql"
   :db db-config})

(defn init-db! []
  (try
    (let [ds (jdbc/get-datasource db-config)]
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