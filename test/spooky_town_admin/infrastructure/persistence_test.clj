(ns spooky-town-admin.infrastructure.persistence-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [spooky-town-admin.infrastructure.persistence :as persistence]))

;; 테스트 픽스처 - 각 테스트 전에 DB 상태 초기화
(defn reset-db-fixture [f]
  (reset! persistence/db-state {:comics {} :next-id 1})
  (f))

(use-fixtures :each reset-db-fixture)

(def test-comic
  {:title "테스트 만화"
   :artist "테스트 작가"
   :author "테스트 글작가"
   :isbn13 "9780306406157"
   :isbn10 "0321146530"
   :publisher "테스트 출판사"
   :price 15000})

(deftest save-comic-test
  (testing "만화 저장"
    (let [repo (persistence/create-comic-repository)
          result (persistence/save-comic repo test-comic)]
      (is (:success result))
      (is (= 1 (:id result))))))

(deftest find-comic-by-id-test
  (testing "ID로 만화 조회"
    (let [repo (persistence/create-comic-repository)]
      (persistence/save-comic repo test-comic)
      (let [comic (persistence/find-comic-by-id repo 1)]
        (is (some? comic))
        (is (= "테스트 만화" (:title comic)))
        (is (= 1 (:id comic)))))))

(deftest find-comic-by-isbn-test
  (testing "ISBN으로 만화 조회"
    (let [repo (persistence/create-comic-repository)]
      (persistence/save-comic repo test-comic)
      (let [comic (persistence/find-comic-by-isbn repo "9780306406157")]
        (is (some? comic))
        (is (= "테스트 만화" (:title comic)))))))

(deftest delete-comic-test
  (testing "만화 삭제"
    (let [repo (persistence/create-comic-repository)]
      (persistence/save-comic repo test-comic)
      (let [delete-result (persistence/delete-comic repo 1)]
        (is (:success delete-result))
        (is (nil? (persistence/find-comic-by-id repo 1)))))))

(deftest list-comics-test
  (testing "만화 목록 조회"
    (let [repo (persistence/create-comic-repository)]
      (persistence/save-comic repo test-comic)
      (persistence/save-comic repo (assoc test-comic :title "테스트 만화 2"))
      (let [comics (persistence/list-comics repo)]
        (is (= 2 (count comics)))
        (is (= #{"테스트 만화" "테스트 만화 2"}
               (set (map :title comics))))))))