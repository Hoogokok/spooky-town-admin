(ns spooky-town-admin.infrastructure.persistence.in-memory
  (:require 
   [clojure.tools.logging :as log]
   [spooky-town-admin.core.result :as r]
   [spooky-town-admin.domain.comic.errors :as errors]
   [spooky-town-admin.domain.comic.types :refer [->Title ->Artist ->Author 
                                                ->ISBN13 ->ISBN10 ->Price ->PublicationDate ->PageCount ->Description]]
   [spooky-town-admin.infrastructure.persistence.protocol :refer [ComicRepository
                                                                PublisherRepository]]))

;; 인메모리 데이터베이스 상태
(def db-state (atom {:comics {} 
                     :publishers {}
                     :comics-publishers #{}
                     :next-comic-id 1
                     :next-publisher-id 1}))

;; 도메인 객체로 변환하는 함수 추가
(defn- ->domain-comic [comic]
  (-> comic
      (update :title ->Title)
      (update :artist ->Artist)
      (update :author ->Author)
      (update :isbn13 ->ISBN13)
      (update :isbn10 ->ISBN10)
      (update :price ->Price)))

(defrecord InMemoryComicRepository [state]
  ComicRepository
  (save-comic [_ comic]
    (try 
      (let [id (:next-comic-id @state)
            comic-with-id (-> comic
                           ->domain-comic  ;; Value Object로 변환
                           (dissoc :cover-image-metadata)
                           (assoc :id id))]
        (swap! state (fn [state]
                       (-> state
                           (update :comics assoc id comic-with-id)
                           (update :next-comic-id inc))))
        (r/success {:id id}))
      (catch Exception e
        (r/failure (errors/->SystemError 
                    :db-error 
                    (errors/get-system-message :db-error)
                    (or (.getMessage e) "Unknown error"))))))
  
  (find-comic-by-id [_ id]
    (try
      (if-let [comic (get-in @state [:comics id])]
        (r/success comic)
        (r/failure (errors/->BusinessError 
                    :not-found 
                    (errors/get-business-message :not-found))))
      (catch Exception e
        (r/failure (errors/->SystemError
                    :db-error 
                    (errors/get-system-message :db-error)
                    (or (.getMessage e) "Unknown error"))))))
  
  (find-comic-by-isbn [_ isbn]
    (try
      (if-let [comic (first (filter #(or (= (get-in % [:isbn13 :value]) isbn) 
                                      (= (get-in % [:isbn10 :value]) isbn))
                                  (vals (:comics @state))))]
        (r/success comic)
        (r/success nil))
      (catch Exception e
        (r/failure (errors/->SystemError 
                    :db-error 
                    (errors/get-system-message :db-error)
                    (or (.getMessage e) "Unknown error"))))))
  
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
            result (first (filter #(or (= (get-in % [:isbn13 :value]) (str isbn13-value))
                                     (= (get-in % [:isbn10 :value]) (str isbn10-value)))
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
        (r/failure (errors/->SystemError 
                    :db-error 
                    (errors/get-system-message :db-error)
                    (or (.getMessage e) "Unknown error"))))))
  
  (delete-comic [_ id]
    (try
      (swap! state update :comics dissoc id)
      (r/success true)
      (catch Exception e
        (r/failure (errors/->SystemError 
                    :db-error 
                    (errors/get-system-message :db-error)
                    (or (.getMessage e) "Unknown error"))))))
  
  (list-comics [_]
    (try
      (r/success (vals (:comics @state)))
      (catch Exception e
        (r/failure (errors/->SystemError 
                    :db-error 
                    (errors/get-system-message :db-error)
                    (or (.getMessage e) "Unknown error")))))) )

(defrecord InMemoryPublisherRepository [state]
  PublisherRepository
  (save-publisher [_ publisher]
    (try 
      (if-let [existing (first (filter #(= (:name %) (:name publisher))
                                     (vals (:publishers @state))))]
        (r/success existing)
        (let [id (:next-publisher-id @state)
              publisher-with-id (assoc publisher :id id)]
          (swap! state (fn [state]
                        (-> state
                            (update :publishers assoc id publisher-with-id)
                            (update :next-publisher-id inc))))
          (r/success publisher-with-id)))
      (catch Exception e
        (r/failure (errors/->SystemError 
                    :db-error 
                    (errors/get-system-message :db-error)
                    (or (.getMessage e) "Unknown error"))))))
  
  (find-publisher-by-id [_ id]
    (try
      (if-let [publisher (get-in @state [:publishers id])]
        (r/success publisher)
        (r/failure (errors/->BusinessError :not-found
                                          (errors/get-business-message :not-found))))
      (catch Exception e
        (r/failure (errors/->SystemError :db-error 
                                      (errors/get-system-message :db-error)
                                      (or (.getMessage e) "Unknown error"))))))
  
  (find-publisher-by-name [_ name]
    (try
      (if-let [publisher (first (filter #(= (:name %) name)
                                      (vals (:publishers @state))))]
        (r/success publisher)
        (r/success nil))
      (catch Exception e
        (r/failure (errors/->SystemError 
                    :db-error 
                    (errors/get-system-message :db-error)
                    (or (.getMessage e) "Unknown error"))))))
  
  (find-publishers-by-comic-id [_ comic-id]
    (try
      (let [publisher-ids (->> (:comics-publishers @state)
                              (filter #(= (:comic-id %) comic-id))
                              (map :publisher-id))
            publishers (map #(get-in @state [:publishers %]) publisher-ids)]
        (r/success publishers))
      (catch Exception e
        (r/failure (errors/->SystemError 
                    :db-error 
                    (errors/get-system-message :db-error)
                    (or (.getMessage e) "Unknown error"))))))
  
  (associate-publisher-with-comic [_ comic-id publisher-id]
    (try
      (swap! state update :comics-publishers conj {:comic-id comic-id 
                                                   :publisher-id publisher-id})
      (r/success true)
      (catch Exception e
        (r/failure (errors/->SystemError 
                    :db-error 
                    (errors/get-system-message :db-error)
                    (or (.getMessage e) "Unknown error")))))))

(defn create-repository []
  (->InMemoryComicRepository db-state))

(defn create-publisher-repository []
  (->InMemoryPublisherRepository db-state))
