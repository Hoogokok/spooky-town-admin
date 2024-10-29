(ns spooky-town-admin.infrastructure.persistence.postgresql-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [next.jdbc :as jdbc]
            [spooky-town-admin.infrastructure.persistence.postgresql :as sut]
            [spooky-town-admin.infrastructure.persistence.protocol :as protocol]
            [spooky-town-admin.core.result :as r]
            [spooky-town-admin.infrastructure.persistence.config :as config])
  (:import [org.testcontainers.containers PostgreSQLContainer]
           [com.zaxxer.hikari HikariConfig HikariDataSource]))

(def ^:dynamic *test-datasource* nil)

(defn create-test-container []
  (let [container (PostgreSQLContainer. "postgres:16")]
    (doto container
      (.withDatabaseName "test_db")
      (.withUsername "test")
      (.withPassword "test")
      ;; Integer 배열 생성 부분 수정
      (.withExposedPorts (into-array Integer [(Integer. 5432)]))  ;; Integer 객체로 명시적 변환
      (.withReuse true)
      (.withTmpFs {"/var/lib/postgresql/data" "rw,noexec,nosuid,size=1024m"})
      ;; 환경변수 설정
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
      (println "Starting container...")
      (.start container)
      (println "Container started. JDBC URL:" (.getJdbcUrl container))
      (let [ds (create-test-datasource container)]
        (println "Created datasource")
        (let [migration-config {:store :database
                              :migration-dir "db/migrations"  ;; 경로 수정
                              :db {:dbtype "postgresql"
                                  :dbname (.getDatabaseName container)
                                  :host "localhost"
                                  :port (.getMappedPort container 5432)
                                  :user (.getUsername container)
                                  :password (.getPassword container)}}]
          (println "Running migrations with config:" (dissoc migration-config :db))
          (let [migration-result (config/run-migrations! migration-config)]
            (if (r/success? migration-result)
              (do
                (println "Migrations successful")
                (binding [*test-datasource* ds]
                  (f)))
              (println "Migration failed:" migration-result)))))
      (catch Exception e
        (println "Error during test setup:" (.getMessage e))
        (throw e))
      (finally
        (println "Stopping container...")
        (.stop container)))))

(use-fixtures :each test-fixture)

(deftest test-save-comic
  (testing "만화 저장"
    (let [repo (sut/->PostgresqlComicRepository *test-datasource*)
          comic {:title "Test Comic"
                :artist "Test Artist"
                :author "Test Author"
                :isbn13 "9791234567890"
                :isbn10 "1234567890"}
          result (protocol/save-comic repo comic)]
      (is (r/success? result))
      (is (number? (:id (:value result)))))))

(deftest test-find-comic-by-id
  (testing "ID로 만화 조회"
    (let [repo (sut/->PostgresqlComicRepository *test-datasource*)
          comic {:title "Test Comic"
                :artist "Test Artist"
                :author "Test Author"
                :isbn13 "9791234567890"
                :isbn10 "1234567890"}
          saved (protocol/save-comic repo comic)
          id (get-in saved [:value :id])
          result (protocol/find-comic-by-id repo id)]
      (is (r/success? result))
      (let [found (:value result)]
        (is (= (:title comic) (:title found)))
        (is (= (:artist comic) (:artist found)))
        (is (= (:author comic) (:author found)))))))

(deftest test-find-comic-by-isbn
  (testing "ISBN으로 만화 조회"
    (let [repo (sut/->PostgresqlComicRepository *test-datasource*)
          comic {:title "Test Comic"
                :artist "Test Artist"
                :author "Test Author"
                :isbn13 "9791234567890"
                :isbn10 "1234567890"}]
      (protocol/save-comic repo comic)
      (testing "ISBN13으로 조회"
        (let [result (protocol/find-comic-by-isbn repo (:isbn13 comic))]
          (is (r/success? result))
          (is (= (:isbn13 comic) (:isbn13 (:value result))))))
      (testing "ISBN10으로 조회"
        (let [result (protocol/find-comic-by-isbn repo (:isbn10 comic))]
          (is (r/success? result))
          (is (= (:isbn10 comic) (:isbn10 (:value result)))))))))

(deftest test-delete-comic
  (testing "만화 삭제"
    (let [repo (sut/->PostgresqlComicRepository *test-datasource*)
          comic {:title "Test Comic"
                :artist "Test Artist"
                :author "Test Author"
                :isbn13 "9791234567890"
                :isbn10 "1234567890"}
          saved (protocol/save-comic repo comic)
          id (get-in saved [:value :id])]
      (let [delete-result (protocol/delete-comic repo id)]
        (is (r/success? delete-result)))
      (let [find-result (protocol/find-comic-by-id repo id)]
        (is (not (r/success? find-result)))))))
