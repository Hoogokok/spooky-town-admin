(ns spooky-town-admin.domain.comic.types
  (:require
   [clojure.spec.alpha :as s]
   [spooky-town-admin.core.result :as r :refer [failure success]]
   [spooky-town-admin.domain.comic.author :as author]
   [spooky-town-admin.domain.comic.errors :refer [business-error
                                                  get-business-message
                                                  get-validation-message
                                                  validation-error]]
   [spooky-town-admin.domain.comic.publisher :as publisher])
  (:import
   [java.awt.image BufferedImage]
   [java.io File]
   [java.nio.file Files Path]
   [javax.imageio ImageIO]
   [spooky_town_admin.domain.comic.author ValidatedAuthor]))

;; --------- 유효성 검사 헬퍼 함수들 ---------
(defn- calculate-isbn13-checksum [isbn]
  (let [digits (map #(Character/digit % 10) 
                   (filter #(Character/isDigit %) isbn))
        products (map-indexed 
                  (fn [idx digit] 
                    (* digit (if (even? idx) 1 3))) 
                  (take 12 digits))
        sum (reduce + products)
        remainder (mod sum 10)]
    (if (zero? remainder) 0 (- 10 remainder))))

(defn- calculate-isbn10-checksum [isbn]
  (let [digits (map #(Character/digit % 10) (butlast isbn))
        sum (reduce + (map-indexed #(* (- 10 %1) %2) digits))
        remainder (mod sum 11)]
    (cond
      (zero? remainder) "0"
      (= 1 remainder) "X"
      :else (str (- 11 remainder)))))

;; --------- 값 객체 스펙 ---------
(s/def ::isbn13-format 
  (s/and string? 
         #(re-matches #"^(?:978|979)\d{10}$" %)))

(s/def ::isbn10-format 
  (s/and string? 
         #(re-matches #"^\d{9}[\dX]$" %)))

(s/def ::title-format (s/and string? #(<= 1 (count %) 100)))
(s/def ::publication-date-format #(re-matches #"^\d{4}-\d{2}-\d{2}$" %))
(s/def ::price-format (s/and number? #(>= % 0)))
(s/def ::page-count-format (s/and integer? pos?))
(s/def ::description-format (s/and string? #(<= 0 (count %) 1000)))

;; --------- 기본 값 객체 정의 ---------
(defrecord ISBN13 [value]
  Object
  (toString [_] value))

(defrecord ISBN10 [value]
  Object
  (toString [_] value))

(defrecord Title [value]
  Object
  (toString [_] value))

(defrecord PublicationDate [value]
  Object
  (toString [_] value))

(defrecord Price [value]
  Object
  (toString [_] value))

(defrecord PageCount [value]
  Object
  (toString [_] (str value)))

(defrecord Description [value]
  Object
  (toString [_] value))

;; --------- 기본 값 객체 생성자 함수들 ---------
(defn create-isbn13 [value]
  (cond
    (not (s/valid? ::isbn13-format value))
    (failure (validation-error :isbn-13 (get-validation-message :isbn-13)))
    
    (not= (str (last value))
          (str (calculate-isbn13-checksum value)))
    (failure (validation-error :isbn-13 "ISBN-13 체크섬이 올바르지 않습니다."))
    
    :else
    (success (->ISBN13 value))))

(defn create-isbn10 [value]
  (cond
    (not (s/valid? ::isbn10-format value))
    (failure (validation-error :isbn-10 (get-validation-message :isbn-10)))
    
    (not= (str (last value))
          (calculate-isbn10-checksum value))
    (failure (validation-error :isbn-10 "ISBN-10 체크섬이 올바르지 않습니다."))
    
    :else
    (success (->ISBN10 value))))

(defn create-title [value]
  (if (s/valid? ::title-format value)
    (success (->Title value))
    (failure (validation-error :title (get-validation-message :title)))))

;; ValidatedComic 레코드 수정 - 타입 힌트 변경
(defrecord ValidatedComic [^Title title 
                          ^spooky_town_admin.domain.comic.author.ValidatedAuthor artist  
                          ^spooky_town_admin.domain.comic.author.ValidatedAuthor author  
                          ^ISBN13 isbn13 
                          ^ISBN10 isbn10
                          ^{:optional true} PublicationDate publication-date
                          ^{:optional true} Object publisher
                          ^{:optional true} Price price
                          ^{:optional true} PageCount page-count
                          ^{:optional true} Description description
                          ^{:optional true} ImageMetadata cover-image-metadata])

;; 작가/아티스트 생성 함수 수정
(defn create-artist [author-data]
  (if (string? author-data)
    (-> (author/create-validated-author {:name author-data :type :artist})
        (r/map #(assoc % :role :artist)))
    (-> (author/create-validated-author (assoc author-data :type :artist))
        (r/map #(assoc % :role :artist)))))

(defn create-author [author-data]
  (if (string? author-data)
    (-> (author/create-validated-author {:name author-data :type :writer})
        (r/map #(assoc % :role :writer)))
    (-> (author/create-validated-author (assoc author-data :type :writer))
        (r/map #(assoc % :role :writer)))))

(defn create-publication-date [value]
  (if (or (nil? value)
          (s/valid? ::publication-date-format value))
    (success (when value (->PublicationDate value)))
    (failure (validation-error :publication-date (get-validation-message :publication-date)))))

(defn create-price [value]
  (if (or (nil? value)
          (s/valid? ::price-format value))
    (success (when value (->Price value)))
    (failure (validation-error :price (get-validation-message :price)))))

(defn create-page-count [value]
  (if (or (nil? value)
          (s/valid? ::page-count-format value))
    (success (when value (->PageCount value)))
    (failure (validation-error :pages (get-validation-message :pages)))))

(defn create-description [value]
  (if (or (nil? value)
          (s/valid? ::description-format value))
    (success (when value (->Description value)))
    (failure (validation-error :description (get-validation-message :description)))))

;; --------- 이미지 관련 값 객체와 도메인 규칙 ---------
(def allowed-image-types #{"image/png" "image/gif" "image/jpeg" "image/webp" "image/svg+xml"})
(def max-dimension 12000)
(def max-area (* 100 1000000))  ;; 100 메가픽셀
(def max-file-size (* 10 1024 1024))  ;; 10MB

(defrecord UnvalidatedImageData [tempfile content-type size filename])
(defrecord ValidatedImageData [metadata tempfile])
(defrecord ImageMetadata [content-type width height size])

(defn validate-image-metadata [metadata-result]
  (r/bind metadata-result
    (fn [metadata]
      (cond
        (not (contains? allowed-image-types (:content-type metadata)))
        (r/failure (validation-error 
                    :cover-image 
                    (get-validation-message :image-type)))
        
        (> (max (:width metadata) (:height metadata)) max-dimension)
        (r/failure (validation-error 
                    :cover-image 
                    (get-validation-message :image-dimensions)))
        
        (> (* (:width metadata) (:height metadata)) max-area)
        (r/failure (validation-error 
                    :cover-image 
                    (get-validation-message :image-area)))
        
        (> (:size metadata) max-file-size)
        (r/failure (validation-error 
                    :cover-image 
                    (get-validation-message :image-size)))
        
        :else
        (r/success metadata)))))

(defn extract-image-metadata [image-data]
  (try
    (let [^File temp-file (:tempfile image-data)
          ^BufferedImage image (ImageIO/read temp-file)
          ^Path path (.toPath temp-file)
          content-type (Files/probeContentType path)]
      (r/success (->ImageMetadata 
                  content-type
                  (.getWidth image)
                  (.getHeight image)
                  (.length temp-file))))
    (catch Exception e
      (r/failure (validation-error 
                  :cover-image 
                  (get-validation-message :invalid-image))))))

(defn validate-image-data [image-data]
  (if (nil? image-data)
    (r/success nil)
    (cond
      ;; 파일 크기가 0인 경우 검증
      (zero? (:size image-data))
      (r/failure (business-error 
                  :invalid-image
                  (get-business-message :invalid-image)))
      
      ;; 파일이 존재하지 않는 경우 검증
      (not (.exists ^File (:tempfile image-data)))
      (r/failure (business-error 
                  :invalid-image
                  (get-business-message :invalid-image)))
      
      :else
      (-> (extract-image-metadata image-data)
          (validate-image-metadata)
          (r/map #(->ValidatedImageData % (:tempfile image-data)))))))

;; --------- 상태 전이를 나타내는 값 객체들 ---------
(defrecord UnvalidatedComic [title artist author isbn13 isbn10 
                            publication-date publisher price 
                            page-count description
                            cover-image])  ;; 추가: 원본 이미지 데이터

;; ValidatedComic 레코드 수정 - 타입 힌트 변경
(defrecord ValidatedComic [^Title title 
                          ^ValidatedAuthor artist  
                          ^ValidatedAuthor author  
                          ^ISBN13 isbn13 
                          ^ISBN10 isbn10
                          ^{:optional true} PublicationDate publication-date
                          ^{:optional true} Object publisher
                          ^{:optional true} Price price
                          ^{:optional true} PageCount page-count
                          ^{:optional true} Description description
                          ^{:optional true} ImageMetadata cover-image-metadata])

;; 작가/아티스트 생성 함수 수정
(defn create-artist [author-data]
  (if (string? author-data)
    (-> (author/create-validated-author {:name author-data :type :artist})
        (r/map #(assoc % :role :artist)))
    (-> (author/create-validated-author (assoc author-data :type :artist))
        (r/map #(assoc % :role :artist)))))

(defn create-author [author-data]
  (if (string? author-data)
    (-> (author/create-validated-author {:name author-data :type :writer})
        (r/map #(assoc % :role :writer)))
    (-> (author/create-validated-author (assoc author-data :type :writer))
        (r/map #(assoc % :role :writer)))))
(defrecord PersistedComic [id validated-comic cover-image-url])

;; --------- 도메인 이벤트 ---------
(defrecord ComicValidated [validated-comic timestamp])
(defrecord ImageUploaded [image-metadata timestamp])
(defrecord ImageStored [comic-data image-url timestamp])  
(defrecord ComicPersisted [persisted-comic timestamp])

;; --------- 도메인 이벤트 생성 함수들 ---------
(defn create-comic-validated [validated-comic]
  (->ComicValidated validated-comic (java.time.Instant/now)))

(defn create-image-uploaded [image-metadata]
  (->ImageUploaded image-metadata (java.time.Instant/now)))

(defn create-image-stored [comic-data image-url]  
  (->ImageStored comic-data image-url (java.time.Instant/now)))

(defn create-comic-persisted [persisted-comic]
  (->ComicPersisted persisted-comic (java.time.Instant/now)))

;; --------- 상태 전이 함수들 ---------
(defn create-unvalidated-comic [data]
  (success (map->UnvalidatedComic data)))

(defn create-validated-comic [{:keys [title artist author isbn13 isbn10
                                    publication-date publisher price
                                    page-count description
                                    cover-image-metadata] :as data}]
  (let [required-validations [(create-title title)
                             (create-artist artist)
                             (create-author author)
                             (create-isbn13 isbn13)
                             (create-isbn10 isbn10)]
        optional-validations [(when (some? publication-date)
                               (create-publication-date publication-date))
                             (when (some? publisher)
                              (publisher/create-validated-publisher publisher))
                             (when (some? price)
                               (create-price price))
                             (when (some? page-count)
                               (create-page-count page-count))
                             (when (some? description)
                               (create-description description))]
        required-errors (keep #(when (not (:success %)) (:error %)) required-validations)
        optional-errors (keep #(when (and % (not (:success %))) (:error %)) optional-validations)]
    
    (if (seq required-errors)
      (failure (first required-errors))
      (success (map->ValidatedComic 
                {:title (:value (create-title title))
                 :artist (:value (create-artist artist))
                 :author (:value (create-author author))
                 :isbn13 (:value (create-isbn13 isbn13))
                 :isbn10 (:value (create-isbn10 isbn10))
                 :publication-date (when publication-date 
                                   (:value (create-publication-date publication-date)))
                 :publisher (when publisher 
                            (:value (publisher/create-validated-publisher publisher)))
                 :price (when price 
                         (:value (create-price price)))
                 :page-count (when page-count 
                             (:value (create-page-count page-count)))
                 :description (when description 
                              (:value (create-description description)))
                 :cover-image-metadata cover-image-metadata})))))

(defn create-persisted-comic [id validated-comic cover-image-url]
  (if validated-comic
    (success (->PersistedComic id validated-comic cover-image-url))
    (failure (business-error :invalid-comic "검증된 만화 정보가 누락되었습니다."))))
