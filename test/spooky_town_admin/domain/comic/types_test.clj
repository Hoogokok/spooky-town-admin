(ns spooky-town-admin.domain.comic.types-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [spooky-town-admin.domain.comic.types :as types]
            [spooky-town-admin.core.result :refer [success? value]] 
            [spooky-town-admin.domain.comic.publisher :as publisher])
  (:import [java.awt.image BufferedImage]
           [javax.imageio ImageIO]
           [java.io File ByteArrayInputStream ByteArrayOutputStream]))

;; 테스트용 이미지 생성 헬퍼 함수
(defn create-test-image [width height]
  (let [image (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
        temp-file (File/createTempFile "test" ".jpg")]
    (.deleteOnExit temp-file)
    (ImageIO/write image "jpg" temp-file)
    temp-file))

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
                :author "테스트 작가"
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

(deftest publisher-value-objects-test
  (testing "출판사 이름 생성"
    (let [valid-result (publisher/validate-publisher-name "사월의 책")
          empty-name (publisher/validate-publisher-name "")
          nil-name (publisher/validate-publisher-name nil)
          long-name (publisher/validate-publisher-name (apply str (repeat 51 "a")))
          invalid-chars (publisher/validate-publisher-name "사월의 책!@#")]
      (is (success? valid-result))
      (is (= "사월의 책" (get-in valid-result [:value :value])))
      (is (not (success? empty-name)))
      (is (success? nil-name))
      (is (not (success? long-name)))
      (is (not (success? invalid-chars)))))

  (testing "UnvalidatedPublisher 생성"
    (let [data {:name "사월의 책"}
          result (publisher/create-unvalidated-publisher data)]
      (is (success? result))
      (is (instance? spooky_town_admin.domain.comic.publisher.UnvalidatedPublisher 
                    (:value result)))
      (is (= "사월의 책" (:name (:value result))))))

  (testing "ValidatedPublisher 생성"
    (let [valid-data {:name "사월의 책"}
          nil-data {:name nil}
          invalid-data {:name ""}
          valid-result (publisher/create-validated-publisher valid-data)
          nil-result (publisher/create-validated-publisher nil-data)
          invalid-result (publisher/create-validated-publisher invalid-data)]
      (is (success? valid-result))
      (is (success? nil-result))
      (is (nil? (:value nil-result)))
      (is (instance? spooky_town_admin.domain.comic.publisher.ValidatedPublisher 
                    (:value valid-result)))
      (is (= "사월의 책" (get-in valid-result [:value :name :value])))
      (is (not (success? invalid-result)))))

  (testing "PersistedPublisher 생성"
    (let [validated-publisher (-> (publisher/create-validated-publisher 
                                  {:name "사월의 책"})
                                :value)
          result (publisher/create-persisted-publisher 1 validated-publisher)
          nil-result (publisher/create-persisted-publisher 1 nil)]
      (is (success? result))
      (is (instance? spooky_town_admin.domain.comic.publisher.PersistedPublisher 
                    (:value result)))
      (is (= 1 (-> result :value :id)))
      (is (= (:name validated-publisher)  ;; ValidatedPublisher의 name 필드만 비교
             (-> result :value :name)))
      (is (not (success? nil-result))))))

(deftest comic-creation-with-publisher-test
  (testing "ValidatedComic with publisher 생성"
    (let [data {:title "테스트 만화"
                :artist "테스트 작가"
                :author "테스트 글작가"
                :isbn13 "9780306406157"
                :isbn10 "0321146530"
                :publisher {:name "사월의 책"}
                :cover-image-metadata {:content-type "image/jpeg"
                                     :size 1000
                                     :width 800
                                     :height 600}}
          result (types/create-validated-comic data)]
      (is (success? result))
      (is (instance? spooky_town_admin.domain.comic.types.ValidatedComic 
                    (:value result)))
      (is (= "사월의 책" 
             (get-in result [:value :publisher :name :value])))  ;; :value 경로 추가
      (is (= (:cover-image-metadata data)
             (-> result :value :cover-image-metadata))))))

(deftest image-validation-test
  (testing "이미지 데이터 검증"
    (let [test-file (create-test-image 800 600)
          image-data (types/->UnvalidatedImageData 
                      test-file
                      "image/jpeg"
                      (.length test-file)
                      "test.jpg")]
      
      (testing "메타데이터 추출"
        (let [result (types/extract-image-metadata image-data)]
          (is (success? result))
          (let [metadata (value result)]
            (is (= "image/jpeg" (:content-type metadata)))
            (is (= 800 (:width metadata)))
            (is (= 600 (:height metadata)))
            (is (pos? (:size metadata))))))
      
      (testing "이미지 데이터 검증"
        (let [result (types/validate-image-data image-data)]
          (is (success? result))
          (is (instance? spooky_town_admin.domain.comic.types.ValidatedImageData 
                        (value result)))))
      
      (testing "nil 이미지 데이터"
        (let [result (types/validate-image-data nil)]
          (is (success? result))
          (is (nil? (value result)))))
      
      (testing "잘못된 크기의 이미지"
        (let [large-file (create-test-image 13000 13000)
              large-image-data (types/->UnvalidatedImageData 
                               large-file
                               "image/jpeg"
                               (.length large-file)
                               "large.jpg")
              result (types/validate-image-data large-image-data)]
          (is (not (success? result)))
          (is (= :cover-image (:field (:error result)))))))))