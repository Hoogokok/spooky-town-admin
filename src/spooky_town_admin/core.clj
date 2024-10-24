(ns spooky-town-admin.core
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :refer [defroutes POST]]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [spooky-town-admin.db :as db]
            [ring.util.response :refer [response bad-request]]
            [clojure.spec.alpha :as s])
  (:gen-class))

(defn validate-comic [comic]
  (if (s/valid? :spooky-town-admin.db/comic comic)
    comic
    (throw (ex-info "Invalid comic data" (s/explain-data :spooky-town-admin.db/comic comic)))))

(defroutes app-routes
  (POST "/api/comics" {body :body}
    (try
      (let [validated-comic (validate-comic body)
            id (db/add-comic validated-comic)]
        (response {:id id}))
      (catch Exception e
        (bad-request {:error (.getMessage e)
                      :details (ex-data e)}))))  ; 상세 오류 정보 추가
  (route/not-found "Not Found"))

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