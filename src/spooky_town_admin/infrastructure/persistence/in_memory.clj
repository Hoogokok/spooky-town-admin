(ns spooky-town-admin.infrastructure.persistence.in-memory
  (:require [spooky-town-admin.infrastructure.persistence.protocol :refer [ComicRepository PublisherRepository]]
            [spooky-town-admin.domain.comic.errors :as errors]
            [spooky-town-admin.core.result :as r]))

;; 인메모리 데이터베이스 상태 확장
(def db-state (atom {:comics {} 
                     :publishers {}
                     :comics-publishers #{}  ;; 다대다 관계를 위한 Set
                     :next-comic-id 1
                     :next-publisher-id 1}))

(defrecord InMemoryComicRepository []
  ComicRepository
  (save-comic [_ comic]
    (try 
      (let [id (:next-comic-id @db-state)
            comic-with-id (-> comic
                            (dissoc :cover-image-metadata)
                            (assoc :id id))]
        (swap! db-state (fn [state]
                         (-> state
                             (update :comics assoc id comic-with-id)
                             (update :next-comic-id inc))))
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

(defrecord InMemoryPublisherRepository []
  PublisherRepository
  (save-publisher [_ publisher]
    (try 
      (if-let [existing (first (filter #(= (:name %) (:name publisher))
                                     (vals (:publishers @db-state))))]
        (r/success existing)  ;; 이미 존재하는 출판사 반환
        (let [id (:next-publisher-id @db-state)
              publisher-with-id (assoc publisher :id id)]
          (swap! db-state (fn [state]
                           (-> state
                               (update :publishers assoc id publisher-with-id)
                               (update :next-publisher-id inc))))
          (r/success publisher-with-id)))
      (catch Exception e
        (r/failure (errors/system-error :db-error 
                                      (errors/get-system-message :db-error)
                                      (.getMessage e))))))
  
  (find-publisher-by-id [_ id]
    (try
      (if-let [publisher (get-in @db-state [:publishers id])]
        (r/success publisher)
        (r/failure (errors/business-error :not-found 
                                        (errors/get-business-message :not-found))))
      (catch Exception e
        (r/failure (errors/system-error :db-error 
                                      (errors/get-system-message :db-error)
                                      (.getMessage e))))))
  
  (find-publisher-by-name [_ name]
    (try
      (if-let [publisher (first (filter #(= (:name %) name)
                                      (vals (:publishers @db-state))))]
        (r/success publisher)
        (r/success nil))
      (catch Exception e
        (r/failure (errors/system-error :db-error 
                                      (errors/get-system-message :db-error)
                                      (.getMessage e))))))
  
  (find-publishers-by-comic-id [_ comic-id]
    (try
      (let [publisher-ids (->> (:comics-publishers @db-state)
                              (filter #(= (:comic-id %) comic-id))
                              (map :publisher-id))
            publishers (map #(get-in @db-state [:publishers %]) publisher-ids)]
        (r/success publishers))
      (catch Exception e
        (r/failure (errors/system-error :db-error 
                                      (errors/get-system-message :db-error)
                                      (.getMessage e))))))
  
  (associate-publisher-with-comic [_ comic-id publisher-id]
    (try
      (swap! db-state update :comics-publishers conj {:comic-id comic-id 
                                                     :publisher-id publisher-id})
      (r/success true)
      (catch Exception e
        (r/failure (errors/system-error :db-error 
                                      (errors/get-system-message :db-error)
                                      (.getMessage e)))))))

(defn create-repository []
  (->InMemoryComicRepository))

(defn create-publisher-repository []
  (->InMemoryPublisherRepository))