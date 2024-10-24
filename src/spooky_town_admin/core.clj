(ns spooky-town-admin.core
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
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
        wrap-json-response
        (wrap-json-body {:keywords? true})
        wrap-multipart-params)))

(defn start-server [port]
  (let [env (or (System/getenv "APP_ENV") :dev)
        app (create-app env)]
    (jetty/run-jetty app {:port port :join? false})))

(defn -main
  "Start the web server"
  [& args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "3000"))]
    (println (str "서버가 http://localhost:" port "에서 실행 중입니다."))
    (start-server port)))