(ns spooky-town-admin.infrastructure.persistence.protocol)

(defprotocol ComicRepository
  (save-comic [this comic] "만화를 저장합니다")
  (find-comic-by-id [this id] "ID로 만화를 조회합니다")
  (find-comic-by-isbn [this isbn] "ISBN으로 만화를 조회합니다")
  (find-comic-by-isbns [this isbn13 isbn10] "ISBN13 또는 ISBN10으로 만화를 조회합니다")
  (delete-comic [this id] "만화를 삭제합니다")
  (list-comics [this] "모든 만화 목록을 조회합니다"))

(defprotocol PublisherRepository
  (save-publisher [this publisher] "출판사를 저장합니다")
  (find-publisher-by-id [this id] "ID로 출판사를 조회합니다")
  (find-publisher-by-name [this name] "이름으로 출판사를 조회합니다")
  (find-publishers-by-comic-id [this comic-id] "만화 ID로 연관된 출판사들을 조회합니다")
  (associate-publisher-with-comic [this comic-id publisher-id] "만화와 출판사를 연결합니다"))


(defprotocol AuthorRepository
  "작가 도메인의 영속성을 담당하는 프로토콜"
  
  (save-author [this author]
    "작가 정보를 저장하고 저장된 작가 정보를 반환합니다.")
  
  (find-author-by-id [this id]
    "주어진 ID로 작가를 찾아 반환합니다.")
  
  (find-authors-by-name [this name]
    "주어진 이름으로 작가를 검색하여 반환합니다. 
     부분 일치하는 모든 작가를 반환합니다.")
  
  (find-authors-by-comic-id [this comic-id]
    "주어진 만화 ID에 연결된 모든 작가를 반환합니다.")
  
  (associate-author-with-comic [this author-id comic-id role]
    "작가와 만화를 연결합니다."))