(ns spooky-town-admin.application.comic.query
  (:require
   [spooky-town-admin.domain.comic.errors :as errors]
   [spooky-town-admin.core.result :as r]
   [spooky-town-admin.infrastructure.persistence :as persistence]))

(defn- domain->response [comic]
  {:id (:id comic)
   :title (get-in comic [:title :value])
   :artist (get-in comic [:artist :value])
   :author (get-in comic [:author :value])
   :isbn13 (get-in comic [:isbn13 :value])
   :isbn10 (get-in comic [:isbn10 :value])
   :price (get-in comic [:price :value])
   :image-url (:image-url comic)})

(defn get-comic [{:keys [comic-repository]} id]
  (let [result (persistence/find-comic-by-id comic-repository id)]
    (if (r/success? result)
      (let [comic (r/value result)]
        (if comic
          (r/success (domain->response comic))
          (r/failure (errors/business-error 
                      :not-found 
                      (errors/get-business-message :not-found)))))
      result)))

(defn list-comics [{:keys [comic-repository]}]
  (let [result (persistence/list-comics comic-repository)]
    (if (r/success? result)
      (r/success (map domain->response (r/value result)))
      result))) 