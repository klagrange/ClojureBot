(ns palo-it-bot.utils)

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
