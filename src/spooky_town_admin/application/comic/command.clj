(ns spooky-town-admin.application.comic.command
  (:require
   [clojure.tools.logging :as log]
   [spooky-town-admin.domain.comic.errors :as errors]
   [spooky-town-admin.domain.comic.workflow :as workflow]
   [spooky-town-admin.core.result :as r]
   [spooky-town-admin.infrastructure.persistence :as persistence]
   [spooky-town-admin.infrastructure.persistence.transaction :refer [with-transaction]]))

(defn- check-duplicate-isbn [comic-repository comic-data]
  (log/debug "Checking duplicate ISBN for:" comic-data)
  (let [isbn (or (:isbn13 comic-data) (:isbn10 comic-data))
        result (persistence/find-comic-by-isbn comic-repository isbn)]
    (if (and (r/success? result) 
             (some? (r/value result)))
      (do
        (log/info "Duplicate ISBN found")
        (r/failure (errors/business-error
                    :duplicate-isbn
                    (errors/get-business-message :duplicate-isbn))))
      (r/success comic-data))))

(defn create-comic [{:keys [comic-repository image-storage] :as service} comic-data]
  (with-transaction
    (log/debug "Transaction started" (pr-str comic-data))
    (-> (check-duplicate-isbn comic-repository comic-data)
        (r/bind #(workflow/create-comic-workflow image-storage %))
        (r/bind (fn [{:keys [comic]}]
                  (persistence/save-comic comic-repository comic)))))) 