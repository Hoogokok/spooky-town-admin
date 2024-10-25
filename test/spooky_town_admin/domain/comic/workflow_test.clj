(ns spooky-town-admin.domain.comic.workflow-test
  (:require [clojure.test :refer [deftest is testing]]
            [spooky-town-admin.domain.comic.workflow :as workflow]
            [spooky-town-admin.domain.comic.errors :as errors]
            [spooky-town-admin.domain.common.result :as r]))

(def valid-comic-data
  {:title "테스트 만화"
   :artist "테스트 작가"
   :author "테스트 글작가"
   :isbn13 "9780306406157"
   :isbn10 "0321146530"
   :publisher "테스트 출판사"
   :price 15000})

;; 모의 이미지 데이터
(def mock-image-data
  {:tempfile nil
   :content-type "image/jpeg"
   :size 1000
   :width 800
   :height 600})

;; extract-image-metadata 모킹
(defn mock-extract-image-metadata [image-data]
  (select-keys image-data [:content-type :size :width :height]))

(deftest create-comic-workflow-test
  (testing "전체 워크플로우 성공 케이스"
    (let [result (workflow/create-comic-workflow valid-comic-data)]
      (is (r/success? result))
      (is (= (:title (:value result)) "테스트 만화"))))
  
  (testing "필수 필드 누락"
    (let [invalid-data (dissoc valid-comic-data :title)
          result (workflow/create-comic-workflow invalid-data)]
      (is (not (r/success? result)))
      (is (= :required-fields (:field (:error result))))))
  
  (testing "잘못된 ISBN"
    (let [invalid-data (assoc valid-comic-data :isbn13 "invalid-isbn")
          result (workflow/create-comic-workflow invalid-data)]
      (is (not (r/success? result)))
      (is (= :isbn13 (:field (:error result))))))
  
  (testing "이미지가 있는 경우"
    (with-redefs [workflow/extract-image-metadata mock-extract-image-metadata]
      (let [data-with-image (assoc valid-comic-data :cover-image mock-image-data)
            result (workflow/create-comic-workflow data-with-image)]
        (is (r/success? result) "워크플로우가 성공해야 합니다")
        (is (:cover-image-metadata (:value result)) "이미지 메타데이터가 있어야 합니다")))))

(deftest process-image-test
  (testing "유효한 이미지 처리"
    (with-redefs [workflow/extract-image-metadata mock-extract-image-metadata]
      (let [comic-data (assoc valid-comic-data :cover-image mock-image-data)
            result (workflow/process-image comic-data)]
        (is (r/success? result))
        (is (:cover-image-metadata (:value result))))))
  
  (testing "이미지가 없는 경우"
    (let [result (workflow/process-image valid-comic-data)]
      (is (r/success? result))
      (is (nil? (:cover-image-metadata (:value result))))))
  
  (testing "잘못된 이미지 데이터"
    (with-redefs [workflow/extract-image-metadata (constantly nil)]
      (let [invalid-image {:tempfile nil
                          :content-type "image/jpeg"
                          :size 1000}
            result (workflow/process-image (assoc valid-comic-data 
                                                :cover-image invalid-image))]
        (is (not (r/success? result)))
        (is (= :cover-image (:field (:error result))))))))

(deftest validate-image-constraints-test
  (testing "유효한 이미지 제약조건"
    (let [valid-image {:content-type "image/jpeg"
                      :size 1000000
                      :width 800
                      :height 600}
          result (workflow/validate-image-constraints valid-image)]
      (is (r/success? result))))
  
  (testing "잘못된 이미지 타입"
    (let [invalid-type {:content-type "image/bmp"
                       :size 1000000
                       :width 800
                       :height 600}
          result (workflow/validate-image-constraints invalid-type)]
      (is (not (r/success? result)))
      (is (= :cover-image (:field (:error result))))))
  
  (testing "너무 큰 이미지 크기"
    (let [too-large {:content-type "image/jpeg"
                    :size (* 20 1024 1024)  ;; 20MB (max_file_size 10MB 초과)
                    :width 800
                    :height 600}
          result (workflow/validate-image-constraints too-large)]
      (is (not (r/success? result)))
      (is (= :cover-image (:field (:error result)))))))