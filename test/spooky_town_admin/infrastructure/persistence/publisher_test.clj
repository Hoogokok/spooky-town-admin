(ns spooky-town-admin.infrastructure.persistence.publisher-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [spooky-town-admin.infrastructure.persistence.postgresql :as sut]
            [spooky-town-admin.infrastructure.persistence.protocol :as protocol]
            [spooky-town-admin.core.result :as r]
            [spooky-town-admin.infrastructure.persistence.test-helper :refer [test-fixture *test-datasource*]]))

(use-fixtures :each test-fixture)

(deftest test-save-publisher
  (testing "출판사 저장"
    (let [repo (sut/->PostgresqlPublisherRepository *test-datasource*)
          publisher {:name "테스트 출판사"}
          result (protocol/save-publisher repo publisher)]
      (is (r/success? result))
      (is (number? (:id (:value result))))
      (is (= (:name publisher) (:name (:value result)))))))

(deftest test-find-publisher-by-name
  (testing "이름으로 출판사 조회"
    (let [repo (sut/->PostgresqlPublisherRepository *test-datasource*)
          publisher {:name "테스트 출판사"}]
      (protocol/save-publisher repo publisher)
      (let [result (protocol/find-publisher-by-name repo (:name publisher))]
        (is (r/success? result))
        (is (= (:name publisher) (:name (:value result))))))))

(deftest test-publisher-comic-association
  (testing "만화와 출판사 연관관계"
    (let [comic-repo (sut/->PostgresqlComicRepository *test-datasource*)
          publisher-repo (sut/->PostgresqlPublisherRepository *test-datasource*)
          publisher {:name "테스트 출판사"}
          comic {:title "테스트 만화"
                :artist "테스트 작가"
                :author "테스트 작가"
                :isbn13 "9791234567890"
                :isbn10 "1234567890"}]
      ;; 출판사와 만화 저장
      (let [publisher-result (protocol/save-publisher publisher-repo publisher)
            comic-result (protocol/save-comic comic-repo comic)]
        (is (r/success? publisher-result))
        (is (r/success? comic-result))
        ;; 연관관계 생성
        (let [assoc-result (protocol/associate-publisher-with-comic 
                           publisher-repo
                           (get-in comic-result [:value :id])
                           (get-in publisher-result [:value :id]))]
          (is (r/success? assoc-result))
          ;; 연관된 출판사 조회
          (let [publishers-result (protocol/find-publishers-by-comic-id 
                                 publisher-repo 
                                 (get-in comic-result [:value :id]))]
            (is (r/success? publishers-result))
            (is (= 1 (count (:value publishers-result))))
            (is (= (:name publisher) 
                   (:name (first (:value publishers-result)))))))))))

(deftest test-duplicate-publisher
  (testing "중복된 출판사 저장 시도"
    (let [repo (sut/->PostgresqlPublisherRepository *test-datasource*)
          publisher {:name "테스트 출판사"}]
      ;; 첫 번째 저장
      (let [first-result (protocol/save-publisher repo publisher)]
        (is (r/success? first-result))
        ;; 동일한 이름으로 두 번째 저장
        (let [second-result (protocol/save-publisher repo publisher)]
          (is (r/success? second-result))
          ;; ID가 동일해야 함 (동일 출판사로 인식)
          (is (= (get-in first-result [:value :id])
                 (get-in second-result [:value :id])))))))) 