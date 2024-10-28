(ns spooky-town-admin.infrastructure.config.cloudinary
  (:require [environ.core :refer [env]]
            [spooky-town-admin.domain.common.result :as r]
            [spooky-town-admin.domain.comic.errors :as errors])
  (:import [com.cloudinary Cloudinary]
           [com.cloudinary.utils ObjectUtils]))

(defn validate-config []
  (let [;; System/getenv를 사용하여 직접 환경 변수 읽기
        cloud-name (env :CLOUDINARY_CLOUD_NAME)
        api-key (env :CLOUDINARY_API_KEY)
        api-secret (env :CLOUDINARY_API_SECRET)]
    
    (println "Cloudinary config values:"
             {:cloud-name (when cloud-name "***")  ;; 실제 값 대신 *** 출력
              :api-key (when api-key "***")
              :api-secret (when api-secret "***")})
    
    (if (and cloud-name api-key api-secret)
      (r/success {:cloud-name cloud-name
                  :api-key api-key
                  :api-secret api-secret})
      (do
        (println "Missing Cloudinary configuration. Available env vars:"
                 {:CLOUDINARY_CLOUD_NAME (boolean cloud-name)
                  :CLOUDINARY_API_KEY (boolean api-key)
                  :CLOUDINARY_API_SECRET (boolean api-secret)})
        (r/failure (errors/system-error
                    :config-error
                    "Required Cloudinary configuration is missing"
                    "One or more required environment variables are not set"))))))

(defn create-cloudinary-config []
  (-> (validate-config)
      (r/map (fn [{:keys [cloud-name api-key api-secret]}]
               (println "Creating Cloudinary config with:"
                        {:cloud-name cloud-name
                         :api-key (when api-key "***")
                         :api-secret (when api-secret "***")})
               (ObjectUtils/asMap
                 (to-array
                   ["cloud_name" cloud-name
                    "api_key" api-key
                    "api_secret" api-secret
                    "secure" true]))))))

(defn create-cloudinary []
  (let [config-result (create-cloudinary-config)]
    (println "Config result:" (r/success? config-result))
    (if (r/success? config-result)
      (let [config (r/value config-result)
            cloudinary (Cloudinary. config)]
        (println "Created Cloudinary instance successfully")
        cloudinary)
      (do
        (println "Failed to create Cloudinary config:" (r/error config-result))
        nil))))

(def upload-options
  (ObjectUtils/asMap
    (to-array
      ["resource_type" "auto"
       "unique_filename" true
       "overwrite" false])))
