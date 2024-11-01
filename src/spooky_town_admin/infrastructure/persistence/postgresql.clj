(ns spooky-town-admin.infrastructure.persistence.postgresql
  (:require
   [clojure.tools.logging :as log]
   [honey.sql :as sql]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [spooky-town-admin.core.result :as r]
   [spooky-town-admin.domain.comic.author :as author]
   [spooky-town-admin.domain.comic.errors :as errors]
   [spooky-town-admin.domain.comic.publisher :as publisher]
   [spooky-town-admin.domain.comic.types :refer [->Description ->ISBN10
                                                 ->ISBN13 ->PageCount ->Price
                                                 ->PublicationDate ->Title]]
   [spooky-town-admin.infrastructure.persistence.config :as config]
   [spooky-town-admin.infrastructure.persistence.protocol :refer [AuthorRepository
                                                                  ComicRepository
                                                                  PublisherRepository]]
   [spooky-town-admin.infrastructure.persistence.transaction :as transaction]))


;; 도메인 객체를 DB 레코드로 변환
(defn- comic->db [comic]
  (log/debug "Converting comic to DB format:" comic)
  (let [get-value (fn [field]
                    (cond
                      (map? field) (:value field)
                      (string? field) field
                      :else (str field)))]
    {:title (get-value (:title comic))
     :artist (author/get-name (:artist comic))  ;; 작가 도메인 객체에서 이름 추출
     :author (author/get-name (:author comic))  ;; 작가 도메인 객체에서 이름 추출
     :isbn13 (get-value (:isbn13 comic))
     :isbn10 (get-value (:isbn10 comic))
     :price (when-let [p (:price comic)]
              (if (map? p)
                (:value p)
                p))
     :image_url (get-value (:image_url comic))}))

;; DB 레코드를 도메인 객체로 변환
(defn- db->comic [row]
  (when row
    (cond-> {}
      (:id row) 
      (assoc :id (:id row))
      
      (:title row) 
      (assoc :title (->Title (:title row)))
      
       (:artist row)
      (assoc :artist (author/->ValidatedAuthor 
                     (author/->AuthorName (:artist row))
                     :artist
                     nil))
      
      (:author row)
      (assoc :author (author/->ValidatedAuthor 
                     (author/->AuthorName (:author row))
                     :writer
                     nil))
      
      
      (:isbn13 row) 
      (assoc :isbn13 (->ISBN13 (:isbn13 row)))
      
      (:isbn10 row) 
      (assoc :isbn10 (->ISBN10 (:isbn10 row)))
      
      (:publication_date row) 
      (assoc :publication-date (->PublicationDate (:publication_date row)))
      
      (:price row) 
      (assoc :price (->Price (:price row)))
      
      (:page_count row) 
      (assoc :page-count (->PageCount (:page_count row)))
      
      (:description row) 
      (assoc :description (->Description (:description row)))
      
      (:image_url row) 
      (assoc :image-url (:image_url row)))))

