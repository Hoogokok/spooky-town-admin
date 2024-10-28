(ns spooky-town-admin.infrastructure.persistence.postgresql
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [honey.sql :as sql]
            [honey.sql.helpers :as h]
            [spooky-town-admin.infrastructure.persistence.protocol :refer [ComicRepository]]
            [spooky-town-admin.domain.comic.errors :as errors]
            [spooky-town-admin.domain.common.result :as r]
            [spooky-town-admin.infrastructure.persistence.config :as config]
            [clojure.string :as str])
  (:import (java.sql Date)))  ;; java.sql.Date import 추가

(defn- get-value [v]
  (cond
    (record? v) (:value v)  ;; record 타입인 경우 :value 키의 값을 가져옴
    :else v))               ;; 그 외의 경우 값을 그대로 사용

;; 날짜 변환 헬퍼 함수 추가
(defn- to-sql-date [date-str]
  (when date-str
    (Date/valueOf date-str)))

;; 도메인 객체를 DB 레코드로 변환
(defn- comic->db [comic]
  (println "Converting to DB format:" comic)
  (let [db-map (cond-> {}
                 ;; nil이 아닌 값만 맵에 추가하고, record 타입은 값을 추출
                 (not (str/blank? (get-value (:title comic)))) 
                 (assoc :title (get-value (:title comic)))
                 
                 (not (str/blank? (get-value (:artist comic)))) 
                 (assoc :artist (get-value (:artist comic)))
                 
                 (not (str/blank? (get-value (:author comic)))) 
                 (assoc :author (get-value (:author comic)))
                 
                 (not (str/blank? (get-value (:isbn13 comic)))) 
                 (assoc :isbn13 (get-value (:isbn13 comic)))
                 
                 (not (str/blank? (get-value (:isbn10 comic)))) 
                 (assoc :isbn10 (get-value (:isbn10 comic)))
                 
                 (:publication-date comic)
                 (assoc :publication_date (-> comic
                                            :publication-date
                                            get-value
                                            to-sql-date))
                 
                 (not (str/blank? (get-value (:publisher comic)))) 
                 (assoc :publisher (get-value (:publisher comic)))
                 
                 (:price comic)
                 (assoc :price (when-let [p (get-value (:price comic))]
                                (bigdec p)))
                 
                 (:page-count comic)
                 (assoc :page_count (get-value (:page-count comic)))
                 
                 (not (str/blank? (get-value (:description comic)))) 
                 (assoc :description (get-value (:description comic)))
                 
                 (not (str/blank? (get-value (:cover-image-url comic)))) 
                 (assoc :image_url (get-value (:cover-image-url comic))))]
    
    (println "Converted DB map:" db-map)
    db-map))

;; DB 레코드를 도메인 객체로 변환
(defn- db->comic [row]
  (when row
    (cond-> {}
      (:id row) (assoc :id (:id row))
      (:title row) (assoc :title (:title row))
      (:artist row) (assoc :artist (:artist row))
      (:author row) (assoc :author (:author row))
      (:isbn13 row) (assoc :isbn13 (:isbn13 row))
      (:isbn10 row) (assoc :isbn10 (:isbn10 row))
      (:publication_date row) (assoc :publication_date (:publication_date row))
      (:publisher row) (assoc :publisher (:publisher row))
      (:price row) (assoc :price (:price row))
      (:page_count row) (assoc :page_count (:page-count row))
      (:description row) (assoc :description (:description row))
      (:image_url row) (assoc :image_url (:image_url row)))))

(defrecord PostgresqlComicRepository [datasource]
  ComicRepository
  (save-comic [_ comic]
    (try 
      (println "Saving comic to database:" comic)
      (let [comic-data (comic->db comic)
            _ (println "Comic data for insert:" comic-data)
            ;; 빈 맵이 아닌 경우에만 쿼리 실행
            query (when (seq comic-data)
                   {:insert-into :comics
                    :values [comic-data]
                    :returning :*})
            _ (when query
                (println "Generated query:" (pr-str query)))
            formatted-query (when query
                            (sql/format query))
            _ (when formatted-query
                (println "Formatted SQL:" formatted-query))]
        (if formatted-query
          (let [result (jdbc/execute-one! datasource 
                                        formatted-query
                                        {:builder-fn rs/as-unqualified-maps})]
            (println "Database result:" result)
            (r/success (db->comic result)))
          (do
            (println "No valid data to insert")
            (r/failure (errors/validation-error
                        :invalid-data
                        "No valid data to insert into database")))))
      (catch Exception e
        (println "Error saving comic:" (.getMessage e))
        (.printStackTrace e)
        (r/failure
          (errors/system-error
            :db-error
            (errors/get-system-message :db-error)
            (.getMessage e))))))

  (find-comic-by-id [_ id]
    (try
      (let [query {:select :*
                   :from :comics
                   :where [:= :id id]}
            result (jdbc/execute-one! datasource (sql/format query)
                                    {:builder-fn rs/as-unqualified-maps})]
        (if result
          (r/success (db->comic result))
          (r/failure (errors/business-error 
                      :not-found 
                      (errors/get-business-message :not-found)))))
      (catch Exception e
        (r/failure (errors/system-error 
                    :db-error 
                    (errors/get-system-message :db-error)
                    (.getMessage e))))))

  (find-comic-by-isbn [_ isbn]
    (try
      (println "Searching for ISBN:" isbn)
      (let [query {:select :*
                   :from :comics
                   :where [:or
                          [:= :isbn13 isbn]
                          [:= :isbn10 isbn]]}
            formatted-query (sql/format query)
            _ (println "Executing query:" formatted-query)
            result (jdbc/execute-one! datasource formatted-query
                                    {:builder-fn rs/as-unqualified-maps})]
        (println "ISBN search result:" result)
        (if result
          (do 
            (println "Found existing comic with ISBN:" isbn)
            (r/success (db->comic result)))
          (do
            (println "No comic found with ISBN:" isbn)
            (r/success nil)))) ;; ISBN 검색은 없는 경우도 정상 케이스
      (catch Exception e
        (println "Error searching for ISBN:" (.getMessage e))
        (.printStackTrace e)
        (r/failure (errors/system-error 
                    :db-error 
                    (errors/get-system-message :db-error)
                    (.getMessage e))))))

  (delete-comic [_ id]
    (try
      (let [query {:delete-from :comics
                   :where [:= :id id]}]
        (jdbc/execute-one! datasource (sql/format query))
        (r/success true))  ;; Result 타입으로 변경
      (catch Exception e
        (r/failure         ;; Result 타입으로 변경
          (errors/system-error 
            :db-error 
            (errors/get-system-message :db-error)
            (.getMessage e))))))

  (list-comics [_]
    (try
      (r/success          ;; Result 타입으로 변경
        (->> (jdbc/execute! datasource (sql/format {:select :* :from :comics})
                         {:builder-fn rs/as-unqualified-maps})
             (map db->comic)))
      (catch Exception e
        (r/failure       ;; Result 타입으로 변경
          (errors/system-error 
            :db-error 
            (errors/get-system-message :db-error)
            (.getMessage e)))))))

(defn create-repository []
  (if-let [ds (config/get-datasource)]  ;; get-datasource 함수 사용
    (->PostgresqlComicRepository ds)
    (throw (ex-info "데이터소스가 초기화되지 않았습니다."
                   {:type :db-error}))))
