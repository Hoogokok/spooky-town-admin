(ns spooky-town-admin.image
  (:require [clojure.string :as str]))

(defn upload-to-cdn [image-data]
  ;; 실제 Cloudflare 업로드 로직 대신 가짜 응답 반환
  (try
    (let [mock-image-id (str (java.util.UUID/randomUUID))]
      {:success true
       :image-id mock-image-id})
    (catch Exception e
      {:success false
       :error "CDN 업로드 실패"
       :details (.getMessage e)})))
