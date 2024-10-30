(ns spooky-town-admin.infrastructure.persistence.in-memory
  (:require 
   [clojure.tools.logging :as log]
   [spooky-town-admin.core.result :as r]
   [spooky-town-admin.domain.comic.errors :as errors]
   [spooky-town-admin.infrastructure.persistence.protocol :refer [ComicRepository
                                                                  PublisherRepository]]))

;; 인메모리 데이터베이스 상태
(def db-state (atom {:comics {} 
                     :publishers {}
                     :comics-publishers #{}
                     :next-comic-id 1
                     :next-publisher-id 1}))

(defrecord InMemoryComicRepository [state]
  ComicRepository
  (save-comic [_ comic]
    (try 
      (let [id (:next-comic-id @state)
            comic-with-id (-> comic
                            (dissoc :cover-image-metadata)
                            (assoc :id id))]
        (swap! state (fn [state]
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
    (get-in @state [:comics id]))
  
  (find-comic-by-isbn [_ isbn]
    (first (filter #(or (= (:isbn13 %) isbn) 
                       (= (:isbn10 %) isbn))
                   (vals (:comics @state)))))
  
  (find-comic-by-isbns [_ isbn13 isbn10]
    (try
      (log/debug "Searching for comic with ISBNs - ISBN13:" isbn13 "ISBN10:" isbn10)
      (let [isbn13-value (when isbn13 
                          (if (record? isbn13)
                            (get-in isbn13 [:value])
                            isbn13))
            isbn10-value (when isbn10
                          (if (record? isbn10)
                            (get-in isbn10 [:value])
                            isbn10))
            comics (vals (:comics @state))
            result (first (filter #(or (= (:isbn13 %) (str isbn13-value))
                                     (= (:isbn10 %) (str isbn10-value)))
                                comics))]
        (if result
          (do 
            (log/debug "Found comic with ISBNs")
            (r/success result))
          (do
            (log/debug "No comic found with ISBNs")
            (r/success nil))))
      (catch Exception e
        (log/error e "Failed to search comic by ISBNs")
        (r/failure (errors/system-error 
                    :db-error 
                    (errors/get-system-message :db-error)
                    (.getMessage e))))))
  
  (delete-comic [_ id]
    (try
      (swap! state update :comics dissoc id)
      {:success true}
      (catch Exception e
        {:success false
         :error (errors/system-error :db-error 
                                   (errors/get-system-message :db-error)
                                   (.getMessage e))})))
  
  (list-comics [_]
    (vals (:comics @state))))

(defrecord InMemoryPublisherRepository [state]
  PublisherRepository
  (save-publisher [_ publisher]
    (try 
      (if-let [existing (first (filter #(= (:name %) (:name publisher))
                                     (vals (:publishers @state))))]
        (r/success existing)  ;; 이미 존재하는 출판사 반환
        (let [id (:next-publisher-id @state)
              publisher-with-id (assoc publisher :id id)]
          (swap! state (fn [state]
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
      (if-let [publisher (get-in @state [:publishers id])]
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
                                      (vals (:publishers @state))))]
        (r/success publisher)
        (r/success nil))
      (catch Exception e
        (r/failure (errors/system-error 
                    :db-error 
                    (errors/get-system-message :db-error)
                    (.getMessage e))))))
  
  (find-publishers-by-comic-id [_ comic-id]
    (try
      (let [publisher-ids (->> (:comics-publishers @state)
                              (filter #(= (:comic-id %) comic-id))
                              (map :publisher-id))
            publishers (map #(get-in @state [:publishers %]) publisher-ids)]
        (r/success publishers))
      (catch Exception e
        (r/failure (errors/system-error :db-error 
                                      (errors/get-system-message :db-error)
                                      (.getMessage e))))))
  
  (associate-publisher-with-comic [_ comic-id publisher-id]
    (try
      (swap! state update :comics-publishers conj {:comic-id comic-id 
                                                     :publisher-id publisher-id})
      (r/success true)
      (catch Exception e
        (r/failure (errors/system-error :db-error 
                                      (errors/get-system-message :db-error)
                                      (.getMessage e)))))))

(defn create-repository []
  (->InMemoryComicRepository db-state))

(defn create-publisher-repository []
  (->InMemoryPublisherRepository db-state))
