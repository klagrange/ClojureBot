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
  [text session-id]
  (let [url (get-in config/TOKENS [:dev :api-ai :URL])
        session-id (apply str
                          (take 36
                                (filter #(and (not= \: %)
                                              (not= \_ %))
                                        (str session-id))))
        client-access-token (get-in config/TOKENS [:dev :api-ai :CLIENT-ACCESS-TOKEN])]
    (kvlt/request! {:url "https://api.api.ai/v1/query?v=20150910"
                    :method :post
                    :headers {:content-type "application/json"
                              :authorization (str "Bearer " client-access-token)}
                    :type :json
                    :form {:sessionId session-id
                           :lang "en"
                           :query text}})))
