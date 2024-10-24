(ns spooky-town-admin.validation-test
  (:require [clojure.test :refer [deftest is testing]]
            [spooky-town-admin.validation :refer [success? validate-title validate-comic]]))

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

(deftest validate-comic-test
  (testing "유효한 만화"
    (let [result (validate-comic {:title "유효한 제목"})]
      (is (success? result))
      (is (= {:title "유효한 제목"} (:value result)))))
  
  (testing "유효하지 않은 만화"
    (let [result (validate-comic {:title ""})]
      (is (not (success? result)))
      (is (= {"제목" "제목의 길이는 1에서 100 사이여야 합니다."} (:error result))))))
