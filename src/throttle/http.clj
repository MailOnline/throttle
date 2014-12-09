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

(defn- async-get [url out pool]
  "Uses a thread from pool to fetch url.
  Put response into out channel adding elapsed time info."
  (letfn [(callback [{:keys [opts] :as res}]
            (let [elapsed (- (System/currentTimeMillis) (:start opts))]
              (go (>! out (assoc res :elapsed elapsed)))))]
    (httpkit/get url {:start (System/currentTimeMillis) :worker-pool pool} callback)))

(defn- consumer [out pool]
  "Consumes urls from created input channel, pipes
  results into out channel. Instructs async-get to use given thread pool."
  (let [in (chan)]
    (go-loop [url (<! in)]
             (when url
               (async-get url out pool)
               (recur (<! in))))
    in))

(defn- producer [urls out w]
  "Pipes urls into out consumer channel waiting w milliseconds each"
  (go
    (doseq [url urls]
      (<! (timeout w))
      (>! out url))))

(defn get
  "Fetch urls up to n requests in parallel at the rate of r req/sec.
  When called with no argument will execute up to 10 parallel requests at
  the rate of 2 requests per second."
  ([urls] (get urls 2 10))
  ([urls r] (get urls r 10))
  ([urls r n]
   (let [out (chan)
         res (atom [])
         wait (long (/ 1000. r))]
     (log/info (format "Starting throttling for %s urls at %s req/sec and %s parallel threads" (count urls) r n))
     (producer urls (consumer out (pool n)) wait)
     (doseq [_ (range (count urls))]
       (let [rmap (<!! out)]
         (swap! res conj (assoc rmap :path (:url (:opts rmap))))))
     @res)))
