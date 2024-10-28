(ns spooky-town-admin.domain.comic.workflow
  (:require [spooky-town-admin.domain.common.result :as r]
            [spooky-town-admin.domain.comic.types :as types]
            [spooky-town-admin.domain.comic.errors :as errors]
            [spooky-town-admin.infrastructure.image-storage :as image-storage])
  (:import [javax.imageio ImageIO]))

(defn extract-image-metadata [image-data]
  (println "Starting metadata extraction for image:" 
           (select-keys image-data [:filename :content-type]))
  (when image-data
    (try
      (let [temp-file (:tempfile image-data)]
        (println "Checking temp file:" 
                 {:exists? (and temp-file (.exists temp-file))
                  :path (.getAbsolutePath temp-file)
                  :size (.length temp-file)})
        (if (and temp-file 
                 (.exists temp-file)
                 (pos? (.length temp-file)))  ;; 파일 크기 체크 추가
          (let [input-stream (ImageIO/createImageInputStream temp-file)]
            (println "Created ImageInputStream")
            (let [readers (ImageIO/getImageReaders input-stream)]
              (println "Got image readers, has next?" (.hasNext readers))
              (if (.hasNext readers)
                (let [reader (.next readers)]
                  (println "Using reader:" (.getClass reader))
                  (.setInput reader input-stream)
                  (let [buffered-image (.read reader 0)]
                    (println "Successfully read image")
                    (let [metadata {:content-type (:content-type image-data)
                                  :size (.length temp-file)
                                  :width (.getWidth buffered-image)
                                  :height (.getHeight buffered-image)}]
                      (println "Extracted metadata:" metadata)
                      metadata)))
                (do
                  (println "No suitable image reader found")
                  nil))))
          (do
            (println "File validation failed:" 
                     {:exists? (.exists temp-file)
                      :size (.length temp-file)})
            nil)))
      (catch Exception e
        (println "Error during metadata extraction:" 
                 {:message (.getMessage e)
                  :type (.getName (.getClass e))})
        (.printStackTrace e)
        nil))))

(defn process-and-store-image [image-storage image-data]
  (println "Starting image processing with data:" 
           (select-keys image-data [:filename :content-type :size]))
  (if image-data
    (let [metadata (extract-image-metadata image-data)]
      (println "Extracted image metadata:" metadata)
      (if metadata
        (-> (types/validate-image-metadata metadata)
            (r/bind (fn [validated-metadata]
                     (println "Image validation successful:" validated-metadata)
                     (-> (image-storage/store-image image-storage image-data)
                         (r/map (fn [result]
                                (println "Raw storage result:" (pr-str result))
                                (let [response {:cover-image-metadata validated-metadata
                                              :cover-image-url (:url result)}]
                                  (println "Processed storage result:" (pr-str response))
                                  response)))))))
        (do
          (println "Failed to extract image metadata")
          (r/failure (errors/validation-error :cover-image 
                                            (errors/get-image-error-message :invalid))))))
    (do
      (println "No image data provided")
      (r/success nil))))

(^:export defn create-comic-workflow [image-storage comic-data]
  (println "Starting comic workflow with data:" comic-data)
  (-> (r/success comic-data)
      (r/bind #(types/create-unvalidated-comic %))
      ;; 만화 정보 검증
      (r/bind (fn [unvalidated]
                (println "Validating comic data:" unvalidated)
                (-> (types/create-validated-comic 
                     (dissoc unvalidated :cover-image))
                    (r/map (fn [validated]
                            {:comic validated
                             :events [(types/create-comic-validated validated)]})))))
      ;; 이미지 처리
      (r/bind (fn [{:keys [comic events]}]
                (println "Processing image for comic:" comic)
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
