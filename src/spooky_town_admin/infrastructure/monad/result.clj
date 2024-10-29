(ns spooky-town-admin.infrastructure.monad.result
  (:refer-clojure :exclude [map sequence]))

(defrecord Result [success value error])

(defn success [value]
  (->Result true value nil))

(defn failure [error]
  (->Result false nil error))

(defn success? [result]
  (:success result))

(defn failure? [result]
  (not (success? result)))

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

;; 새로운 유틸리티 함수들
(defn sequence
  "Result 값들의 시퀀스를 단일 Result로 변환합니다.
   모든 값이 성공이면 success를 반환하고, 하나라도 실패하면 첫 번째 실패를 반환합니다."
  [results]
  (reduce (fn [acc result]
            (bind acc
                  (fn [values]
                    (bind result
                          (fn [value]
                            (success (conj values value)))))))
          (success [])
          results))

(defn traverse
  "시퀀스의 각 요소에 Result를 반환하는 함수를 적용하고,
   결과를 단일 Result로 변환합니다."
  [f coll]
  (sequence (mapv f coll)))

;; Result와 일반 맵 간의 변환 함수
(defn to-map [result]
  (if (success? result)
    {:success true
     :value (:value result)}
    {:success false
     :error (if (record? (:error result))
              (into {} (:error result))
              (:error result))}))

(defn from-map [m]
  (if (:success m)
    (success (:value m))
    (failure (:error m))))

;; 테스트를 위한 헬퍼 함수들
(defn unwrap
  "Result에서 값을 추출합니다. 실패인 경우 예외를 발생시킵니다."
  [result]
  (if (success? result)
    (:value result)
    (throw (ex-info "Failed to unwrap Result" {:error (:error result)}))))

(defn unwrap-or
  "Result에서 값을 추출합니다. 실패인 경우 기본값을 반환합니다."
  [result default]
  (if (success? result)
    (:value result)
    default)) 