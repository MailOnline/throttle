# throttle

A request throttling library for Clojure. It comes equipped with http get request throttling out of the box, which is one important use case. If you need generic function throttling (and then the http-kit plumbing and thread-pool definition is up to you) please see https://github.com/brunoV/throttler

## how to use

Add:

        [throttle "0.1.2"]

to your project.clj. Or:

        (require '[throttle.http :as http])

at the REPL. Here's an example snippet:

```clojure
    (ns test
      (:require [throttle.http :as http]))

    (def fb "https://api.facebook.com/method/links.getStats?format=json&urls=")

    ;; You want to know how much buzz these news are generating on facebook
    ;; say you have 10k of these urls:
    (def urls [
     (str fb "http://www.nbcnews.com/science/space/orion-spaceship-looks-good-during-first-test-road-mars-n262146")
     (str fb "http://www.foxnews.com/politics/2014/12/05/president-obama-picks-former-pentagon-official-ashton-carter-to-be-defense/")
     (str fb "http://www.nytimes.com/2014/12/06/business/economy/november-jobs-unemployment-figures.html")])

  ; retrieve them with max (+ 2 NumOfProcessors) concurrent reqs at the total of 2 req/second
  (def res (http/get urls))

  ; will retrieve them with max 10 concurrent connections at the total of 20 req/second
  (def res (http/get urls 20))

  ; will retrieve them with max 20 concurrent connections at the total of 5 req/second
  (def res (http/get urls 5 20))

  ; the response is going to be the same map of keys http-kit returns plus the elapsed
  (map #(-> [(:throttle.http/elapsed %) (last (re-find #"share_count\":(\d+)" (:body %)))]) res)

  ; that returns something like ([1273 "85"] [88 "245"] [112 "208"]) where the first
  ; number is the elapsed and the second are the share counts.

```

Invoking http/throttle will return a map of all the keys returned by http-kit with the additional elasped added by throttle itself. To avoid potential overriding, :throttle.http/elapsed needs to be fully qualified.

## Passing optional metadata

If you need to pass additional metadata for each request apart from the url, use a map instead of a string. The only constrain is that the map should contain the :url key containing the request url:

```
;; transform each url in a map with an additional metadata info
(http/get (map #(-> {:url % :mymeta "tada"}) urls) 20))

;; metadata will reappear for each response in the :opts key
(:mymeta (:opts (first res)))

;; will print "tada"
```

## Logging

Throttle will log into a configured Logback logger if one is provided. It is shipped with an example logback.sample.xml in the resource folder. Put the logback.sample.xml in the /resource folder of your project (unless you have one already) to have throttle start logging:

* DEBUG level: will print every request made
* INFO level: will print progress every 100 requests

### TODO

* other kind of throttling for services not necessarily on HTTP, like native ElasticSearch
* throttling post
* tests. Tricky but doable once you get an handle to the in/out channels used internally.

## License

Copyright © 2014-2015 Mailonline - DMGMedia

Distributed under the Eclipse Public License either version 1.0 or `(at your option)` any later version.
