(ns spooky-town-admin.application.comic-service
  (:require [spooky-town-admin.domain.comic.workflow :as workflow]
            [spooky-town-admin.domain.comic.errors :as errors]
            [spooky-town-admin.infrastructure.persistence :as persistence]
            [spooky-town-admin.infrastructure.image-storage :as image-storage]))

(defrecord ComicService [comic-repository image-storage])

;; 이미지 처리 관련 함수들
(defn- extract-and-validate-metadata [image-data]
  (let [{:keys [success metadata]} (image-storage/extract-image-metadata image-data)]
    (if success
      (workflow/validate-image-constraints metadata)
      (workflow/failure (errors/validation-error :cover-image 
                                               (errors/get-image-error-message :invalid))))))

(defn- store-image [image-storage image-data metadata]
  (let [{:keys [success image-id]} (image-storage/store-image image-storage image-data)]
    (if success
      (workflow/success {:image-id image-id :metadata metadata})
      (workflow/failure (errors/validation-error :cover-image 
                                               (errors/get-image-error-message :invalid))))))

(defn- process-cover-image [image-storage image-data]
  (when image-data
    (let [metadata-result (extract-and-validate-metadata image-data)]
      (if (workflow/success? metadata-result)
        (store-image image-storage image-data (:value metadata-result))
        metadata-result))))

;; 만화 생성 관련 함수들
(defn- check-duplicate-isbn [comic-repository comic-data]
  (if-let [existing-comic (persistence/find-comic-by-isbn 
                          comic-repository 
                          (or (:isbn13 comic-data) (:isbn10 comic-data)))]
    (workflow/failure (errors/business-error :duplicate-isbn 
                                           (errors/get-business-message :duplicate-isbn)))
    (workflow/success comic-data)))

(defn- create-comic-with-image [comic-repository image-storage comic-data image]
  (let [image-result (process-cover-image image-storage image)]
    (if (workflow/success? image-result)
      (let [comic-with-image (assoc comic-data 
                                   :cover-image (:image-id (:value image-result))
                                   :cover-image-metadata (:metadata (:value image-result)))
            workflow-result (workflow/create-comic-workflow comic-with-image)]
        (if (workflow/success? workflow-result)
          (persistence/save-comic comic-repository (:value workflow-result))
          workflow-result))
      image-result)))

(defn- create-comic-without-image [comic-repository comic-data]
  (let [workflow-result (workflow/create-comic-workflow comic-data)]
    (if (workflow/success? workflow-result)
      (persistence/save-comic comic-repository (:value workflow-result))
      workflow-result)))

;; 만화 생성 서비스
(defn create-comic [{:keys [comic-repository image-storage]} comic-data]
  (persistence/with-transaction
    (let [duplicate-check (check-duplicate-isbn comic-repository comic-data)]
      (if (workflow/success? duplicate-check)
        (if-let [image (:cover-image comic-data)]
          (create-comic-with-image comic-repository image-storage comic-data image)
          (create-comic-without-image comic-repository comic-data))
        duplicate-check))))

;; 만화 조회 서비스
(defn get-comic [{:keys [comic-repository]} id]
  (if-let [comic (persistence/find-comic-by-id comic-repository id)]
    {:success true :comic comic}
    {:success false
     :error (errors/business-error :not-found "만화를 찾을 수 없습니다.")}))

;; 만화 목록 조회 서비스
(defn list-comics [{:keys [comic-repository]}]
  {:success true
   :comics (persistence/list-comics comic-repository)})

;; 서비스 인스턴스 생성
(defn create-comic-service [env config]
  (->ComicService 
   (persistence/create-comic-repository)
   (image-storage/create-image-storage env config)))