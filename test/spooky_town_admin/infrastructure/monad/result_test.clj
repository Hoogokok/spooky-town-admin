(ns spooky-town-admin.infrastructure.monad.result-test
  (:require [clojure.test :refer [deftest testing is]]
            [spooky-town-admin.infrastructure.monad.result :as r]))

(deftest result-basic-operations
  (testing "success creation and unwrapping"
    (let [result (r/success 42)]
      (is (r/success? result))
      (is (= 42 (r/unwrap result)))))
  
  (testing "failure creation"
    (let [result (r/failure "error")]
      (is (r/failure? result))
      (is (thrown? clojure.lang.ExceptionInfo (r/unwrap result)))
      (is (= "default" (r/unwrap-or result "default")))))
  
  (testing "map operation"
    (is (= 43 (r/unwrap (r/map (r/success 42) inc))))
    (is (r/failure? (r/map (r/failure "error") inc))))
  
  (testing "bind operation"
    (is (= 43 (r/unwrap (r/bind (r/success 42) #(r/success (inc %))))))
    (is (r/failure? (r/bind (r/failure "error") #(r/success (inc %)))))))

(deftest result-sequence-operations
  (testing "sequence with all successes"
    (let [results [(r/success 1) (r/success 2) (r/success 3)]
          combined (r/sequence results)]
      (is (r/success? combined))
      (is (= [1 2 3] (r/unwrap combined)))))
  
  (testing "sequence with one failure"
    (let [results [(r/success 1) (r/failure "error") (r/success 3)]
          combined (r/sequence results)]
      (is (r/failure? combined))
      (is (= "error" (:error combined)))))
  
  (testing "traverse operation"
    (let [numbers [1 2 3]
          success-fn #(r/success (inc %))
          failure-fn (fn [x] (if (= x 2)
                             (r/failure "error")
                             (r/success (inc x))))]
      (is (= [2 3 4] (r/unwrap (r/traverse success-fn numbers))))
      (is (r/failure? (r/traverse failure-fn numbers)))))) 