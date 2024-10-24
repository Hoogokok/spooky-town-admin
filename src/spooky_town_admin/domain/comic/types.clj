(ns spooky-town-admin.domain.comic.types
  (:require [clojure.spec.alpha :as s]))

;; 필수 값 객체들
(defrecord ISBN13 [value])
(defrecord ISBN10 [value])
(defrecord Title [value])
(defrecord Artist [value])
(defrecord Author [value])

;; 선택적 값 객체들
(defrecord Publisher [value])
(defrecord PublicationDate [value])
(defrecord Price [value])
(defrecord PageCount [value])
(defrecord Description [value])
(defrecord CoverImage [value])

;; 도메인 타입 스펙 - 필수 필드
(s/def ::isbn13 (s/and string? #(re-matches #"^(?:978|979)-\d-\d{2,7}-\d{1,7}-\d$" %)))
(s/def ::isbn10 (s/and string? #(re-matches #"^\d{1,5}-\d{1,7}-\d{1,6}-[\dX]$" %)))
(s/def ::title (s/and string? #(<= 1 (count %) 100)))
(s/def ::artist (s/and string? #(<= 1 (count %) 20)))
(s/def ::author (s/and string? #(<= 1 (count %) 20)))

;; 도메인 타입 스펙 - 선택적 필드
(s/def ::publisher (s/nilable (s/and string? #(<= 1 (count %) 50))))
(s/def ::publication-date (s/nilable (s/and string? #(re-matches #"^\d{4}-\d{2}-\d{2}$" %))))
(s/def ::price (s/nilable (s/and number? #(>= % 0))))
(s/def ::page-count (s/nilable (s/and integer? pos?)))
(s/def ::description (s/nilable (s/and string? #(<= 0 (count %) 1000))))
(s/def ::cover-image any?)

;; 집계 루트(Aggregate Root)
(defrecord Comic [id title artist author isbn13 isbn10 
                  publication-date publisher price 
                  page-count description cover-image])

;; Comic 생성자
(defn create-comic [{:keys [title artist author isbn13 isbn10] :as required-fields}
                    optional-fields]
  (when (and title artist author isbn13 isbn10)
    (map->Comic (merge required-fields optional-fields))))