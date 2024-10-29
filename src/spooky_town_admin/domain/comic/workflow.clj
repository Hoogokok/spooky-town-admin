(ns spooky-town-admin.domain.comic.workflow
  (:require [spooky-town-admin.core.result :as r]
            [clojure.tools.logging :as log]
            [spooky-town-admin.domain.comic.types :as types]
            [spooky-town-admin.domain.comic.errors :as errors]
            [spooky-town-admin.infrastructure.image-storage :as image-storage])
  (:import [javax.imageio ImageIO]))

(defn extract-image-metadata [image-data]
  (log/debug "Extracting metadata from image:" 
             (select-keys image-data [:filename :content-type]))
  (when image-data
    (try
      (let [temp-file (:tempfile image-data)]
        (when (and temp-file (.exists temp-file) (pos? (.length temp-file)))
          (let [input-stream (ImageIO/createImageInputStream temp-file)
                readers (ImageIO/getImageReaders input-stream)]
            (when (.hasNext readers)
              (let [reader (.next readers)]
                (.setInput reader input-stream)
                (let [buffered-image (.read reader 0)
                      metadata {:content-type (:content-type image-data)
                              :size (.length temp-file)
                              :width (.getWidth buffered-image)
                              :height (.getHeight buffered-image)}]
                  (log/debug "Successfully extracted image metadata:" metadata)
                  metadata))))))
      (catch Exception e
        (log/error e "Failed to extract image metadata")
        nil))))

(defn process-and-store-image [image-storage image-data]
  (log/debug "Starting image processing with data:" 
           (select-keys image-data [:filename :content-type :size]))
  (if image-data
    (let [metadata (extract-image-metadata image-data)]
      (if metadata
        (-> (types/validate-image-metadata metadata)
            (r/bind (fn [validated-metadata]
                     (-> (image-storage/store-image image-storage image-data)
                         (r/map (fn [result]
                                (let [response {:cover-image-metadata validated-metadata
                                              :cover-image-url (:url result)}]
                                  response)))))))
        (do
          (log/error "Failed to extract image metadata")
          (r/failure (errors/validation-error :cover-image 
                                            (errors/get-image-error-message :invalid))))))
    (do
      (log/error "No image data provided")
      (r/success nil))))

(^:export defn create-comic-workflow [image-storage comic-data]
  (log/debug "Starting comic creation workflow")
  (-> (r/success comic-data)
      (r/bind #(types/create-unvalidated-comic %))
      (r/bind (fn [unvalidated]
                (log/debug "Validating comic data")
                (-> (types/create-validated-comic (dissoc unvalidated :cover-image))
                    (r/map (fn [validated]
                            {:comic validated
                             :events [(types/create-comic-validated validated)]})))))
      (r/bind (fn [{:keys [comic events]}]
                (log/debug "Processing image for comic:" comic)
                (-> (process-and-store-image image-storage (:cover-image comic-data))
                    (r/map (fn [image-result]
                            (if image-result
                              (let [comic-with-image (-> comic
                                                       (assoc :cover-image-metadata 
                                                             (:cover-image-metadata image-result))
                                                       (assoc :cover-image-url 
                                                             (:cover-image-url image-result)))]
                                {:comic comic-with-image
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
