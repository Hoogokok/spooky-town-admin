(ns spooky-town-admin.components.comic-form
  (:require [reagent.core :as r]
            [ajax.core :refer [POST]]))

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
              (POST "/api/comics"
                {:params @form-data
                 :handler (fn [response]
                           (reset! form-data initial-form-state)
                           (reset! error-message nil))
                 :error-handler (fn [{:keys [response]}]
                                (reset! error-message 
                                        (or (get-in response [:error :message])
                                            "서버 오류가 발생했습니다.")))}))]
      
      (fn []
        [:div.comic-form
         [:h2 "만화 정보 입력"]
         
         (when @error-message
           [:div.error-message @error-message])
         
         [:form {:on-submit handle-submit}
          ;; 필수 필드
          [:div.form-group
           [:label "제목"]
           [:input {:type "text"
                   :value (:title @form-data)
                   :required true
                   :on-change #(handle-input-change % :title)}]]
          
          [:div.form-group
           [:label "작가"]
           [:input {:type "text"
                   :value (:artist @form-data)
                   :required true
                   :on-change #(handle-input-change % :artist)}]]
          
          [:div.form-group
           [:label "글작가"]
           [:input {:type "text"
                   :value (:author @form-data)
                   :required true
                   :on-change #(handle-input-change % :author)}]]
          
          [:div.form-group
           [:label "ISBN-13"]
           [:input {:type "text"
                   :value (:isbn13 @form-data)
                   :required true
                   :pattern "^(?:978|979)-\\d-\\d{2,7}-\\d{1,7}-\\d$"
                   :on-change #(handle-input-change % :isbn13)}]]
          
          [:div.form-group
           [:label "ISBN-10"]
           [:input {:type "text"
                   :value (:isbn10 @form-data)
                   :required true
                   :pattern "^\\d{1,5}-\\d{1,7}-\\d{1,6}-[\\dX]$"
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