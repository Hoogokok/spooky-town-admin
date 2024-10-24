(ns spooky-town-admin.validation)

(defn success [value]
  {:success true :value value})

(defn failure [error]
  {:success false :error error})

(defn success? [result]
  (:success result))

(defn validate-length [field-name min max]
  (fn [v]
    (if (and (string? v) (<= min (count v) max))
      (success v)
      (failure {field-name (str field-name "의 길이는 " min "에서 " max " 사이여야 합니다.")}))))

(def validate-title (validate-length "제목" 1 100))

(defn validate-comic [comic]
  (let [title-result (validate-title (:title comic))]
    (if (success? title-result)
      (success (assoc comic :title (:value title-result)))
      (failure (:error title-result)))))
