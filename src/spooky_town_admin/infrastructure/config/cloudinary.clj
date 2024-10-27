(ns spooky-town-admin.infrastructure.config.cloudinary
  (:require [environ.core :refer [env]])
  (:import [com.cloudinary Cloudinary]
           [com.cloudinary.utils ObjectUtils]))

(defn create-cloudinary-config []
  {:cloud_name (env :cloudinary-cloud-name)
   :api_key (env :cloudinary-api-key)
   :api_secret (env :cloudinary-api-secret)
   :secure true})

(defn create-cloudinary []
  (Cloudinary. (ObjectUtils/asMap (create-cloudinary-config))))

(def upload-options
  (ObjectUtils/asMap
    {"resource_type" "auto"
     "unique_filename" true
     "overwrite" false}))