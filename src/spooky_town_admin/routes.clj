(ns spooky-town-admin.routes
  (:require [compojure.core :refer [defroutes POST]]
            [compojure.route :as route]
            [ring.util.response :refer [response bad-request]]
            [spooky-town-admin.db :as db]
            [clojure.spec.alpha :as s]))

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
                      :details (ex-data e)}))))
  (route/not-found "Not Found"))
