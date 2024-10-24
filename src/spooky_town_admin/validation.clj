(ns spooky-town-admin.validation
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [java.io File]
           [java.nio.file Path Files]
           [javax.imageio ImageIO]
           [java.awt.image BufferedImage]))

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

(def error-messages
  {:title "제목의 길이는 1에서 100 사이여야 합니다."
   :artist "그림 작가의 길이는 1에서 20 사이여야 합니다."
   :author "글 작가의 길이는 1에서 20 사이여야 합니다."
   :publisher "출판사의 길이는 1에서 50 사이여야 합니다."
   :isbn-13 "유효하지 않은 ISBN-13 형식입니다."
   :isbn-10 "유효하지 않은 ISBN-10 형식입니다."
   :publication-date "유효하지 않은 출판일 형식입니다. YYYY-MM-DD 형식이어야 합니다."
   :price "가격은 0 이상의 숫자여야 합니다."
   :pages "쪽수는 1 이상의 정수여야 합니다."
   :description "설명의 길이는 0에서 1000 사이여야 합니다."
   :cover-image-type "JPEG 또는 PNG 형식의 이미지만 허용됩니다."
   :cover-image-dimensions "이미지 크기는 1000x1500 픽셀을 초과할 수 없습니다."
   :cover-image-area "이미지 영역이 100 메가 픽셀을 초과합니다."
   :cover-image-size "파일 크기가 10MB를 초과합니다."
   :cover-image-missing "파일이 존재하지 않습니다."})

(defn validate-isbn-13 [isbn]
  (if (and (string? isbn)
           (re-matches #"^(?:978|979)-\d-\d{2,7}-\d{1,7}-\d$" isbn))
    (success isbn)
    (failure {"ISBN-13" (error-messages :isbn-13)})))

(defn validate-isbn-10 [isbn]
  (if (and (string? isbn)
           (re-matches #"^\d{1,5}-\d{1,7}-\d{1,6}-[\dX]$" isbn))
    (success isbn)
    (failure {"ISBN-10" (error-messages :isbn-10)})))

(defn validate-publication-date [date]
  (if (and (string? date)
           (re-matches #"^\d{4}-\d{2}-\d{2}$" date))
    (success date)
    (failure {"출판일" (error-messages :publication-date)})))

(defn validate-price [price]
  (if (and (number? price) (>= price 0))
    (success price)
    (failure {"가격" (error-messages :price)})))

(defn validate-pages [pages]
  (if (and (integer? pages) (> pages 0))
    (success pages)
    (failure {"쪽수" (error-messages :pages)})))

(def validate-description (validate-length "설명" 0 1000))

(def allowed-image-types #{"image/png" "image/gif" "image/jpeg" "image/webp" "image/svg+xml"})

(defn probe-content-type [^Path path]
  (Files/probeContentType path))

(defn read-buffered-image [^File file]
  (ImageIO/read file))

(defn get-image-info [file]
  (let [image (read-buffered-image file)
        width (.getWidth image)
        height (.getHeight image)]
    {:image image
     :width width
     :height height
     :content-type (probe-content-type (.toPath file))}))

(defn validate-image-type [file]
  (let [{:keys [content-type]} (get-image-info file)]
    (if (contains? #{"image/jpeg" "image/png"} content-type)
      (success file)
      (failure {"표지 이미지" (error-messages :cover-image-type)}))))

(defn validate-image-dimensions [file]
  (let [{:keys [width height]} (get-image-info file)]
    (if (and (<= width 1000) (<= height 1500))
      (success file)
      (failure {"표지 이미지" (error-messages :cover-image-dimensions)}))))

(defn validate-image-area [file]
  (let [{:keys [width height]} (get-image-info file)
        area (* width height)]
    (if (<= area 100000000)  ; 100 메가 픽셀
      (success area)
      (failure {"표지 이미지" (error-messages :cover-image-area)}))))

(defn validate-file-size [file]
  (let [size (.length (io/file file))]
    (if (<= size (* 10 1024 1024))  ; 10MB
      (success size)
      (failure {"표지 이미지" (error-messages :cover-image-size)}))))

(defn validate-cover-image [file]
  (if (and file (.exists (io/file file)))
    (let [type-result (validate-image-type file)
          dimensions-result (validate-image-dimensions file)
          area-result (validate-image-area file)
          size-result (validate-file-size file)
          all-results [type-result dimensions-result area-result size-result]
          errors (reduce (fn [acc result]
                           (if (success? result)
                             acc
                             (merge acc (:error result))))
                         {}
                         all-results)]
      (if (empty? errors)
        (success file)
        (failure errors)))
    (failure {"표지 이미지" (error-messages :cover-image-missing)})))

(defn validate-optional-field [field-name validator]
  (fn [value]
    (if (nil? value)
      (success nil)
      (let [result (validator value)]
        (if (success? result)
          result
          (failure {field-name (get (:error result) field-name)}))))))

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
        cover-image-result ((validate-optional-field "표지 이미지" validate-cover-image) (:cover-image comic))
        all-results [title-result artist-result author-result isbn-13-result isbn-10-result
                     publisher-result publication-date-result price-result pages-result description-result
                     cover-image-result]
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
                   (assoc :description (:value description-result))
                   (assoc :cover-image (:value cover-image-result))))
      (failure errors))))
