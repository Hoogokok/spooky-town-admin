(ns spooky-town-admin.domain.comic.workflow-test
  (:require [clojure.test :refer [deftest is testing]]
            [spooky-town-admin.domain.comic.workflow :as workflow]
            [spooky-town-admin.domain.comic.errors :as errors])
  (:import [spooky_town_admin.domain.comic.errors ValidationError]))  ;; 여기에 import 추가

(def valid-comic-data
  {:title "테스트 만화"
   :artist "테스트 작가"
   :author "테스트 글작가"
   :isbn13 "9780306406157"
   :isbn10 "0321146530"
   :publisher "테스트 출판사"
   :price 15000})

(deftest create-comic-workflow-test
  (testing "유효한 데이터로 만화 생성"
    (let [result (workflow/create-comic-workflow valid-comic-data)]
      (is (workflow/success? result))
      (is (= (:title (:value result)) "테스트 만화"))))
  
  (testing "필수 필드 누락 시 실패"
    (let [invalid-data (dissoc valid-comic-data :author)
          result (workflow/create-comic-workflow invalid-data)]
      (is (not (workflow/success? result)))
      (is (instance? ValidationError (:error result)))))
  
  (testing "잘못된 ISBN 형식"
    (let [invalid-data (assoc valid-comic-data :isbn13 "invalid-isbn")
          result (workflow/create-comic-workflow invalid-data)]
      (is (not (workflow/success? result)))
      (is (instance? ValidationError (:error result))))))  ;; errors/ 제거

(deftest validate-image-constraints-test
  (testing "이미지 제약조건 검증"
    (let [valid-image {:content-type "image/jpeg"
                      :width 800
                      :height 1200
                      :size (* 5 1024 1024)}
          result (workflow/validate-image-constraints valid-image)]
      (is (workflow/success? result))))
  
  (testing "크기 초과 이미지 실패"
    (let [invalid-image {:content-type "image/jpeg"
                        :width 20000  ;; max-dimension(12000)을 초과
                        :height 30000  ;; max-dimension(12000)을 초과
                        :size (* 5 1024 1024)}
          result (workflow/validate-image-constraints invalid-image)]
      (is (not (workflow/success? result)))
      (is (instance? ValidationError (:error result))))))