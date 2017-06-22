(ns palo-it-bot.db
  (:require [monger.core :as mg]
            [palo-it-bot.utils :refer [uuid]]
            [monger.collection :as mc]
            [monger.operators :refer [$push $each $slice]]))

;; Helper - DB Wrapper
(defn db-wrapper [function]
  (let [conn (mg/connect)
        db (mg/get-db conn "palo-it-bot")
        out (function db)]
    (mg/disconnect conn)
    out))

;; Backend ID
(defn get-backend-id
  "Return an id according to:
   - entrypoint: 'telegram', 'messenger', ...
   - id: the id given by the entrypoint"
  [entrypoint id]
  (db-wrapper (fn [db]
                (let [id (str id)
                      out-id (get (mc/find-one db entrypoint {:entrypoint-id id}) "id")]
                  (or out-id
                      (get (mc/insert-and-return db entrypoint {:entrypoint-id id :id (uuid)}) :id))))))

;; History
(defn append-history
  "Append an activity to the history according to:
   - id: the backend ID
   - activity: the activity"
  [id activity]
  (db-wrapper (fn [db]
                (let [id (str id)]
                  (mc/update db "history"
                             {:id id}
                             {$push {:history {$each [activity]
                                               $slice -10}}}
                             {:upsert true})))))
(defn get-history
  "Return the history according to:
   - id: the backend ID"
  [id]
  (db-wrapper (fn [db]
                (let [id (str id)]
                  (get (mc/find-one db "history" {:id id}) "history")))))
