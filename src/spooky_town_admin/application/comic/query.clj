(ns spooky-town-admin.application.comic.query
  (:require
   [spooky-town-admin.domain.comic.errors :as errors]
   [spooky-town-admin.core.result :as r]
   [spooky-town-admin.infrastructure.persistence :as persistence]))

(defn get-comic [{:keys [comic-repository]} id]
  (-> (if-let [comic (persistence/find-comic-by-id comic-repository id)]
        (r/success comic)
        (r/failure (errors/business-error :not-found 
                                        (errors/get-business-message :not-found))))
      (r/map #(select-keys % [:id :title :artist :author :isbn13 :isbn10]))
      r/to-map))

(defn list-comics [{:keys [comic-repository]}]
  (let [comics (persistence/list-comics comic-repository)]
    {:success true
     :comics (map #(select-keys % [:id :title :artist :author :isbn13 :isbn10])
                  comics)})) 