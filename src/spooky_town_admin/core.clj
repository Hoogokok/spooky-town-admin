(ns spooky-town-admin.core
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.cors :refer [wrap-cors]]
            [spooky-town-admin.web.routes :as routes]
            [spooky-town-admin.application.comic-service :as comic-service]
            [ring.middleware.multipart-params.temp-file :as temp-file]
            [spooky-town-admin.infrastructure.persistence.config :as db-config])
  (:gen-class))

(defn create-app [env]
  (let [service (comic-service/create-comic-service env)]
    (-> (routes/create-app service)
        wrap-keyword-params
        wrap-params
        wrap-json-response
        (wrap-cors :access-control-allow-origin [#"http://localhost:8280"]
                  :access-control-allow-methods [:get :put :post :delete]
                  :access-control-allow-headers ["Content-Type" "Authorization" "Accept" "Origin"]))))

(def port (Integer/parseInt (or (System/getenv "PORT") "3000")))

(defonce server (atom nil))

(defn start-server! []
  (when-not @server
    (let [env (keyword (or (System/getenv "ENVIRONMENT") "dev"))
          app (create-app env)]
      (println "서버가 http://localhost:" port "에서 실행 중입니다.")
      (reset! server (jetty/run-jetty app {:port port :join? false})))))

(defn stop-server! []
  (when @server
    (.stop @server)
    (reset! server nil)))

(defn -main [& args]
  (db-config/init-db!)
  (start-server!))
