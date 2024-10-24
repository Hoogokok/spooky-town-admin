(ns spooky-town-admin.application.comic-service-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [spooky-town-admin.application.comic-service :as service]
            [spooky-town-admin.infrastructure.persistence :as persistence]
            [spooky-town-admin.infrastructure.image-storage :as image-storage]
            [spooky-town-admin.domain.comic.errors :as errors])
  (:import [java.io File]
           [javax.imageio ImageIO]
           [java.awt.image BufferedImage]))

;; Mock 이미지 저장소
(defrecord MockImageStorage []
  image-storage/ImageStorage
  (store-image [_ image]
    (try
      (let [metadata-result (image-storage/extract-image-metadata image)]
        (if (:success metadata-result)
          {:success true
           :image-id "test-image-id"
           :metadata (:metadata metadata-result)}
          metadata-result))
      (catch Exception _
        {:success false
         :error (errors/validation-error :cover-image 
                                       (errors/get-image-error-message :invalid))})))
  
  (delete-image [_ _]
    {:success true})
  
  (get-image-url [_ image-id]
    (str "https://mock-cdn.example.com/images/" image-id)))

;; 테스트 픽스처
(defn reset-db-fixture [f]
  (reset! persistence/db-state {:comics {} :next-id 1})
  (with-redefs [image-storage/create-image-storage 
                (fn [& _] (->MockImageStorage))]
    (f)))

(use-fixtures :each reset-db-fixture)

;; 테스트 데이터
(def test-comic-data
  {:title "테스트 만화"
   :artist "테스트 작가"
   :author "테스트 글작가"
   :isbn13 "978-1-23456-789-0"
   :isbn10 "1-23456-789-0"
   :publisher "테스트 출판사"
   :price 15000})

;; 테스트용 이미지 생성 헬퍼
(defn create-test-image [width height]
  (let [image (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
        temp-file (File/createTempFile "test-image" ".jpg")]
    (.deleteOnExit temp-file)  ;; 테스트 후 파일 삭제
    (ImageIO/write image "jpg" temp-file)
    temp-file))


(deftest create-comic-test
  (let [service (service/create-comic-service :test {})]
    
    (testing "이미지가 포함된 만화 생성"
      (let [test-image (create-test-image 800 1200)
            _ (println "Test image created:" (.exists test-image))
            comic-with-image (assoc test-comic-data 
                                  :isbn13 "978-1-23456-789-2"  ;; 다른 ISBN 사용
                                  :isbn10 "1-23456-789-2"
                                  :cover-image test-image)
            result (service/create-comic service comic-with-image)]
        (println "Metadata result:" (image-storage/extract-image-metadata test-image))
        (println "Create result:" result)
        (is (:success result))
        (is (pos? (:id result)))))
    
    (testing "기본 만화 생성"
      (let [result (service/create-comic service test-comic-data)]
        (is (:success result))
        (is (pos? (:id result)))))
    
    (testing "중복된 ISBN으로 만화 생성 시도"
      (let [result (service/create-comic service test-comic-data)]
        (is (not (:success result)))
        (is (= :duplicate-isbn (get-in result [:error :code])))))
    
    (testing "잘못된 이미지로 만화 생성 시도"
      (let [invalid-image (File/createTempFile "invalid" ".jpg")
            _ (spit invalid-image "이것은 이미지가 아닙니다")
            comic-with-invalid-image (assoc test-comic-data 
                                          :isbn13 "978-1-23456-789-3"  ;; 다른 ISBN 사용
                                          :isbn10 "1-23456-789-3"
                                          :cover-image invalid-image)
            result (service/create-comic service comic-with-invalid-image)]
        (is (not (:success result)))
        (is (= :cover-image (get-in result [:error :field])))))))

(deftest get-comic-test
  (let [service (service/create-comic-service :test {})]
    
    (testing "존재하는 만화 조회"
      (let [create-result (service/create-comic service test-comic-data)
            get-result (service/get-comic service (:id create-result))]
        (is (:success get-result))
        (is (= "테스트 만화" (get-in get-result [:comic :title])))))
    
    (testing "존재하지 않는 만화 조회"
      (let [result (service/get-comic service 999)]
        (is (not (:success result)))
        (is (= :not-found (get-in result [:error :code])))))))

(deftest list-comics-test
  (let [service (service/create-comic-service :test {})]
    
    (testing "만화 목록 조회"
      (service/create-comic service test-comic-data)
      (service/create-comic service (assoc test-comic-data 
                                         :title "테스트 만화 2"
                                         :isbn13 "978-1-23456-789-1"
                                         :isbn10 "1-23456-789-1"))
      (let [result (service/list-comics service)]
        (is (:success result))
        (is (= 2 (count (:comics result))))
        (is (= #{"테스트 만화" "테스트 만화 2"}
               (set (map :title (:comics result)))))))))