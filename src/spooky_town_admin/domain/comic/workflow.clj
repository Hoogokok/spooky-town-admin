(ns spooky-town-admin.domain.comic.workflow
  (:require [spooky-town-admin.domain.comic.types :as types]
            [spooky-town-admin.domain.comic.errors :as errors]
            [clojure.spec.alpha :as s]))

;; 결과 래퍼 타입
(defrecord Success [value])
(defrecord Failure [error])

(defn success [value]
  (->Success value))

(defn failure [error]
  (->Failure error))

(defn success? [result]
  (instance? Success result))

;; 이미지 검증 워크플로우
(defn validate-image-constraints [image]
  (let [constraints [{:check #(contains? #{"image/jpeg" "image/png"} (:content-type %))
                      :error-type :type}
                     {:check #(and (<= (:width %) 1000) (<= (:height %) 1500))
                      :error-type :dimensions}
                     {:check #(<= (* (:width %) (:height %)) 100000000)
                      :error-type :area}
                     {:check #(<= (:size %) (* 10 1024 1024))
                      :error-type :size}]]
    (if-let [failed-constraint (first (filter #(not ((:check %) image)) constraints))]
      (failure (errors/validation-error :cover-image 
                                      (errors/get-image-error-message (:error-type failed-constraint))))
      (success image))))

;; 만화 생성 워크플로우
(defn create-comic-workflow [{:keys [title artist author isbn13 isbn10 
                                    publication-date publisher price 
                                    page-count description cover-image] :as comic-data}]
  (let [;; 필수 필드 검증
        required-fields {:title title
                        :artist artist
                        :author author
                        :isbn13 isbn13
                        :isbn10 isbn10}
        
        ;; 선택적 필드 검증
        optional-fields (cond-> {}
                         publication-date (assoc :publication-date publication-date)
                         publisher (assoc :publisher publisher)
                         price (assoc :price price)
                         page-count (assoc :page-count page-count)
                         description (assoc :description description))]
    
    ;; 필수 필드가 모두 존재하는지 검증
    (if-not (every? some? (vals required-fields))
      (failure (errors/validation-error :required-fields "필수 필드가 누락되었습니다."))
      
      ;; spec 검증
      (if-not (s/valid? ::types/isbn13 isbn13)
        (failure (errors/validation-error :isbn13 (errors/get-validation-message :isbn13)))
        (if-not (s/valid? ::types/isbn10 isbn10)
          (failure (errors/validation-error :isbn10 (errors/get-validation-message :isbn10)))
          
          ;; 이미지 검증 (있는 경우에만)
          (if cover-image
            (let [image-result (validate-image-constraints cover-image)]
              (if (success? image-result)
                (success (types/create-comic required-fields 
                                           (assoc optional-fields :cover-image (:value image-result))))
                image-result))
            
            ;; 이미지가 없는 경우
            (success (types/create-comic required-fields optional-fields))))))))

;; 만화 수정 워크플로우 (향후 구현)
(defn update-comic-workflow [id comic-data]
  ;; TODO: 구현 예정
  )

;; 만화 삭제 워크플로우 (향후 구현)
(defn delete-comic-workflow [id]
  ;; TODO: 구현 예정
  )
