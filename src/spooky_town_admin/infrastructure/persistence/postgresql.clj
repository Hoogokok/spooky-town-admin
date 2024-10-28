(ns spooky-town-admin.infrastructure.persistence.postgresql
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [honey.sql :as sql]
            [honey.sql.helpers :as h]
            [spooky-town-admin.infrastructure.persistence.protocol :refer [ComicRepository]]
            [spooky-town-admin.domain.comic.errors :as errors]
            [spooky-town-admin.domain.common.result :as r]
            [spooky-town-admin.infrastructure.persistence.config :as config]
            [clojure.string :as str]))

;; 도메인 객체를 DB 레코드로 변환
(defn- comic->db [comic]
  (cond-> {}
    (:title comic) (assoc :title (str (:title comic)))
    (:artist comic) (assoc :artist (str (:artist comic)))
    (:author comic) (assoc :author (str (:author comic)))
    (:isbn13 comic) (assoc :isbn13 (str (:isbn13 comic)))
    (:isbn10 comic) (assoc :isbn10 (str (:isbn10 comic)))
    (:publication_date comic) (assoc :publication_date (:publication_date comic))
    (:publisher comic) (assoc :publisher (str (:publisher comic)))
    (:price comic) (assoc :price (:price comic))
    (:page_count comic) (assoc :page_count (:page_count comic))
    (:description comic) (assoc :description (str (:description comic)))
    (:image_url comic) (assoc :image_url (str (:image_url comic)))))

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
      (:page_count row) (assoc :page_count (:page_count row))
      (:description row) (assoc :description (:description row))
      (:image_url row) (assoc :image_url (:image_url row)))))

(defrecord PostgresqlComicRepository [datasource]
  ComicRepository
  (save-comic [_ comic]
    (try
      (let [comic-data (comic->db comic)
            query {:insert-into :comics
                  :values [comic-data]
                  :returning :*}
            result (jdbc/execute-one! datasource (sql/format query)
                                    {:builder-fn rs/as-unqualified-maps})]
        (r/success {:id (:id result)}))
      (catch Exception e
        (r/failure (errors/system-error 
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
      (let [query {:select :*
                   :from :comics
                   :where [:or
                          [:= :isbn13 isbn]
                          [:= :isbn10 isbn]]}
            result (jdbc/execute-one! datasource (sql/format query)
                                    {:builder-fn rs/as-unqualified-maps})]
        (if result
          (r/success (db->comic result))
          (r/success nil))) ;; ISBN 검색은 없는 경우도 정상 케이스
      (catch Exception e
        (r/failure (errors/system-error 
                    :db-error 
                    (errors/get-system-message :db-error)
                    (.getMessage e))))))

  (delete-comic [_ id]
    (try
      (let [query {:delete-from :comics
                   :where [:= :id id]}]
        (jdbc/execute-one! datasource (sql/format query))
        {:success true})
      (catch Exception e
        {:success false
         :error (errors/system-error :db-error 
                                   (errors/get-system-message :db-error)
                                   (.getMessage e))})))

  (list-comics [_]
    (try
      (->> (jdbc/execute! datasource (sql/format {:select :* :from :comics})
                         {:builder-fn rs/as-unqualified-maps})
           (map db->comic))
      (catch Exception _
        []))))

(defn create-repository []
  (->PostgresqlComicRepository @config/datasource))
