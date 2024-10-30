(ns spooky-town-admin.domain.comic.publisher
  (:require [clojure.string :as str]
            [spooky-town-admin.core.result :as r]
            [spooky-town-admin.domain.comic.errors :as errors]))

(def ^:private min-name-length 1)
(def ^:private max-name-length 50)
(def ^:private name-pattern #"^[가-힣a-zA-Z0-9\s&.'()-]+$")  
;; Value Objects
(defrecord PublisherName [value])
(defrecord UnvalidatedPublisher [name])
(defrecord ValidatedPublisher [name])
(defrecord PersistedPublisher [id name])

(defn validate-publisher-name [name]
  (cond
    (nil? name)
    (r/success nil)

    (str/blank? name)
    (r/failure (errors/business-error 
                :invalid-publisher-name 
                (errors/get-business-message :invalid-publisher-name)))

    (> (count name) max-name-length)
    (r/failure (errors/business-error 
                :invalid-publisher-name 
                (errors/get-business-message :invalid-publisher-name)))

    (not (re-matches name-pattern name))
    (r/failure (errors/business-error 
                :invalid-publisher-name 
                (errors/get-business-message :invalid-publisher-name)))

    :else
    (r/success (->PublisherName name))))

(defn create-unvalidated-publisher [data]
  (r/success (->UnvalidatedPublisher (:name data))))

(defn create-validated-publisher [data]
  (-> (validate-publisher-name (:name data))
      (r/bind (fn [name]
                (if name
                  (r/success (->ValidatedPublisher name))
                  (r/success nil))))))

(defn create-persisted-publisher [id validated-publisher]
  (if validated-publisher
    (r/success (->PersistedPublisher id (:name validated-publisher)))
    (r/failure (errors/business-error 
                :invalid-publisher 
                "검증된 출판사 정보가 누락되었습니다."))))

(defn get-id [publisher]
  (:id publisher))

(defn get-name [publisher]
  (if (instance? PublisherName (:name publisher))
    (get-in publisher [:name :value])
    (:name publisher)))

(defn update-name [publisher new-name]
  (r/bind (validate-publisher-name new-name)
          (fn [validated-name]
            (r/success (assoc publisher :name validated-name))))) 