(ns spooky-town-admin.application.comic.query-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [clojure.tools.logging :as log]
   [spooky-town-admin.application.comic.query :as query]
   [spooky-town-admin.application.comic.service :as service]
   [spooky-town-admin.core.result :as r]
   [spooky-town-admin.infrastructure.persistence :as persistence]
   [spooky-town-admin.infrastructure.persistence.config :as db-config]
   [spooky-town-admin.infrastructure.persistence.postgresql :as postgresql])
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
        (log/debug "Created test datasource")
        (db-config/set-datasource! ds)
        (db-config/run-migrations! {:db config :env :test})
        (try
          (with-redefs [persistence/create-comic-repository 
                       (fn [] (postgresql/->PostgresqlComicRepository ds))]
            ;; 테스트 데이터 삽입
            (let [repo (postgresql/->PostgresqlComicRepository ds)
                  result (persistence/save-comic repo test-comic-data)]
              (when (r/failure? result)
                (throw (ex-info "Failed to save test data" 
                              {:error (r/error result)}))))
            (f))
          (finally
            (db-config/rollback-migrations! {:db config :env :test}))))
      (finally
        (.stop container)
        (db-config/set-datasource! nil)))))

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
        (is (= (:title test-comic-data)  ;; 테스트 데이터와 비교
               (:title (first comics)))))))) 