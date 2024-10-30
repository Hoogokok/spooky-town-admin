(ns spooky-town-admin.infrastructure.image-storage-test
  (:require [clojure.test :refer [deftest testing is]]
            [spooky-town-admin.infrastructure.image-storage :as image-storage]
            [spooky-town-admin.domain.comic.types :as types]
            [spooky-town-admin.core.result :as r]))

(deftest mock-image-storage-test
  (testing "이미지 저장"
    (let [storage (image-storage/create-mock-image-storage)
          metadata (types/->ImageMetadata 
                    "image/jpeg" 800 600 1000)
          validated-image (types/->ValidatedImageData 
                          metadata
                          (java.io.File. "test.jpg"))
          result (image-storage/store-image storage validated-image)]
      (is (r/success? result))
      (let [value (r/value result)]
        (is (string? (:image-id value)))
        (is (= metadata (:metadata value))))))

  (testing "이미지 URL 생성"
    (let [storage (image-storage/create-mock-image-storage)
          result (image-storage/get-image-url storage "test-123")]
      (is (r/success? result))
      (is (string? (r/value result)))))

  (testing "이미지 삭제"
    (let [storage (image-storage/create-mock-image-storage)
          result (image-storage/delete-image storage "test-123")]
      (is (r/success? result))
      (is (true? (r/value result))))))

(deftest image-storage-factory-test
  (testing "환경별 저장소 생성"
    (testing "테스트 환경"
      (let [storage (image-storage/create-image-storage :test)]
        (is (instance? spooky_town_admin.infrastructure.image_storage.MockCDNImageStorage 
                      storage))))
    
    (testing "기본 환경"
      (let [storage (image-storage/create-image-storage :default)]
        (is (instance? spooky_town_admin.infrastructure.image_storage.CloudinaryImageStorage 
                      storage))))))