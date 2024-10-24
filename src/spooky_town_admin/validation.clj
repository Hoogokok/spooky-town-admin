(ns spooky-town-admin.validation
  (:require [clojure.string :as str]))

(defn success [value]
  {:success true :value value})

(defn failure [error]
  {:success false :error error})

(defn success? [result]
  (:success result))

(defn validate-length [field-name min max]
  (fn [v]
    (if (and (string? v) (<= min (count v) max))
      (success v)
      (failure {field-name (str field-name "의 길이는 " min "에서 " max " 사이여야 합니다.")}))))

(def validate-title (validate-length "제목" 1 100))
(def validate-artist (validate-length "그림 작가" 1 20))
(def validate-author (validate-length "글 작가" 1 20))
(def validate-publisher (validate-length "출판사" 1 50))

(defn validate-isbn-13 [isbn]
  (if (and (string? isbn)
           (re-matches #"^(?:978|979)-\d-\d{2,7}-\d{1,7}-\d$" isbn))
    (success isbn)
    (failure {"ISBN-13" "유효하지 않은 ISBN-13 형식입니다."})))

(defn validate-isbn-10 [isbn]
  (if (and (string? isbn)
           (re-matches #"^\d{1,5}-\d{1,7}-\d{1,6}-[\dX]$" isbn))
    (success isbn)
    (failure {"ISBN-10" "유효하지 않은 ISBN-10 형식입니다."})))

(defn validate-publication-date [date]
  (if (and (string? date)
           (re-matches #"^\d{4}-\d{2}-\d{2}$" date))
    (success date)
    (failure {"출판일" "유효하지 않은 출판일 형식입니다. YYYY-MM-DD 형식이어야 합니다."})))

(defn validate-price [price]
  (if (and (number? price) (>= price 0))
    (success price)
    (failure {"가격" "가격은 0 이상의 숫자여야 합니다."})))

(defn validate-pages [pages]
  (if (and (integer? pages) (> pages 0))
    (success pages)
    (failure {"쪽수" "쪽수는 1 이상의 정수여야 합니다."})))

(def validate-description (validate-length "설명" 0 1000))

(defn validate-optional-field [field-name validator]
  (fn [value]
    (if (nil? value)
      (success nil)
      (validator value))))

(defn validate-comic [comic]
  (let [title-result (validate-title (:title comic))
        artist-result (validate-artist (:artist comic))
        author-result (validate-author (:author comic))
        isbn-13-result (validate-isbn-13 (:isbn-13 comic))
        isbn-10-result (validate-isbn-10 (:isbn-10 comic))
        publisher-result ((validate-optional-field "출판사" validate-publisher) (:publisher comic))
        publication-date-result ((validate-optional-field "출판일" validate-publication-date) (:publication-date comic))
        price-result ((validate-optional-field "가격" validate-price) (:price comic))
        pages-result ((validate-optional-field "쪽수" validate-pages) (:pages comic))
        description-result ((validate-optional-field "설명" validate-description) (:description comic))
        all-results [title-result artist-result author-result isbn-13-result isbn-10-result
                     publisher-result publication-date-result price-result pages-result description-result]
        errors (reduce (fn [acc result]
                         (if (success? result)
                           acc
                           (merge acc (:error result))))
                       {}
                       all-results)]
    (if (empty? errors)
      (success (-> comic
                   (assoc :title (:value title-result))
                   (assoc :artist (:value artist-result))
                   (assoc :author (:value author-result))
                   (assoc :isbn-13 (:value isbn-13-result))
                   (assoc :isbn-10 (:value isbn-10-result))
                   (assoc :publisher (:value publisher-result))
                   (assoc :publication-date (:value publication-date-result))
                   (assoc :price (:value price-result))
                   (assoc :pages (:value pages-result))
                   (assoc :description (:value description-result))))
      (failure errors))))
