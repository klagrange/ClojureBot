(ns palo-it-bot.entrypoints.messenger
  (:require [palo-it-bot.api-ai :as api-ai]
            [palo-it-bot.config :as config]
            [palo-it-bot.utils :as utils]
            [kvlt.chan :as kvlt]
            [palo-it-bot.core :as core]
            [ring.util.http-response :refer :all]
            [clojure.core.async :as a]
            [clojure.pprint :refer [pprint]]))


;; Token
(def messenger-access-token (-> config/TOKENS :dev :messenger :PAGE-ACCESS-TOKEN))

;; Messenger Out!
(defmulti messenger-out
  (fn [payload]
    (:message-type payload)))
;; Messenger Out! (text)
(defmethod messenger-out :text
  [payload]
  (let [fb_graph_uri (str "https://graph.facebook.com/v2.6/me/messages?access_token="
                          messenger-access-token)
        sender-id (payload :sender-id)
        text (payload :message-value)]
    (kvlt/request! {:url fb_graph_uri
                    :method :post
                    :headers {:content-type "application/json"}
                    :type :json
                    :form {:recipient {:id sender-id}
                           :message {:text text}}})))

; ;; Telegram Out! (photo)
; (defmethod messenger-out :photo
;   [payload]
;   (let [sender-id (payload :sender-id)
;         photo (:photo (payload :message-value))
;         caption (:caption (payload :message-value))]
;     (kvlt/request! {:url (str "https://api.telegram.org/bot" telegram-token "/sendPhoto")
;                     :method :post
;                     :headers {:content-type "application/json"}
;                     :type :json
;                     :form (cond-> {:chat_id sender-id
;                                    :photo photo}
;                             caption (assoc :caption caption))})))







(defn- messenger-send-img
  "Returns a channel according to a given specific payload for image"
  [request img-url]
  (let [fb_graph_uri (str "https://graph.facebook.com/v2.6/me/messages?access_token="
                          (-> config/TOKENS :dev :messenger :PAGE-ACCESS-TOKEN))
        body-params (:body-params request)
        sender-id (-> body-params :entry (get 0) :messaging (get 0) :sender :id)
        payload {:recipient {:id sender-id}
                 :message {:attachment {:type "image"
                                        :payload {:url img-url}}}}]
    (kvlt/request! {:url fb_graph_uri
                    :method :post
                    :headers {:content-type "application/json"}
                    :type :json
                    :form payload})))

(defn- messenger-send-text
  "Returns a channel according to a given specific payload for text"
  [request text]
  (let [fb_graph_uri (str "https://graph.facebook.com/v2.6/me/messages?access_token="
                          (-> config/TOKENS :dev :messenger :PAGE-ACCESS-TOKEN))
        body-params (:body-params request)
        sender-id (-> body-params :entry (get 0) :messaging (get 0) :sender :id)
        payload {:recipient {:id sender-id}
                 :message {:text text}}]
    (kvlt/request! {:url fb_graph_uri
                    :method :post
                    :headers {:content-type "application/json"}
                    :type :json
                    :form payload})))

(defn messenger-register-webhook
  "Attempts to register facebook webhook."
  [request respond raise]
  (let [query-params (:query-params request)
        query-params--hub-mode (get query-params "hub.mode")
        query-params--hub-challenge (get query-params "hub.challenge")
        query-params--hub-verify-token (get query-params "hub.verify_token")
        verify-token (get-in config/TOKENS [:dev :messenger :VERIFY-TOKEN])]

    (if (and (= query-params--hub-mode "subscribe")
             (= query-params--hub-verify-token verify-token))
        (respond (ok query-params--hub-challenge))
        (respond {:status 403
                  :headers {}
                  :body "Failed to register webook. Check the official facebook documentation at: 'https://developers.facebook.com/docs/graph-api/webhooks'"}))))

(defn messenger-in!
  "Messenger IN"
  [request respond raise]
  (respond (ok))
  (let [body-params (:body-params request)
        sender-id (-> body-params :entry (get 0) :messaging (get 0) :sender :id)
        text (-> body-params :entry (get 0) :messaging (get 0)
                 :message :text)
        photo (-> body-params :entry (get 0) :messaging (get 0)
                             :message :attachments (get 0) :payload :url)
        formatted-message {:sender-id sender-id
                           :message-type (cond
                                           text :text
                                           photo :photo
                                           :else :unknown)
                           :message-value (or text photo)
                           :sender-medium :messenger}]

    ; (pprint request)
    (println "===================================================================================")
    (pprint formatted-message)

    (when formatted-message
          ; (a/go
          ;        (->
          ;             ; "What is PALO IT's vision?"
          ;             text
          ;             ; (api-ai/api-ai-send)
          ;             ; (a/<!)
          ;             ; (api-ai/treat-api-ai-return)
          ;             (send-to-messenger)
          ;
          ;             (a/go))))))
        (-> formatted-message
            (core/core)
            (a/<!)
            (messenger-out)))))
