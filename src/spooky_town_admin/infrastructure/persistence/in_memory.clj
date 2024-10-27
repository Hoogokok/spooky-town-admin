(ns spooky-town-admin.infrastructure.persistence.in-memory
  (:require [spooky-town-admin.infrastructure.persistence.protocol :refer [ComicRepository]]
            [spooky-town-admin.domain.comic.errors :as errors]))

;; 인메모리 데이터베이스 상태
(def db-state (atom {:comics {} :next-id 1}))

(defrecord InMemoryComicRepository []
  ComicRepository
  (save-comic [_ comic]
    (try 
      (let [id (:next-id @db-state)
            comic-with-id (-> comic
                            (dissoc :cover-image-metadata)
                            (assoc :id id))]
        (swap! db-state (fn [state]
                         (-> state
                             (update :comics assoc id comic-with-id)
                             (update :next-id inc))))
        {:success true :id id})
      (catch Exception e
        {:success false 
         :error (errors/system-error :db-error 
                                   (errors/get-system-message :db-error)
                                   (.getMessage e))})))
  
  (find-comic-by-id [_ id]
    (get-in @db-state [:comics id]))
  
  (find-comic-by-isbn [_ isbn]
    (first (filter #(or (= (:isbn13 %) isbn) 
                       (= (:isbn10 %) isbn))
                   (vals (:comics @db-state)))))
  
  (delete-comic [_ id]
    (try
      (swap! db-state update :comics dissoc id)
      {:success true}
      (catch Exception e
        {:success false
         :error (errors/system-error :db-error 
                                   (errors/get-system-message :db-error)
                                   (.getMessage e))})))
  
  (list-comics [_]
    (vals (:comics @db-state))))

(defn create-repository []
  (->InMemoryComicRepository))