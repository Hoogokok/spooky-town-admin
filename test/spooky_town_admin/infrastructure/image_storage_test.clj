(ns spooky-town-admin.infrastructure.image-storage-test
  (:require [clojure.test :refer [deftest testing is]]
            [spooky-town-admin.infrastructure.image-storage :as image-storage])
  (:import [java.io File]
           [javax.imageio ImageIO]
           [java.awt.image BufferedImage]
           ))

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
      (let [value (:value result)]  ;; Result의 value 필드에서 실제 값을 가져옴
        (is (string? (:image-id value)))
        (is (map? (:metadata value)))
        (is (= 800 (get-in value [:metadata :width])))
        (is (= 1200 (get-in value [:metadata :height])))
        (is (= "image/jpeg" (get-in value [:metadata :content-type]))))))

  (testing "이미지 URL 생성"
    (let [storage (image-storage/create-mock-image-storage)
          result (image-storage/get-image-url storage "test-123")]
      (is (:success result))
      (is (= "https://mock-cdn.example.com/images/test-123"
             (:value result)))))  ;; Result의 value 필드 확인

  (testing "이미지 삭제"
    (let [storage (image-storage/create-mock-image-storage)
          result (image-storage/delete-image storage "test-123")]
      (is (:success result))
      (is (true? (:value result))))))  ;; Result의 value 필드가 true인지 확인

(deftest image-metadata-extraction-test
  (testing "유효한 이미지 메타데이터 추출"
    (let [image-data (create-test-image 800 1200)
          result (image-storage/extract-image-metadata image-data)]
      (is (:success result))
      (let [metadata (:value result)]  ;; Result의 value 필드에서 메타데이터 가져옴
        (is (= 800 (:width metadata)))
        (is (= 1200 (:height metadata)))
        (is (= "image/jpeg" (:content-type metadata)))
        (is (pos? (:size metadata))))))

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
        (is (instance? spooky_town_admin.domain.comic.errors.SystemError 
                      (:error result)))))))  ;; 에러 타입 확인

(deftest image-storage-factory-test
  (testing "테스트 환경에서 Mock 저장소 생성"
    (let [storage (image-storage/create-image-storage :test)]
      (is (instance? spooky_town_admin.infrastructure.image_storage.MockCDNImageStorage 
                    storage)))))