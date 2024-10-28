(ns spooky-town-admin.infrastructure.persistence.postgresql
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [honey.sql :as sql]
            [honey.sql.helpers :as h]
            [spooky-town-admin.infrastructure.persistence.protocol :refer [ComicRepository]]
            [spooky-town-admin.domain.comic.errors :as errors]
            [spooky-town-admin.domain.common.result :as r]
            [clojure.string :as str]))

(def db-spec
  {:dbtype "postgresql"
   :dbname (or (System/getenv "DB_NAME") "spooky_town")
   :host (or (System/getenv "DB_HOST") "localhost")
   :port (or (System/getenv "DB_PORT") 5432)
   :user (or (System/getenv "DB_USER") "postgres")
   :password (or (System/getenv "DB_PASSWORD") "postgres")})

(def ds (jdbc/get-datasource db-spec))

(defn- comic->db [comic]
  (-> comic
      (update :title str)
      (update :artist str)
      (update :author str)
      (update :isbn13 str)
      (update :isbn10 str)))

(defn- db->comic [row]
  (when row
    (-> row
        (update :title str)
        (update :artist str)
        (update :author str)
        (update :isbn13 str)
        (update :isbn10 str))))

(defrecord PostgresqlComicRepository []
  ComicRepository
  (save-comic [_ comic]
    (try
      (let [comic-data (comic->db comic)
            query {:insert-into :comics
                  :values [comic-data]
                  :returning :*}
            result (jdbc/execute-one! ds (sql/format query)
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
            result (jdbc/execute-one! ds (sql/format query)
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
            result (jdbc/execute-one! ds (sql/format query)
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
        (jdbc/execute-one! ds (sql/format query))
        {:success true})
      (catch Exception e
        {:success false
         :error (errors/system-error :db-error 
                                   (errors/get-system-message :db-error)
                                   (.getMessage e))})))

  (list-comics [_]
    (try
      (->> (jdbc/execute! ds (sql/format {:select :* :from :comics})
                         {:builder-fn rs/as-unqualified-maps})
           (map db->comic))
      (catch Exception _
        []))))

(defn create-repository []
  (->PostgresqlComicRepository))
