(ns palo-it-bot.messenger
  (:require [palo-it-bot.api-ai :as api-ai]
            [palo-it-bot.config :as config]
            [palo-it-bot.utils :as utils]
            [kvlt.chan :as kvlt]
            [ring.util.http-response :refer :all]
            [clojure.core.async :as a]
            [clojure.pprint :refer [pprint]]))


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
  (let [body-params (:body-params request)
        sender-id (-> body-params :entry (get 0) :messaging (get 0) :sender :id)
        received-msg (-> body-params :entry (get 0) :messaging (get 0)
                         :message :text)
        received-img-url (-> body-params :entry (get 0) :messaging (get 0)
                             :message :attachments (get 0) :payload :url)
        ; treat-api-ai-return (fn [res]
        ;                       (let [status (:status res)
        ;                             body (utils/js->clj (:body res) true)
        ;                             speech (-> body :result :fulfillment :speech)
        ;                             score (-> body :result :score)
        ;                             threshold (-> config/TOKENS :dev :api-ai :THRESHOLD)
        ;                             answer (cond
        ;                                      (and (= status 200) (> score threshold)) speech
        ;                                      (not= status 200) "My brain shut down for some reason :("
        ;                                      (and (= status 200) (< score threshold)) (rand-nth utils/api-ai-score-not-met))]
        ;                         (println "API.AI return: " answer)
        ;                         answer))
        send-to-messenger (fn [text]
                            (do (println "send-to-messenger: " text)
                                (messenger-send-text request text)))]

    (when received-msg
          (a/go
                 (->
                      ; "What is PALO IT's vision?"
                      received-msg
                      (api-ai/api-ai-send)
                      (a/<!)
                      (api-ai/treat-api-ai-return)
                      (send-to-messenger))))

    (respond (ok))))
