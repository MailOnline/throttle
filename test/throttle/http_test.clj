(ns throttle.http-test
  (:require [midje.sweet :refer :all]
            [org.httpkit.client :as http]
            [org.httpkit.fake :refer :all]
            [throttle.http :as t]))

(facts "sanity"
       (with-fake-http ["http://google.com/" "get"]
         (fact "get"
               (:body (first (t/get [{:url "http://google.com/"}]))) => "get"))
       #_(with-fake-http [{:url  "http://foo.co/" :method :post}  {:status 201 :body "post"}]
         (fact "post"
               (:body (first (t/post [{:url "http://foo.co/"}])) => "post"))))
