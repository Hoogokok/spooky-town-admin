(ns spooky-town-admin.domain.comic.types-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [spooky-town-admin.domain.comic.types :as types]
   [spooky-town-admin.infrastructure.monad.result :refer [success?]]))

(deftest value-objects-test
  (testing "ISBN-13 생성"
    (let [valid-result (types/create-isbn13 "9780306406157")
          invalid-format (types/create-isbn13 "invalid-isbn")
          invalid-checksum (types/create-isbn13 "9780306406158")]
      (is (success? valid-result))
      (is (= "9780306406157" (str (:value valid-result))))
      (is (not (success? invalid-format)))
      (is (not (success? invalid-checksum)))))
  
  (testing "ISBN-10 생성"
    (let [valid-result (types/create-isbn10 "0321146530")
          invalid-format (types/create-isbn10 "invalid-isbn")
          invalid-checksum (types/create-isbn10 "0321146531")]
      (is (success? valid-result))
      (is (= "0321146530" (str (:value valid-result))))
      (is (not (success? invalid-format)))
      (is (not (success? invalid-checksum)))))
  
  (testing "제목 생성"
    (let [valid-result (types/create-title "정상적인 제목")
          empty-title (types/create-title "")
          long-title (types/create-title (apply str (repeat 101 "a")))]
      (is (success? valid-result))
      (is (= "정상적인 제목" (str (:value valid-result))))
      (is (not (success? empty-title)))
      (is (not (success? long-title)))))
  
  (testing "작가 생성"
    (let [valid-result (types/create-author "홍길동")
          empty-author (types/create-author "")
          long-author (types/create-author (apply str (repeat 21 "a")))]
      (is (success? valid-result))
      (is (= "홍길동" (str (:value valid-result))))
      (is (not (success? empty-author)))
      (is (not (success? long-author)))))
  
  (testing "출판일 생성"
    (let [valid-result (types/create-publication-date "2024-03-20")
          invalid-format (types/create-publication-date "2024/03/20")
          nil-date (types/create-publication-date nil)]
      (is (success? valid-result))
      (is (success? nil-date))  ;; 선택적 필드 테스트
      (is (not (success? invalid-format)))))
  
  (testing "가격 생성"
    (let [valid-result (types/create-price 1000)
          zero-price (types/create-price 0)
          negative-price (types/create-price -1000)]
      (is (success? valid-result))
      (is (success? zero-price))
      (is (not (success? negative-price)))))
  
  (testing "페이지 수 생성"
    (let [valid-result (types/create-page-count 100)
          zero-pages (types/create-page-count 0)
          negative-pages (types/create-page-count -1)]
      (is (success? valid-result))
      (is (not (success? zero-pages)))
      (is (not (success? negative-pages))))))

(deftest comic-creation-test
  (testing "UnvalidatedComic 생성"
    (let [data {:title "테스트 만화"
                :artist "테스트 작가"
                :author "테스트 글작가"
                :isbn13 "9780306406157"
                :isbn10 "0321146530"
                :cover-image {:tempfile (java.io.File. "test.jpg")}}
          result (types/create-unvalidated-comic data)]
      (is (success? result))
      (is (instance? spooky_town_admin.domain.comic.types.UnvalidatedComic 
                    (:value result)))
      (is (= (:cover-image data) 
             (-> result :value :cover-image)))))
  
  (testing "ValidatedComic 생성"
    (let [data {:title "테스트 만화"
                :artist "테스트 작가"
                :author "테스트 글작가"
                :isbn13 "9780306406157"
                :isbn10 "0321146530"
                :cover-image-metadata {:content-type "image/jpeg"
                                     :size 1000
                                     :width 800
                                     :height 600}}
          result (types/create-validated-comic data)]
      (is (success? result))
      (is (instance? spooky_town_admin.domain.comic.types.ValidatedComic 
                    (:value result)))
      (is (= (:cover-image-metadata data)
             (-> result :value :cover-image-metadata)))))
  
  (testing "PersistedComic 생성"
    (let [validated-comic (-> (types/create-validated-comic
                              {:title "테스트 만화"
                               :artist "테스트 작가"
                               :author "테스트 글작가"
                               :isbn13 "9780306406157"
                               :isbn10 "0321146530"})
                             :value)
          image-url "https://example.com/image.jpg"
          result (types/create-persisted-comic 1 validated-comic image-url)]
      (is (success? result))
      (is (instance? spooky_town_admin.domain.comic.types.PersistedComic 
                    (:value result)))
      (is (= image-url (-> result :value :cover-image-url))))))

(deftest domain-events-test
  (testing "ComicValidated 이벤트 생성"
    (let [validated-comic {:title "테스트 만화"}
          event (types/create-comic-validated validated-comic)]
      (is (instance? spooky_town_admin.domain.comic.types.ComicValidated event))
      (is (= validated-comic (:validated-comic event)))
      (is (instance? java.time.Instant (:timestamp event)))))
  
  (testing "ImageUploaded 이벤트 생성"
    (let [metadata {:content-type "image/jpeg"
                   :size 1000
                   :width 800
                   :height 600}
          event (types/create-image-uploaded metadata)]
      (is (instance? spooky_town_admin.domain.comic.types.ImageUploaded event))
      (is (= metadata (:image-metadata event)))
      (is (instance? java.time.Instant (:timestamp event)))))
  
  (testing "ComicPersisted 이벤트 생성"
    (let [persisted-comic {:id 1 :title "테스트 만화"}
          event (types/create-comic-persisted persisted-comic)]
      (is (instance? spooky_town_admin.domain.comic.types.ComicPersisted event))
      (is (= persisted-comic (:persisted-comic event)))
      (is (instance? java.time.Instant (:timestamp event))))))