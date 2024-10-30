(ns spooky-town-admin.domain.comic.types
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [spooky-town-admin.core.result :as r :refer [failure success]]
   [spooky-town-admin.domain.comic.errors :refer [business-error
                                                  get-validation-message
                                                  validation-error]]
   [spooky-town-admin.domain.comic.publisher :as publisher]))

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

(defn validate-image-metadata [image]
  (if (or (nil? image)
          (nil? (:width image))
          (nil? (:height image)))
    (do
      (log/error "Invalid image metadata structure")
      (failure (validation-error :cover-image
                                 (get-validation-message :cover-image))))
    (let [constraints [{:check #(contains? allowed-image-types (:content-type %))
                       :error-type :type}
                      {:check #(>= max-dimension (max (:width %) (:height %)))
                       :error-type :dimensions}
                      {:check #(>= max-area (* (:width %) (:height %)))
                       :error-type :area}
                      {:check #(>= max-file-size (:size %))
                       :error-type :size}]]
      (if-let [failed-constraint (first (filter #(not ((:check %) image)) constraints))]
        (do
          (log/error "Image validation failed:" (:error-type failed-constraint))
          (failure (validation-error :cover-image 
                      (get-validation-message (:error-type failed-constraint)))))
        (do
          (println "Image validation successful")
          (r/success image))))))

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
                          ^{:optional true} PublicationDate publication-date  ;; 선택
                          ^{:optional true} Object publisher  ;; 선택
                          ^{:optional true} Price price  ;; 선택
                          ^{:optional true} PageCount page-count  ;; 선택
                          ^{:optional true} Description description  ;; 선택
                          ^{:optional true} ImageMetadata cover-image-metadata])  ;; 선택

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
