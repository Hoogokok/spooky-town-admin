(ns spooky-town-admin.application.comic-service
  (:require [spooky-town-admin.domain.comic.workflow :as workflow]
            [spooky-town-admin.domain.comic.errors :as errors]
            [spooky-town-admin.infrastructure.persistence :as persistence]
            [spooky-town-admin.infrastructure.image-storage :as image-storage]))

;; 서비스 상태 (의존성 주입을 위한)
(defrecord ComicService [comic-repository image-storage])

;; 이미지 처리 헬퍼 함수
(defn- process-cover-image [image-storage image-data]
  (when image-data
    (let [metadata-result (image-storage/extract-image-metadata image-data)]
      (println "Metadata result:" metadata-result)  ;; 디버깅용
      (if (:success metadata-result)
        (let [constraints-result (workflow/validate-image-constraints (:metadata metadata-result))]
          (println "Constraints result:" constraints-result)  ;; 디버깅용
          (if (workflow/success? constraints-result)
            (let [store-result (image-storage/store-image image-storage image-data)]
              (println "Store result:" store-result)  ;; 디버깅용
              (if (:success store-result)
                {:success true
                 :image-id (:image-id store-result)
                 :metadata (:value constraints-result)}  ;; Success 레코드에서 값 추출
                {:success false
                 :error (errors/validation-error :cover-image 
                                              (errors/get-image-error-message :invalid))}))
            {:success false 
             :error (:error constraints-result)}))  ;; Failure 레코드에서 에러 추출
        {:success false 
         :error (errors/validation-error :cover-image 
                                       (errors/get-image-error-message :invalid))}))))

;; 만화 생성 서비스
(defn create-comic [{:keys [comic-repository image-storage]} comic-data]
  (persistence/with-transaction
    (let [;; ISBN 중복 체크
          existing-comic (persistence/find-comic-by-isbn 
                         comic-repository 
                         (or (:isbn13 comic-data) (:isbn10 comic-data)))]
      
      (if existing-comic
        {:success false
         :error (errors/business-error :duplicate-isbn 
                                     (errors/get-business-message :duplicate-isbn))}
        
        ;; 이미지 처리 및 만화 생성
        (if-let [image (:cover-image comic-data)]
          ;; 이미지가 있는 경우
          (let [image-result (process-cover-image image-storage image)]
            (if (:success image-result)
              ;; 이미지 처리 성공
              (let [comic-with-image (assoc comic-data 
                                          :cover-image (:image-id image-result)
                                          :cover-image-metadata (:metadata image-result))
                    workflow-result (workflow/create-comic-workflow comic-with-image)]
                (if (workflow/success? workflow-result)
                  (persistence/save-comic comic-repository (:value workflow-result))
                  {:success false :error (:error workflow-result)}))
              ;; 이미지 처리 실패
              image-result))
          
          ;; 이미지가 없는 경우
          (let [workflow-result (workflow/create-comic-workflow comic-data)]
            (if (workflow/success? workflow-result)
              (persistence/save-comic comic-repository (:value workflow-result))
              {:success false :error (:error workflow-result)})))))))

;; 만화 조회 서비스
(defn get-comic [{:keys [comic-repository]} id]
  (if-let [comic (persistence/find-comic-by-id comic-repository id)]
    {:success true :comic comic}
    {:success false
     :error (errors/business-error :not-found "만화를 찾을 수 없습니다.")}))

;; 만화 목록 조회 서비스
(defn list-comics [{:keys [comic-repository]}]
  {:success true
   :comics (persistence/list-comics comic-repository)})

;; 서비스 인스턴스 생성
(defn create-comic-service [env config]
  (->ComicService 
   (persistence/create-comic-repository)
   (image-storage/create-image-storage env config)))