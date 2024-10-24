(ns spooky-town-admin.routes
  (:require [compojure.core :refer [defroutes POST]]
            [compojure.route :as route]
            [ring.util.response :refer [response bad-request]]
            [spooky-town-admin.comic :as comic]))

(defn handle-create-comic [body]
  (let [result (comic/create-comic body)]
    (if (:success result)
      (response {:id (:id result)})
      (bad-request {:error (:error result)
                   :details (:details result)}))))

(defroutes app-routes
  (POST "/api/comics" {body :body}
    (handle-create-comic body))
  (route/not-found "Not Found"))
