(ns spooky-town-admin.application.comic.command-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [spooky-town-admin.application.comic.command :as command]
   [spooky-town-admin.application.comic.service :as service]
   [spooky-town-admin.core.result :as r]
   [spooky-town-admin.domain.comic.errors :as errors]
   [spooky-town-admin.domain.comic.workflow :as workflow]
   [spooky-town-admin.infrastructure.image-storage :as image-storage]
   [spooky-town-admin.infrastructure.persistence :as persistence]
   [spooky-town-admin.infrastructure.persistence.config :as db-config]
   [spooky-town-admin.infrastructure.persistence.postgresql :as postgresql])
  (:import
   [java.awt.image BufferedImage]
   [java.io File]
   [javax.imageio ImageIO]
   [org.testcontainers.containers PostgreSQLContainer]))

;; TestContainers를 위한 설정
(defn create-test-container []
  (doto (PostgreSQLContainer. "postgres:16")
    (.withDatabaseName "test_db")
    (.withUsername "test")
    (.withPassword "test")))

;; Mock 이미지 저장소
(defrecord MockImageStorage []
  image-storage/ImageStorage
  (store-image [_ image]
    (try
      (let [metadata (workflow/extract-image-metadata image)]
        (if metadata
          (r/success {:image-id "test-image-id"
                     :metadata metadata})
          (r/failure (errors/validation-error :cover-image 
                                            (errors/get-image-error-message :invalid)))))
      (catch Exception _
        (r/failure (errors/validation-error :cover-image 
                                          (errors/get-image-error-message :invalid))))))
  
  (delete-image [_ _]
    (r/success true))
  
  (get-image-url [_ image-id]
    (r/success (str "https://mock-cdn.example.com/images/" image-id))))

;; 테스트 픽스처
(def ^:dynamic *test-datasource* nil)

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
                     (fn [] (postgresql/->PostgresqlComicRepository ds))
                     image-storage/create-image-storage 
                     (fn [& _] (->MockImageStorage))]
          (f)))
      (finally
        (.stop container)))))

(use-fixtures :each test-fixture)

;; 테스트 데이터
(def test-comic-data
  {:title "테스트 만화"
   :artist "테스트 작가"
   :author "테스트 글작가"
   :isbn13 "9791163243021"
   :isbn10 "1163243027"
   :publisher "테스트 출판사"
   :price 15000})

;; 테스트용 이미지 생성 헬퍼
(defn create-test-image [width height]
  (let [image (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
        temp-file (File/createTempFile "test-image" ".jpg")]
    (.deleteOnExit temp-file)
    (ImageIO/write image "jpg" temp-file)
    {:tempfile temp-file
     :content-type "image/jpeg"
     :size (.length temp-file)
     :filename "test-image.jpg"}))

(deftest create-comic-test
  (testing "만화 생성 - 잘못된 이미지 실패"
    (let [service (service/create-comic-service {})
          test-image (create-test-image 100 100)
          _ (command/create-comic service  
              (assoc test-comic-data :cover-image test-image))
          invalid-image {:tempfile (File. "non-existent.jpg")
                        :content-type "image/jpeg"
                        :size 0
                        :filename "invalid.jpg"}
          result (command/create-comic service 
                  (assoc test-comic-data :cover-image invalid-image))]
      (is (not (r/success? result)))
      (is (= :duplicate-isbn (:code (:error result))))))

  (testing "만화 생성 - 중복 ISBN 실패"
    (let [service (service/create-comic-service {})
          test-image (create-test-image 100 100)
          _ (command/create-comic service 
              (assoc test-comic-data :cover-image test-image))
          duplicate-result (command/create-comic service 
                           (assoc test-comic-data 
                                 :cover-image test-image
                                 :title "다른 제목"))]
      (is (not (r/success? duplicate-result)))
      (is (= :duplicate-isbn (:code (:error duplicate-result))))))

  (testing "만화 생성 - 성공 케이스"
    (let [service (service/create-comic-service {})
          test-image (create-test-image 100 100)
          result (command/create-comic service 
                  (assoc test-comic-data 
                        :isbn13 "9791193534168"
                        :isbn10 "119353416X"
                        :cover-image test-image))]
      (is (r/success? result))
      (is (number? (:id (r/value result))))
      (is (= "테스트 만화" (:title (r/value result)))))))
