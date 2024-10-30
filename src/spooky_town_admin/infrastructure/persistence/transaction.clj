(ns spooky-town-admin.infrastructure.persistence.transaction
  (:require [next.jdbc :as jdbc]
            [spooky-town-admin.core.result :as r]
            [spooky-town-admin.domain.comic.errors :as errors]
            [spooky-town-admin.infrastructure.persistence.config :as config]
            [clojure.tools.logging :as log]))

(def ^:dynamic *current-tx* nil)

(defn with-transaction* [f]
  (log/debug "Starting new database transaction")
  (if-let [ds (config/get-datasource)]
    (try 
      (jdbc/with-transaction [tx ds]
        (binding [*current-tx* tx]
          (log/debug "Transaction started")
          (let [result (f tx)]
            (log/debug "Transaction completed with result:" result)
            result)))
      (catch Exception e
        (log/error e "Transaction failed")
        (r/failure
          (errors/system-error
            :db-error
            "트랜잭션 실행 중 오류 발생"
            (str "상세 오류: " (.getMessage e))))))
    (do 
      (log/error "Failed to start transaction: No datasource available")
      (r/failure
        (errors/system-error
          :db-error
          "트랜잭션 시작 실패"
          "데이터소스가 초기화되지 않았습니다")))))

(defmacro with-transaction [& body]
  `(if-let [datasource# (config/get-datasource)]
     (try
       (jdbc/with-transaction [tx# datasource# {:isolation :serializable}]
         (binding [*current-tx* tx#]
           ~@body))
       (catch Exception e#
         (log/error e# "Transaction failed")
         (r/failure (errors/system-error
                    :db-error
                    (errors/get-system-message :db-error)
                    (.getMessage e#)))))
     (r/failure (errors/system-error
                :db-error
                "데이터소스가 초기화되지 않았습니다."))))

(defn get-current-tx []
  (or *current-tx* 
      (config/get-datasource)))
