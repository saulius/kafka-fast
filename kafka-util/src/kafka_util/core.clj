(ns kafka-util.core
  (:require [clojure.tools.cli :as cli]
            [clojure.string :as clj-str]
            [kafka-util.test :as ktest])
  (:gen-class))


(defn- parse-host-port [hp]
  (let [[host port] (clj-str/split hp #":")] {:host host :port (if port (Integer/parseInt port) 9092) }))

(defn- parse-brokers
  "Parse broker1:port1,broker2:port2 into [{:host broker1 :port por1} {:host broker2 :port port2}]"
  [s]
  (map parse-host-port (clj-str/split s #"[;,]")))

(defn- parse-redis [s]
  (let [[host port] (clj-str/split s #":")]
    (if port {:host host :port port} {:host host})))

(def cli-options-test-producer
  [
   ["-b" "--brokers BROKERS" "Kafka broker comma separated list" :parse-fn parse-brokers :validate [not-empty "Must specify at least one broker"]]
   ["-t" "--topic TOPIC" "Topic to produce to" :validate [not-empty "Must specify a topic"]]
   ["-n" "--n i" "Number of messages to send" :parse-fn #(Integer/parseInt %) :validate [number? "Must specify the number or messages"]]
   ["-p" "--prefix PREFIX" "Prefix in the message to use the message will be $prefix-$i where i = 0 < n" :validate [not-empty "Must specify a message prefix"]]
   ["-h" "--help HELP" "Prints the help message"]
   ])

(def cli-options-test-consumer
  [
    ["-b" "--brokers BROKERS" "Kafka broker comma separated list" :parse-fn parse-brokers :validate [not-empty "Must specify at least one broker"]]
    ["-r" "--redis REDIS" "Redis server for kafka consumer" :parse-fn parse-redis :validate [not-empty "Must specify a redis conf"]]
    ["-t" "--topic TOPIC" "Topic to read from" :validate [not-empty "Must specify a topic"]]
    ["-f" "--file-out FILENAME" "File to write message read to, this file is flushed every second" :parse-fn #(clojure.java.io/file %)]
    ["-h" "--help HELP" "Prints the help message"]
   ])

(defn- run-test-producer [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options-test-producer)]
    (cond
      (:help options) (clojure.pprint/pprint summary)
      errors (clojure.pprint/pprint errors)
      :else (ktest/produce-test-messages! (:topic options) (:prefix options) (:n options) {:bootstrap-brokers (:brokers options)}))))
;[topic file-out {:keys [bootstrap-brokers redis-conf] :as conf}]
(defn- run-test-consumer [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options-test-consumer)]

    (cond
      (:help options) (clojure.pprint/pprint summary)
      errors (clojure.pprint/pprint errors)
      :else (ktest/consume-test-messages! (:topic options) (:file-out options) {:conf {}  :bootstrap-brokers (:brokers options) :redis-conf (merge
                                                                                                                                              (:redis options)
                                                                                                                                              {
                                                                                                                                                :group-name (str "test-" (System/currentTimeMillis))
                                                                                                                                                :max-active 20
                                                                                                                                                :timeout 5000

                                                                                                                                                })}))))
(defn- prn-main-help [args]
  (clojure.pprint/pprint
    ["Kafka utility to test the kafka consumer and producer api"
     "Programs are"
     "test-producer" "test-consumer"
     "Usage: e.g kafka-util test-producer -b \"localhost:9092\" -t mytopic -n 1000 -p \"test-message\""]))

(defn -main [& args]
  (let [cmd (first args)]
    (condp = cmd
      "test-producer" (run-test-producer (rest args))
      "test-consumer" (run-test-consumer (rest args))
      (prn-main-help args))))
