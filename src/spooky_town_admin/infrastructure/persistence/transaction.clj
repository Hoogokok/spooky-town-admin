(ns spooky-town-admin.infrastructure.persistence.transaction
  (:require [next.jdbc :as jdbc]
            [spooky-town-admin.domain.common.result :as r]
            [spooky-town-admin.domain.comic.errors :as errors]
            [spooky-town-admin.infrastructure.persistence.config :as config]))

(def ^:dynamic *current-tx* nil)

(defn with-transaction* [f]
  (println "Starting transaction with function:" f)
  (if-let [ds (config/get-datasource)]
    (try 
      (jdbc/with-transaction [tx ds]
        (binding [*current-tx* tx]
          (println "Transaction started with connection:" tx)
          (let [result (f tx)]
            (println "Transaction execution result:" result)
            result)))
      (catch Exception e
        (println "Transaction failed:" (.getMessage e))
        (.printStackTrace e)
        (r/failure
          (errors/system-error
            :db-error
            "트랜잭션 실행 중 오류 발생"
            (str "상세 오류: " (.getMessage e))))))
    (do 
      (println "No datasource available")
      (r/failure
        (errors/system-error
          :db-error
          "트랜잭션 시작 실패"
          "데이터소스가 초기화되지 않았습니다")))))

(defmacro with-transaction [& body]
  `(with-transaction* 
     (fn [tx#]
       (binding [*current-tx* tx#]
         (println "Executing transaction body with tx:" tx#)
         ~@body))))

(defn get-current-tx []
  (or *current-tx* 
      (config/get-datasource)))
