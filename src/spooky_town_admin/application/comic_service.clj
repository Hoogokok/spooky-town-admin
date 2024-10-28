(ns spooky-town-admin.application.comic-service
  (:require [spooky-town-admin.domain.comic.workflow :as workflow]
            [spooky-town-admin.domain.comic.errors :as errors]
            [spooky-town-admin.infrastructure.persistence :as persistence]
            [spooky-town-admin.infrastructure.image-storage :as image-storage]
            [spooky-town-admin.domain.common.result :as r]))

(defrecord ComicService [comic-repository image-storage])

;; 이미지 처리 관련 함수들
(defn- extract-and-validate-metadata [image-data]
  (if-let [metadata (workflow/extract-image-metadata image-data)]
    (workflow/validate-image-constraints metadata)
    (r/failure (errors/validation-error :cover-image 
                                      (errors/get-image-error-message :invalid)))))

(defn- store-image [image-storage image-data metadata]
  (-> (image-storage/store-image image-storage image-data)
      (r/map-error #(errors/validation-error :cover-image 
                                           (errors/get-image-error-message :invalid)))
      (r/bind #(r/success {:image-id (:image-id %) :metadata metadata}))))

(defn- process-cover-image [image-storage image-data]
  (if image-data
    (-> (extract-and-validate-metadata image-data)
        (r/bind #(store-image image-storage image-data %)))
    (r/success nil)))

;; 만화 생성 관련 함수들
(defn- check-duplicate-isbn [comic-repository comic-data]
  (if-let [existing-comic (persistence/find-comic-by-isbn 
                          comic-repository 
                          (or (:isbn13 comic-data) (:isbn10 comic-data)))]
    (r/failure (errors/business-error :duplicate-isbn 
                                    (errors/get-business-message :duplicate-isbn)))
    (r/success comic-data)))

(defn- save-comic [comic-repository comic]
  (try
    (r/success (persistence/save-comic comic-repository comic))
    (catch Exception e
      (r/failure (errors/system-error :db-error 
                                    (errors/get-system-message :db-error)
                                    (.getMessage e))))))

(defn- create-comic-with-image [comic-repository image-storage comic-data image]
  (-> (process-cover-image image-storage image)
      (r/bind #(r/success (assoc comic-data 
                                :cover-image image
                                :cover-image-metadata (:metadata %))))
      (r/bind workflow/create-comic-workflow)
      (r/bind #(save-comic comic-repository %))))

(defn- create-comic-without-image [comic-repository comic-data]
  (-> (r/success comic-data)
      (r/bind workflow/create-comic-workflow)
      (r/bind #(save-comic comic-repository %))))

;; 만화 생성 서비스
(defn create-comic [{:keys [comic-repository image-storage]} comic-data]
  (-> (persistence/with-transaction
        (-> (r/success comic-data)
            (r/bind #(check-duplicate-isbn comic-repository %))
            (r/bind #(if-let [image (:cover-image %)]
                      (create-comic-with-image comic-repository image-storage % image)
                      (create-comic-without-image comic-repository %)))
            ;; 성공 시 id만 추출
            (r/map #(select-keys % [:id]))))
      r/to-map))

;; 만화 조회 서비스
(defn get-comic [{:keys [comic-repository]} id]
  (-> (if-let [comic (persistence/find-comic-by-id comic-repository id)]
        (r/success comic)
        (r/failure (errors/business-error :not-found 
                                        (errors/get-business-message :not-found))))
      (r/map #(select-keys % [:id :title :artist :author :isbn13 :isbn10]))
      r/to-map))

;; 만화 목록 조회 서비스
(defn list-comics [{:keys [comic-repository]}]
  {:success true
   :comics (map #(select-keys % [:id :title :artist :author :isbn13 :isbn10])
                (persistence/list-comics comic-repository))})

;; 서비스 인스턴스 생성
(defn create-comic-service [env config]
  (->ComicService 
   (persistence/create-comic-repository env)
   (image-storage/create-image-storage env config)))