(ns spooky-town-admin.domain.comic.workflow-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [spooky-town-admin.domain.comic.workflow :as workflow]
            [spooky-town-admin.domain.comic.types :as types]
            [spooky-town-admin.domain.common.result :refer [success?]]))

(def valid-comic-data
  {:title "테스트 만화"
   :artist "테스트 작가"
   :author "테스트 글작가"
   :isbn13 "9780306406157"
   :isbn10 "0321146530"})

(def mock-image-data
  {:tempfile (java.io.File. "test.jpg")
   :content-type "image/jpeg"
   :size 1000
   :width 800
   :height 600})

(defn mock-extract-image-metadata [_]
  {:content-type "image/jpeg"
   :size 1000
   :width 800
   :height 600})

(use-fixtures :each
  (fn [f]
    (with-redefs [workflow/extract-image-metadata mock-extract-image-metadata]
      (f))))

(deftest create-comic-workflow-test
  (testing "이미지가 없는 기본 워크플로우"
    (let [result (workflow/create-comic-workflow valid-comic-data)]
      (is (success? result))
      (let [validated-comic (:value result)]
        (is (instance? spooky_town_admin.domain.comic.types.ValidatedComic 
                      validated-comic))
        (is (nil? (:cover-image-metadata validated-comic))))))
  
  (testing "이미지가 있는 워크플로우"
    (let [data-with-image (assoc valid-comic-data :cover-image mock-image-data)
          result (workflow/create-comic-workflow data-with-image)]
      (is (success? result))
      (let [validated-comic (:value result)]
        (is (instance? spooky_town_admin.domain.comic.types.ValidatedComic 
                      validated-comic))
        (is (some? (:cover-image-metadata validated-comic)))))))

(deftest process-image-test
  (testing "유효한 이미지 처리"
    (let [data-with-image (assoc valid-comic-data :cover-image mock-image-data)
          result (workflow/process-image data-with-image)]
      (is (success? result))
      (is (some? (-> result :value :cover-image-metadata)))))
  
  (testing "이미지가 없는 경우"
    (let [result (workflow/process-image valid-comic-data)]
      (is (success? result))
      (is (nil? (-> result :value :cover-image-metadata))))))