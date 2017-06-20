(ns palo-it-bot.utils
  (:require [cheshire.core :refer [generate-string parse-string]]))

(defn uuid [] (.toString (java.util.UUID/randomUUID)))

(defn get-value-from-qs
  "Return the value of a specific key from a query string:
  - qs: '?x=3&y=3'
  - param: 'x'
  "
  [qs param]
  (let [regex (re-pattern (str ".*" param "=([^&]*)[&]?.*"))
        res (re-matches regex qs)]
    (get res 1)))

(defn clj->js [form]
  (generate-string form))

(defn js->clj
  ([form] (parse-string form))
  ([form keywordize?] (parse-string form keywordize?)))

(def api-ai-score-not-met 
  ["Sorry, I didn't get what you said!"])
