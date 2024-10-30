(ns spooky-town-admin.application.comic.command-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [clojure.tools.logging :as log]
   [spooky-town-admin.application.comic.command :as command]
   [spooky-town-admin.application.comic.service :as service]
   [spooky-town-admin.core.result :as r]
   [spooky-town-admin.domain.comic.errors :as errors]
   [spooky-town-admin.domain.comic.types :as types]
   [spooky-town-admin.infrastructure.image-storage :as image-storage]
   [spooky-town-admin.infrastructure.persistence :as persistence]
   [spooky-town-admin.infrastructure.persistence.config :as db-config]
   [spooky-town-admin.infrastructure.persistence.postgresql :as postgresql]
   [spooky-town-admin.infrastructure.persistence.protocol :as protocol])
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
      (let [metadata (types/extract-image-metadata image)]
        (if (r/success? metadata)
          (r/success {:url "https://mock-cdn.example.com/test-image.jpg"
                     :metadata (r/value metadata)})
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
          (with-redefs [persistence/create-comic-repository 
                       (fn [] (postgresql/->PostgresqlComicRepository ds))
                       persistence/create-publisher-repository 
                       (fn [] (postgresql/->PostgresqlPublisherRepository ds))
                       image-storage/create-image-storage 
                       (fn [& _] (->MockImageStorage))]
            (f))
          (finally
            ;; 테스트 종료 후 롤백
            (db-config/rollback-migrations! 
             {:db config 
              :env :test}))))
      (finally
        (.stop container)
        (db-config/set-datasource! nil)))))

(use-fixtures :each test-fixture)

;;1198390115
;;1193166144
;;8937834790
;;8934950196
;;8937833662
;;1189231603


