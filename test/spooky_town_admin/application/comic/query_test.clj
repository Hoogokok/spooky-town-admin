(ns spooky-town-admin.application.comic.query-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [spooky-town-admin.application.comic.command :as command]
   [spooky-town-admin.application.comic.query :as query]
   [spooky-town-admin.application.comic.service :as service]
   [spooky-town-admin.core.result :as r]
   [spooky-town-admin.infrastructure.persistence :as persistence]
   [spooky-town-admin.infrastructure.persistence.config :as db-config]
   [spooky-town-admin.infrastructure.persistence.postgresql :as postgresql]
   )
  (:import
   [org.testcontainers.containers PostgreSQLContainer]))

;; TestContainers를 위한 설정
(defn create-test-container []
  (doto (PostgreSQLContainer. "postgres:16")
    (.withDatabaseName "test_db")
    (.withUsername "test")
    (.withPassword "test")))

;; 테스트 픽스처
(def ^:dynamic *test-datasource* nil)

(def test-comic-data
  {:title "테스트 만화"
   :artist "테스트 작가"
   :author "테스트 글작가"
   :isbn13 "9791163243021"
   :isbn10 "1163243027"
   :publisher "테스트 출판사"
   :price 15000})

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
        (println "Created test datasource")
        (db-config/run-migrations! {:store :database
                                  :migration-dir "db/migrations"
                                  :db config})
        (with-redefs [db-config/get-datasource (constantly ds)
                     persistence/create-comic-repository 
                     (fn [] (postgresql/->PostgresqlComicRepository ds))]
          ;; 테스트 데이터 생성
          (let [service (service/create-comic-service {})
                result (command/create-comic service test-comic-data)]
            (when-not (r/success? result)
              (throw (ex-info "Failed to create test data" {:error (:error result)}))))
          (f)))
      (finally
        (.stop container)))))

(use-fixtures :each test-fixture)

(deftest get-comic-test
  (testing "만화 조회 - 성공 케이스"
    (let [service (service/create-comic-service {})
          comic-id 1
          result (query/get-comic service comic-id)]
      (is (r/success? result))
      (is (= comic-id (:id (r/value result))))
      (is (= "테스트 만화" (:title (r/value result))))))

  (testing "만화 조회 - 실패 케이스"
    (let [service (service/create-comic-service {})
          comic-id 9999
          result (query/get-comic service comic-id)]
      (is (not (r/success? result)))
      (is (= :not-found (:code (:error result)))))))

(deftest list-comics-test
  (testing "만화 목록 조회"
    (let [service (service/create-comic-service {})
          result (query/list-comics service)]
      (is (r/success? result))
      (let [comics (r/value result)]
        (is (seq comics))
        (is (= "테스트 만화" (:title (first comics)))))))) 