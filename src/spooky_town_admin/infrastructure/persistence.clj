(ns spooky-town-admin.infrastructure.persistence
  (:require [spooky-town-admin.infrastructure.persistence.protocol :as protocol]
            [spooky-town-admin.infrastructure.persistence.in-memory :as in-memory]
            [spooky-town-admin.infrastructure.persistence.postgresql :as postgresql]))

(defn create-comic-repository
  "환경에 따른 저장소 인스턴스 생성"
  [env]
  (case env
    :test (in-memory/create-repository)
    :prod (postgresql/create-repository)
    ;; 기본값은 인메모리 저장소
    (in-memory/create-repository)))

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

;; 트랜잭션 관리 (향후 실제 DB 사용 시 확장)
(defmacro with-transaction [& body]
  `(do ~@body))