;; 테스트 데이터 - 기본 ISBN
(def test-comic-data
  {:title "테스트 만화"
   :artist "테스트 작가"
   :author "테스트 글작가"
   :isbn13 "9791198390110"  ;; 실제 ISBN-13
   :isbn10 "1198390115"     ;; 실제 ISBN-10
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
  (testing "만화 생성 - 성공"
    (let [service (service/create-comic-service {})
          test-image (create-test-image 100 100)
          result (command/create-comic service 
                  (assoc test-comic-data :cover-image test-image))]
      (is (r/success? result))
      (is (some? (get-in result [:value :id])))))

  (testing "만화 생성 - 잘못된 이미지 실패"
    (let [service (service/create-comic-service {})
          invalid-image {:tempfile (File. "non-existent.jpg")
                        :content-type "image/jpeg"
                        :size 0
                        :filename "invalid.jpg"}
          result (command/create-comic service 
                  (assoc test-comic-data 
                        :isbn13 "9791193166147"  ;; 다른 ISBN 사용
                        :isbn10 "1193166144"
                        :cover-image invalid-image))]
      (is (not (r/success? result)))
      (is (= :invalid-image (:code (:error result))))))  ;; 이미지 검증 실패 기대

  (testing "만화 생성 - 중복 ISBN 실패"
    (let [service (service/create-comic-service {})
          test-image (create-test-image 100 100)
          ;; 첫 번째 만화 생성
          _ (command/create-comic service 
              (assoc test-comic-data :cover-image test-image))
          ;; 같은 ISBN으로 두 번째 만화 생성 시도
          result (command/create-comic service 
                  (assoc test-comic-data :cover-image test-image))]
      (is (not (r/success? result)))
      (is (= :duplicate-isbn (:code (:error result)))))))

(deftest create-comic-with-publisher-test
  (testing "만화 생성 - 출판사 연관관계 생성"
    (let [service (service/create-comic-service {})
          test-image (create-test-image 100 100)
          comic-data (assoc test-comic-data 
                           :isbn13 "9788937834790"
                           :isbn10 "8937834790"
                           :cover-image test-image
                           :publisher "테스트 출판사")
          result (command/create-comic service comic-data)]
      (is (r/success? result))
      (let [comic-id (:id (r/value result))
            publisher-repo (persistence/create-publisher-repository)
            publishers (persistence/find-publishers-by-comic-id publisher-repo comic-id)]
        (is (r/success? publishers))
        (is (= 1 (count (r/value publishers))))
        (is (= "테스트 출판사" (:name (first (r/value publishers))))))))

  (testing "만화 생성 - 동일 출판사로 여러 만화 생성"
    (let [service (service/create-comic-service {})
          test-image (create-test-image 100 100)
          comic1-data (assoc test-comic-data 
                            :isbn13 "9788934950196"
                            :isbn10 "8934950196"
                            :cover-image test-image
                            :publisher "테스트 출판사")
          comic2-data (assoc test-comic-data 
                            :isbn13 "9788937833663"
                            :isbn10 "8937833662"
                            :cover-image test-image
                            :publisher "테스트 출판사")
          result1 (command/create-comic service comic1-data)
          result2 (command/create-comic service comic2-data)]
      (is (r/success? result1))
      (is (r/success? result2))
      (let [publisher-repo (persistence/create-publisher-repository)
            publishers1 (persistence/find-publishers-by-comic-id 
                        publisher-repo 
                        (:id (r/value result1)))
            publishers2 (persistence/find-publishers-by-comic-id 
                        publisher-repo 
                        (:id (r/value result2)))]
        ;; 두 만화 모두 같은 출판사와 연결되어야 함
        (is (= (get-in publishers1 [:value 0 :id])
               (get-in publishers2 [:value 0 :id]))))))

  (testing "만화 생성 - 잘못된 출판사 이름"
    (let [service (service/create-comic-service {})
          test-image (create-test-image 100 100)
          invalid-cases [{:name "빈 문자열"
                         :publisher ""
                         :expected-error :invalid-publisher-name}
                        {:name "공백 문자열"
                         :publisher "   "
                         :expected-error :invalid-publisher-name}
                        {:name "최대 길이 초과"
                         :publisher (apply str (repeat 51 "가"))
                         :expected-error :invalid-publisher-name}
                        {:name "허용되지 않는 특수문자"
                         :publisher "Invalid!@#$"
                         :expected-error :invalid-publisher-name}]]
      (doseq [{:keys [name publisher expected-error]} invalid-cases]
        (testing name
          (let [comic-data (assoc test-comic-data 
                                 :isbn13 "9791189231606"
                                 :isbn10 "1189231603"
                                 :cover-image test-image
                                 :publisher publisher)
                result (command/create-comic service comic-data)]
            (is (not (r/success? result)))
            (is (= expected-error (:code (:error result)))))))))

  (testing "만화 생성 - 출판사 연관관계 생성 실패"
    (let [service (service/create-comic-service {})
          test-image (create-test-image 100 100)
          comic-data (assoc test-comic-data 
                           :isbn13 "9791198390110"
                           :isbn10 "1198390115"
                           :cover-image test-image
                           :publisher "테스트 출판사")
          failing-publisher-repo (reify protocol/PublisherRepository
                                 (save-publisher [_ publisher]
                                   (r/success {:id 1 :name (:name publisher)}))
                                 (find-publisher-by-id [_ _] (r/success nil))
                                 (find-publisher-by-name [_ _] (r/success nil))
                                 (find-publishers-by-comic-id [_ _] (r/success []))
                                 (associate-publisher-with-comic [_ _ _] 
                                   (r/failure (errors/system-error 
                                              :publisher-association-error 
                                              (errors/get-system-message :publisher-association-error)
                                              "Failed to create association"))))]
      (with-redefs [persistence/create-publisher-repository 
                    (constantly failing-publisher-repo)]
        (let [result (command/create-comic service comic-data)]
          (is (not (r/success? result)))
          (is (= :publisher-association-error (:code (:error result)))))))))

(deftest create-comic-transaction-test
  (testing "만화 생성 - 트랜잭션 롤백"
    (let [service (service/create-comic-service {})
          test-image (create-test-image 100 100)
          comic-data (assoc test-comic-data 
                           :isbn13 "9791193166147"
                           :isbn10 "1193166144"
                           :cover-image test-image
                           :publisher {:value "테스트 출판사"})
          ;; 출판사 저장을 실패하게 만드는 mock repository
          failing-publisher-repo (reify protocol/PublisherRepository
                                 (save-publisher [_ _]
                                   (r/failure (errors/system-error 
                                              :db-error 
                                              (errors/get-system-message :db-error)
                                              "Failed to save publisher")))
                                 (find-publisher-by-id [_ _] (r/success nil))
                                 (find-publisher-by-name [_ _] (r/success nil))
                                 (find-publishers-by-comic-id [_ _] (r/success []))
                                 (associate-publisher-with-comic [_ _ _] (r/success true)))]
      (with-redefs [persistence/create-publisher-repository 
                    (constantly failing-publisher-repo)]
        (let [result (command/create-comic service comic-data)]
          (is (not (r/success? result)))
          (is (= :db-error (:code (:error result))))
          ;; 만화도 저장되지 않아야 함
          (let [comic-repo (persistence/create-comic-repository)
                find-result (persistence/find-comic-by-isbn 
                            comic-repo 
                            (:isbn13 comic-data))]
            (is (r/success? find-result))
            (is (nil? (r/value find-result)))))))))
