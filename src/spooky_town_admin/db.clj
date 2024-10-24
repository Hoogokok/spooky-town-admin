(ns spooky-town-admin.db
  (:require [clojure.spec.alpha :as s]))

;; 데이터 모델 정의
(s/def ::id int?)
(s/def ::title string?)
(s/def ::artist string?)
(s/def ::author string?)
(s/def ::publication-date (s/nilable string?))
(s/def ::publisher (s/nilable string?))
(s/def ::isbn-10 string?)
(s/def ::isbn-13 string?)
(s/def ::price (s/nilable number?))
(s/def ::page-count (s/nilable int?))
(s/def ::description (s/nilable string?))
(s/def ::cover-image (s/nilable string?))

(s/def ::comic (s/keys :req-un [::title ::artist ::author ::isbn-10 ::isbn-13]
                       :opt-un [::id ::publication-date ::publisher ::price ::page-count ::description ::cover-image]))

;; 인메모리 데이터베이스
(def db-state (atom {:comics {} :next-id 1}))

;; Create 함수
(defn add-comic [comic]
  (let [id (:next-id @db-state)]
    (swap! db-state (fn [state]
                      (-> state
                          (update :comics assoc id (assoc comic :id id))
                          (update :next-id inc))))
    id))