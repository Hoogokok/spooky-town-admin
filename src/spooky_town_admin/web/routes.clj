(ns spooky-town-admin.web.routes
  (:require [compojure.core :refer [defroutes POST GET]]
            [compojure.route :as route]
            [ring.util.response :refer [response bad-request not-found]]
            [spooky-town-admin.application.comic-service :as comic-service]
            [spooky-town-admin.domain.comic.errors :as errors]))

;; 에러 응답 생성 헬퍼
(defn- error-response [error]
  (let [status (cond
                 (instance? errors/ValidationError error) 400
                 (instance? errors/BusinessError error) 400
                 (instance? errors/SystemError error) 500
                 :else 500)]
    {:status status
     :body {:error (type error)
            :message (:message error)
            :details (when (instance? errors/SystemError error)
                      (:details error))}}))

;; 요청 처리 핸들러
(defn handle-create-comic [service body]
  (let [result (comic-service/create-comic service body)]
    (if (:success result)
      (response {:id (:id result)})
      (bad-request (error-response (:error result))))))

(defn handle-get-comic [service id]
  (let [result (comic-service/get-comic service id)]
    (if (:success result)
      (response (:comic result))
      (not-found (error-response (:error result))))))

(defn handle-list-comics [service]
  (let [result (comic-service/list-comics service)]
    (response (:comics result))))

;; 라우트 정의
(defn create-routes [service]
  (defroutes app-routes
    ;; 만화 생성
    (POST "/api/comics" {body :body}
      (handle-create-comic service body))
    
    ;; 만화 조회
    (GET "/api/comics/:id" [id]
      (handle-get-comic service (Integer/parseInt id)))
    
    ;; 만화 목록
    (GET "/api/comics" []
      (handle-list-comics service))
    
    ;; 404 처리
    (route/not-found 
     {:error "Not Found"
      :message "요청한 리소스를 찾을 수 없습니다."})))

;; 미들웨어 적용
(defn wrap-error-handling [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        {:status 500
         :body {:error "InternalServerError"
                :message "서버 내부 오류가 발생했습니다."}}))))

;; 앱 생성
(defn create-app [service]
  (-> (create-routes service)
      wrap-error-handling))
