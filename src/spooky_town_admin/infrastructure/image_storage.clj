(ns spooky-town-admin.infrastructure.image-storage
  (:require [spooky-town-admin.domain.comic.errors :as errors]
            [clojure.java.io :as io])
  (:import [java.io File]
           [java.nio.file Files Path]
           [javax.imageio ImageIO]
           [java.awt.image BufferedImage]
           [java.util UUID]))

;; 이미지 저장소 프로토콜
(defprotocol ImageStorage
  (store-image [this image-data])
  (delete-image [this image-id])
  (get-image-url [this image-id]))

;; 이미지 메타데이터 추출
(defn extract-image-metadata [image-data]
  (try
    (let [^File temp-file (:tempfile image-data)
          ^BufferedImage image (ImageIO/read temp-file)
          ^Path path (.toPath temp-file)
          content-type (Files/probeContentType path)]
      {:success true
       :metadata {:width (.getWidth image)
                 :height (.getHeight image)
                 :content-type content-type
                 :size (.length temp-file)}})
    (catch Exception e
      {:success false
       :error (errors/system-error 
               :image-processing-error
               (errors/get-system-message :image-processing-error)
               (.getMessage e))})))


;; 테스트용 Mock CDN 저장소 구현
(defrecord MockCDNImageStorage []
  ImageStorage
  (store-image [_ image-data]
    (try
      (let [metadata-result (extract-image-metadata image-data)]
        (if (:success metadata-result)
          {:success true
           :image-id (str (UUID/randomUUID))
           :metadata (:metadata metadata-result)}
          metadata-result))
      (catch Exception e
        {:success false
         :error (errors/system-error 
                :image-upload-error
                (errors/get-system-message :image-upload-error)
                (.getMessage e))})))
  
  (delete-image [_ image-id]
    {:success true})
  
  (get-image-url [_ image-id]
    (str "https://mock-cdn.example.com/images/" image-id)))

;; 실제 Cloudflare R2 저장소 구현 (향후 구현)
(defrecord CloudflareR2ImageStorage [config]
  ImageStorage
  (store-image [_ image-data]
    ;; TODO: Cloudflare R2 구현
    )
  
  (delete-image [_ image-id]
    ;; TODO: Cloudflare R2 구현
    )
  
  (get-image-url [_ image-id]
    ;; TODO: Cloudflare R2 구현
    ))

;; 저장소 팩토리 함수들
(defn create-mock-image-storage []
  (->MockCDNImageStorage))

(defn create-r2-image-storage [config]
  (->CloudflareR2ImageStorage config))

;; 환경에 따른 저장소 생성
(defn create-image-storage [env config]
  (case env
    :test (create-mock-image-storage)
    :prod (create-r2-image-storage config)
    ;; 기본값은 mock 저장소
    (create-mock-image-storage)))
