(ns palo-it-bot.core
  (:require [palo-it-bot.api-ai :as api-ai]
            [palo-it-bot.config :as config]
            [palo-it-bot.utils :as utils]
            [kvlt.chan :as kvlt]
            [ring.util.http-response :refer :all]
            [clojure.core.async :as a]
            [clojure.pprint :refer [pprint]]))

;; Core Dispatch
(defmulti core-dispatch
  (fn [_ message-type _ _]
    message-type))
;; Core Dispatch (text)
(defmethod core-dispatch :text
  [ch-out _ sender-id message-value]
  (let [api-return (-> message-value
                       (api-ai/api-ai-send)
                       (a/<!!)
                       (api-ai/treat-api-ai-return))]
    (a/>!! ch-out {:sender-id sender-id
                  :message-type :text
                  :message-value api-return})))
;; Core Dispatch (photo)
(defmethod core-dispatch :photo
  [ch-out _ sender-id message-value]
  (a/>!! ch-out {:sender-id sender-id
                :message-type :text
                :message-value "You send me a picture"}))
;; Core Dispatch (unknown)
(defmethod core-dispatch :unknown
  [ch-out _ sender-id message-value]
  (a/>!! ch-out {:sender-id sender-id
                 :message-type :text
                 :message-value "What did you just do?"}))

;; Core Handler
(defn core
  [payload]
  (let [message-value (payload :message-value)
        sender-id (payload :sender-id)
        message-type (get payload :message-type :unknown)
        ch-out (a/chan)]
    (a/go
      (core-dispatch ch-out message-type sender-id message-value))
    ch-out))
