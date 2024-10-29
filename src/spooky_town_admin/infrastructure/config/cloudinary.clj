(ns spooky-town-admin.infrastructure.config.cloudinary
  (:require [environ.core :refer [env]]
            [spooky-town-admin.infrastructure.monad.result :as r]
            [spooky-town-admin.domain.comic.errors :as errors])
  (:import [com.cloudinary Cloudinary]
           [com.cloudinary.utils ObjectUtils]))

(defn validate-config []
  (if (and (env :cloudinary-cloud-name)
           (env :cloudinary-api-key)
           (env :cloudinary-api-secret))
    (r/success {:cloud-name (env :cloudinary-cloud-name)
                :api-key (env :cloudinary-api-key)
                :api-secret (env :cloudinary-api-secret)})
    (r/failure (errors/system-error
                :config-error
                "Required Cloudinary configuration is missing"
                "One or more required environment variables are not set"))))

(defn create-cloudinary-config []
  (-> (validate-config)
      (r/map (fn [{:keys [cloud-name api-key api-secret]}]
               (ObjectUtils/asMap
                 (to-array
                   ["cloud_name" cloud-name
                    "api_key" api-key
                    "api_secret" api-secret
                    "secure" true]))))))

(defn create-cloudinary []
  (-> (create-cloudinary-config)
      (r/map #(Cloudinary. %))
      (r/map-error (fn [error]
                     (errors/system-error
                       :cloudinary-init-error
                       "Failed to initialize Cloudinary"
                       (:message error))))
      ;; Result를 풀어서 성공이면 Cloudinary 인스턴스를, 실패면 예외를 던짐
      (r/bind (fn [cloudinary]
                (if (:success cloudinary)
                  (:value cloudinary)
                  (throw (ex-info "Failed to initialize Cloudinary"
                                {:error (:error cloudinary)})))))))

(def upload-options
  (ObjectUtils/asMap
    (to-array
      ["resource_type" "auto"
       "unique_filename" true
       "overwrite" false])))