(ns spooky-town-admin.validation-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [spooky-town-admin.validation :refer [success? validate-comic validate-cover-image probe-content-type read-buffered-image error-messages]]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io File]
           [java.nio.file Path Paths]
           [java.awt.image BufferedImage]))

(defn mock-file [path content-type size exists]
  (proxy [File] [path]
    (exists [] exists)
    (getPath [] path)
    (length [] size)
    (toPath [] (Paths/get path (into-array String [])))))

(defn mock-image [width height]
  (proxy [BufferedImage] [width height BufferedImage/TYPE_INT_RGB]))

(use-fixtures :each
  (fn [test-fn]
    (with-redefs [probe-content-type (fn [path]
                                       (case (.toString path)
                                         "test/resources/valid_cover.jpg" "image/jpeg"
                                         "test/resources/invalid_type.txt" "text/plain"
                                         "test/resources/large_dimension.png" "image/png"
                                         "test/resources/large_area.png" "image/png"
                                         "test/resources/large_file.jpg" "image/jpeg"
                                         nil))
                  read-buffered-image (fn [file]
                                        (case (.getPath file)
                                          "test/resources/valid_cover.jpg" (mock-image 800 1200)
                                          "test/resources/large_dimension.png" (mock-image 13000 13000)
                                          "test/resources/large_area.png" (mock-image 10000 10000)
                                          (mock-image 100 100)))]
      (test-fn))))

(def valid-comic-data
  {:title "유효한 제목"
   :artist "홍길동"
   :author "김철수"
   :isbn-13 "978-1-23456-789-0"
   :isbn-10 "1-23456-789-0"})

(defn comic-with-optional-fields [base-comic & fields]
  (merge base-comic
         {:publisher "좋은출판사"
          :publication-date "2023-05-15"
          :price 15000
          :pages 200
          :description "재미있는 만화입니다."
          :cover-image nil}
         (apply hash-map fields)))

(deftest validate-comic-test
  (testing "유효한 만화 (필수 필드만)"
    (let [result (validate-comic valid-comic-data)]
      (is (success? result))
      (is (= (assoc valid-comic-data
                    :publisher nil
                    :publication-date nil
                    :price nil
                    :pages nil
                    :description nil
                    :cover-image nil)
             (:value result)))))

  (testing "유효한 만화 (모든 필드 포함)"
    (let [comic-data (comic-with-optional-fields valid-comic-data)
          result (validate-comic comic-data)]
      (is (success? result))
      (is (= comic-data (:value result)))))

  (testing "유효하지 않은 만화 - 필수 필드 오류"
    (doseq [[field invalid-value error-key] [[:title "" :title]
                                             [:artist "" :artist]
                                             [:author "" :author]
                                             [:isbn-13 "invalid-isbn" :isbn-13]
                                             [:isbn-10 "invalid-isbn" :isbn-10]]]
      (let [invalid-comic (assoc valid-comic-data field invalid-value)
            result (validate-comic invalid-comic)]
        (is (not (success? result)))
        (is (= (error-messages error-key) (get-in result [:error field]))))))

  (testing "유효하지 않은 만화 - 선택적 필드 오류"
    (let [invalid-comic (comic-with-optional-fields valid-comic-data
                                                    :publication-date "2023/05/15"
                                                    :price -1000
                                                    :pages 0
                                                    :description (apply str (repeat 1001 "a")))
          result (validate-comic invalid-comic)]
      (is (not (success? result)))
      (is (= (error-messages :publication-date) (get-in result [:error :publication-date])))
      (is (= (error-messages :price) (get-in result [:error :price])))
      (is (= (error-messages :pages) (get-in result [:error :pages])))
      (is (= (error-messages :description) (get-in result [:error :description]))))))

(deftest validate-cover-image-test
  (testing "유효한 표지 이미지"
    (let [valid-image (mock-file "test/resources/valid_cover.jpg" "image/jpeg" (* 5 1024 1024) true)
          result (validate-cover-image valid-image)]
      (is (success? result))
      (is (= valid-image (:value result)))))

  (doseq [[test-name file-path content-type size exists]
          [["존재하지 않는 파일" "test/resources/non_existent.jpg" nil 0 false]
           ["유효하지 않은 이미지 형식" "test/resources/invalid_type.txt" "text/plain" 1024 true]
           ["너무 큰 이미지 차원" "test/resources/large_dimension.png" "image/png" (* 5 1024 1024) true]
           ["너무 큰 이미지 영역" "test/resources/large_area.png" "image/png" (* 5 1024 1024) true]
           ["너무 큰 파일 크기" "test/resources/large_file.jpg" "image/jpeg" (* 11 1024 1024) true]]]
    (testing test-name
      (let [invalid-image (mock-file file-path content-type size exists)
            result (validate-cover-image invalid-image)]
        (is (not (success? result)))
        (is (string? (get-in result [:error :cover-image])))))))

(deftest validate-comic-with-cover-image-test
  (testing "유효한 만화 (표지 이미지 포함)"
    (let [valid-image (mock-file "test/resources/valid_cover.jpg" "image/jpeg" (* 5 1024 1024) true)
          comic-data (comic-with-optional-fields valid-comic-data :cover-image valid-image)
          result (validate-comic comic-data)]
      (is (success? result))
      (is (= valid-image (:cover-image (:value result))))))

  (testing "유효하지 않은 만화 - 표지 이미지 오류"
    (let [invalid-image (mock-file "test/resources/non_existent.txt" nil 0 false)
          comic-data (comic-with-optional-fields valid-comic-data :cover-image invalid-image)
          result (validate-comic comic-data)]
      (is (not (success? result)))
      (is (= (error-messages :cover-image-missing) (get-in result [:error :cover-image]))))))
