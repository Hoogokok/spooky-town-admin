(ns spooky-town-admin.core
  (:require [reagent.dom :as rdom]
            [reagent.core :as r]
            [spooky-town-admin.components.comic-form :refer [comic-form]]))

(defn app []
  [:div.container
   [:h1 "Spooky Town Admin"]
   [comic-form]])

(defn mount-root []
  (rdom/render [app] (.getElementById js/document "app")))

(defn init []
  (mount-root))

(defn reload! []
  (mount-root))