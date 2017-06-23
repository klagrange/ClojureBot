(ns palo-it-bot.entrypoints.skype
  (:require [palo-it-bot.api-ai :as api-ai]
            [palo-it-bot.config :as config]
            [palo-it-bot.core :as core]
            [palo-it-bot.utils :as utils]
            [kvlt.chan :as kvlt]
            [ring.util.http-response :refer :all]
            [clojure.core.async :as a]
            [clojure.pprint :refer [pprint]]))

;; Token
(def skype-token (get-in config/TOKENS [:dev :skype :TOKEN]))

;; Telegram Out!
(defmulti skype-out
  (fn [payload]
    (:message-type payload)))
;; Telegram Out! (text)
(defmethod skype-out :text
  [payload]
  (let [sender-id (payload :sender-id)
        text (payload :message-value)]
    (kvlt/request! {:url (str "https://apis.skype.com/v3/conversations/" sender-id "/activities")
                    :method :post
                    :headers {:content-type "application/json"
                              "Authorization" (str "Bearer " skype-token)}
                    :type :json
                    :form {:type "message/text"
                           :text text}})))
;; Telegram Out! (photo)
(defmethod skype-out :photo
  [payload]
  (let [sender-id (payload :sender-id)
        photo (:photo (payload :message-value))
        caption (:caption (payload :message-value))]
    (kvlt/request! {:url (str "https://apis.skype.com/v3/conversations/" sender-id "/activities")
                    :method :post
                    :headers {:content-type "application/json"
                              "Authorization" (str "Bearer " skype-token)}
                    :type :json
                    :form {:type "message/text"
                           :text caption
                           ;; :attachments [{:contentType "image/png"
                           ;;                :contentUrl "http://aka.ms/Fo983c"
                           ;;                :name "duck-on-a-rock.jpg"}]
                           }})))
;; Telegram Out! (unknown)
(defmethod skype-out :unknown
  [payload]
  (let [sender-id (payload :sender-id)
        text (payload :message-value)
        text (str "You just send me \"" text "\"")]
    (kvlt/request! {:url (str "https://apis.skype.com/v3/conversations/" sender-id "/activities")
                    :method :post
                    :headers {:content-type "application/json"
                              "Authorization" (str "Bearer " skype-token)}
                    :type :json
                    :form {:type "message/text"
                           :text "I don't know"}})))

;; Telegram In!
(defn skype-in!
  [request respond raise]
  (respond (ok {:result true}))
  (let [params (:params request)
        sender-id (get-in params [:conversation :id])
        text (str (get-in params [:channelData :text]))
        ;; photo (get-in params [:message :photo])
        formatted-message {:sender-id sender-id
                           :sender-medium :skype
                           :message-type (cond
                                           text :text
                                           ;; photo :photo
                                           :else :unknown)
                           :message-value text}]
    (println "====>" text)
    (a/go
      (-> formatted-message
          (core/core)
          (a/<!)
          (skype-out)))))
