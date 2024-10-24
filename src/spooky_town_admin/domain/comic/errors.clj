(ns spooky-town-admin.domain.comic.errors
  (:require [clojure.spec.alpha :as s]))

;; 에러 타입 정의
(defrecord ValidationError [field message])
(defrecord BusinessError [code message])
(defrecord SystemError [code message details])

;; 에러 생성 함수들
(defn validation-error [field message]
  (->ValidationError field message))

(defn business-error [code message]
  (->BusinessError code message))

(defn system-error [code message details]
  (->SystemError code message details))

;; 도메인 에러 메시지 정의
(def error-messages
  {:validation
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
    :cover-image "표지 이미지가 유효하지 않습니다."}

   :business
   {:duplicate-isbn "이미 존재하는 ISBN입니다."
    :invalid-publication-date "출판일은 미래일 수 없습니다."
    :invalid-cover-image "표지 이미지가 요구사항을 충족하지 않습니다."}
   
   :system
   {:db-error "데이터베이스 오류가 발생했습니다."
    :image-upload-error "이미지 업로드 중 오류가 발생했습니다."
    :image-processing-error "이미지 처리 중 오류가 발생했습니다."
    :image-storage-error "이미지 저장소 접근 중 오류가 발생했습니다."
    :image-metadata-error "이미지 메타데이터 처리 중 오류가 발생했습니다."}})

;; 이미지 관련 에러 메시지
(def image-error-messages
  {:type "PNG, GIF, JPEG, WebP, SVG 형식의 이미지만 허용됩니다."
   :dimensions "이미지 크기는 12000x12000 픽셀을 초과할 수 없습니다."
   :area "이미지 영역이 100 메가 픽셀을 초과합니다."
   :size "파일 크기가 10MB를 초과합니다."
   :missing "파일이 존재하지 않습니다."
   :invalid "표지 이미지가 유효하지 않습니다."})


;; 헬퍼 함수들
(defn get-validation-message [field]
  (get-in error-messages [:validation field]))

(defn get-business-message [code]
  (get-in error-messages [:business code]))

(defn get-system-message [code]
  (get-in error-messages [:system code]))

(defn get-image-error-message [type]
  (get image-error-messages type))
