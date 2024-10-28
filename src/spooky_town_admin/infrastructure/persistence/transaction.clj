(ns spooky-town-admin.infrastructure.persistence.transaction
  (:require [next.jdbc :as jdbc]
            [spooky-town-admin.domain.common.result :as r]
            [spooky-town-admin.domain.comic.errors :as errors]
            [spooky-town-admin.infrastructure.persistence.config :as config]))

(defn with-transaction* [f]
  (try
    (if-let [ds @config/datasource]
      (jdbc/with-transaction [tx ds]
        (f tx))
      (r/failure
        (errors/system-error
          :db-error
          "트랜잭션 시작 실패"
          "데이터소스가 초기화되지 않았습니다")))
    (catch Exception e
      (r/failure
        (errors/system-error
          :db-error
          "트랜잭션 실행 중 오류 발생"
          (.getMessage e))))))

(defmacro with-transaction [binding & body]
  `(with-transaction* (fn [~(first binding)]
                       ~@body)))
