(ns spooky-town-admin.domain.comic.workflow
  (:require [spooky-town-admin.infrastructure.monad.result :as r]
            [spooky-town-admin.domain.comic.types :as types]
            [spooky-town-admin.domain.comic.errors :as errors]
            [spooky-town-admin.infrastructure.image-storage :as image-storage])
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

(defn process-and-store-image [image-storage image-data]
  (if image-data
    (if-let [metadata (extract-image-metadata image-data)]
      (-> (validate-image-constraints metadata)
          (r/bind (fn [validated-metadata]
                   (-> (image-storage/store-image image-storage image-data)
                       (r/map (fn [result]
                               {:cover-image-metadata validated-metadata
                                :cover-image-url (:url result)}))))))
      (r/failure (errors/validation-error :cover-image 
                                        (errors/get-image-error-message :invalid))))
    (r/success nil)))

(defn create-comic-workflow [image-storage comic-data]
  (-> (r/success comic-data)
      (r/bind #(types/create-unvalidated-comic %))
      ;; 만화 정보 검증
      (r/bind (fn [unvalidated]
                (-> (types/create-validated-comic 
                      (dissoc unvalidated :cover-image))
                    (r/map (fn [validated]
                            {:comic validated
                             :events [(types/create-comic-validated validated)]})))))
      ;; 이미지 처리
      (r/bind (fn [{:keys [comic events]}]
                (-> (process-and-store-image image-storage (:cover-image comic-data))
                    (r/map (fn [image-result]
                            (if image-result
                              (let [comic-with-image (assoc comic 
                                                          :cover-image-metadata 
                                                          (:cover-image-metadata image-result))]
                                {:comic comic-with-image
                                 :image-url (:cover-image-url image-result)
                                 :events (concat events
                                               [(types/create-image-uploaded 
                                                  (:cover-image-metadata image-result))
                                                (types/create-image-stored 
                                                  comic-with-image 
                                                  (:cover-image-url image-result))])})
                              {:comic comic
                               :events events}))))))))

;; 만화 수정 워크플로우 (향후 구현)
(defn update-comic-workflow [id comic-data]
  ;; TODO: 구현 예정
  )

;; 만화 삭제 워크플로우 (향후 구현)
(defn delete-comic-workflow [id]
  ;; TODO: 구현 예정
  )
