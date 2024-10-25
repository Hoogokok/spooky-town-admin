(ns spooky-town-admin.domain.comic.types-test
  (:require [clojure.test :refer :all]
            [spooky-town-admin.domain.comic.types :as types]
            [clojure.spec.alpha :as s]))

(deftest comic-creation-test
  (testing "필수 필드가 모두 있는 경우 만화 생성"
    (let [required-fields {:title "테스트 만화"
                          :artist "테스트 작가"
                          :author "테스트 글작가"
                          :isbn13 "9781234567890"
                          :isbn10 "1234567890"}
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
                          :isbn13 "9781234567890"
                          :isbn10 "1234567890"}
          optional-fields {}
          comic (types/create-comic required-fields optional-fields)]
      
      (is (nil? comic)))))

(deftest spec-validation-test
  (testing "ISBN-13 형식 검증"
    (is (s/valid? ::types/isbn13 "9780306406157"))  ;; 체크섬이 맞는 실제 ISBN-13
    (is (s/valid? ::types/isbn13 "9780132350884"))  ;; Clean Code의 ISBN-13
    (is (not (s/valid? ::types/isbn13 "978-0-123-45678-9")))  ;; 하이픈 포함은 실패
    (is (not (s/valid? ::types/isbn13 "invalid-isbn"))))  ;; 잘못된 형식은 실패
  
  (testing "ISBN-10 형식 검증"
    (is (s/valid? ::types/isbn10 "0321146530"))
    (is (s/valid? ::types/isbn10 "0132350882"))  ;; Clean Code의 ISBN-10
    (is (not (s/valid? ::types/isbn10 "0-321-14653-0")))  ;; 하이픈 포함은 실패
    (is (not (s/valid? ::types/isbn10 "invalid-isbn"))))
  
  (testing "제목 길이 검증"
    (is (s/valid? ::types/title "정상적인 제목"))
    (is (not (s/valid? ::types/title "")))
    (is (not (s/valid? ::types/title (apply str (repeat 101 "a"))))))
  
  (testing "가격 검증"
    (is (s/valid? ::types/price 1000))
    (is (s/valid? ::types/price 0))
    (is (not (s/valid? ::types/price -1000)))))
