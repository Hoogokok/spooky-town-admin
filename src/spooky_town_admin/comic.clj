(ns spooky-town-admin.comic
  (:require [spooky-town-admin.validation :as validation]
            [spooky-town-admin.image :as image]
            [spooky-town-admin.db :as db]))

(defn create-comic [comic-data]
  (let [validation-result (validation/validate-comic comic-data)]
    (if (:success validation-result)
      (let [validated-comic (:value validation-result)]
        (if-let [cover-image (:cover-image validated-comic)]
          ;; 이미지가 있는 경우
          (let [upload-result (image/upload-to-cdn cover-image)]
            (if (:success upload-result)
              (let [comic-with-image (assoc validated-comic :cover-image (:image-id upload-result))
                    db-result (db/add-comic comic-with-image)]
                (if (:success db-result)
                  {:success true :id (:id db-result)}
                  {:success false :error (:error db-result) :details (:details db-result)}))
              {:success false :error "이미지 업로드 실패" :details (:error upload-result)}))
          ;; 이미지가 없는 경우
          (let [db-result (db/add-comic validated-comic)]
            (if (:success db-result)
              {:success true :id (:id db-result)}
              {:success false :error (:error db-result) :details (:details db-result)}))))
      {:success false :error "유효성 검사 실패" :details (:error validation-result)})))
