(ns spooky-town-admin.domain.common.result)

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

(defn map-error [result f]
  (if (success? result)
    result
    (failure (f (:error result)))))

(defn lift [f]
  (fn [value]
    (try
      (success (f value))
      (catch Exception e
        (failure e)))))