;; 만화 데이터 검증을 위한 네임스페이스
(ns spooky-town-admin.validation
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [java.io File]
           [java.nio.file Path Files]
           [javax.imageio ImageIO]
           [java.awt.image BufferedImage]))

;; 각 필드별 검증 실패 시 표시할 에러 메시지
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

;; 검증 성공 시 결과를 래핑하는 함수
(defn success [value]
  {:success true :value value})

;; 검증 실패 시 에러를 래핑하는 함수
(defn failure [error]
  {:success false :error error})

;; 검증 결과가 성공인지 확인하는 함수
(defn success? [result]
  (:success result))

;; 문자열 길이를 검증하는 함수를 생성하는 고차 함수
;; field-name: 필드 이름
;; min: 최소 길이
;; max: 최대 길이
(defn validate-length [field-name min max]
  (fn [v]
    (if (and (string? v) (not= v "") (<= min (count v) max))
      (success v)
      (failure {field-name (error-messages (keyword field-name))}))))

;; 각 필드별 길이 검증 함수 정의
(def validate-title (validate-length :title 1 100))
(def validate-artist (validate-length :artist 1 20))
(def validate-author (validate-length :author 1 20))
(def validate-publisher (validate-length :publisher 1 50))
(def validate-description (validate-length :description 0 1000))

;; ISBN-13 형식 검증 (978 또는 979로 시작하는 13자리 숫자)
(defn validate-isbn-13 [isbn]
  (if (and (string? isbn)
           (re-matches #"^(?:978|979)-\d-\d{2,7}-\d{1,7}-\d$" isbn))
    (success isbn)
    (failure {:isbn-13 (error-messages :isbn-13)})))

;; ISBN-10 형식 검증 (10자리 숫자 또는 마지막 자리가 X)
(defn validate-isbn-10 [isbn]
  (if (and (string? isbn)
           (re-matches #"^\d{1,5}-\d{1,7}-\d{1,6}-[\dX]$" isbn))
    (success isbn)
    (failure {:isbn-10 (error-messages :isbn-10)})))

;; 출판일 형식 검증 (YYYY-MM-DD)
(defn validate-publication-date [date]
  (if (and (string? date)
           (re-matches #"^\d{4}-\d{2}-\d{2}$" date))
    (success date)
    (failure {:publication-date (error-messages :publication-date)})))

;; 가격 검증 (0 이상의 숫자)
(defn validate-price [price]
  (if (and (number? price) (>= price 0))
    (success price)
    (failure {:price (error-messages :price)})))

;; 페이지 수 검증 (1 이상의 정수)
(defn validate-pages [pages]
  (if (and (integer? pages) (> pages 0))
    (success pages)
    (failure {:pages (error-messages :pages)})))

;; 허용되는 이미지 MIME 타입
(def allowed-image-types #{"image/jpeg" "image/png"})

;; 파일의 MIME 타입을 확인하는 함수
(defn probe-content-type [^Path path]
  (Files/probeContentType path))

;; 이미지 파일을 BufferedImage 객체로 읽는 함수
(defn read-buffered-image [^File file]
  (ImageIO/read file))

;; 이미지 파일의 정보(크기, 타입 등)를 추출하는 함수
(defn get-image-info [file]
  (let [image (read-buffered-image file)
        width (.getWidth image)
        height (.getHeight image)]
    {:image image
     :width width
     :height height
     :content-type (probe-content-type (.toPath file))}))

;; 이미지 타입 검증 (JPEG 또는 PNG만 허용)
(defn validate-image-type [file]
  (let [content-type (probe-content-type (.toPath file))]
    (if (contains? allowed-image-types content-type)
      (success file)
      (failure {:cover-image (error-messages :cover-image-type)}))))

;; 이미지 크기 검증 (1000x1500 픽셀 이하)
(defn validate-image-dimensions [file]
  (let [{:keys [width height]} (get-image-info file)]
    (if (and (<= width 1000) (<= height 1500))
      (success file)
      (failure {:cover-image (error-messages :cover-image-dimensions)}))))

;; 이미지 영역 검증 (100 메가픽셀 이하)
(defn validate-image-area [file]
  (let [{:keys [width height]} (get-image-info file)
        area (* width height)]
    (if (<= area 100000000)  ; 100 메가 픽셀
      (success area)
      (failure {:cover-image (error-messages :cover-image-area)}))))

;; 파일 크기 검증 (10MB 이하)
(defn validate-file-size [file]
  (let [size (.length (io/file file))]
    (if (<= size (* 10 1024 1024))  ; 10MB
      (success size)
      (failure {:cover-image (error-messages :cover-image-size)}))))

;; 표지 이미지 검증 (모든 이미지 관련 검증 수행)
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

;; 선택적 필드 검증을 위한 래퍼 함수 (nil 값 허용)
(defn validate-optional-field [field-name validator]
  (fn [value]
    (if (nil? value)
      (success nil)
      (validator value))))

;; 만화 데이터 전체 검증
(defn validate-comic [comic]
  (let [validations [[:title validate-title]  ; 필수 필드들
                     [:artist validate-artist]
                     [:author validate-author]
                     [:isbn-13 validate-isbn-13]
                     [:isbn-10 validate-isbn-10]
                     [:publisher (validate-optional-field :publisher validate-publisher)]  ; 선택적 필드들
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
