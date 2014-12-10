(ns throttle.http
  (:require [org.httpkit.client :as httpkit]
            [clojure.tools.logging :as log]
            [clojure.core.async :as async :refer [<!! >! go go-loop timeout chan alt!!]])
  (:refer-clojure :exclude [get])
  (:import [java.util.concurrent ThreadPoolExecutor LinkedBlockingQueue TimeUnit]
           [org.httpkit PrefixThreadFactory]))

(defn- pool
  "Creates the thread pool for concurrent http-kit execution."
  ([] (pool Integer/MAX_VALUE 30))
  ([size] (pool size 30))
  ([size timeout]
   (let [min (max (+ 2 (.availableProcessors (Runtime/getRuntime))) size)
         queue (LinkedBlockingQueue.)
         factory (PrefixThreadFactory. "throttle-worker-")]
     (ThreadPoolExecutor. min size timeout TimeUnit/SECONDS queue factory))))

(defn- async-get [url out pool & opts]
  "Uses a thread from pool to fetch url.
  Put response into out channel adding elapsed time info."
  (letfn [(callback [{:keys [opts] :as res}]
            (let [elapsed (- (System/currentTimeMillis) (::start opts))]
              (go (>! out (assoc res ::elapsed elapsed)))))]
    (log/debug "Executing request for url" url)
    (httpkit/get url (merge {::start (System/currentTimeMillis) ::worker-pool pool} (first opts)) callback)))

(defn- consumer [out pool]
  "Consumes urls from created input channel, pipes
  results into out channel. Instructs async-get to use given thread pool."
  (let [in (chan)]
    (go-loop [req (<! in)]
             (when (and req (:url req))
               (let [url (if (associative? req) (:url req) req)
                     opts (if (associative? req) (dissoc req :url) {})]
                 (async-get url out pool opts))
               (recur (<! in))))
    in))

(defn- producer [reqs out w]
  "Pipes urls into out consumer channel waiting w milliseconds each"
  (go
    (doseq [req reqs]
      (<! (timeout w))
      (>! out req))))

(defn get
  "Fetch urls up to n requests in parallel at the rate of r req/sec.
  When called with no argument will execute up to 10 parallel requests at
  the rate of 2 requests per second."
  ([reqs] (get reqs 2 10))
  ([reqs r] (get reqs r 10))
  ([reqs r n]
   (let [out (chan)
         res (atom [])
         wait (long (/ 1000. r))]
     (log/info (format "Start throttling for %s urls at %s req/sec and up to %s parallel threads" (count reqs) r n))
     (producer reqs (consumer out (pool n)) wait)
     (doseq [_ (range (count reqs))]
       (let [rmap (<!! out)]
         (swap! res conj (assoc rmap ::path (:url (:opts rmap))))))
     @res)))
