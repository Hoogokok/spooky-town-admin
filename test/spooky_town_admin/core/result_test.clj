(ns spooky-town-admin.core.result-test
  (:require [clojure.test :refer [deftest testing is]]
            [spooky-town-admin.core.result :as r]))

(deftest success-test
  (testing "success creates successful result"
    (let [result (r/success 42)]
      (is (r/success? result))
      (is (= 42 (r/value result)))
      (is (nil? (r/error result)))))

  (testing "success with nil value"
    (let [result (r/success nil)]
      (is (r/success? result))
      (is (nil? (r/value result)))
      (is (nil? (r/error result))))))

(deftest failure-test
  (testing "failure creates failed result"
    (let [error {:type :test-error}
          result (r/failure error)]
      (is (not (r/success? result)))
      (is (nil? (r/value result)))
      (is (= error (r/error result))))))

(deftest bind-test
  (testing "bind with success"
    (let [result (-> (r/success 1)
                     (r/bind #(r/success (inc %))))]
      (is (r/success? result))
      (is (= 2 (r/value result)))))

  (testing "bind with failure"
    (let [error {:type :test-error}
          result (-> (r/failure error)
                     (r/bind #(r/success (inc %))))]
      (is (not (r/success? result)))
      (is (= error (r/error result))))))

(deftest map-test
  (testing "map with success"
    (let [result (-> (r/success 1)
                     (r/map inc))]
      (is (r/success? result))
      (is (= 2 (r/value result)))))

  (testing "map with failure"
    (let [error {:type :test-error}
          result (-> (r/failure error)
                     (r/map inc))]
      (is (not (r/success? result)))
      (is (= error (r/error result))))))

(deftest to-map-test
  (testing "converts success result to map"
    (let [result (r/success 42)
          m (r/to-map result)]
      (is (:success m))
      (is (= 42 (:value m)))
      (is (nil? (:error m)))))

  (testing "converts failure result to map"
    (let [error {:type :test-error}
          result (r/failure error)
          m (r/to-map result)]
      (is (not (:success m)))
      (is (nil? (:value m)))
      (is (= error (:error m)))))) 