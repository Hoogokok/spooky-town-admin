(ns spooky-town-admin.validation
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [java.io File]
           [java.nio.file Path Files]
           [javax.imageio ImageIO]
           [java.awt.image BufferedImage]))

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
   :cover-image-missing "파일이 존재하지 않습니다."
   :cover-image "표지 이미지가 유효하지 않습니다."})

(defn success [value]
  {:success true :value value})

(defn failure [error]
  {:success false :error error})

(defn success? [result]
  (:success result))

(defn validate-length [field-name min max]
  (fn [v]
    (if (and (string? v) (not= v "") (<= min (count v) max))
      (success v)
      (failure {field-name (error-messages (keyword field-name))}))))

(def validate-title (validate-length :title 1 100))
(def validate-artist (validate-length :artist 1 20))
(def validate-author (validate-length :author 1 20))
(def validate-publisher (validate-length :publisher 1 50))
(def validate-description (validate-length :description 0 1000))

(defn validate-isbn-13 [isbn]
  (if (and (string? isbn)
           (re-matches #"^(?:978|979)-\d-\d{2,7}-\d{1,7}-\d$" isbn))
    (success isbn)
    (failure {:isbn-13 (error-messages :isbn-13)})))

(defn validate-isbn-10 [isbn]
  (if (and (string? isbn)
           (re-matches #"^\d{1,5}-\d{1,7}-\d{1,6}-[\dX]$" isbn))
    (success isbn)
    (failure {:isbn-10 (error-messages :isbn-10)})))

(defn validate-publication-date [date]
  (if (and (string? date)
           (re-matches #"^\d{4}-\d{2}-\d{2}$" date))
    (success date)
    (failure {:publication-date (error-messages :publication-date)})))

(defn validate-price [price]
  (if (and (number? price) (>= price 0))
    (success price)
    (failure {:price (error-messages :price)})))

(defn validate-pages [pages]
  (if (and (integer? pages) (> pages 0))
    (success pages)
    (failure {:pages (error-messages :pages)})))

(def allowed-image-types #{"image/jpeg" "image/png"})

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
  (let [content-type (probe-content-type (.toPath file))]
    (if (contains? allowed-image-types content-type)
      (success file)
      (failure {:cover-image (error-messages :cover-image-type)}))))

(defn validate-image-dimensions [file]
  (let [{:keys [width height]} (get-image-info file)]
    (if (and (<= width 1000) (<= height 1500))
      (success file)
      (failure {:cover-image (error-messages :cover-image-dimensions)}))))

(defn validate-image-area [file]
  (let [{:keys [width height]} (get-image-info file)
        area (* width height)]
    (if (<= area 100000000)  ; 100 메가 픽셀
      (success area)
      (failure {:cover-image (error-messages :cover-image-area)}))))

(defn validate-file-size [file]
  (let [size (.length (io/file file))]
    (if (<= size (* 10 1024 1024))  ; 10MB
      (success size)
      (failure {:cover-image (error-messages :cover-image-size)}))))

(defn validate-cover-image [file]
  (if (and file (.exists (io/file file)))
    (let [validations [validate-image-type
                       validate-image-dimensions
                       validate-image-area
                       validate-file-size]
          results (map #(% file) validations)
          errors (keep #(when-not (success? %) (:error %)) results)]
      (if (empty? errors)
        (success file)
        (failure (first errors))))
    (failure {:cover-image (error-messages :cover-image-missing)})))

(defn validate-optional-field [field-name validator]
  (fn [value]
    (if (nil? value)
      (success nil)
      (validator value))))

(defn validate-comic [comic]
  (let [validations [[:title validate-title]
                     [:artist validate-artist]
                     [:author validate-author]
                     [:isbn-13 validate-isbn-13]
                     [:isbn-10 validate-isbn-10]
                     [:publisher (validate-optional-field :publisher validate-publisher)]
                     [:publication-date (validate-optional-field :publication-date validate-publication-date)]
                     [:price (validate-optional-field :price validate-price)]
                     [:pages (validate-optional-field :pages validate-pages)]
                     [:description (validate-optional-field :description validate-description)]
                     [:cover-image (validate-optional-field :cover-image validate-cover-image)]]
        results (map (fn [[field validator]]
                       [field (validator (get comic field))])
                     validations)
        errors (into {} (keep (fn [[field result]]
                                (when-not (success? result)
                                  [field (get-in result [:error field])]))  ;; 중첩된 에러 메시지에서 실제 메시지만 추출
                              results))]
    (if (empty? errors)
      (success (into {} (map (fn [[field result]] [field (:value result)]) results)))
      (failure errors))))
