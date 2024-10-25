(ns spooky-town-admin.domain.comic.workflow
  (:require [spooky-town-admin.domain.common.result :as r]
            [spooky-town-admin.domain.comic.types :as types]
            [clojure.spec.alpha :as s]
            [spooky-town-admin.domain.comic.errors :as errors])
  
  (:import [javax.imageio ImageIO]
           [java.io File]
           [java.io FileInputStream]))

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
(defn validate-required-fields [{:keys [title artist author isbn13 isbn10] :as comic-data}]
  (if (every? some? [title artist author isbn13 isbn10])
    (r/success comic-data)
    (r/failure (errors/validation-error :required-fields "필수 필드가 누락되었습니다."))))

(defn validate-isbn [{:keys [isbn13 isbn10] :as comic-data}]
  (cond
    (not (s/valid? ::types/isbn13 isbn13))
    (r/failure (errors/validation-error :isbn13 (errors/get-validation-message :isbn13)))
    
    (not (s/valid? ::types/isbn10 isbn10))
    (r/failure (errors/validation-error :isbn10 (errors/get-validation-message :isbn10)))
    
    :else
    (r/success comic-data)))

(defn validate-image-constraints [image]
  (if (or (nil? image)
          (nil? (:width image))
          (nil? (:height image)))
    (r/failure (errors/validation-error :cover-image 
                                    (errors/get-image-error-message :invalid)))
    (let [constraints [{:check #(contains? types/allowed-image-types (:content-type %))
                       :error-type :type}
                      {:check #(>= types/max-dimension (max (:width %) (:height %)))
                       :error-type :dimensions}
                      {:check #(>= types/max-area (* (:width %) (:height %)))
                       :error-type :area}
                      {:check #(>= types/max-file-size (:size %))
                       :error-type :size}]]
      (if-let [failed-constraint (first (filter #(not ((:check %) image)) constraints))]
        (r/failure (errors/validation-error :cover-image 
                                        (errors/get-image-error-message (:error-type failed-constraint))))
        (r/success (types/create-image-metadata image))))))

(defn process-image [{:keys [cover-image] :as comic-data}]
  (if cover-image
    (if-let [metadata (extract-image-metadata cover-image)]
      (-> metadata
          validate-image-constraints
          (r/bind #(r/success (assoc comic-data 
                                    :cover-image-metadata %))))
      (r/failure (errors/validation-error :cover-image 
                                        (errors/get-image-error-message :invalid))))
    (r/success comic-data)))

(defn create-comic-workflow [comic-data]
  (-> (r/success comic-data)
      (r/bind validate-required-fields)
      (r/bind validate-isbn)
      (r/bind process-image)
      (r/bind #(r/success (types/create-comic 
                           (select-keys % [:title :artist :author :isbn13 :isbn10])
                           (select-keys % [:publication-date :publisher :price 
                                         :page-count :description :cover-image 
                                         :cover-image-metadata]))))))

;; 만화 수정 워크플로우 (향후 구현)
(defn update-comic-workflow [id comic-data]
  ;; TODO: 구현 예정
  )

;; 만화 삭제 워크플로우 (향후 구현)
(defn delete-comic-workflow [id]
  ;; TODO: 구현 예정
  )
