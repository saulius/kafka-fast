(ns kafka-clj.integration-v2-node-tests
  (:require [kafka-clj.consumer.node :refer [create-node! shutdown-node! msg-seq!]]
            [kafka-clj.consumer.work-organiser :refer [get-queue-data]]
            [taoensso.carmine :as car :refer [wcar]]
            [clojure.core.async :refer [alts!! chan timeout]]
            [clojure.tools.logging :refer [info]]
            [clojure.edn :as edn])
  (:use midje.sweet))


(def config (edn/read-string (slurp (str (System/getProperty "user.home") "/" ".integration/kafka-clj/conf.edn"))))

(def bootstrap-brokers (:bootstrap-brokers config))
(def topic (:topic config))
(def redis-conf (:redis-conf config))


;=========== Requirements ===========
;===== This test requires a running kafka cluster and a redis server
;===== It also requires a topic with name "ping" and it must have at least a 100 messages published
;=====
;====================================

(facts "Test Create Work Units and Consumer"

  (fact "Test organiser and wait-and-do-work-unit!"

        (let [ts (System/currentTimeMillis)
              consumer-conf {:bootstrap-brokers [{:host "localhost" :port 9092}] :consume-step 10 :redis-conf {:host "localhost" :max-active 5 :timeout 1000 :group-name "test"} :conf {}}
              redis-conf (:redis-conf consumer-conf)
              redis-conn {:pool {:max-active (get redis-conf :max-active 20)}
                          :spec {:host  (get redis-conf :host "localhost")
                                 :port    (get redis-conf :port 6379)
                                 :password (get redis-conf :password)
                                 :timeout  (get redis-conf :timeout 4000)}}
              node (create-node! consumer-conf ["ping"])
              ]
          ;we must close the complete processor
          (car/wcar redis-conn
                    (car/flushall))

          (Thread/sleep 3000)
          ;check queues, we should have zero in work, working and complete queues
          ;its expected that the node should have processed all work
          (count (get-queue-data consumer-conf (:work-queue consumer-conf))) => 0
          (count (get-queue-data consumer-conf (:working-queue consumer-conf))) => 0
          (count (get-queue-data consumer-conf (:complete-queue consumer-conf))) => 0
          (count (take 10 (msg-seq! node))) => 10
          (shutdown-node! node)

          )))
