(ns palo-it-bot.handler
  "Asynchronous compojure-api application."
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ring.util.http-status :as http-status]
            [manifold.deferred :as d]
            [clojure.core.async :as a]
            [clojure.pprint :refer [pprint]]
            [schema.core :as s]
            [palo-it-bot.config :as config]
            [palo-it-bot.utils :as utils]
            [kvlt.chan :as kvlt]
            [cheshire.core :refer [generate-string parse-string]]
            compojure.api.async))

(defn- api-ai-send
  "Returns a channel according to:
   - text: keyed-in text."
  [text]
  (let [url (get-in config/TOKENS [:dev :api-ai :URL])
        client-access-token (get-in config/TOKENS [:dev :api-ai :CLIENT-ACCESS-TOKEN])
        session-id (get-in config/TOKENS [:dev :api-ai :SESSION-ID])]
    (kvlt/request! {:url "https://api.api.ai/v1/query?v=20150910"
                    :method :post
                    :headers {:content-type "application/json"
                              :authorization (str "Bearer " client-access-token)}
                    :type :json
                    :form {:sessionId session-id
                           :lang "en"
                           :query text}})))

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

(defn- messenger-register-webhook
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
        treat-api-ai-return (fn [res]
                              (let [status (:status res)
                                    body (utils/js->clj (:body res) true)
                                    speech (-> body :result :fulfillment :speech)
                                    score (-> body :result :score)
                                    threshold (-> config/TOKENS :dev :api-ai :THRESHOLD)
                                    answer (cond
                                             (and (= status 200) (> score threshold)) speech
                                             (not= status 200) "My brain shut down for some reason :("
                                             (and (= status 200) (< score threshold)) (nth utils/api-ai-score-not-met))]
                                (println "API.AI return: " answer)
                                answer))
        send-to-messenger (fn [text]
                            (do (println "send-to-messenger: " text)
                                (messenger-send-text request text)))]

    (when received-msg
          (a/go
                 (->
                      ; "What is PALO IT's vision?"
                      received-msg
                      (api-ai-send)
                      (a/<!)
                      (treat-api-ai-return)
                      (send-to-messenger))))

    (respond (ok))))

(defn telegram-in
  "telegram IN"
  [request respond raise]
  (let []
    (respond (ok {:result true}))))

(def app
  (api
   {:swagger
    {:ui "/"
     :spec "/swagger.json"
     :data {:info {:title "Palo IT Bots"
                   :description "Portfolio for API calls on several channels"}
            :tags [{:name "messenger" :description "API calls for messenger"}
                   {:name "telegram" :description "API calls for telegram"}
                   {:name "api-ai" :description "API calls for API.AI"}]}}}

   (routes
     (context "/messenger" []
       :tags ["messenger"]
         (GET "/" []
           :summary "Registers messenger webhook"
           (fn [request respond raise]
            (messenger-register-webhook request respond raise)))
         (POST "/" []
           :summary "Handles incoming messages"
           (fn [request respond raise]
             (messenger-in! request respond raise))))
     (context "/telegram" []
       :tags ["telegram"]
         (GET "/" []
           :summary "Re"
           (ok {:result "WORKING"}))
         (POST "/" []
           :summary "Handles incoming messages"
           (fn [request respond raise]
            (do (println "--------------- telegram-in ---------------")
                (pprint request)
                (telegram-in request respond raise)))))

     (context "/api-ai" []
      :tags ["api-ai"]
      (GET "/" []
        :summary "Used for testing purposes only"
        (fn [request respond raise]
            (a/go
              (let [res (a/<! (api-ai-send "What is PALO IT's vision?"))
                    status (:status res)
                    body (utils/js->clj (:body res) true)
                    speech (-> body :result :fulfillment :speech)
                    score (-> body :result :score)
                    threshold (-> config/TOKENS :dev :api-ai :THRESHOLD)
                    answer (cond
                             (and (= status 200) (> score threshold)) speech
                             (not= status 200) "My brain shut down for some reason :("
                             (and (= status 200) (< score threshold)) (nth utils/api-ai-score-not-met))]
                (println "answer: " answer)
                (respond (ok res))))))))))
