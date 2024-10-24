(ns spooky-town-admin.validation-test
  (:require [clojure.test :refer [deftest is testing]]
            [spooky-town-admin.validation :refer [success? validate-title validate-artist validate-author validate-comic]]))

(deftest validate-title-test
  (testing "유효한 제목"
    (let [result (validate-title "유효한 제목")]
      (is (success? result))
      (is (= "유효한 제목" (:value result)))))
  
  (testing "빈 제목"
    (let [result (validate-title "")]
      (is (not (success? result)))
      (is (= {"제목" "제목의 길이는 1에서 100 사이여야 합니다."} (:error result)))))
  
  (testing "너무 긴 제목"
    (let [result (validate-title (apply str (repeat 101 "a")))]
      (is (not (success? result)))
      (is (= {"제목" "제목의 길이는 1에서 100 사이여야 합니다."} (:error result))))))

(deftest validate-artist-test
  (testing "유효한 그림 작가"
    (let [result (validate-artist "홍길동")]
      (is (success? result))
      (is (= "홍길동" (:value result)))))
  
  (testing "빈 그림 작가"
    (let [result (validate-artist "")]
      (is (not (success? result)))
      (is (= {"그림 작가" "그림 작가의 길이는 1에서 20 사이여야 합니다."} (:error result)))))
  
  (testing "너무 긴 그림 작가"
    (let [result (validate-artist (apply str (repeat 21 "a")))]
      (is (not (success? result)))
      (is (= {"그림 작가" "그림 작가의 길이는 1에서 20 사이여야 합니다."} (:error result))))))

(deftest validate-author-test
  (testing "유효한 글 작가"
    (let [result (validate-author "김철수")]
      (is (success? result))
      (is (= "김철수" (:value result)))))
  
  (testing "빈 글 작가"
    (let [result (validate-author "")]
      (is (not (success? result)))
      (is (= {"글 작가" "글 작가의 길이는 1에서 20 사이여야 합니다."} (:error result)))))
  
  (testing "너무 긴 글 작가"
    (let [result (validate-author (apply str (repeat 21 "a")))]
      (is (not (success? result)))
      (is (= {"글 작가" "글 작가의 길이는 1에서 20 사이여야 합니다."} (:error result))))))

(deftest validate-comic-test
  (testing "유효한 만화"
    (let [result (validate-comic {:title "유효한 제목" :artist "홍길동" :author "김철수"})]
      (is (success? result))
      (is (= {:title "유효한 제목" :artist "홍길동" :author "김철수"} (:value result)))))
  
  (testing "유효하지 않은 만화 - 제목 오류"
    (let [result (validate-comic {:title "" :artist "홍길동" :author "김철수"})]
      (is (not (success? result)))
      (is (= {"제목" "제목의 길이는 1에서 100 사이여야 합니다."} (:error result)))))

  (testing "유효하지 않은 만화 - 그림 작가 오류"
    (let [result (validate-comic {:title "유효한 제목" :artist "" :author "김철수"})]
      (is (not (success? result)))
      (is (= {"그림 작가" "그림 작가의 길이는 1에서 20 사이여야 합니다."} (:error result)))))

  (testing "유효하지 않은 만화 - 글 작가 오류"
    (let [result (validate-comic {:title "유효한 제목" :artist "홍길동" :author ""})]
      (is (not (success? result)))
      (is (= {"글 작가" "글 작가의 길이는 1에서 20 사이여야 합니다."} (:error result))))))