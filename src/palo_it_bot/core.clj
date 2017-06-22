(ns palo-it-bot.core
  (:require [palo-it-bot.api-ai :as api-ai]
            [palo-it-bot.config :as config]
            [palo-it-bot.utils :as utils]
            [palo-it-bot.db :as db]
            [kvlt.chan :as kvlt]
            [ring.util.http-response :refer :all]
            [clojure.core.async :as a]
            [clojure.string :refer [join replace]]
            [clojure.pprint :refer [pprint]]))

;; Token
(def telegram-token (get-in config/TOKENS [:dev :telegram :TOKEN]))

;; Core Dispatch
(defmulti core-dispatch
  (fn [_ message-type _ _ _]
    message-type))
;; Core Dispatch (text)
(defmethod core-dispatch :text
  [ch-out _ sender-id sender-medium message-value]
  (let [api-return (-> message-value
                       (api-ai/api-ai-send (str (name sender-medium) sender-id))
                       (a/<!!))
        backend-id (db/get-backend-id (name sender-medium) sender-id)
        body (utils/js->clj (:body api-return) true)
        speech (get-in body [:result :fulfillment :speech])
        action (get-in body [:result :action])
        action-over? (not (get-in body [:result :actionIncomplete] false))

        out (cond
              (= action "cto?") {:sender-id sender-id
                                 :message-type :photo
                                 :message-value {:photo "http://user.photos.s3.amazonaws.com/user_213695_1440575935.jpg"
                                                 :caption speech}}
              (= action "managing-director?") {:sender-id sender-id
                                               :message-type :photo
                                               :message-value {:photo "https://media.licdn.com/mpr/mpr/shrinknp_200_200/AAEAAQAAAAAAAAhhAAAAJGZhZjkyNjg4LTRkZGMtNGY0Zi04MzM0LTUzZTI5NzBjNjgxOA.jpg"
                                                               :caption speech}}
              (= action "get_my_identity") {:sender-id sender-id
                                            :message-type :text
                                            :message-value (replace speech
                                                                    #"\[\[identity\]\]"
                                                                    (str "`" backend-id "`"))}
              (= action "get_history") {:sender-id sender-id
                                        :message-type :text
                                        :message-value (replace speech
                                                                #"\[\[history\]\]"
                                                                (str "\n  - " (join "\n  - " (db/get-history backend-id))))}
              :else {:sender-id sender-id
                     :message-type :text
                     :message-value speech})]


    ;; Feedback
    (when (and (= action "feedback")
               action-over?)
      (kvlt/request! {:url (str "https://api.telegram.org/bot" telegram-token "/sendMessage")
                      :method :post
                      :headers {:content-type "application/json"}
                      :type :json
                      :form {:chat_id "-1001138612424"
                             :text (str "\"" message-value "\"")}}))

    ;; History
    (when action-over?
      (db/append-history backend-id action))

    (a/>!! ch-out out)))
;; Core Dispatch (photo)
(defmethod core-dispatch :photo
  [ch-out _ sender-id sender-medium message-value]
  (a/>!! ch-out {:sender-id sender-id
                :message-type :text
                :message-value "You send me a picture"}))
;; Core Dispatch (unknown)
(defmethod core-dispatch :unknown
  [ch-out _ sender-id sender-medium message-value]
  (a/>!! ch-out {:sender-id sender-id
                 :message-type :text
                 :message-value "What did you just do?"}))

;; Core Handler
(defn core
  [payload]
  (let [message-value (payload :message-value)
        sender-id (payload :sender-id)
        sender-medium (payload :sender-medium)
        message-type (get payload :message-type :unknown)
        ch-out (a/chan)
        backend-id (db/get-backend-id (name sender-medium)
                                      sender-id)]
    (a/go
           (-> message-value
               (api-ai/api-ai-send)
               ; side effect calls
               (a/<!)
               (api-ai/treat-api-ai-return)
               (->> (a/>! ch-out))))
    (core-dispatch ch-out message-type sender-id sender-medium message-value)
    ch-out))
