(ns spooky-town-admin.validation
  (:require [clojure.spec.alpha :as s]
            [spooky-town-admin.db :as db]))

(defn validate-comic [comic]
  (if (s/valid? ::db/comic comic)
    comic
    (throw (ex-info "Invalid comic data" (s/explain-data ::db/comic comic)))))
