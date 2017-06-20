(ns palo-it-bot.api-ai
  (:require [kvlt.chan :as kvlt]
            [palo-it-bot.config :as config]
            [palo-it-bot.utils :as utils]
            [kvlt.chan :as kvlt]
            [ring.util.http-response :refer :all]
            [clojure.pprint :refer [pprint]]))

(defn api-ai-send
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

(defn treat-api-ai-return
  "Returns a string according to:
  - res: the return from a request call to API.AI"
  [res]
  (let [status (:status res)
        body (utils/js->clj (:body res) true)
        speech (-> body :result :fulfillment :speech)
        score (-> body :result :score)
        threshold (-> config/TOKENS :dev :api-ai :THRESHOLD)
        answer (cond
                 (and (= status 200) (> score threshold)) speech
                 (not= status 200) "My brain shut down for some reason :("
                 (and (= status 200) (< score threshold)) (rand-nth utils/api-ai-score-not-met))]
   (println "API.AI return: " answer)
   answer))
