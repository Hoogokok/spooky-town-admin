(ns spooky-town-admin.infrastructure.persistence.test-helper
  (:require [clojure.tools.logging :as log]
            [spooky-town-admin.infrastructure.persistence.config :as db-config]
            [spooky-town-admin.core.result :as r])
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
      (let [config {:dbtype "postgresql"
                   :dbname (.getDatabaseName container)
                   :host "localhost"
                   :port (.getMappedPort container 5432)
                   :user (.getUsername container)
                   :password (.getPassword container)}
            ds (db-config/create-datasource config)]
        (log/debug "Created test datasource")
        (db-config/set-datasource! ds)
        ;; 테스트용 마이그레이션 실행
        (let [migration-result (db-config/run-migrations! 
                               {:db config 
                                :env :test})]
          (when (r/failure? migration-result)
            (throw (ex-info "Migration failed" 
                          {:error (r/error migration-result)}))))
        (try
          (binding [*test-datasource* ds]
            (f))
          (finally
            ;; 테스트 종료 후 롤백
            (db-config/rollback-migrations! 
             {:db config 
              :env :test}))))
      (finally
        (.stop container)
        (db-config/set-datasource! nil)))))
