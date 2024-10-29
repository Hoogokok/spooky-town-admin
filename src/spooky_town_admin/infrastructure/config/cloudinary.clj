(ns spooky-town-admin.infrastructure.config.cloudinary
  (:require [environ.core :refer [env]]
            [spooky-town-admin.core.result :as r]
            [spooky-town-admin.domain.comic.errors :as errors]
            [clojure.tools.logging :as log])
  (:import [com.cloudinary Cloudinary]
           [com.cloudinary.utils ObjectUtils]))

(defn validate-config []
  (let [cloud-name (env :cloudinary-cloud-name)
        api-key (env :cloudinary-api-key)
        api-secret (env :cloudinary-api-secret)]
    
    (log/debug "Validating Cloudinary configuration"
               {:cloud-name (when cloud-name "***")
                :api-key (when api-key "***")
                :api-secret (when api-secret "***")})
    
    (if (and cloud-name api-key api-secret)
      (do
        (log/info "Cloudinary configuration validated successfully")
        (r/success {:cloud-name cloud-name
                   :api-key api-key
                   :api-secret api-secret}))
      (do
        (log/error "Missing required Cloudinary configuration"
                  {:cloudinary-cloud-name (boolean cloud-name)
                   :cloudinary-api-key (boolean api-key)
                   :cloudinary-api-secret (boolean api-secret)})
        (r/failure (errors/system-error
                    :config-error
                    "Required Cloudinary configuration is missing"
                    "One or more required environment variables are not set"))))))

(defn create-cloudinary-config []
  (-> (validate-config)
      (r/map (fn [{:keys [cloud-name api-key api-secret]}]
               (log/debug "Creating Cloudinary configuration"
                         {:cloud-name cloud-name
                          :api-key "***"
                          :api-secret "***"})
               (ObjectUtils/asMap
                 (to-array
                   ["cloud_name" cloud-name
                    "api_key" api-key
                    "api_secret" api-secret
                    "secure" true]))))))

(defn create-cloudinary []
  (let [config-result (create-cloudinary-config)]
    (log/debug "Cloudinary config creation result:" 
               {:success (r/success? config-result)})
    (if (r/success? config-result)
      (let [config (r/value config-result)
            cloudinary (Cloudinary. config)]
        (log/info "Cloudinary instance created successfully")
        cloudinary)
      (do
        (log/error "Failed to create Cloudinary instance" 
                  {:error (r/error config-result)})
        nil))))

(def upload-options
  (ObjectUtils/asMap
    (to-array
      ["resource_type" "auto"
       "unique_filename" true
       "overwrite" false])))
