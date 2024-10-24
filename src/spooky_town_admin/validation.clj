(ns spooky-town-admin.validation
  (:require [clojure.string :as str]))

(defn success [value]
  {:success true :value value})

(defn failure [error]
  {:success false :error error})

(defn success? [result]
  (:success result))

(defn validate-length [field-name min max]
  (fn [v]
    (if (and (string? v) (<= min (count v) max))
      (success v)
      (failure {field-name (str field-name "의 길이는 " min "에서 " max " 사이여야 합니다.")}))))

(def validate-title (validate-length "제목" 1 100))
(def validate-artist (validate-length "그림 작가" 1 20))
(def validate-author (validate-length "글 작가" 1 20))

(defn validate-isbn-13 [isbn]
  (if (and (string? isbn)
           (re-matches #"^(?:978|979)-\d-\d{2,7}-\d{1,7}-\d$" isbn))
    (success isbn)
    (failure {"ISBN-13" "유효하지 않은 ISBN-13 형식입니다."})))

(defn validate-isbn-10 [isbn]
  (if (and (string? isbn)
           (re-matches #"^\d{1,5}-\d{1,7}-\d{1,6}-[\dX]$" isbn))
    (success isbn)
    (failure {"ISBN-10" "유효하지 않은 ISBN-10 형식입니다."})))

(defn validate-comic [comic]
  (let [title-result (validate-title (:title comic))
        artist-result (validate-artist (:artist comic))
        author-result (validate-author (:author comic))
        isbn-13-result (validate-isbn-13 (:isbn-13 comic))
        isbn-10-result (validate-isbn-10 (:isbn-10 comic))]
    (cond
      (not (success? title-result)) (failure (:error title-result))
      (not (success? artist-result)) (failure (:error artist-result))
      (not (success? author-result)) (failure (:error author-result))
      (not (success? isbn-13-result)) (failure (:error isbn-13-result))
      (not (success? isbn-10-result)) (failure (:error isbn-10-result))
      :else (success (assoc comic 
                            :title (:value title-result)
                            :artist (:value artist-result)
                            :author (:value author-result)
                            :isbn-13 (:value isbn-13-result)
                            :isbn-10 (:value isbn-10-result))))))
