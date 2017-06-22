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
            [palo-it-bot.entrypoints.messenger :as messenger]
            [palo-it-bot.entrypoints.telegram :as telegram]
            [palo-it-bot.entrypoints.skype :as skype]
            [palo-it-bot.api-ai :as api-ai]
            [cheshire.core :refer [generate-string parse-string]]
            compojure.api.async))

(def app
  (api
   {:swagger
    {:ui "/"
     :spec "/swagger.json"
     :data {:info {:title "Palo IT Bots"
                   :description "Portfolio for API calls on several channels"}
            :tags [{:name "messenger" :description "API calls for messenger"}
                   {:name "telegram" :description "API calls for telegram"}
                   {:name "api-ai" :description "API calls for API.AI"}
                   {:name "core" :description "used for testing purposes only"}]}}}

   (routes
     ;; Messenger
     (context "/messenger" []
       :tags ["messenger"]
         (GET "/" []
           :summary "Registers messenger webhook"
           (fn [request respond raise]
            (messenger/messenger-register-webhook request respond raise)))
         (POST "/" []
           :summary "Handles incoming messages"
           (fn [request respond raise]
             (messenger/messenger-in! request respond raise))))

     ;; Telegram
     (context "/telegram" []
       :tags ["telegram"]
         (GET "/" []
           :summary "Re"
           (ok {:result "WORKING"}))
         (POST "/" []
           :summary "Handles incoming messages"
           (fn [request respond raise]
             (telegram/telegram-in! request respond raise))))

     ;; Skype
     (context "/skype" []
       :tags ["skype"]
         (GET "/" []
           :summary "Re"
           (ok {:result "WORKING"}))
         (POST "/" []
           :summary "Handles incoming messages"
           (fn [request respond raise]
             (skype/skype-in! request respond raise))))

     (context "/api-ai" []
      :tags ["api-ai"]
      (GET "/" []
        :summary "Used for testing purposes only"
        (fn [request respond raise]
            (a/go
              (let [res (a/<! (api-ai/api-ai-send "What is PALO IT's vision?"))
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
                (respond (ok res)))))))
     (context "/core" []
      :tags ["core"]
      (GET "/" []
        :summary "Used for testing purposes only"
        (fn [request respond raise]
          (pprint request)
          (respond (ok))))))))
