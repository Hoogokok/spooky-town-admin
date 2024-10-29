(ns spooky-town-admin.infrastructure.persistence.postgresql-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [spooky-town-admin.infrastructure.persistence.postgresql :as sut]
            [spooky-town-admin.infrastructure.persistence.protocol :as protocol]
            [spooky-town-admin.core.result :as r]
            [spooky-town-admin.infrastructure.persistence.test-helper :refer [test-fixture *test-datasource*]]))

(use-fixtures :each test-fixture)

(deftest test-save-comic
  (testing "만화 저장"
    (let [repo (sut/->PostgresqlComicRepository *test-datasource*)
          comic {:title "Test Comic"
                :artist "Test Artist"
                :author "Test Author"
                :isbn13 "9791234567890"
                :isbn10 "1234567890"}
          result (protocol/save-comic repo comic)]
      (is (r/success? result))
      (is (number? (:id (:value result)))))))

(deftest test-find-comic-by-id
  (testing "ID로 만화 조회"
    (let [repo (sut/->PostgresqlComicRepository *test-datasource*)
          comic {:title "Test Comic"
                :artist "Test Artist"
                :author "Test Author"
                :isbn13 "9791234567890"
                :isbn10 "1234567890"}
          saved (protocol/save-comic repo comic)
          id (get-in saved [:value :id])
          result (protocol/find-comic-by-id repo id)]
      (is (r/success? result))
      (let [found (:value result)]
        (is (= (:title comic) (:title found)))
        (is (= (:artist comic) (:artist found)))
        (is (= (:author comic) (:author found)))))))

(deftest test-find-comic-by-isbn
  (testing "ISBN으로 만화 조회"
    (let [repo (sut/->PostgresqlComicRepository *test-datasource*)
          comic {:title "Test Comic"
                :artist "Test Artist"
                :author "Test Author"
                :isbn13 "9791234567890"
                :isbn10 "1234567890"}]
      (protocol/save-comic repo comic)
      (testing "ISBN13으로 조회"
        (let [result (protocol/find-comic-by-isbn repo (:isbn13 comic))]
          (is (r/success? result))
          (is (= (:isbn13 comic) (:isbn13 (:value result))))))
      (testing "ISBN10으로 조회"
        (let [result (protocol/find-comic-by-isbn repo (:isbn10 comic))]
          (is (r/success? result))
          (is (= (:isbn10 comic) (:isbn10 (:value result)))))))))

(deftest test-delete-comic
  (testing "만화 삭제"
    (let [repo (sut/->PostgresqlComicRepository *test-datasource*)
          comic {:title "Test Comic"
                :artist "Test Artist"
                :author "Test Author"
                :isbn13 "9791234567890"
                :isbn10 "1234567890"}
          saved (protocol/save-comic repo comic)
          id (get-in saved [:value :id])]
      (let [delete-result (protocol/delete-comic repo id)]
        (is (r/success? delete-result)))
      (let [find-result (protocol/find-comic-by-id repo id)]
        (is (not (r/success? find-result)))))))

(deftest test-save-comic-with-publisher
  (testing "출판사 정보를 포함한 만화 저장"
    (let [comic-repo (sut/->PostgresqlComicRepository *test-datasource*)
          publisher-repo (sut/->PostgresqlPublisherRepository *test-datasource*)
          publisher {:name "Test Publisher"}
          comic {:title "Test Comic"
                :artist "Test Artist"
                :author "Test Author"
                :isbn13 "9791234567890"
                :isbn10 "1234567890"
                :publisher "Test Publisher"}]
      ;; 출판사 먼저 저장
      (let [publisher-result (protocol/save-publisher publisher-repo publisher)
            _ (is (r/success? publisher-result))
            ;; 만화 저장
            comic-result (protocol/save-comic comic-repo (dissoc comic :publisher))]
        (is (r/success? comic-result))
        ;; 연관관계 생성
        (let [assoc-result (protocol/associate-publisher-with-comic 
                           publisher-repo
                           (get-in comic-result [:value :id])
                           (get-in publisher-result [:value :id]))]
          (is (r/success? assoc-result))
          ;; 연관된 출판사 확인
          (let [publishers (protocol/find-publishers-by-comic-id 
                           publisher-repo 
                           (get-in comic-result [:value :id]))]
            (is (r/success? publishers))
            (is (= "Test Publisher" 
                   (:name (first (:value publishers)))))))))))

(deftest test-delete-comic-with-publisher
  (testing "출판사 연관관계가 있는 만화 삭제"
    (let [comic-repo (sut/->PostgresqlComicRepository *test-datasource*)
          publisher-repo (sut/->PostgresqlPublisherRepository *test-datasource*)
          publisher {:name "Test Publisher"}
          comic {:title "Test Comic"
                :artist "Test Artist"
                :author "Test Author"
                :isbn13 "9791234567890"
                :isbn10 "1234567890"}]
      ;; 출판사와 만화 저장, 연관관계 생성
      (let [publisher-result (protocol/save-publisher publisher-repo publisher)
            comic-result (protocol/save-comic comic-repo comic)
            _ (protocol/associate-publisher-with-comic 
               publisher-repo
               (get-in comic-result [:value :id])
               (get-in publisher-result [:value :id]))
            ;; 만화 삭제
            delete-result (protocol/delete-comic 
                          comic-repo 
                          (get-in comic-result [:value :id]))]
        (is (r/success? delete-result))
        ;; 출판사는 여전히 존재해야 함
        (let [publisher-check (protocol/find-publisher-by-id 
                              publisher-repo 
                              (get-in publisher-result [:value :id]))]
          (is (r/success? publisher-check)))))))
