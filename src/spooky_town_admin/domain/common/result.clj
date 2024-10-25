(ns spooky-town-admin.domain.common.result
  (:refer-clojure :exclude [map]))  ;; clojure.core의 map과 충돌 방지

(defrecord Result [success value error])

(defn success [value]
  (->Result true value nil))

(defn failure [error]
  (->Result false nil error))

(defn success? [result]
  (:success result))

(defn bind [result f]
  (if (success? result)
    (f (:value result))
    result))

(defn map [result f]
  (if (success? result)
    (success (f (:value result)))
    result))

(defn map-error [result f]
  (if (success? result)
    result
    (failure (f (:error result)))))

;; Result와 일반 맵 간의 변환 함수 추가
(defn to-map [result]
  (if (success? result)
    {:success true
     :value (:value result)}
    {:success false
     :error (:error result)}))

(defn from-map [m]
  (if (:success m)
    (success (:value m))
    (failure (:error m))))