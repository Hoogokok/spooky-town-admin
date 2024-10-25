(ns spooky-town-admin.core
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.cors :refer [wrap-cors]]  ;; 추가 
            [spooky-town-admin.web.routes :as routes]
            [spooky-town-admin.application.comic-service :as comic-service])
  (:gen-class))

(def config
  {:image-storage {:bucket "comics"
                   :region "auto"
                   :account-id "your-account-id"}})

(defn create-app [env]
  (let [service (comic-service/create-comic-service env config)]
    (-> (routes/create-app service)
        wrap-keyword-params
        wrap-params
        wrap-multipart-params
        wrap-json-response
        (wrap-json-body {:keywords? true})
        (wrap-cors :access-control-allow-origin [#"http://localhost:8280"]
                  :access-control-allow-methods [:get :put :post :delete]
                  :access-control-allow-headers ["Content-Type" "Authorization" "Accept" "Origin"]
                  :access-control-expose-headers ["Content-Type" "Authorization"]))))

(defn start-server [port]
  (let [env (or (System/getenv "APP_ENV") :dev)
        app (create-app env)]
    (jetty/run-jetty app {:port port :join? false})))

(defn -main [& args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "3000"))]
    (println (str "서버가 http://localhost:" port "에서 실행 중입니다."))
    (start-server port)))