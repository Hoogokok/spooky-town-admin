(ns spooky-town-admin.application.comic.service
  (:require
   [spooky-town-admin.infrastructure.image-storage :as image-storage]
   [spooky-town-admin.infrastructure.persistence :as persistence]))

(defrecord ComicService [comic-repository publisher-repository image-storage])

(defn create-comic-service [env]
  (->ComicService 
   (persistence/create-comic-repository)
   (persistence/create-publisher-repository)
   (image-storage/create-image-storage env))) 