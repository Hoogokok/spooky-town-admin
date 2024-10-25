(ns spooky-town-admin.components.comic-form
  (:require [reagent.core :as r]
            [ajax.core :refer [POST]]))
(def api-base-url "http://localhost:3000")  ;; 추가

(def initial-form-state
  {:title ""
   :artist ""
   :author ""
   :isbn13 ""
   :isbn10 ""
   :publisher ""
   :publication-date ""
   :price nil
   :page-count nil
   :description ""
   :cover-image nil})

(defn create-form-data [data]
  (let [form-data (js/FormData.)]
    (println "Creating form data from:" data)  ;; 로깅 추가
    ;; 일반 필드들 추가
    (doseq [[key value] (dissoc data :cover-image)]
      (when (some? value)
        (println "Appending field:" key "with value:" value)  ;; 로깅 추가
        (.append form-data (name key) value)))
    ;; 파일 필드 추가
    (when-let [file (:cover-image data)]
      (println "Appending file field:" "cover-image" "with value:" file)  ;; 로깅 추가
      (.append form-data "cover-image" file))
    form-data))

(defn format-error-message [{:keys [error message field]}]
  (case error
    "ValidationError" (str (case field
                           :title "제목"
                           :artist "작가"
                           :author "글작가"
                           :isbn13 "ISBN-13"
                           :isbn10 "ISBN-10"
                           :publisher "출판사"
                           :publication-date "출판일"
                           :price "가격"
                           :page-count "페이지 수"
                           :description "설명"
                           :cover-image "표지 이미지"
                           (name field))
                         " 필드가 유효하지 않습니다: "
                         (or message "입력값을 확인해주세요."))
    "BusinessError" (or message "비즈니스 규칙 위반")
    "SystemError" "서버 오류가 발생했습니다."
    message))


(defn comic-form []
  (let [form-data (r/atom initial-form-state)
        error-message (r/atom nil)]
    
    (letfn [(handle-input-change [e field]
              (let [value (.. e -target -value)]
                (swap! form-data assoc field value)))
            
            (handle-number-change [e field]
              (let [value (.. e -target -value)
                    parsed-value (when-not (empty? value)
                                  (js/parseInt value))]
                (swap! form-data assoc field parsed-value)))
            
            (handle-file-change [e]
              (let [file (-> e .-target .-files (aget 0))]
                (swap! form-data assoc :cover-image file)))
            
            (handle-submit [e]
              (.preventDefault e)
              (println "Submitting form data:" @form-data)
              (POST (str api-base-url "/api/comics")
                {:body (create-form-data @form-data)
                 :format :raw
                 :response-format :json
                 :keywords? true
                 :headers {"Accept" "application/json"}
                 :handler (fn [response]
                           (println "Success response:" response)
                           (reset! form-data initial-form-state)
                           (reset! error-message nil))
                 :error-handler (fn [{:keys [response status-text]}]
                                (println "Error response:" response status-text)
                                (reset! error-message 
                                        (if response
                                          (format-error-message (:body response))
                                          "서버 오류가 발생했습니다.")))}))
            ]
      
      
         (fn []
        [:div.comic-form
         [:h2 "만화 정보 입력"]
         
         (when @error-message
           [:div.error-message.alert.alert-danger
            [:i.fas.fa-exclamation-circle]
            [:span @error-message]])
         
         [:form {:on-submit handle-submit}
          ;; 필수 필드
          [:div.form-group
           [:label.required "제목"]
           [:input {:type "text"
                   :value (:title @form-data)
                   :required true
                   :on-change #(handle-input-change % :title)}]]
          
          [:div.form-group
           [:label.required "작가"]
           [:input {:type "text"
                   :value (:artist @form-data)
                   :required true
                   :on-change #(handle-input-change % :artist)}]]
          
          [:div.form-group
           [:label.required "글작가"]
           [:input {:type "text"
                   :value (:author @form-data)
                   :required true
                   :on-change #(handle-input-change % :author)}]]
          
          [:div.form-group
           [:label.required "ISBN-13"]
           [:input {:type "text"
                   :value (:isbn13 @form-data)
                   :required true
                   :pattern "^(?:978|979)\\d{10}$"
                   :placeholder "예: 9780596520687"
                   :title "ISBN-13 형식: 978/979로 시작하는 13자리 숫자"
                   :on-change #(handle-input-change % :isbn13)}]]
          
          [:div.form-group
           [:label.required "ISBN-10"]
           [:input {:type "text"
                    :value (:isbn10 @form-data)
                    :required true
                    :pattern "^\\d{9}[\\dX]$"
                    :placeholder "예: 0321146530"
                    :title "ISBN-10 형식: 숫자 9자리 + 숫자 또는 X"
                    :on-change #(handle-input-change % :isbn10)}]]
          
          ;; 선택 필드
          [:div.form-group
           [:label "출판사"]
           [:input {:type "text"
                   :value (:publisher @form-data)
                   :on-change #(handle-input-change % :publisher)}]]
          
          [:div.form-group
           [:label "출판일"]
           [:input {:type "date"
                   :value (:publication-date @form-data)
                   :on-change #(handle-input-change % :publication-date)}]]
          
          [:div.form-group
           [:label "가격"]
           [:input {:type "number"
                   :min "0"
                   :value (:price @form-data)
                   :on-change #(handle-number-change % :price)}]]
          
          [:div.form-group
           [:label "페이지 수"]
           [:input {:type "number"
                   :min "1"
                   :value (:page-count @form-data)
                   :on-change #(handle-number-change % :page-count)}]]
          
          [:div.form-group
           [:label "설명"]
           [:textarea {:value (:description @form-data)
                      :on-change #(handle-input-change % :description)}]]
          
          [:div.form-group
           [:label "표지 이미지"]
           [:input {:type "file"
                   :accept "image/*"
                   :on-change handle-file-change}]]
          
          [:button.submit-button {:type "submit"}
           "만화 등록"]]]))))