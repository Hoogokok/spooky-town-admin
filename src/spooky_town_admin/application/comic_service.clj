(ns spooky-town-admin.application.comic-service
  (:require
   [next.jdbc.connection :as connection]
   [clojure.tools.logging :as log]
   [spooky-town-admin.domain.comic.errors :as errors]
   [spooky-town-admin.domain.comic.workflow :as workflow]
   [spooky-town-admin.core.result :as r]
   [spooky-town-admin.infrastructure.image-storage :as image-storage]
   [spooky-town-admin.infrastructure.persistence :as persistence]
   [spooky-town-admin.infrastructure.persistence.transaction :refer [with-transaction]]))

(defrecord ComicService [comic-repository image-storage])
;; 만화 생성 관련 함수들
(defn- check-duplicate-isbn [comic-repository comic-data]
  (log/debug "Checking duplicate ISBN for:" comic-data)
  (let [isbn (or (:isbn13 comic-data) (:isbn10 comic-data))
        result (persistence/find-comic-by-isbn comic-repository isbn)]
    (if (and (r/success? result) 
             (some? (r/value result)))  ;; 이제 r/value 함수 사용 가능
      (do
        (log/info "Duplicate ISBN found")
        (r/failure (errors/business-error
                    :duplicate-isbn
                    (errors/get-business-message :duplicate-isbn))))
      (r/success comic-data))))

(defn- save-comic [comic-repository comic image-url]
  (try
    (let [persisted (persistence/save-comic comic-repository 
                     (-> comic
                         (update :title str)  ;; Title 레코드를 문자열로 변환
                         (update :artist str)  ;; Artist 레코드를 문자열로 변환
                         (update :author str)  ;; Author 레코드를 문자열로 변환
                         (update :isbn13 str)  ;; ISBN13 레코드를 문자열로 변환
                         (update :isbn10 str)  ;; ISBN10 레코드를 문자열로 변환
                         (assoc :image-url image-url)))]
      (r/success persisted))
    (catch Exception e
      (r/failure (errors/system-error :db-error 
                                    (errors/get-system-message :db-error)
                                    (.getMessage e))))))

(defn create-comic [{:keys [comic-repository image-storage] :as service} comic-data]
  (with-transaction
    (log/debug "Transaction started" (pr-str comic-data))
    (-> (check-duplicate-isbn comic-repository comic-data)
        (r/bind #(workflow/create-comic-workflow image-storage %))
        (r/bind (fn [{:keys [comic]}]
                  (persistence/save-comic comic-repository comic))))))

;; 만화 조회 서비스
(defn get-comic [{:keys [comic-repository]} id]
  (-> (if-let [comic (persistence/find-comic-by-id comic-repository id)]
        (r/success comic)
        (r/failure (errors/business-error :not-found 
                                        (errors/get-business-message :not-found))))
      (r/map #(select-keys % [:id :title :artist :author :isbn13 :isbn10]))
      r/to-map))

;; 만화 목록 조회 서비스
(defn list-comics [{:keys [comic-repository]}]
  (let [comics (persistence/list-comics comic-repository)]
    {:success true
     :comics (map #(select-keys % [:id :title :artist :author :isbn13 :isbn10])
                  comics)}))

;; 서비스 인스턴스 생성
(defn create-comic-service [env]
  (->ComicService 
   (persistence/create-comic-repository)
   (image-storage/create-image-storage env )))
