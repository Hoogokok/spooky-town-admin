(ns spooky-town-admin.domain.comic.workflow
  (:require [spooky-town-admin.domain.common.result :as r]
            [spooky-town-admin.domain.comic.types :as types]
            [spooky-town-admin.domain.comic.errors :as errors])
  (:import [javax.imageio ImageIO]))

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
        (r/success image)))))

(defn process-image [{:keys [cover-image] :as comic-data}]
  (if cover-image
    (if-let [metadata (extract-image-metadata cover-image)]
      (-> metadata
          validate-image-constraints
          (r/bind #(r/success (assoc comic-data :cover-image-metadata %))))
      (r/failure (errors/validation-error :cover-image 
                                      (errors/get-image-error-message :invalid))))
    (r/success comic-data)))

(defn create-comic-workflow [comic-data]
  (-> (r/success comic-data)
      (r/bind #(types/create-unvalidated-comic %))
      (r/bind process-image)
      (r/bind #(types/create-validated-comic %))
      (r/map #(do (types/create-comic-validated %) %))
      (r/map (fn [validated-comic]
               (when-let [metadata (:cover-image-metadata validated-comic)]
                 (types/create-image-uploaded metadata))
               validated-comic))))

;; 만화 수정 워크플로우 (향후 구현)
(defn update-comic-workflow [id comic-data]
  ;; TODO: 구현 예정
  )

;; 만화 삭제 워크플로우 (향후 구현)
(defn delete-comic-workflow [id]
  ;; TODO: 구현 예정
  )
