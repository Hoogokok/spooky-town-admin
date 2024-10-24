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
(def validate-artist (validate-length "그림 작가" 1 20))
(def validate-author (validate-length "글 작가" 1 20))

(defn validate-comic [comic]
  (let [title-result (validate-title (:title comic))
        artist-result (validate-artist (:artist comic))
        author-result (validate-author (:author comic))]
    (cond
      (not (success? title-result)) (failure (:error title-result))
      (not (success? artist-result)) (failure (:error artist-result))
      (not (success? author-result)) (failure (:error author-result))
      :else (success (assoc comic 
                            :title (:value title-result)
                            :artist (:value artist-result)
                            :author (:value author-result))))))
