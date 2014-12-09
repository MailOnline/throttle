(ns throttle.http-test
  (:require [midje.sweet :refer :all]
            [throttle.http :refer :all]))

(facts ""
       (fact ""
             (get ["http://google.com/?http://some.url"])))
