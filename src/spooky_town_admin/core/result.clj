(ns spooky-town-admin.core.result
  (:refer-clojure :exclude [map]))  ;; clojure.core의 map과 충돌 방지

(defrecord Result [success value error])

(defn success [value]
  (->Result true value nil))

(defn failure [error]
  (->Result false nil error))

(defn success? [result]
  (:success result))

(defn failure? [result]
  (not (:success result)))

(defn value [result]
  (:value result))

(defn error [result]
  (:error result))

(defn bind [result f]
  (if (success? result)
    (f (value result))
    result))

(defn map [result f]
  (if (success? result)
    (success (f (value result)))
    result))

(defn map-error [result f]
  (if (success? result)
    result
    (failure (f (:error result)))))

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