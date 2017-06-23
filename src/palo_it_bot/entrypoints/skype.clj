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


(defn get-skype-token
  []
  (let [skype-public (get-in config/TOKENS [:dev :skype :public])
        skype-private (get-in config/TOKENS [:dev :skype :private])
        return (a/<!! (kvlt/request! {:url "https://login.microsoftonline.com/botframework.com/oauth2/v2.0/token"
                                      :method :post
                                      :type :x-www-form-urlencoded
                                      :as :json
                                      :body (str "grant_type=client_credentials&client_id="
                                                 skype-public
                                                 "&client_secret="
                                                 skype-private
                                                 "&scope=https%3A%2F%2Fapi.botframework.com%2F.default")}))]
    (get-in return [:body :access_token])))

;; Skype Out!
(defmulti skype-out
  (fn [payload]
    (:message-type payload)))
;; Skype Out! (text)
(defmethod skype-out :text
  [payload]
  (let [sender-id (payload :sender-id)
        text (payload :message-value)
        skype-token (get-skype-token)]
    (kvlt/request! {:url (str "https://apis.skype.com/v3/conversations/" sender-id "/activities")
                    :method :post
                    :headers {:content-type "application/json"
                              "Authorization" (str "Bearer " skype-token)}
                    :type :json
                    :form {:type "message/text"
                           :text text}})))
;; Skype Out! (photo)
(defmethod skype-out :photo
  [payload]
  (let [sender-id (payload :sender-id)
        photo (:photo (payload :message-value))
        caption (:caption (payload :message-value))
        skype-token (get-skype-token)]
    (kvlt/request! {:url (str "https://apis.skype.com/v3/conversations/" sender-id "/activities")
                    :method :post
                    :headers {:content-type "application/json"
                              "Authorization" (str "Bearer " skype-token)}
                    :type :json
                    :form {:type "message/text"
                           :text caption}})))
;; Skype Out! (unknown)
(defmethod skype-out :unknown
  [payload]
  (let [sender-id (payload :sender-id)
        text (payload :message-value)
        text (str "You just send me \"" text "\"")
        skype-token (get-skype-token)]
    (kvlt/request! {:url (str "https://apis.skype.com/v3/conversations/" sender-id "/activities")
                    :method :post
                    :headers {:content-type "application/json"
                              "Authorization" (str "Bearer " skype-token)}
                    :type :json
                    :form {:type "message/text"
                           :text "I don't know"}})))

;; Skype In!
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
