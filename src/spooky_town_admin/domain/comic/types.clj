(ns spooky-town-admin.domain.comic.types
  (:require
   [clojure.spec.alpha :as s]
   [spooky-town-admin.infrastructure.monad.result :refer [success failure]]
   [spooky-town-admin.domain.comic.errors :refer [validation-error
                                                  business-error
                                                  get-validation-message
                                                  get-image-error-message]]))

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
(s/def ::artist-format (s/and string? #(<= 1 (count %) 20)))
(s/def ::author-format (s/and string? #(<= 1 (count %) 20)))
(s/def ::publisher-format (s/and string? #(<= 1 (count %) 50)))
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

(defrecord Artist [value]
  Object
  (toString [_] value))

(defrecord Author [value]
  Object
  (toString [_] value))

(defrecord Publisher [value]
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

(defn create-artist [value]
  (if (s/valid? ::artist-format value)
    (success (->Artist value))
    (failure (validation-error :artist (get-validation-message :artist)))))

(defn create-author [value]
  (if (s/valid? ::author-format value)
    (success (->Author value))
    (failure (validation-error :author (get-validation-message :author)))))

(defn create-publisher [value]
  (if (or (nil? value)
          (s/valid? ::publisher-format value))
    (success (when value (->Publisher value)))
    (failure (validation-error :publisher (get-validation-message :publisher)))))

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

;; --------- 이미지 관련 값 객체 ---------
(def allowed-image-types #{"image/png" "image/gif" "image/jpeg" "image/webp" "image/svg+xml"})
(def max-dimension 12000)
(def max-area (* 100 1000000))  ;; 100 메가픽셀
(def max-file-size (* 10 1024 1024))  ;; 10MB

(defrecord ImageMetadata [content-type width height size]
  Object
  (toString [_] 
    (format "Image[type=%s, %dx%d, %d bytes]" 
            content-type width height size)))

(defn create-image-metadata [{:keys [content-type width height size] :as data}]
  (cond
    (not (contains? allowed-image-types content-type))
    (failure (validation-error :image-type (get-image-error-message :type)))
    
    (or (> width max-dimension) (> height max-dimension))
    (failure (validation-error :image-dimensions (get-image-error-message :dimensions)))
    
    (> (* width height) max-area)
    (failure (validation-error :image-area (get-image-error-message :area)))
    
    (> size max-file-size)
    (failure (validation-error :image-size (get-image-error-message :size)))
    
    :else
    (success (map->ImageMetadata data))))

;; --------- 상태 전이를 나타내는 값 객체들 ---------
(defrecord UnvalidatedComic [title artist author isbn13 isbn10 
                            publication-date publisher price 
                            page-count description
                            cover-image])  ;; 추가: 원본 이미지 데이터

(defrecord ValidatedComic [^Title title 
                          ^Artist artist 
                          ^Author author 
                          ^ISBN13 isbn13 
                          ^ISBN10 isbn10
                          ^{:optional true} PublicationDate publication-date
                          ^{:optional true} Publisher publisher
                          ^{:optional true} Price price
                          ^{:optional true} PageCount page-count
                          ^{:optional true} Description description
                          ^{:optional true} ImageMetadata cover-image-metadata])

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
  (let [validations [(create-title title)
                     (create-artist artist)
                     (create-author author)
                     (create-isbn13 isbn13)
                     (create-isbn10 isbn10)
                     (create-publication-date publication-date)
                     (create-publisher publisher)
                     (create-price price)
                     (create-page-count page-count)
                     (create-description description)]
        errors (keep #(when (not (:success %)) (:error %)) validations)]
    (if (seq errors)
      (failure (first errors))
      (success (map->ValidatedComic 
                {:title (:value (create-title title))
                 :artist (:value (create-artist artist))
                 :author (:value (create-author author))
                 :isbn13 (:value (create-isbn13 isbn13))
                 :isbn10 (:value (create-isbn10 isbn10))
                 :publication-date (:value (create-publication-date publication-date))
                 :publisher (:value (create-publisher publisher))
                 :price (:value (create-price price))
                 :page-count (:value (create-page-count page-count))
                 :description (:value (create-description description))
                 :cover-image-metadata cover-image-metadata})))))

(defn create-persisted-comic [id validated-comic cover-image-url]
  (if validated-comic
    (success (->PersistedComic id validated-comic cover-image-url))
    (failure (business-error :invalid-comic "검증된 만화 정보가 누락되었습니다."))))