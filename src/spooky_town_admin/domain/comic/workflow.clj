(ns spooky-town-admin.domain.comic.workflow
  (:require [spooky-town-admin.domain.comic.types :as types]
            [spooky-town-admin.domain.comic.errors :as errors]
            [clojure.spec.alpha :as s]
           )
  
  (:import [javax.imageio ImageIO]
           [java.io File]
           [java.io FileInputStream]))

;; 결과 래퍼 타입
(defrecord Success [value])
(defrecord Failure [error])

(defn success [value]
  (->Success value))

(defn failure [error]
  (->Failure error))

(defn success? [result]
  (instance? Success result))

(defn validate-image-constraints [image]
  (if (or (nil? image)
          (nil? (:width image))
          (nil? (:height image)))
    (failure (errors/validation-error :cover-image 
                                    (errors/get-image-error-message :invalid)))
    (let [constraints [{:check #(and (:content-type %) 
                                   (contains? types/allowed-image-types (:content-type %)))
                       :error-type :type}
                      {:check #(and (:width %) (:height %)
                                  (<= (:width %) types/max-dimension)
                                  (<= (:height %) types/max-dimension))
                       :error-type :dimensions}
                      {:check #(and (:width %) (:height %)
                                  (<= (* (:width %) (:height %)) types/max-area))
                       :error-type :area}
                      {:check #(and (:size %)
                                  (<= (:size %) types/max-file-size))
                       :error-type :size}]]
      (if-let [failed-constraint (first (filter #(not ((:check %) image)) constraints))]
        (failure (errors/validation-error :cover-image 
                                        (errors/get-image-error-message (:error-type failed-constraint))))
        (success (types/create-image-metadata image))))))

;; 이미지 메타데이터 추출 함수 수정
(defn extract-image-metadata [image-data]
  (when image-data
    (try
      (let [temp-file (:tempfile image-data)]
        (if (and temp-file (.exists temp-file))
          (let [input-stream (ImageIO/createImageInputStream temp-file)
                readers (ImageIO/getImageReaders input-stream)]
            (if (.hasNext readers)
              (let [reader (.next readers)]
                (.setInput reader input-stream)
                (let [buffered-image (.read reader 0)]
                  {:content-type (:content-type image-data)
                   :size (:size image-data)
                   :width (.getWidth buffered-image)
                   :height (.getHeight buffered-image)}))
              nil))
          nil))
      (catch Exception _ nil))))

;;만화 생성 워크 플로우
;; 만화 생성 워크플로우에서 이미지 처리 부분 수정
(defn create-comic-workflow [{:keys [title artist author isbn13 isbn10 
                                   publication-date publisher price 
                                   page-count description cover-image
                                   cover-image-metadata] :as comic-data}]
  (println "Starting comic workflow with data:" 
           (dissoc comic-data :cover-image))
  
  (let [required-fields {:title title
                        :artist artist
                        :author author
                        :isbn13 isbn13
                        :isbn10 isbn10}
        
        optional-fields (cond-> {}
                         publication-date (assoc :publication-date publication-date)
                         publisher (assoc :publisher publisher)
                         price (assoc :price price)
                         page-count (assoc :page-count page-count)
                         description (assoc :description description)
                         (and cover-image cover-image-metadata)
                         (assoc :cover-image cover-image
                               :cover-image-metadata cover-image-metadata))]
    
    ;; 필수 필드 검증
    (cond
      ;; 1. 필수 필드 존재 여부 검증
      (not (every? some? (vals required-fields)))
      (failure (errors/validation-error :required-fields "필수 필드가 누락되었습니다."))
      
      ;; 2. ISBN13 검증
      (not (s/valid? ::types/isbn13 isbn13))
      (failure (errors/validation-error :isbn13 (errors/get-validation-message :isbn13)))
      
      ;; 3. ISBN10 검증
      (not (s/valid? ::types/isbn10 isbn10))
      (failure (errors/validation-error :isbn10 (errors/get-validation-message :isbn10)))
      
      ;; 4. 이미지가 있는 경우, 이미 메타데이터가 있으므로 추가 검증 불필요
      :else
      (success (types/create-comic required-fields optional-fields)))))

;; 만화 수정 워크플로우 (향후 구현)
(defn update-comic-workflow [id comic-data]
  ;; TODO: 구현 예정
  )

;; 만화 삭제 워크플로우 (향후 구현)
(defn delete-comic-workflow [id]
  ;; TODO: 구현 예정
  )
