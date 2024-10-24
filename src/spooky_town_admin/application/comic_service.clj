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
    (let [result (image-storage/store-image image-storage image-data)]
      (if (:success result)
        {:success true
         :image-id (:image-id result)
         :metadata (:metadata result)}
        result))))

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
        
        (let [;; 이미지 처리 (있는 경우)
              image-result (when-let [image (:cover-image comic-data)]
                            (process-cover-image image-storage image))
              
              ;; 이미지 ID로 교체된 만화 데이터
              comic-with-image (if (and image-result (:success image-result))
                                (assoc comic-data :cover-image (:image-id image-result))
                                comic-data)
              
              ;; 도메인 워크플로우 실행
              workflow-result (workflow/create-comic-workflow comic-with-image)]
          
          (if (workflow/success? workflow-result)
            ;; 저장소에 저장
            (let [save-result (persistence/save-comic 
                             comic-repository 
                             (:value workflow-result))]
              (if (:success save-result)
                save-result
                ;; 실패시 이미지 삭제
                (do
                  (when (and image-result (:success image-result))
                    (image-storage/delete-image 
                     image-storage 
                     (:image-id image-result)))
                  save-result)))
            
            ;; 도메인 검증 실패
            {:success false
             :error (:error workflow-result)}))))))

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
