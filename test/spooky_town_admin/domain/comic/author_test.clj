 (ns spooky-town-admin.domain.comic.author-test
  (:require [clojure.test :refer [deftest testing is]]
            [spooky-town-admin.domain.comic.author :as author]
            [spooky-town-admin.core.result :as r]))

(deftest author-name-validation-test
  (testing "작가 이름 유효성 검사"
    (testing "유효한 이름"
      (let [valid-names ["김작가" "John Doe" "金作家" "Kim-Author" "J.K. Rowling"]]
        (doseq [name valid-names]
          (testing (str "이름: " name)
            (let [result (author/validate-author-name name)]
              (is (r/success? result))
              (is (= name (get-in (r/value result) [:value])))))))))
    
    (testing "유효하지 않은 이름"
      (let [invalid-cases
            [["" "이름은 비어있을 수 없습니다"]
             [nil "이름은 필수 항목입니다"]
             [(apply str (repeat 51 "a")) "이름은 50자를 초과할 수 없습니다"]
             ["!@#$%" "이름에 허용되지 않는 특수문자가 포함되어 있습니다"]]]
        (doseq [[name expected-error] invalid-cases]
          (testing (str "이름: " name)
            (let [result (author/validate-author-name name)]
              (is (r/failure? result))
              (is (= expected-error (:message (r/error result))))))))))

(deftest create-author-test
  (testing "작가 생성"
    (testing "유효한 데이터로 작가 생성"
      (let [author-data {:name "김작가"
                        :type :writer
                        :description "베스트셀러 작가"}
            result (author/create-validated-author author-data)]
        (is (r/success? result))
        (let [created-author (r/value result)]
          (is (= "김작가" (get-in created-author [:name :value])))
          (is (= :writer (:type created-author)))
          (is (= "베스트셀러 작가" (get-in created-author [:description :value]))))))
    
    (testing "필수 필드 누락"
      (let [invalid-data {:description "설명"}
            result (author/create-validated-author invalid-data)]
        (is (r/failure? result))
        (is (= "이름은 필수 항목입니다" (:message (r/error result))))))
    
    (testing "유효하지 않은 작가 유형"
      (let [invalid-data {:name "김작가" 
                         :type :invalid-type
                         :description "설명"}
            result (author/create-validated-author invalid-data)]
        (is (r/failure? result))
        (is (= "유효하지 않은 작가 유형입니다" (:message (r/error result))))))))

(deftest author-equality-test
  (testing "작가 동등성 비교"
    (let [author1 (r/value (author/create-validated-author 
                            {:name "김작가" :type :writer}))
          author2 (r/value (author/create-validated-author 
                            {:name "김작가" :type :writer}))
          author3 (r/value (author/create-validated-author 
                            {:name "이작가" :type :writer}))]
      (is (= author1 author2))
      (is (not= author1 author3)))))

(deftest author-events-test
  (testing "작가 도메인 이벤트"
    (testing "작가 검증 이벤트"
      (let [author-data {:name "김작가" :type :writer}
            result (author/create-validated-author author-data)
            event (author/create-author-validated (r/value result))]
        (is (instance? spooky_town_admin.domain.comic.author.AuthorValidated event))
        (is (= (get-in (r/value result) [:name :value])
               (get-in (:validated-author event) [:name :value])))))
    
    (testing "작가 생성 이벤트"
      (let [validated-author (r/value (author/create-validated-author 
                                      {:name "김작가" :type :writer}))
            result (author/create-persisted-author 1 validated-author)
            event (author/create-author-created (r/value result))]
        (is (instance? spooky_town_admin.domain.comic.author.AuthorCreated event))
        (is (= 1 (:id (:persisted-author event))))))))