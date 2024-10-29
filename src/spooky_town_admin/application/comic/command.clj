(ns spooky-town-admin.application.comic.command
  (:require
   [clojure.tools.logging :as log]
   [spooky-town-admin.domain.comic.errors :as errors]
   [spooky-town-admin.domain.comic.workflow :as workflow]
   [spooky-town-admin.core.result :as r]
   [spooky-town-admin.infrastructure.persistence :as persistence]
   [spooky-town-admin.infrastructure.persistence.transaction :refer [with-transaction]]))

(defn- check-duplicate-isbn [comic-repository comic-data]
  (log/debug "Checking duplicate ISBN for:" comic-data)
  (let [isbn (or (:isbn13 comic-data) (:isbn10 comic-data))
        result (persistence/find-comic-by-isbn comic-repository isbn)]
    (if (and (r/success? result) 
             (some? (r/value result)))
      (do
        (log/info "Duplicate ISBN found")
        (r/failure (errors/business-error
                    :duplicate-isbn
                    (errors/get-business-message :duplicate-isbn))))
      (r/success comic-data))))

(defn- save-comic-with-publisher [comic-repository publisher-repository comic image-url]
  (try
    (with-transaction
      (let [;; 1. 출판사 정보가 있는 경우에만 저장/조회
            publisher-result (when-let [publisher-name (get-in comic [:publisher :value])]
                             (log/debug "Saving publisher:" publisher-name)
                             (persistence/save-publisher 
                              publisher-repository 
                              {:name publisher-name}))
            _ (when publisher-result
                (log/debug "Publisher saved:" (r/value publisher-result)))
            ;; 2. 만화 저장 (publisher 필드 제외)
            comic-result (persistence/save-comic 
                         comic-repository 
                         (-> comic
                             (update-in [:title :value] str)
                             (update-in [:artist :value] str)
                             (update-in [:author :value] str)
                             (update-in [:isbn13 :value] str)
                             (update-in [:isbn10 :value] str)
                             (update-in [:price :value] identity)
                             (dissoc :publisher)
                             (assoc :image-url image-url)))]
        (if (r/success? comic-result)
          (do
            ;; 3. 출판사가 있고 저장이 성공한 경우에만 연관 관계 생성
            (when (and publisher-result 
                      (r/success? publisher-result))
              (let [comic-id (get-in comic-result [:value :id])
                    publisher-id (get-in publisher-result [:value :id])]
                (log/debug "Creating association between comic" comic-id "and publisher" publisher-id)
                (persistence/associate-publisher-with-comic 
                 publisher-repository
                 comic-id
                 publisher-id)))
            ;; 4. 만화 저장 결과 반환
            comic-result)
          comic-result)))  ;; 실패한 경우 그대로 반환
    (catch Exception e
      (log/error e "Failed to save comic with publisher")
      (r/failure (errors/system-error 
                  :db-error 
                  (errors/get-system-message :db-error)
                  (.getMessage e))))))

(defn create-comic [{:keys [comic-repository publisher-repository image-storage] :as service} 
                    comic-data]
  (with-transaction
    (log/debug "Transaction started" (pr-str comic-data))
    (-> (check-duplicate-isbn comic-repository comic-data)
        (r/bind #(workflow/create-comic-workflow image-storage %))
        (r/bind (fn [{:keys [comic image-url]}]
                  (save-comic-with-publisher comic-repository publisher-repository comic image-url)))))) 