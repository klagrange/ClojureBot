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
            compojure.api.async))

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
        fb_graph_uri (str "https://graph.facebook.com/v2.6/me/messages?access_token="
                          (-> config/TOKENS :dev :messenger :PAGE-ACCESS-TOKEN))
        payload-text {:recipient {:id sender-id}
                      :message {:text (str "You just said: " received-msg)}}
        payload-img {:recipient {:id sender-id}
                     :message {:attachment {:type "image"
                                            :payload {:url received-img-url}}}}
        payload (cond
                  received-msg payload-text
                  received-img-url payload-img
                  :else nil)]

    (when payload
         (kvlt/request! {:url fb_graph_uri
                         :method :post
                         :headers {:content-type "application/json"}
                         :type :json
                         :form payload}))
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
                   {:name "telegram" :description "API calls for telegram"}]}}}

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
                (telegram-in request respond raise))))))))
