(ns spooky-town-admin.core
  (:require
   [clojure.tools.logging :as log]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.cors :refer [wrap-cors]]
   [ring.middleware.json :refer [wrap-json-response]]
   [ring.middleware.keyword-params :refer [wrap-keyword-params]]
   [ring.middleware.params :refer [wrap-params]]
   [spooky-town-admin.application.comic.service :as service]
   [spooky-town-admin.infrastructure.persistence.config :as db-config]
   [spooky-town-admin.web.routes :as routes])
  (:gen-class))

(defn create-app [env]
  (let [service (service/create-comic-service env)]
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
      (log/info "Server started at http://localhost:" port)
      (reset! server (jetty/run-jetty app {:port port :join? false})))))

(defn stop-server! []
  (when @server
    (.stop @server)
    (reset! server nil)))

(defn -main [& args]
  (db-config/init-db!)
  (start-server!))
