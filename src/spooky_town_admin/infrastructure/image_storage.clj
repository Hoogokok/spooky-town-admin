(ns spooky-town-admin.infrastructure.image-storage
  (:require [spooky-town-admin.domain.comic.errors :as errors]
            [spooky-town-admin.domain.common.result :as r]
            [spooky-town-admin.infrastructure.config.cloudinary :as cloud-config]
            [clojure.java.io :as io])
  (:import [java.io File]
           [java.nio.file Files Path]
           [javax.imageio ImageIO]
           [java.awt.image BufferedImage]
           [java.util UUID]
           [com.cloudinary.utils ObjectUtils]
           [com.cloudinary Cloudinary]))

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
      (r/success {:width (.getWidth image)
                 :height (.getHeight image)
                 :content-type content-type
                 :size (.length temp-file)}))
    (catch Exception e
      (r/failure (errors/system-error
                  :image-processing-error
                  (errors/get-system-message :image-processing-error)
                  (.getMessage e))))))


;; 테스트용 Mock CDN 저장소 구현
(defrecord MockCDNImageStorage []
  ImageStorage
  (store-image [_ image-data]
    (-> (extract-image-metadata image-data)
        (r/map (fn [metadata]
                 {:image-id (str (UUID/randomUUID))
                  :metadata metadata}))))

  (delete-image [_ image-id]
    (r/success true))

  (get-image-url [_ image-id]
    (r/success (str "https://mock-cdn.example.com/images/" image-id))))

;; Cloudinary 저장소 구현소 
(defrecord CloudinaryImageStorage [cloudinary]
  ImageStorage
  (store-image [_ image-data]
    (try
      (let [temp-file (:tempfile image-data)
            options (ObjectUtils/asMap (to-array
                     ["resource_type" "auto"
                      "unique_filename" true
                      "overwrite" false]))
            uploader (.uploader cloudinary)
            upload-result (.upload uploader temp-file options)
            public-id (.get upload-result "public_id")]
        (r/success
          {:image-id public-id
           :metadata {:width (.get upload-result "width")
                     :height (.get upload-result "height")
                     :content-type (str "image/" (.get upload-result "format"))
                     :size (.get upload-result "bytes")
                     :url (.get upload-result "secure_url")}}))
      (catch Exception e
        (r/failure (errors/system-error
                    :image-upload-error
                    (errors/get-system-message :image-upload-error)
                    (.getMessage e))))))

  (delete-image [_ image-id]
    (try
      (let [options (ObjectUtils/asMap (to-array []))
            api (.api cloudinary)]
        (.deleteResources api (into-array String [image-id]) options)
        (r/success true))
      (catch Exception e
        (r/failure (errors/system-error
                    :image-delete-error
                    (errors/get-system-message :image-delete-error)
                    (.getMessage e))))))

  (get-image-url [_ image-id]
    (try
      (let [url (.url cloudinary)]
        (r/success (.generate url image-id)))
      (catch Exception e
        (r/failure (errors/system-error
                    :image-url-error
                    (errors/get-system-message :image-url-error)
                    (.getMessage e)))))))

;; 저장소 팩토리 함수들
(defn create-mock-image-storage []
  (->MockCDNImageStorage))

(defn create-cloudinary-storage []
  (->CloudinaryImageStorage (cloud-config/create-cloudinary)))

;; 환경에 따른 저장소 생성
(defn create-image-storage [env config]
  (case env
    :test (create-mock-image-storage)
    :prod (create-cloudinary-storage)
    ;; 기본값은 mock 저장소
    (create-mock-image-storage)))
