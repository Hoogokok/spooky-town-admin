(ns spooky-town-admin.domain.comic.workflow
  (:require [spooky-town-admin.core.result :as r]
            [clojure.tools.logging :as log]
            [spooky-town-admin.domain.comic.types :as types]
            [spooky-town-admin.domain.comic.errors :as errors]
            [spooky-town-admin.infrastructure.image-storage :as image-storage]))

(defn process-and-store-image [image-storage image-data]
  (log/debug "Starting image processing with data:" 
             (select-keys image-data [:filename :content-type :size]))
  (if image-data
    (-> (types/validate-image-data image-data)
        (r/bind (fn [validated-image]
                  (-> (image-storage/store-image image-storage validated-image)
                      (r/map (fn [result]
                              {:cover-image-metadata (:metadata validated-image)
                               :cover-image-url (:url result)}))))))
    (r/success nil)))

(^:export defn create-comic-workflow [image-storage comic-data]
  (log/debug "Starting comic creation workflow")
  (-> (types/create-unvalidated-comic comic-data)
      (r/bind (fn [unvalidated]
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
