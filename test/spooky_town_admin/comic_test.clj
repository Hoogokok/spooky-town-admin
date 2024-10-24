(ns spooky-town-admin.comic-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [spooky-town-admin.comic :as comic]
            [spooky-town-admin.validation :refer [probe-content-type read-buffered-image]]
            [spooky-town-admin.image :as image]
            [spooky-town-admin.db :as db])
  (:import [java.io File]
           [java.nio.file Paths]
           [java.awt.image BufferedImage]))

;; Mock 파일 생성 함수
(defn mock-file [path content-type size exists]
  (proxy [File] [path]
    (exists [] exists)
    (getPath [] path)
    (length [] size)
    (toPath [] (Paths/get path (into-array String [])))))

(defn mock-image [width height]
  (proxy [BufferedImage] [width height BufferedImage/TYPE_INT_RGB]))

;; 테스트용 mock 이미지 파일
(def test-image-file 
  (mock-file "test/resources/valid_cover.jpg" "image/jpeg" (* 5 1024 1024) true))

;; 이미지 관련 함수들을 mock으로 대체
(use-fixtures :each
  (fn [test-fn]
    (with-redefs [probe-content-type (fn [path]
                                      (case (.toString path)
                                        "test/resources/valid_cover.jpg" "image/jpeg"
                                        nil))
                  read-buffered-image (fn [file]
                                       (case (.getPath file)
                                         "test/resources/valid_cover.jpg" (mock-image 800 1200)
                                         (mock-image 100 100)))]
      (test-fn))))

(deftest create-comic-test
  (testing "만화 생성 - 이미지가 없는 경우"
    (let [comic-data {:title "테스트 만화"
                     :artist "테스트 작가"
                     :author "테스트 작가"
                     :isbn-10 "1-23456-789-0"
                     :isbn-13 "978-1-23456-789-0"}
          result (comic/create-comic comic-data)]
      (is (:success result))
      (is (number? (:id result)))))

  (testing "만화 생성 - 이미지가 있는 경우"
    (let [comic-data {:title "테스트 만화"
                     :artist "테스트 작가"
                     :author "테스트 작가"
                     :isbn-10 "1-23456-789-0"
                     :isbn-13 "978-1-23456-789-0"
                     :cover-image test-image-file}
          result (comic/create-comic comic-data)]
      (is (:success result))
      (is (number? (:id result)))))

  (testing "만화 생성 실패 - 유효성 검사 실패"
    (let [invalid-comic {:title "" ;; 빈 제목
                        :artist "테스트 작가"
                        :author "테스트 작가"
                        :isbn-10 "1-23456-789-0"
                        :isbn-13 "978-1-23456-789-0"}
          result (comic/create-comic invalid-comic)]
      (is (not (:success result)))
      (is (= "유효성 검사 실패" (:error result)))))

  (testing "만화 생성 실패 - 이미지 업로드 실패"
    (with-redefs [image/upload-to-cdn (fn [_] {:success false
                                              :error "CDN 업로드 실패"
                                              :details "Mock error"})]
      (let [comic-data {:title "테스트 만화"
                       :artist "테스트 작가"
                       :author "테스트 작가"
                       :isbn-10 "1-23456-789-0"
                       :isbn-13 "978-1-23456-789-0"
                       :cover-image test-image-file}
            result (comic/create-comic comic-data)]
        (is (not (:success result)))
        (is (= "이미지 업로드 실패" (:error result))))))

  (testing "만화 생성 실패 - DB 저장 실패"
    (with-redefs [db/add-comic (fn [_] {:success false
                                       :error "만화 정보 저장 실패"
                                       :details "Mock DB error"})]
      (let [comic-data {:title "테스트 만화"
                       :artist "테스트 작가"
                       :author "테스트 작가"
                       :isbn-10 "1-23456-789-0"
                       :isbn-13 "978-1-23456-789-0"}
            result (comic/create-comic comic-data)]
        (is (not (:success result)))
        (is (= "만화 정보 저장 실패" (:error result)))))))
