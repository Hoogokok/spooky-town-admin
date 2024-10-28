(ns spooky-town-admin.infrastructure.persistence.protocol)

(defprotocol ComicRepository
  (save-comic [this comic] "만화를 저장합니다")
  (find-comic-by-id [this id] "ID로 만화를 조회합니다")
  (find-comic-by-isbn [this isbn] "ISBN으로 만화를 조회합니다")
  (delete-comic [this id] "만화를 삭제합니다")
  (list-comics [this] "모든 만화 목록을 조회합니다"))