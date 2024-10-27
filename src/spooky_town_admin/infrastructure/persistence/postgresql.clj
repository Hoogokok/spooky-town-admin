(ns spooky-town-admin.infrastructure.persistence.postgresql
  (:require [spooky-town-admin.infrastructure.persistence.protocol :refer [ComicRepository]]
            [spooky-town-admin.domain.comic.errors :as errors]))

(defrecord PostgreSQLComicRepository []
  ComicRepository
  (save-comic [_ comic]
    (throw (ex-info "PostgreSQL 저장소는 아직 구현되지 않았습니다." {})))
  
  (find-comic-by-id [_ id]
    (throw (ex-info "PostgreSQL 저장소는 아직 구현되지 않았습니다." {})))
  
  (find-comic-by-isbn [_ isbn]
    (throw (ex-info "PostgreSQL 저장소는 아직 구현되지 않았습니다." {})))
  
  (delete-comic [_ id]
    (throw (ex-info "PostgreSQL 저장소는 아직 구현되지 않았습니다." {})))
  
  (list-comics [_]
    (throw (ex-info "PostgreSQL 저장소는 아직 구현되지 않았습니다." {}))))

(defn create-repository []
  (->PostgreSQLComicRepository))