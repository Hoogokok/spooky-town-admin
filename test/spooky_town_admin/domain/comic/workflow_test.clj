(ns spooky-town-admin.domain.comic.workflow-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [spooky-town-admin.core.result :refer [failure success success?]]
   [spooky-town-admin.domain.comic.errors :as errors]
   [spooky-town-admin.domain.comic.types :as types]
   [spooky-town-admin.domain.comic.workflow :as workflow]
   [spooky-town-admin.infrastructure.image-storage :as image-storage]))

;; 테스트 데이터
(def valid-comic-data
  {:title "테스트 만화"
   :artist "테스트 작가"
   :author "테스트 글작가"
   :isbn13 "9780306406157"
   :isbn10 "0321146530"
   :publisher "테스트 출판사"
   :price 15000})

(def mock-image-data
  {:filename "test.jpg"
   :tempfile (java.io.File. "test.jpg")
   :content-type "image/jpeg"
   :size 1000})

(def mock-image-metadata
  {:content-type "image/jpeg"
   :size 1000
   :width 800
   :height 600})

;; Mock 이미지 저장소
(defrecord MockImageStorage []
  image-storage/ImageStorage
  (store-image [_ _]
    (success {:url "https://example.com/test-image.jpg"})))

;; 테스트 픽스처
(use-fixtures :each
  (fn [f]
    (with-redefs [types/extract-image-metadata 
                  (constantly (success mock-image-metadata))]
      (f))))

;; 워크플로우 테스트
(deftest create-comic-workflow-test
  (let [mock-storage (->MockImageStorage)]
    
    (testing "이미지가 없는 기본 워크플로우"
      (let [result (workflow/create-comic-workflow mock-storage valid-comic-data)]
        (is (success? result))
        (let [{:keys [comic events]} (:value result)]
          (is (= (:title valid-comic-data) (-> comic :title :value)))
          (is (= 1 (count events))))))
    
    (testing "이미지가 있는 워크플로우"
      (let [data-with-image (assoc valid-comic-data :cover-image mock-image-data)
            result (workflow/create-comic-workflow mock-storage data-with-image)]
        (is (success? result))
        (let [{:keys [comic events]} (:value result)]
          (is (= (:title valid-comic-data) (-> comic :title :value)))
          (is (some? (:cover-image-metadata comic)))
          (is (= mock-image-metadata (:cover-image-metadata comic)))
          (is (= "https://example.com/test-image.jpg" (:cover-image-url comic)))
          (is (= 3 (count events))))))))

;; 이미지 처리 테스트
(deftest process-and-store-image-test
  (let [mock-storage (->MockImageStorage)]
    
    (testing "유효한 이미지 처리 및 저장"
      (let [result (workflow/process-and-store-image mock-storage mock-image-data)]
        (is (success? result))
        (let [image-result (:value result)]
          (is (= mock-image-metadata (:cover-image-metadata image-result)))
          (is (= "https://example.com/test-image.jpg" (:cover-image-url image-result))))))
    
    (testing "이미지가 없는 경우"
      (let [result (workflow/process-and-store-image mock-storage nil)]
        (is (success? result))
        (is (nil? (:value result)))))
    
    (testing "잘못된 이미지 데이터"
      (with-redefs [types/validate-image-data 
                    (fn [_] (failure (errors/validation-error :cover-image "잘못된 이미지")))]
        (let [result (workflow/process-and-store-image mock-storage mock-image-data)]
          (is (not (success? result)))
          (is (= :cover-image (get-in result [:error :field]))))))))