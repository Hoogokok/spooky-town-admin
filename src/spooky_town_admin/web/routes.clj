(ns spooky-town-admin.web.routes
    (:require [compojure.core :refer [defroutes GET POST DELETE]]
              [compojure.route :as route]
              [ring.middleware.multipart-params :refer [wrap-multipart-params]]
              [spooky-town-admin.web.handler :as handler]
              [ring.middleware.multipart-params.temp-file :as temp-file]))

(defn create-app [service]
  (-> (defroutes app-routes
        (GET "/health" [] {:status 200 :body {:status "ok"}})
        (GET "/api/comics" [] (handler/list-comics service))
        (GET "/api/comics/:id" [id] (handler/get-comic-by-id service id))
        (POST "/api/comics" request
          (println "Received request with params:" (:multipart-params request))
          (let [form-params (:multipart-params request)
                comic-data {:title (get form-params "title")
                           :artist (get form-params "artist")
                           :author (get form-params "author")
                           :isbn13 (get form-params "isbn13")
                           :isbn10 (get form-params "isbn10")
                           :publisher (get form-params "publisher")
                           :publication-date (get form-params "publication-date")
                           :price (when-let [p (get form-params "price")]
                                  (try (Integer/parseInt p)
                                       (catch Exception _ nil)))
                           :page-count (when-let [p (get form-params "page-count")]
                                       (try (Integer/parseInt p)
                                            (catch Exception _ nil)))
                           :description (get form-params "description")
                           :cover-image (get form-params "cover-image")}]
            (handler/create-comic service comic-data)))
        (route/not-found {:status 404 :body {:error "Not Found"}}))
      (wrap-multipart-params {:store (temp-file/temp-file-store
                                     {:filename-fn (fn [_ filename]
                                                   filename)})})))
