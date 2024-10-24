(ns spooky-town-admin.core
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :as route])
  (:gen-class))

(defroutes app-routes
  (GET "/" [] "Hello, Spooky Town Admin!")
  (route/not-found "Not Found"))

(defn start-server [port]
  (jetty/run-jetty app-routes {:port port :join? false}))

(defn -main
  "Start the web server"
  [& args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "3000"))]
    (println (str "서버가 http://localhost:" port "에서 실행 중입니다."))
    (start-server port)))