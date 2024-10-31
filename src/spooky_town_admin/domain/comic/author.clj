(ns spooky-town-admin.domain.comic.author
  (:require [clojure.string :as str]
            [spooky-town-admin.core.result :as r]
            [spooky-town-admin.domain.comic.errors :as errors]))

;; 상수 정의
(def ^:private min-name-length 1)
(def ^:private max-name-length 50)
(def ^:private name-pattern #"^[가-힣\u4e00-\u9fff一-龥a-zA-Z0-9\s.'()-]+$")
(def valid-author-types #{:writer :artist})

;; 값 객체
(defrecord AuthorName [value])
(defrecord AuthorDescription [value])

;; 엔티티
(defrecord UnvalidatedAuthor [name type description])
(defrecord ValidatedAuthor [name type description])
(defrecord PersistedAuthor [id name type description])

;; --------- 도메인 이벤트 ---------
(defrecord AuthorValidated [validated-author timestamp])
(defrecord AuthorCreated [persisted-author timestamp])
(defrecord AuthorUpdated [persisted-author timestamp])
(defrecord AuthorAssociatedWithComic [author-id comic-id timestamp])

;; --------- 도메인 이벤트 생성 함수들 ---------
(defn create-author-validated [validated-author]
  (->AuthorValidated validated-author (java.time.Instant/now)))

(defn create-author-created [persisted-author]
  (->AuthorCreated persisted-author (java.time.Instant/now)))

(defn create-author-updated [persisted-author]
  (->AuthorUpdated persisted-author (java.time.Instant/now)))

(defn create-author-associated-with-comic [author-id comic-id]
  (->AuthorAssociatedWithComic author-id comic-id (java.time.Instant/now)))

(defn validate-author-name [name]
  (cond
    (nil? name)
    (r/failure (errors/validation-error 
                :name "이름은 필수 항목입니다"))
    
    (str/blank? name)
    (r/failure (errors/validation-error 
                :name "이름은 비어있을 수 없습니다"))
    
    (> (count name) max-name-length)
    (r/failure (errors/validation-error 
                :name "이름은 50자를 초과할 수 없습니다"))
    
    (not (re-matches name-pattern name))
    (r/failure (errors/validation-error 
                :name "이름에 허용되지 않는 특수문자가 포함되어 있습니다"))
    
    :else
    (r/success (->AuthorName name))))

(defn validate-author-type [type]
  (if (contains? valid-author-types type)
    (r/success type)
    (r/failure (errors/validation-error 
                :type "유효하지 않은 작가 유형입니다"))))

(defn validate-author-description [description]
  (if (nil? description)
    (r/success nil)
    (if (< (count description) 1000)
      (r/success (->AuthorDescription description))
      (r/failure (errors/validation-error 
                  :description "설명은 1000자를 초과할 수 없습니다")))))

(defn create-unvalidated-author [data]
  (map->UnvalidatedAuthor data))

(defn create-validated-author [data]
  (let [{:keys [name type description]} data]
    (-> (validate-author-name name)
        (r/bind (fn [validated-name]
                  (-> (validate-author-type type)
                      (r/bind (fn [validated-type]
                               (-> (validate-author-description description)
                                   (r/bind (fn [validated-description]
                                            (let [validated-author (->ValidatedAuthor 
                                                                   validated-name
                                                                   validated-type
                                                                   validated-description)]
                                              ;; 이벤트 발생
                                              (create-author-validated validated-author)
                                              (r/success validated-author)))))))))))))

(defn create-persisted-author [id validated-author]
  (if validated-author
    (let [persisted-author (map->PersistedAuthor 
                            (assoc validated-author :id id))]
      ;; 이벤트 발생
      (create-author-created persisted-author)
      (r/success persisted-author))
    (r/failure (errors/business-error 
                :invalid-author 
                "유효하지 않은 작가 정보입니다"))))

(defn get-name [author]
  (get-in author [:name :value]))

(defn get-type [author]
  (:type author))

(defn get-description [author]
  (get-in author [:description :value]))