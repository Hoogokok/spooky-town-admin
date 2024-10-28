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

(defn map
  "Result 모나드의 값을 변환하는 함수
   result: Result 인스턴스
   f: 변환 함수"
  [result f]  ;; 파라미터 순서 명확히 지정
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
     :error (if (record? (:error result))  ;; record인 경우 map으로 변환
             (into {} (:error result))
             (:error result))}))

(defn from-map [m]
  (if (:success m)
    (success (:value m))
    (failure (:error m))))