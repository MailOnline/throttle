(defproject throttle "0.2.0"
  :description "A request throttler library for Clojure."
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [http-kit "2.1.19"]
                 [org.clojure/core.async "0.2.374"]

                 ;; logging stuff
                 [org.clojure/tools.logging "0.2.6"]
                 [ch.qos.logback/logback-classic "1.0.13" :exclusions [org.slf4j/slf4j-api]]
                 [ch.qos.logback/logback-access "1.0.13"]
                 [ch.qos.logback/logback-core "1.0.13"]
                 [org.slf4j/slf4j-api "1.7.5"]]

  :repl-options {:init (do (require 'midje.repl) (midje.repl/autotest :filter :core (complement :slow)))}
  :aliases {"midje-fast" ["midje" ":filter" "-slow"]}
  :profiles {:dev
             {:dependencies [[midje "1.6.3"]
                             [http-kit.fake  "0.2.1"]]
              :plugins [[lein-midje "3.1.1"]]}})
