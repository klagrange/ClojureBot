(ns palo-it-bot.db
  (:require [monger.core :as mg]
            [palo-it-bot.utils :refer [uuid]]
            [monger.collection :as mc]))

(defn db-wrapper [function]
  (let [conn (mg/connect)
        db (mg/get-db conn "palo-it-bot")
        out (function db)]
    (mg/disconnect conn)
    out))

(defn get-context
  "Return a context according to:
   - entrypoint: 'telegram', 'messenger', ...
   - id: the id given by the entrypoint"
  [entrypoint id]
  (db-wrapper (fn [db]
                (let [id (str id)
                      out-id (get (mc/find-one db entrypoint {:entrypoint-id id}) "id")]
                  (or out-id
                      (get (mc/insert-and-return db entrypoint {:entrypoint-id id :id (uuid)}) :id))))))

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
