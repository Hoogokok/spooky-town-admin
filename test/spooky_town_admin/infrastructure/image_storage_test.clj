(ns spooky-town-admin.infrastructure.image-storage-test
  (:require [clojure.test :refer [deftest testing is]]
            [spooky-town-admin.infrastructure.image-storage :as image-storage]
            [clojure.java.io :as io])
  (:import [java.io File]
           [javax.imageio ImageIO]
           [java.awt.image BufferedImage]))

;; 테스트용 이미지 생성 헬퍼 함수 수정
(defn create-test-image [width height]
  (let [image (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
        temp-file (File/createTempFile "test-image" ".jpg")]
    (.deleteOnExit temp-file)  ;; 테스트 후 파일 정리
    (ImageIO/write image "jpg" temp-file)
    {:tempfile temp-file
     :content-type "image/jpeg"
     :size (.length temp-file)
     :filename "test-image.jpg"}))  ;; Ring 멀티파트 형식에 맞게 맵 반환

(deftest mock-image-storage-test
  (testing "이미지 저장"
    (let [storage (image-storage/create-mock-image-storage)
          image-data (create-test-image 800 1200)
          result (image-storage/store-image storage image-data)]
      (is (:success result))
      (is (string? (:image-id result)))
      (is (map? (:metadata result)))
      (is (= 800 (get-in result [:metadata :width])))
      (is (= 1200 (get-in result [:metadata :height])))
      (is (= "image/jpeg" (get-in result [:metadata :content-type])))))
  
  (testing "이미지 URL 생성"
    (let [storage (image-storage/create-mock-image-storage)
          image-id "test-123"]
      (is (= "https://mock-cdn.example.com/images/test-123"
             (image-storage/get-image-url storage image-id)))))
  
  (testing "이미지 삭제"
    (let [storage (image-storage/create-mock-image-storage)
          result (image-storage/delete-image storage "test-123")]
      (is (:success result)))))

(deftest image-metadata-extraction-test
  (testing "유효한 이미지 메타데이터 추출"
    (let [image-data (create-test-image 800 1200)
          result (image-storage/extract-image-metadata image-data)]
      (is (:success result))
      (is (= 800 (get-in result [:metadata :width])))
      (is (= 1200 (get-in result [:metadata :height])))
      (is (= "image/jpeg" (get-in result [:metadata :content-type])))
      (is (pos? (get-in result [:metadata :size])))))
  
  (testing "잘못된 이미지 파일"
    (let [invalid-file (File/createTempFile "invalid" ".jpg")
          _ (spit invalid-file "이것은 이미지가 아닙니다")
          invalid-image-data {:tempfile invalid-file
                            :content-type "image/jpeg"
                            :size (.length invalid-file)
                            :filename "invalid.jpg"}]
      (let [result (image-storage/extract-image-metadata invalid-image-data)]
        (.delete invalid-file)  ;; 테스트 후 파일 정리
        (is (not (:success result)))
        (is (:error result))))))

(deftest image-storage-factory-test
  (testing "테스트 환경에서 Mock 저장소 생성"
    (let [storage (image-storage/create-image-storage :test {})]
      (is (instance? spooky_town_admin.infrastructure.image_storage.MockCDNImageStorage storage))))
  
  (testing "프로덕션 환경에서 R2 저장소 생성"
    (let [storage (image-storage/create-image-storage :prod {:bucket "test"})]
      (is (instance? spooky_town_admin.infrastructure.image_storage.CloudflareR2ImageStorage storage)))))