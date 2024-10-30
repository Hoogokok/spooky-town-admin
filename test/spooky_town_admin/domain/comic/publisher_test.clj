(ns spooky-town-admin.domain.comic.publisher-test
  (:require [clojure.test :refer [deftest testing is]]
            [spooky-town-admin.domain.comic.publisher :as publisher]
            [spooky-town-admin.core.result :as r]))

(deftest publisher-validation-test
  (testing "출판사 이름 유효성 검증"
    (testing "성공 케이스"
      (let [valid-names ["대한출판사"
                        "ABC Publishing"
                        "한빛미디어(주)"
                        "O'Reilly & Associates"
                        "길벗"]]
        (doseq [name valid-names]
          (testing name
            (let [result (publisher/create-validated-publisher {:name name})]
              (is (r/success? result))
              (is (= name (publisher/get-name (:value result)))))))))

    (testing "실패 케이스"
      (let [invalid-cases
            [{:name ""
              :desc "빈 문자열"}
             {:name "   "
              :desc "공백 문자열"}
             {:name (apply str (repeat 51 "가"))
              :desc "최대 길이 초과"}
             {:name "Invalid!@#$"
              :desc "허용되지 않는 특수문자"}]]
        (doseq [{:keys [name desc]} invalid-cases]
          (testing desc
            (let [result (publisher/create-validated-publisher {:name name})]
              (is (r/failure? result))
              (is (= :invalid-publisher-name 
                    (:code (r/error result))))))))))

  (testing "nil 이름 처리"
    (let [result (publisher/create-validated-publisher {:name nil})]
      (is (r/success? result))
      (is (nil? (:value result))))))

(deftest publisher-lifecycle-test
  (testing "출판사 상태 전이"
    (let [data {:name "테스트 출판사"}
          unvalidated (publisher/create-unvalidated-publisher data)
          validated (publisher/create-validated-publisher data)
          persisted (publisher/create-persisted-publisher 1 (:value validated))]
      
      (testing "UnvalidatedPublisher 생성"
        (is (r/success? unvalidated))
        (is (instance? spooky_town_admin.domain.comic.publisher.UnvalidatedPublisher 
                      (:value unvalidated)))
        (is (= "테스트 출판사" (:name (:value unvalidated)))))
      
      (testing "ValidatedPublisher 생성"
        (is (r/success? validated))
        (is (instance? spooky_town_admin.domain.comic.publisher.ValidatedPublisher 
                      (:value validated)))
        (is (= "테스트 출판사" 
               (get-in validated [:value :name :value]))))
      
      (testing "PersistedPublisher 생성"
        (is (r/success? persisted))
        (is (instance? spooky_town_admin.domain.comic.publisher.PersistedPublisher 
                      (:value persisted)))
        (is (= 1 (-> persisted :value :id)))
        (is (= (get-in validated [:value :name])  ;; PublisherName 객체 비교
               (-> persisted :value :name)))))))

(deftest publisher-update-test
  (testing "출판사 이름 업데이트"
    (let [publisher (-> (publisher/create-validated-publisher {:name "테스트 출판사"})
                       :value)]
      
      (testing "성공 케이스"
        (let [result (publisher/update-name publisher "새 출판사")]
          (is (r/success? result))
          (is (= "새 출판사" (publisher/get-name (:value result))))))
      
      (testing "실패 케이스"
        (let [result (publisher/update-name publisher "")]
          (is (r/failure? result))
          (is (= :invalid-publisher-name 
                 (:code (r/error result))))))))) 