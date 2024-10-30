(ns spooky-town-admin.infrastructure.persistence-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [spooky-town-admin.infrastructure.persistence :as persistence]
            [spooky-town-admin.infrastructure.persistence.in-memory :as in-memory]
            [spooky-town-admin.core.result :as r]
            [environ.core :refer [env]]))

;; 테스트 픽스처 - 각 테스트 전에 DB 상태 초기화
(defn reset-db-fixture [f]
  (reset! in-memory/db-state {:comics {} 
                             :publishers {}
                             :comics-publishers #{}
                             :next-comic-id 1
                             :next-publisher-id 1})  ;; 전체 상태 초기화
  (f))

;; 테스트 환경 설정을 위한 픽스처
(defn with-test-env [f]
  (with-redefs [env (assoc env :environment "test")]
    (f)))

(use-fixtures :once with-test-env)  ;; 모든 테스트에 환경 설정 적용
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
      (is (r/success? result))
      (is (= 1 (:id (r/value result)))))))

(deftest find-comic-by-id-test
  (testing "ID로 만화 조회"
    (let [repo (persistence/create-comic-repository)
          save-result (persistence/save-comic repo test-comic)]
      (when (r/success? save-result)
        (let [result (persistence/find-comic-by-id repo 1)]
          (is (r/success? result))
          (let [comic (r/value result)]
            (is (some? comic))
            (is (= "테스트 만화" (get-in comic [:title :value])))
            (is (= 1 (:id comic)))))))))

(deftest find-comic-by-isbn-test
  (testing "ISBN으로 만화 조회"
    (let [repo (persistence/create-comic-repository)
          _ (persistence/save-comic repo test-comic)
          result (persistence/find-comic-by-isbn repo "9780306406157")]
      (is (r/success? result))
      (let [comic (r/value result)]
        (is (some? comic))
        (is (= "테스트 만화" (get-in comic [:title :value])))))))

(deftest delete-comic-test
  (testing "만화 삭제"
    (let [repo (persistence/create-comic-repository)
          _ (persistence/save-comic repo test-comic)
          delete-result (persistence/delete-comic repo 1)]
      (is (r/success? delete-result))
      (let [find-result (persistence/find-comic-by-id repo 1)]
        (is (r/failure? find-result))
        (is (nil? (r/value find-result)))))))

(deftest list-comics-test
  (testing "만화 목록 조회"
    (let [repo (persistence/create-comic-repository)]
      (persistence/save-comic repo test-comic)
      (persistence/save-comic repo (assoc test-comic :title "테스트 만화 2"))
      (let [result (persistence/list-comics repo)]
        (is (r/success? result))
        (let [comics (r/value result)]
          (is (= 2 (count comics)))
          (is (= #{"테스트 만화" "테스트 만화 2"}
                 (set (map #(get-in % [:title :value]) comics)))))))))