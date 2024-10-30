(ns spooky-town-admin.application.comic.command
  (:require
   [clojure.tools.logging :as log]
   [spooky-town-admin.core.result :as r]
   [spooky-town-admin.domain.comic.errors :as errors]
   [spooky-town-admin.domain.comic.publisher :as publisher]
   [spooky-town-admin.domain.comic.workflow :as workflow]
   [spooky-town-admin.infrastructure.image-storage :as image-storage]
   [spooky-town-admin.infrastructure.persistence :as persistence]
   [spooky-town-admin.infrastructure.persistence.transaction :refer [with-transaction]]))

(defn- check-duplicate-isbn [comic-repository comic-data]
  (log/debug "Checking duplicate ISBN for:" comic-data)
  (let [isbn13-value (when-let [isbn13 (:isbn13 comic-data)]
                      (if (record? isbn13)
                        (:value isbn13)
                        isbn13))
        isbn10-value (when-let [isbn10 (:isbn10 comic-data)]
                      (if (record? isbn10)
                        (:value isbn10)
                        isbn10))
        _ (log/debug "Extracted ISBNs - ISBN13:" isbn13-value "ISBN10:" isbn10-value)
        result (persistence/find-comic-by-isbns comic-repository isbn13-value isbn10-value)]
    (if (and (r/success? result) 
             (some? (r/value result)))
      (do
        (log/info "Duplicate ISBN found - ISBN13:" isbn13-value "ISBN10:" isbn10-value)
        (r/failure (errors/business-error
                    :duplicate-isbn
                    (errors/get-business-message :duplicate-isbn))))
      (r/success comic-data))))

(defn- save-comic-with-publisher [comic-repository publisher-repository comic image-url validated-publisher]
  (if validated-publisher
    (let [publisher-name (publisher/get-name validated-publisher)]
      (-> (persistence/find-publisher-by-name publisher-repository publisher-name)
          (r/bind (fn [existing-publisher]
                   (if existing-publisher
                     (r/success existing-publisher)
                     (persistence/save-publisher 
                      publisher-repository 
                      validated-publisher))))
          (r/bind (fn [publisher-result]
                   (when (nil? (:id publisher-result))
                     (throw (ex-info "출판사 저장 실패" 
                                   {:error (errors/system-error
                                           :publisher-save-error
                                           "출판사 저장에 실패했습니다"
                                           "출판사 ID가 생성되지 않았습니다")})))
                   (-> (persistence/save-comic 
                        comic-repository 
                        (assoc comic :image-url image-url))
                       (r/bind (fn [comic-result]
                                (when (nil? (:id comic-result))
                                  (throw (ex-info "만화 저장 실패"
                                                {:error (errors/system-error
                                                        :comic-save-error
                                                        "만화 저장에 실패했습니다"
                                                        "만화 ID가 생성되지 않았습니다")})))
                                (-> (persistence/associate-publisher-with-comic 
                                     publisher-repository
                                     (:id comic-result)
                                     (:id publisher-result))
                                    (r/bind (fn [_]
                                            (log/debug "Successfully associated publisher" (:id publisher-result) 
                                                      "with comic" (:id comic-result))
                                            (r/success comic-result)))))))))))
    ;; 출판사가 없는 경우 만화만 저장
    (persistence/save-comic comic-repository 
                          (assoc comic :image-url image-url))))

(defn create-comic [service comic-data]
  (with-transaction
    (try
      (let [comic-repository (persistence/create-comic-repository)
            publisher-repository (persistence/create-publisher-repository)
            image-storage (image-storage/create-image-storage service)
              publisher-validation (when-let [publisher-name (:publisher comic-data)]
                                 (publisher/create-validated-publisher {:name publisher-name}))]
        (if (and publisher-validation (r/failure? publisher-validation))
          publisher-validation
          (-> (check-duplicate-isbn comic-repository comic-data)
              (r/bind #(workflow/create-comic-workflow image-storage %))
              (r/bind (fn [{:keys [comic events]}]
                       (save-comic-with-publisher comic-repository 
                                                publisher-repository 
                                                comic 
                                                (:cover-image-url events)
                                                (when publisher-validation 
                                                  (r/value publisher-validation))))))))
      (catch Exception e
        (log/error e "Failed to create comic with publisher")
        (if-let [error (get-in (ex-data e) [:error])]
          (r/failure error)
          (r/failure (errors/system-error 
                     :db-error 
                     (errors/get-system-message :db-error)
                     (.getMessage e))))))))