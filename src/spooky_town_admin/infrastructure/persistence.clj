(ns spooky-town-admin.infrastructure.persistence
  (:require [spooky-town-admin.infrastructure.persistence.protocol :as protocol]
            [spooky-town-admin.infrastructure.persistence.in-memory :as in-memory]
            [spooky-town-admin.infrastructure.persistence.postgresql :as postgresql]
            [environ.core :refer [env]]
            [spooky-town-admin.infrastructure.persistence.transaction :refer [with-transaction]]))

(defn create-comic-repository
  "환경에 따른 저장소 인스턴스 생성"
  []
  (case (keyword (env :environment))
    :test (in-memory/create-repository)
    :prod (postgresql/create-repository)
    ;; 기본값은 인메모리 저장소
    (postgresql/create-repository)))

(defn create-publisher-repository []
  (case (keyword (env :environment))
    :test (in-memory/create-publisher-repository)
    :prod (postgresql/create-publisher-repository)
    (postgresql/create-publisher-repository)))

;; 프로토콜 메서드를 외부에서 호출할 수 있는 함수들
(defn save-comic [repo comic]
  (protocol/save-comic repo comic))

(defn find-comic-by-id [repo id]
  (protocol/find-comic-by-id repo id))

(defn find-comic-by-isbn [repo isbn]
  (protocol/find-comic-by-isbn repo isbn))

(defn delete-comic [repo id]
  (protocol/delete-comic repo id))

(defn list-comics [repo]
  (protocol/list-comics repo))

;; Publisher 관련 함수들 추가
(defn save-publisher [repo publisher]
  (protocol/save-publisher repo publisher))

(defn find-publisher-by-id [repo id]
  (protocol/find-publisher-by-id repo id))

(defn find-comic-by-isbns [repo isbn13 isbn10]
  (protocol/find-comic-by-isbns repo isbn13 isbn10))

(defn find-publisher-by-name [repo name]
  (protocol/find-publisher-by-name repo name))

(defn find-publishers-by-comic-id [repo comic-id]
  (protocol/find-publishers-by-comic-id repo comic-id))

(defn associate-publisher-with-comic [repo comic-id publisher-id]
  (protocol/associate-publisher-with-comic repo comic-id publisher-id))