(defrecord PostgresqlComicRepository [datasource]
  ComicRepository
  (save-comic [_ comic]
    (try
      (let [tx (transaction/get-current-tx)  ;; 현재 트랜잭션 컨텍스트 사용
            db-comic (comic->db comic)
            query {:insert-into :comics
                  :values [db-comic]
                  :returning :*}
            result (jdbc/execute-one! tx (sql/format query)
                                   {:builder-fn rs/as-unqualified-maps})]
        (r/success (db->comic result)))
      (catch Exception e
        (r/failure (errors/system-error
                   :db-error
                   (errors/get-system-message :db-error)
                   (.getMessage e))))))

  (find-comic-by-id [_ id]
    (try
      (let [tx (transaction/get-current-tx)
            query {:select :*
                   :from :comics
                   :where [:= :id id]}
            formatted-query (sql/format query)
            _ (log/debug "Executing query:" formatted-query)
            result (jdbc/execute-one! tx formatted-query
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
      (log/debug "Searching for comic with ISBN:" isbn)
      (let [tx (transaction/get-current-tx)
            isbn-value (if (record? isbn)  ;; record 타입 체크
                        (get-in isbn [:value])
                        isbn)
            query {:select :*
                  :from :comics
                  :where [:or
                         [:= :isbn13 (str isbn-value)]  ;; 문자열로 변환
                         [:= :isbn10 (str isbn-value)]]}  ;; 문자열로 변환
            formatted-query (sql/format query)
            _ (log/debug "Executing query:" formatted-query)
            result (jdbc/execute-one! tx formatted-query
                                    {:builder-fn rs/as-unqualified-maps})]
        (if result
          (do 
            (log/debug "Found comic with ISBN:" isbn)
            (r/success (db->comic result)))
          (do
            (log/debug "No comic found with ISBN:" isbn)
            (r/success nil))))
      (catch Exception e
        (log/error e "Failed to search comic by ISBN:" isbn)
        (r/failure (errors/system-error 
                    :db-error 
                    (errors/get-system-message :db-error)
                    (.getMessage e))))))

  (find-comic-by-isbns [_ isbn13 isbn10]
    (try
      (log/debug "Searching for comic with ISBNs - ISBN13:" isbn13 "ISBN10:" isbn10)
      (let [tx (transaction/get-current-tx)
            conditions (filterv some? 
                                [(when isbn13 [:= :isbn13 (str isbn13)])
                                 (when isbn10 [:= :isbn10 (str isbn10)])])
            where-clause (when (seq conditions)
                            (into [:or] conditions))
            query (cond-> {:select :*
                          :from :comics}
                   where-clause (assoc :where where-clause))
            formatted-query (sql/format query)
            _ (log/debug "Executing query:" formatted-query)
            result (jdbc/execute-one! tx formatted-query
                                    {:builder-fn rs/as-unqualified-maps})]
        (if result
          (do 
            (log/debug "Found comic with ISBNs - Result:" result)
            (r/success (db->comic result)))
          (do
            (log/debug "No comic found with ISBNs")
            (r/success nil))))
      (catch Exception e
        (log/error e "Failed to search comic by ISBNs")
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
      (let [tx (transaction/get-current-tx)
            query {:select [:*]
                   :from [:comics]}
            formatted-query (sql/format query)
            _ (log/debug "Executing query:" formatted-query)
            result (jdbc/execute! tx formatted-query
                                  {:builder-fn rs/as-unqualified-maps})]
        (log/debug "Found comics:" result)
        (r/success (map db->comic result)))  ;; Result 타입으로 감싸서 반환
      
      (catch Exception e
        (log/error e "Failed to list comics")
        (r/failure (errors/system-error
                   :db-error
                   (errors/get-system-message :db-error)
                   (.getMessage e)))))))

(defn- publisher->db [publisher]
  (when publisher
    {:name (if (record? (:name publisher))
             (get-in publisher [:name :value])
             (:name publisher))}))

(defn- db->publisher [row]
  (when row
    (publisher/->PersistedPublisher (:id row) (:name row))))

(defrecord PostgresqlPublisherRepository [datasource]
  PublisherRepository
  (save-publisher [_ publisher]
    (try
      (let [tx (transaction/get-current-tx)
            db-publisher (publisher->db publisher)
            query {:insert-into :publishers
                  :values [db-publisher]
                  :on-conflict [:name]
                  :do-update-set {:name :EXCLUDED.name}
                  :returning [:*]}
            result (jdbc/execute-one! tx
                                    (sql/format query)
                                    {:builder-fn rs/as-unqualified-maps})]
        (r/success (db->publisher result)))
      (catch Exception e
        (log/error e "Failed to save publisher:" publisher)
        (r/failure (errors/system-error
                   :db-error
                   (errors/get-system-message :db-error)
                   (.getMessage e))))))

  (find-publisher-by-id [_ id]
    (try
      (let [tx (transaction/get-current-tx)
            query {:select :*
                   :from :publishers
                   :where [:= :id id]}
            result (jdbc/execute-one! tx
                                    (sql/format query)
                                    {:builder-fn rs/as-unqualified-maps})]
        (if result
          (r/success (db->publisher result))
          (r/failure (errors/business-error
                     :not-found
                     (errors/get-business-message :not-found)))))
      (catch Exception e
        (r/failure (errors/system-error
                   :db-error
                   (errors/get-system-message :db-error)
                   (.getMessage e))))))

  (find-publisher-by-name [_ name]
    (try
      (let [query {:select :*
                   :from :publishers
                   :where [:= :name name]}
            result (jdbc/execute-one! datasource
                                    (sql/format query)
                                    {:builder-fn rs/as-unqualified-maps})]
        (r/success (db->publisher result)))
      (catch Exception e
        (r/failure (errors/system-error
                   :db-error
                   (errors/get-system-message :db-error)
                   (.getMessage e))))))

  (find-publishers-by-comic-id [_ comic-id]
    (try
      (let [tx (transaction/get-current-tx)
            query {:select [:p.*]
                   :from [[:publishers :p]]
                   :join [[:comics_publishers :cp] [:= :p.id :cp.publisher_id]]
                   :where [:= :cp.comic_id comic-id]}
            formatted-query (sql/format query)
            _ (log/debug "Executing query:" formatted-query)
            result (jdbc/execute! tx formatted-query
                                  {:builder-fn rs/as-unqualified-maps})]
        (log/debug "Found publishers for comic:" comic-id "Result:" result)
        (r/success (map db->publisher result)))
      (catch Exception e
        (log/error e "Failed to find publishers for comic ID:" comic-id)
        (r/failure (errors/system-error
                   :db-error
                   (errors/get-system-message :db-error)
                   (.getMessage e))))))

  (associate-publisher-with-comic [_ comic-id publisher-id]
    (try
      (let [tx (transaction/get-current-tx)
            ;; 먼저 comic과 publisher가 존재하는지 확인
            comic-exists? (-> {:select [1]
                             :from :comics
                             :where [:= :id comic-id]}
                            sql/format
                            (->> (jdbc/execute-one! tx))
                            boolean)
            publisher-exists? (-> {:select [1]
                                 :from :publishers
                                 :where [:= :id publisher-id]}
                                sql/format
                                (->> (jdbc/execute-one! tx))
                                boolean)]
        (if (and comic-exists? publisher-exists?)
          (let [query {:insert-into :comics_publishers
                      :values [{:comic_id comic-id
                              :publisher_id publisher-id}]}
                _ (log/debug "Associating publisher" publisher-id "with comic" comic-id)
                result (jdbc/execute-one! tx (sql/format query))]
            (log/debug "Association result:" result)
            (r/success true))
          (do
            (log/error "Comic or publisher not found. Comic exists:" comic-exists? "Publisher exists:" publisher-exists?)
            (r/failure (errors/system-error
                       :db-error
                       "연관관계 생성 실패"
                       "만화 또는 출판사가 존재하지 않습니다")))))
      (catch Exception e
        (log/error e "Failed to associate publisher with comic. Comic ID:" comic-id "Publisher ID:" publisher-id)
        (r/failure (errors/system-error
                   :db-error
                   (errors/get-system-message :db-error)
                   (.getMessage e)))))))

(defn- author->db [author]
  (when author
    {:name (if (record? (:name author))
             (get-in author [:name :value])
             (:name author))
     :type (name (:type author))
     :description (when-let [desc (:description author)]
                   (if (record? desc)
                     (get-in desc [:value])
                     desc))}))

(defn- db->author [row]
  (when row
    (author/->PersistedAuthor (:id row)
                             (:name row)
                             (keyword (:type row))
                             (:description row))))

(defrecord PostgresqlAuthorRepository [datasource]
  AuthorRepository

  (save-author [_ author]
    (try
      (let [tx (transaction/get-current-tx)
            db-author (author->db author)
            query {:insert-into :authors
                  :values [db-author]
                  :returning [:*]}
            result (jdbc/execute-one! tx
                                    (sql/format query)
                                    {:builder-fn rs/as-unqualified-maps})]
        (r/success (db->author result)))
      (catch Exception e
        (log/error e "Failed to save author:" author)
        (r/failure (errors/system-error
                   :db-error
                   (errors/get-system-message :db-error)
                   (.getMessage e))))))
  
  (find-author-by-id [_ id]
    (try
      (let [tx (transaction/get-current-tx)
            query {:select :*
                  :from :authors
                  :where [:= :id id]}
            result (jdbc/execute-one! tx
                                    (sql/format query)
                                    {:builder-fn rs/as-unqualified-maps})]
        (if result
          (r/success (db->author result))
          (r/failure (errors/business-error
                     :not-found
                     (errors/get-business-message :not-found)))))
      (catch Exception e
        (r/failure (errors/system-error
                   :db-error
                   (errors/get-system-message :db-error)
                   (.getMessage e))))))
  
    (find-authors-by-name [_ name]
    (try
      (let [tx (transaction/get-current-tx)
            query {:select :*
                  :from :authors
                  :where [:ilike :name (str "%" name "%")]}
            result (jdbc/execute! tx
                                (sql/format query)
                                {:builder-fn rs/as-unqualified-maps})]
        (r/success (map db->author result)))
      (catch Exception e
        (r/failure (errors/system-error
                   :db-error
                   (errors/get-system-message :db-error)
                   (.getMessage e))))))
    
      (find-authors-by-comic-id [_ comic-id]
    (try
      (let [tx (transaction/get-current-tx)
            query {:select [:a.* :ca.role]
                  :from [[:authors :a]]
                  :join [[:comic_authors :ca] [:= :a.id :ca.author_id]]
                  :where [:= :ca.comic_id comic-id]}
            result (jdbc/execute! tx
                                (sql/format query)
                                {:builder-fn rs/as-unqualified-maps})]
        (r/success (map #(assoc (db->author %) :role (keyword (:role %))) result)))
      (catch Exception e
        (r/failure (errors/system-error
                   :db-error
                   (errors/get-system-message :db-error)
                   (.getMessage e))))))
      
  (associate-author-with-comic [_ author-id comic-id role]
    (try
      (let [tx (transaction/get-current-tx)
            query {:insert-into :comic_authors
                  :values [{:comic_id comic-id
                           :author_id author-id
                           :role (name role)}]}
            _ (jdbc/execute-one! tx (sql/format query))]
        (r/success true))
      (catch Exception e
        (r/failure (errors/system-error
                    :db-error
                    (errors/get-system-message :db-error)
                    (.getMessage e))))))
  )

(defn create-repository []
  (if-let [ds (config/get-datasource)]  ;; get-datasource 함수 사용
    (->PostgresqlComicRepository ds)
    (throw (ex-info "데이터소스가 초기화되지 않았습니다."
                   {:type :db-error}))))

(defn create-publisher-repository []
  (if-let [ds (config/get-datasource)]
    (->PostgresqlPublisherRepository ds)
    (throw (ex-info "데이터소스가 초기화되지 않았습니다."
                   {:type :db-error}))))

(defn create-author-repository []
  (if-let [ds (config/get-datasource)]
    (->PostgresqlAuthorRepository ds)
    (throw (ex-info "데이터소스가 초기화되지 않았습니다."
                   {:type :db-error}))))