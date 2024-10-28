(ns spooky-town-admin.infrastructure.persistence.transaction
  (:require [next.jdbc :as jdbc]
            [spooky-town-admin.domain.common.result :as r]
            [spooky-town-admin.domain.comic.errors :as errors]
            [spooky-town-admin.infrastructure.persistence.config :as config]))

(defn with-transaction* [f]
  (println "Starting transaction with function:" f)
  (if-let [ds (config/get-datasource)]
    (try 
      (let [result (jdbc/with-transaction [tx ds]
                     (println "Transaction started with connection:" tx)
                     (let [execution-result (f tx)]
                       (println "Transaction execution result:" execution-result)
                       execution-result))]
        (println "Final transaction result:" result)
        result)
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
       (println "Executing transaction body with tx:" tx#)
       ~@body)))
