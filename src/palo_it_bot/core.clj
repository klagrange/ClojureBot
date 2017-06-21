(ns palo-it-bot.core
  (:require [palo-it-bot.api-ai :as api-ai]
            [palo-it-bot.config :as config]
            [palo-it-bot.utils :as utils]
            [kvlt.chan :as kvlt]
            [ring.util.http-response :refer :all]
            [clojure.core.async :as a]
            [clojure.pprint :refer [pprint]]))


(defn core
  [payload]
  (let [message-value (:message-value payload)
        ch-out (a/chan)]
    (a/go
           (-> message-value
               (api-ai/api-ai-send)
               ; side effect calls
               (a/<!)
               (api-ai/treat-api-ai-return)
               (a/>! ch-out)))
    ch-out))
