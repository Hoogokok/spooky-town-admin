(ns spooky-town-admin.web.handler
  (:require
   [spooky-town-admin.application.comic-service :as comic-service]
   [spooky-town-admin.core.result :as r]
   [clojure.tools.logging :as log]))

(defn- handle-result [result]
  (if (r/success? result)
    {:status 200
     :body (:value result)}
    (let [error (:error result)]
      {:status (case (:type error)
                 :not-found 404
                 :duplicate-isbn 409
                 :validation 400
                 500)
       :body {:error (:type error)
              :message (:message error)
              :field (:field error)
              :details (:details error)}})))

(defn list-comics [service]
  (try
    (let [result (comic-service/list-comics service)]
      (handle-result result))
    (catch Exception e
      {:status 500
       :body {:error :unknown
              :message "알 수 없는 오류가 발생했습니다."}})))

(defn get-comic-by-id [service id]
  (try
    (let [comic-id (Integer/parseInt id)
          result (comic-service/get-comic service comic-id)]
      (handle-result result))
    (catch NumberFormatException _
      {:status 400
       :body {:error :invalid-id
              :message "잘못된 ID 형식입니다."}})
    (catch Exception e
      {:status 500
       :body {:error :unknown
              :message "알 수 없는 오류가 발생했습니다."}})))

(defn create-comic [service body-params]
  (try
    (log/debug "Received create comic request with params:" body-params)
    (if (nil? body-params)
      (do
        (log/warn "Empty request body received")
        {:status 400
         :body {:error :validation
                :message "요청 본문이 비어있습니다."}})
      (let [result (comic-service/create-comic service body-params)]
        (if (r/success? result)
          {:status 201
           :body (:value result)}
          (handle-result result))))
    (catch Exception e
      (log/error e "Failed to create comic")
      {:status 500
       :body {:error :unknown
              :message "알 수 없는 오류가 발생했습니다."
              :details (.getMessage e)}})))
