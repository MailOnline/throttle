(defproject throttle "0.1.0"
  :description "A request throttler library for Clojure."
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [http-kit "2.1.19"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]]
  :repl-options {:init (do
                         (require 'midje.repl)
                         (midje.repl/autotest :filter :core (complement :slow)))}
  :aliases {"midje-fast" ["midje" ":filter" "-slow"]}
  :profiles {:dev
             {:dependencies [[midje "1.6.3"]]
              :plugins [[lein-midje "3.1.1"]]}})
