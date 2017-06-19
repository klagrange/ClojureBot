(defproject palo-it-bot "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [metosin/compojure-api "1.2.0-alpha6" :exclude [compojure, metosin/muuntaja]]
                 [ring/ring "1.6.0-RC1"]
                 [compojure "1.6.0-beta3"]
                 [manifold "0.1.6"]
                 [org.clojure/core.async "0.3.441"]
                 [com.novemberain/monger "3.1.0"]
                 [io.nervous/kvlt "0.1.4"]]
  :ring {:handler palo-it-bot.handler/app
         :async? true}
  :uberjar-name "server.jar"
  :profiles {:dev {:plugins [[lein-ring "0.11.0"]]}})
