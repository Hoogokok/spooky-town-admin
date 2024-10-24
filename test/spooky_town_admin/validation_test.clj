 (ns spooky-town-admin.validation-test
  (:require [clojure.test :refer [deftest is testing]]
            [spooky-town-admin.validation :refer [success? validate-comic]]))

(deftest validate-comic-test
  (testing "유효한 만화 (필수 필드만)"
    (let [result (validate-comic {:title "유효한 제목" 
                                  :artist "홍길동" 
                                  :author "김철수" 
                                  :isbn-13 "978-1-23456-789-0" 
                                  :isbn-10 "1-23456-789-0"})]
      (is (success? result))
      (is (= {:title "유효한 제목" 
              :artist "홍길동" 
              :author "김철수" 
              :isbn-13 "978-1-23456-789-0" 
              :isbn-10 "1-23456-789-0"
              :publisher nil
              :publication-date nil
              :price nil
              :pages nil
              :description nil} (:value result)))))

  (testing "유효한 만화 (모든 필드 포함)"
    (let [result (validate-comic {:title "유효한 제목" 
                                  :artist "홍길동" 
                                  :author "김철수" 
                                  :isbn-13 "978-1-23456-789-0" 
                                  :isbn-10 "1-23456-789-0"
                                  :publisher "좋은출판사"
                                  :publication-date "2023-05-15"
                                  :price 15000
                                  :pages 200
                                  :description "재미있는 만화입니다."})]
      (is (success? result))
      (is (= {:title "유효한 제목" 
              :artist "홍길동" 
              :author "김철수" 
              :isbn-13 "978-1-23456-789-0" 
              :isbn-10 "1-23456-789-0"
              :publisher "좋은출판사"
              :publication-date "2023-05-15"
              :price 15000
              :pages 200
              :description "재미있는 만화입니다."} (:value result)))))

  (testing "유효하지 않은 만화 - 제목 오류"
    (let [result (validate-comic {:title "" :artist "홍길동" :author "김철수" :isbn-13 "978-1-23456-789-0" :isbn-10 "1-23456-789-0"})]
      (is (not (success? result)))
      (is (= {"제목" "제목의 길이는 1에서 100 사이여야 합니다."} (:error result)))))

  (testing "유효하지 않은 만화 - 그림 작가 오류"
    (let [result (validate-comic {:title "유효한 제목" :artist "" :author "김철수" :isbn-13 "978-1-23456-789-0" :isbn-10 "1-23456-789-0"})]
      (is (not (success? result)))
      (is (= {"그림 작가" "그림 작가의 길이는 1에서 20 사이여야 합니다."} (:error result)))))

  (testing "유효하지 않은 만화 - 글 작가 오류"
    (let [result (validate-comic {:title "유효한 제목" :artist "홍길동" :author "" :isbn-13 "978-1-23456-789-0" :isbn-10 "1-23456-789-0"})]
      (is (not (success? result)))
      (is (= {"글 작가" "글 작가의 길이는 1에서 20 사이여야 합니다."} (:error result)))))

  (testing "유효하지 않은 만화 - ISBN-13 오류"
    (let [result (validate-comic {:title "유효한 제목" :artist "홍길동" :author "김철수" :isbn-13 "invalid-isbn" :isbn-10 "1-23456-789-0"})]
      (is (not (success? result)))
      (is (= {"ISBN-13" "유효하지 않은 ISBN-13 형식입니다."} (:error result)))))

  (testing "유효하지 않은 만화 - ISBN-10 오류"
    (let [result (validate-comic {:title "유효한 제목" :artist "홍길동" :author "김철수" :isbn-13 "978-1-23456-789-0" :isbn-10 "invalid-isbn"})]
      (is (not (success? result)))
      (is (= {"ISBN-10" "유효하지 않은 ISBN-10 형식입니다."} (:error result)))))

  (testing "유효하지 않은 만화 - 선택적 필드 오류"
    (let [result (validate-comic {:title "유효한 제목" 
                                  :artist "홍길동" 
                                  :author "김철수" 
                                  :isbn-13 "978-1-23456-789-0" 
                                  :isbn-10 "1-23456-789-0"
                                  :publisher "좋은출판사"
                                  :publication-date "2023/05/15"
                                  :price -1000
                                  :pages 0
                                  :description (apply str (repeat 1001 "a"))})]
      (is (not (success? result)))
      (is (contains? (:error result) "출판일"))
      (is (contains? (:error result) "가격"))
      (is (contains? (:error result) "쪽수"))
      (is (contains? (:error result) "설명")))))
