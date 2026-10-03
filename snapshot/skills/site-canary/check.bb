#!/usr/bin/env bb
;; site-canary check - prints ONLY failures, one per line: label | problem | url
;; Exit 0 always. Empty output = all green. Used as both cron trigger and the
;; agent's check step, so it must stay silent on green days.
(require '[clojure.java.shell :as sh]
         '[clojure.string :as str])

(defn- check-one [url label]
  (try
    (let [{:keys [out]} (sh/sh "curl" "-s" "-o" "/dev/null" "-m" "8"
                               "-w" "%{http_code} %{time_total}" url)
          [code secs] (str/split (str/trim out) #"\s+")
          code-num    (parse-long (or code ""))
          secs-num    (parse-double (or secs "0"))]
      (cond
        (or (nil? code-num) (zero? code-num))
        (println label "| no response (timeout or refused) |" url)

        (not (<= 200 code-num 399))
        (println label "| HTTP" code-num "|" url)

        (and secs-num (> secs-num 3.0))
        (println label "| SLOW" secs "s |" url)))
    (catch Exception e
      (println label "| check error:" (.getMessage e) "|" url))))

(def urls-file "/Users/moe/.opencrabs/state/site-canary/urls.txt")
(if (.exists (java.io.File. urls-file))
  (doseq [line (str/split-lines (slurp urls-file))
          :let [line (str/trim line)]
          :when (and (seq line) (not (str/starts-with? line "#")))
          :let [[url label] (map str/trim (str/split line #"\|" 2))]
          :when (seq url)]
    (check-one url (if (seq label) label url)))
  (println "site-canary | urls.txt missing - state not seeded |" urls-file))
