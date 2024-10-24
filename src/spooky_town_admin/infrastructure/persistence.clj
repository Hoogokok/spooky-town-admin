(ns spooky-town-admin.infrastructure.persistence
  (:require [spooky-town-admin.domain.comic.types :as types]
            [spooky-town-admin.domain.comic.errors :as errors]
            [clojure.spec.alpha :as s]))

;; 인메모리 데이터베이스 상태 - 단순화
(def db-state (atom {:comics {} :next-id 1}))

;; 저장소 프로토콜 정의 - 이미지 메타데이터 관련 메서드 제거
(defprotocol ComicRepository
  (save-comic [this comic])
  (find-comic-by-id [this id])
  (find-comic-by-isbn [this isbn])
  (delete-comic [this id])
  (list-comics [this]))

;; 인메모리 저장소 구현
(defrecord InMemoryComicRepository []
  ComicRepository
  (save-comic [_ comic]
    (try 
      (let [id (:next-id @db-state)
            ;; cover-image-metadata는 저장하지 않고 cover-image-id만 저장
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

;; 저장소 인스턴스 생성 함수
(defn create-comic-repository []
  (->InMemoryComicRepository))

;; 트랜잭션 관리 (향후 실제 DB 사용 시 확장)
(defmacro with-transaction [& body]
  `(do ~@body))