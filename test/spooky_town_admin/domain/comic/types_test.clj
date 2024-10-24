(ns spooky-town-admin.domain.comic.types-test
  (:require [clojure.test :refer :all]
            [spooky-town-admin.domain.comic.types :as types]
            [clojure.spec.alpha :as s]))

(deftest comic-creation-test
  (testing "필수 필드가 모두 있는 경우 만화 생성"
    (let [required-fields {:title "테스트 만화"
                          :artist "테스트 작가"
                          :author "테스트 글작가"
                          :isbn13 "978-1-23456-789-0"
                          :isbn10 "1-23456-789-0"}
          optional-fields {:publisher "테스트 출판사"
                         :price 15000}
          comic (types/create-comic required-fields optional-fields)]
      
      (is (some? comic))
      (is (= (:title comic) "테스트 만화"))
      (is (= (:price comic) 15000))))
  
  (testing "필수 필드가 누락된 경우 nil 반환"
    (let [required-fields {:title "테스트 만화"
                          :artist "테스트 작가"
                          ;; author 누락
                          :isbn13 "978-1-23456-789-0"
                          :isbn10 "1-23456-789-0"}
          optional-fields {}
          comic (types/create-comic required-fields optional-fields)]
      
      (is (nil? comic)))))

(deftest spec-validation-test
  (testing "ISBN-13 형식 검증"
    (is (s/valid? ::types/isbn13 "978-1-23456-789-0"))
    (is (not (s/valid? ::types/isbn13 "invalid-isbn"))))
  
  (testing "ISBN-10 형식 검증"
    (is (s/valid? ::types/isbn10 "1-23456-789-0"))
    (is (not (s/valid? ::types/isbn10 "invalid-isbn"))))
  
  (testing "제목 길이 검증"
    (is (s/valid? ::types/title "정상적인 제목"))
    (is (not (s/valid? ::types/title "")))
    (is (not (s/valid? ::types/title (apply str (repeat 101 "a"))))))
  
  (testing "가격 검증"
    (is (s/valid? ::types/price 1000))
    (is (s/valid? ::types/price 0))
    (is (not (s/valid? ::types/price -1000)))))
