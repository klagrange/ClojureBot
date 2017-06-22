(ns palo-it-bot.entrypoints.telegram
  (:require [palo-it-bot.api-ai :as api-ai]
            [palo-it-bot.config :as config]
            [palo-it-bot.core :as core]
            [palo-it-bot.utils :as utils]
            [kvlt.chan :as kvlt]
            [ring.util.http-response :refer :all]
            [clojure.core.async :as a]
            [clojure.pprint :refer [pprint]]))

;; Token
(def telegram-token (get-in config/TOKENS [:dev :telegram :TOKEN]))

;; Telegram Out!
(defmulti telegram-out
  (fn [payload]
    (:message-type payload)))
;; Telegram Out! (text)
(defmethod telegram-out :text
  [payload]
  (let [sender-id (payload :sender-id)
        text (payload :message-value)]
    (kvlt/request! {:url (str "https://api.telegram.org/bot" telegram-token "/sendMessage")
                    :method :post
                    :headers {:content-type "application/json"}
                    :type :json
                    :form {:chat_id sender-id
                           :parse_mode "Markdown"
                           :text text}})))
;; Telegram Out! (photo)
(defmethod telegram-out :photo
  [payload]
  (let [sender-id (payload :sender-id)
        photo (:photo (payload :message-value))
        caption (:caption (payload :message-value))]
    (kvlt/request! {:url (str "https://api.telegram.org/bot" telegram-token "/sendPhoto")
                    :method :post
                    :headers {:content-type "application/json"}
                    :type :json
                    :form (cond-> {:chat_id sender-id
                                   :photo photo}
                            caption (assoc :caption caption))})))
;; Telegram Out! (unknown)
(defmethod telegram-out :unknown
  [payload]
  (let [sender-id (payload :sender-id)
        text (payload :message-value)
        text (str "You just send me \"" text "\"")]
    (kvlt/request! {:url (str "https://api.telegram.org/bot" telegram-token "/sendMessage")
                    :method :post
                    :headers {:content-type "application/json"}
                    :type :json
                    :form {:chat_id sender-id
                           :text text}})))

;; Telegram In!
(defn telegram-in!
  [request respond raise]
  (respond (ok {:result true}))
  (let [params (:params request)
        sender-id (get-in params [:message :chat :id])
        text (get-in params [:message :text])
        photo (get-in params [:message :photo])
        formatted-message {:sender-id sender-id
                           :sender-medium :telegram
                           :message-type (cond
                                           text :text
                                           photo :photo
                                           :else :unknown)
                           :message-value (or text
                                              photo)}]
    (a/go
      (-> formatted-message
          (core/core)
          (a/<!)
          (telegram-out)))))
