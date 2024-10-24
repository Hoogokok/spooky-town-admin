(ns spooky-town-admin.core
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [spooky-town-admin.routes :refer [app-routes]])
  (:gen-class))

(def app
  (-> app-routes
      wrap-json-response
      (wrap-json-body {:keywords? true})
      wrap-multipart-params))

(defn start-server [port]
  (jetty/run-jetty app {:port port :join? false}))

(defn -main
  "Start the web server"
  [& args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "3000"))]
    (println (str "서버가 http://localhost:" port "에서 실행 중입니다."))
    (start-server port)))
