(ns spooky-town-admin.application.comic-service-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [spooky-town-admin.application.comic-service :as service]
            [spooky-town-admin.infrastructure.persistence :as persistence]
            [spooky-town-admin.infrastructure.persistence.in-memory :as in-memory]  ;; 추가
            [spooky-town-admin.infrastructure.image-storage :as image-storage]
            [spooky-town-admin.domain.common.result :as r]
            [spooky-town-admin.domain.comic.workflow :as workflow]
            [spooky-town-admin.domain.comic.errors :as errors])
  (:import [java.io File]
           [javax.imageio ImageIO]
           [java.awt.image BufferedImage]))

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
(defn reset-db-fixture [f]
  (reset! in-memory/db-state {:comics {} :next-id 1})
  (with-redefs [image-storage/create-image-storage 
                (fn [& _] (->MockImageStorage))]
    (f)))

(use-fixtures :each reset-db-fixture)

;; 테스트 데이터
(def test-comic-data
  {:title "테스트 만화"
   :artist "테스트 작가"
   :author "테스트 글작가"
   :isbn13 "9780306406157"
   :isbn10 "0321146530"
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
     :filename "test-image.jpg"}))  ;; Ring 멀티파트 형식에 맞게 맵 반환


(deftest create-comic-test
  (let [service (service/create-comic-service :test {})]
    
    (testing "기본 만화 생성 (이미지 없음)"
      (let [result (service/create-comic service test-comic-data)]
        (is (:success result) "만화 생성이 성공해야 합니다")
        (is (map? (:value result)) "결과는 맵이어야 합니다")
        (is (pos-int? (:id (:value result))) "ID는 양의 정수여야 합니다")))
    
    (testing "이미지가 포함된 만화 생성"
      (let [image-data (create-test-image 800 1200)
            comic-with-image (assoc test-comic-data 
                                  :isbn13 "9780132350884"
                                  :isbn10 "0132350882"
                                  :cover-image image-data)
            result (service/create-comic service comic-with-image)]
        (is (:success result) "만화 생성이 성공해야 합니다")
        (is (map? (:value result)) "결과는 맵이어야 합니다")
        (is (pos-int? (:id (:value result))) "ID는 양의 정수여야 합니다")))
    
    (testing "중복된 ISBN으로 만화 생성 시도"
      (let [result (service/create-comic service test-comic-data)
            duplicate-result (service/create-comic service test-comic-data)]
        (is (not (:success duplicate-result)))
        (is (= :duplicate-isbn (get-in duplicate-result [:error :code])))))
    
    (testing "잘못된 이미지로 만화 생성 시도"
  (let [invalid-file (File/createTempFile "invalid" ".jpg")
        _ (spit invalid-file "이것은 이미지가 아닙니다")
        invalid-image-data {:tempfile invalid-file
                          :content-type "image/jpeg"
                          :size (.length invalid-file)
                          :filename "invalid.jpg"}
        comic-with-invalid-image (assoc test-comic-data 
                                      :isbn13 "9791158513009"  ;; 다른 ISBN 사용
                                      :isbn10 "1158513003"     ;; 다른 ISBN 사용
                                      :cover-image invalid-image-data)
        result (service/create-comic service comic-with-invalid-image)]
        (.delete invalid-file)
        (is (not (:success result)))
        (is (= :cover-image (get-in result [:error :field])))))))

(deftest get-comic-test
  (let [service (service/create-comic-service :test {})]
    
    (testing "존재하는 만화 조회"
      (let [create-result (service/create-comic service test-comic-data)
            comic-id (get-in create-result [:value :id])
            get-result (service/get-comic service comic-id)]
        (is (:success get-result))
        (is (= "테스트 만화" (get-in get-result [:value :title])))))
    
    (testing "존재하지 않는 만화 조회"
      (let [result (service/get-comic service 999)]
        (is (not (:success result)))
        (is (= :not-found (get-in result [:error :code])))))))

(deftest list-comics-test
  (let [service (service/create-comic-service :test {})]
    
    (testing "만화 목록 조회"
      ;; 첫 번째 만화 생성
      (service/create-comic service test-comic-data)
      ;; 두 번째 만화 생성 (다른 ISBN 사용)
      (service/create-comic service 
        (assoc test-comic-data 
               :title "테스트 만화 2"
               :isbn13 "9780132350884"  ;; Clean Code의 ISBN-13
               :isbn10 "0132350882"))  ;; Clean Code의 ISBN-10
      (let [result (service/list-comics service)]
        (is (:success result))
        (is (= 2 (count (:comics result))))
        (is (= #{"테스트 만화" "테스트 만화 2"}
               (set (map :title (:comics result)))))))))