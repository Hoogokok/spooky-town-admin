(ns spooky-town-admin.domain.comic.workflow-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [spooky-town-admin.domain.comic.workflow :as workflow]
   [spooky-town-admin.domain.common.result :refer [failure success success?]]
   [spooky-town-admin.infrastructure.image-storage :as image-storage]))

;; 테스트 데이터
(def valid-comic-data
  {:title "테스트 만화"
   :artist "테스트 작가"
   :author "테스트 글작가"
   :isbn13 "9780306406157"
   :isbn10 "0321146530"})

(def mock-image-data
  {:tempfile (java.io.File. "test.jpg")
   :content-type "image/jpeg"
   :size 1000
   :width 800
   :height 600})

(def mock-image-metadata
  {:content-type "image/jpeg"
   :size 1000
   :width 800
   :height 600})

;; Mock 이미지 저장소
(defrecord MockImageStorage []
  image-storage/ImageStorage
  (store-image [_ _]
    (success {:image-id "test-image-id"
              :metadata mock-image-metadata
              :url "https://example.com/test-image.jpg"}))
  
  (delete-image [_ _]
    (success true))
  
  (get-image-url [_ _]
    (success "https://example.com/test-image.jpg")))

;; 테스트 픽스처
(use-fixtures :each
  (fn [f]
    (with-redefs [workflow/extract-image-metadata (constantly mock-image-metadata)]
      (f))))

;; 워크플로우 테스트
(deftest create-comic-workflow-test
  (let [mock-storage (->MockImageStorage)]
    
    (testing "이미지가 없는 기본 워크플로우"
      (let [result (workflow/create-comic-workflow mock-storage valid-comic-data)]
        (is (success? result))
        (let [{:keys [comic events]} (:value result)]
          (is (instance? spooky_town_admin.domain.comic.types.ValidatedComic comic))
          (is (nil? (:cover-image-metadata comic)))
          (is (= 1 (count events)))
          (is (instance? spooky_town_admin.domain.comic.types.ComicValidated 
                        (first events))))))
    
    (testing "이미지가 있는 워크플로우"
      (let [data-with-image (assoc valid-comic-data :cover-image mock-image-data)
            result (workflow/create-comic-workflow mock-storage data-with-image)]
        (is (success? result))
        (let [{:keys [comic events image-url]} (:value result)]
          (is (instance? spooky_town_admin.domain.comic.types.ValidatedComic comic))
          (is (some? (:cover-image-metadata comic)))
          (is (= mock-image-metadata (:cover-image-metadata comic)))
          (is (= "https://example.com/test-image.jpg" image-url))
          (is (= 3 (count events)))
          (is (instance? spooky_town_admin.domain.comic.types.ComicValidated 
                        (first events)))
          (is (instance? spooky_town_admin.domain.comic.types.ImageUploaded 
                        (second events)))
          (is (instance? spooky_town_admin.domain.comic.types.ImageStored 
                        (nth events 2))))))))

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
      (with-redefs [workflow/extract-image-metadata (constantly nil)]
        (let [result (workflow/process-and-store-image mock-storage mock-image-data)]
          (is (failure result))
          (is (= :cover-image (get-in result [:error :field]))))))))

;; 이미지 메타데이터 검증 테스트
(deftest validate-image-constraints-test
  (testing "유효한 이미지 메타데이터"
    (let [result (workflow/validate-image-constraints mock-image-metadata)]
      (is (success? result))
      (is (= mock-image-metadata (:value result)))))
  
  (testing "지원하지 않는 이미지 타입"
    (let [invalid-type (assoc mock-image-metadata :content-type "image/tiff")
          result (workflow/validate-image-constraints invalid-type)]
      (is (failure result))
      (is (= :cover-image (get-in result [:error :field])))))
  
  (testing "너무 큰 이미지 크기"
    (let [too-large (assoc mock-image-metadata :size (* 20 1024 1024))  ;; 20MB
          result (workflow/validate-image-constraints too-large)]
      (is (failure result))
      (is (= :cover-image (get-in result [:error :field])))))
  
  (testing "너무 큰 이미지 치수"
    (let [too-large (assoc mock-image-metadata :width 15000 :height 15000)
          result (workflow/validate-image-constraints too-large)]
      (is (failure result))
      (is (= :cover-image (get-in result [:error :field]))))))