(ns throttle.http
  (:require [org.httpkit.client :as httpkit]
            [clojure.tools.logging :as log]
            [clojure.core.async :as async :refer [<!! >! go go-loop timeout chan alt!!]])
  (:refer-clojure :exclude [get])
  (:import [java.util.concurrent ThreadPoolExecutor LinkedBlockingQueue TimeUnit]
           [org.httpkit PrefixThreadFactory]))

(defn- pool
  "Creates the thread pool for concurrent http-kit execution. With no arguments
  it will default to a max of +2 number of processors threads and 30 secs timeout."
  ([] (pool (+ 2 (.availableProcessors (Runtime/getRuntime))) 30))
  ([size] (pool size 30))
  ([size timeout]
   (let [queue (LinkedBlockingQueue.)
         factory (PrefixThreadFactory. "throttle-worker-")]
     (ThreadPoolExecutor. 1 (if (<= size 1) 2 size) timeout TimeUnit/SECONDS queue factory))))

(defn- async-execute [url out pool & opts]
  "Uses a thread from pool to fetch url.
  Put response into out channel adding elapsed time info."
  (letfn [(callback [{:keys [opts] :as res}]
            (let [elapsed (- (System/currentTimeMillis) (::start opts))]
              (go (>! out (assoc res ::elapsed elapsed)))))]
    (log/debug "Executing request for url" url)
    (if (= :post (:method (first opts)))
      (httpkit/post url (merge {::start (System/currentTimeMillis) ::worker-pool pool} (first opts)) callback)
      (httpkit/get url (merge {::start (System/currentTimeMillis) ::worker-pool pool} (first opts)) callback))))

(defn- consumer [out pool]
  "Consumes urls from created input channel, pipes
  results into out channel. Instructs async-get to use given thread pool."
  (let [in (chan)]
    (go-loop [req (<! in)]
             (when req
               (let [url (if (associative? req) (:url req) req)
                     opts (if (associative? req) (dissoc req :url) {})]
                 (when url (async-execute url out pool opts)))
               (recur (<! in))))
    in))

(defn- producer [reqs out w]
  "Pipes urls into out consumer channel waiting w milliseconds each"
  (go
    (doseq [[i req] (map-indexed #(-> [%1 %2]) reqs)]
      (<! (timeout w))
      (when (zero? (rem i 100)) (log/info (format "Processing req %s of %s" i (count reqs))))
      (>! out req))))

(defn- execute-requests [reqs r n]
  (let [out (chan)
         res (atom [])
         wait (long (/ 1000. r))]
     (log/info (format "Start throttling for %s urls at %s req/sec and up to %s parallel threads" (count reqs) r n))
     (producer reqs (consumer out (pool n)) wait)
     (doseq [_ (range (count reqs))]
       (let [rmap (<!! out)]
         (swap! res conj (assoc rmap ::path (:url (:opts rmap))))))
     @res))

(defn get
  "Fetch urls up to n requests in parallel at the rate of r req/sec.
  When called with no argument will execute up to 10 parallel requests at
  the rate of 2 requests per second."
  ([reqs] (get reqs 2 10))
  ([reqs r] (get reqs r 10))
  ([reqs r n]
   (execute-requests reqs r n)))

(defn post
  "Post to urls up to n requests in parallel at the rate of r req/sec.
  When called with no argument will execute up to 10 parallel requests at
  the rate of 2 requests per second.
  The request should either be a String containing the URL or a map containg the following keys: :  url a string, :form-params a map containing data to be posted"
  ([reqs] (post reqs 2 10))
  ([reqs r] (post reqs r 10))
  ([reqs r n]
   (execute-requests (map #(if (associative? %)
                             (assoc % :method :post)
                             (hash-map :url % :method :post)) reqs) r n)))
