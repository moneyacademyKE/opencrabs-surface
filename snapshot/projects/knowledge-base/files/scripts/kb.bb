#!/usr/bin/env bb
(ns kb
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def kb-home (fs/cwd))
(def db-path (str (fs/file kb-home "nsm_kb.sqlite")))
(def config-path (fs/file kb-home "config.json"))
(def env-path (fs/file kb-home ".env"))
(def rust-bin (fs/file kb-home ".." "bin" "kb"))
(def babashka-bin "/opt/homebrew/bin/bb")

(defn println-err [& xs]
  (binding [*out* *err*]
    (apply println xs)))

(defn exit! [code msg]
  (when msg (println-err msg))
  (System/exit code))

(defn exists? [p] (fs/exists? p))

(defn inherited-env []
  (into {} (System/getenv)))

(defn run-proc [& args]
  (apply p/shell {:out :string :err :string :continue true :env (inherited-env)} args))

(defn shell! [& args]
  (let [res (apply sh/sh args)]
    (when-not (str/blank? (:out res)) (print (:out res)))
    (when-not (str/blank? (:err res)) (binding [*out* *err*] (print (:err res))))
    (when-not (zero? (:exit res)) (System/exit (:exit res)))
    res))

(defn load-dotenv []
  (when (exists? env-path)
    (doseq [line (str/split-lines (slurp (str env-path)))
            :let [line (str/trim line)]
            :when (and (not (str/blank? line)) (not (str/starts-with? line "#")) (str/includes? line "="))]
      (let [[k v] (str/split line #"=" 2)
            v (-> v str/trim (str/replace #"^['\"]|['\"]$" ""))]
        (System/setProperty k v)))))

(defn have-rust? []
  (exists? rust-bin))

(defn rust [& args]
  (apply shell! (str rust-bin) args))

(defn sqlite-stats []
  (if-not (exists? db-path)
    (println "[]")
    (shell! "sqlite3" "-json" db-path "SELECT source, COUNT(*) AS n, SUM(embedding IS NOT NULL) AS embedded, MAX(ts) AS newest FROM docs GROUP BY source ORDER BY source;")))

(defn fts-query [q]
  (->> (str/split q #"\s+")
       (remove str/blank?)
       (map #(str "\"" (str/replace % "\"" "") "\""))
       (str/join " OR ")))

(defn sql-quote [s]
  (str "'" (str/replace s "'" "''") "'"))

(defn lexical-search [query]
  (when (str/blank? query)
    (exit! 2 "usage: bb kb:search [--mode titles|snippets|full] [--source github|youtube] [--private] <query>"))
  (if-not (exists? db-path)
    (println "[]")
    (let [sql (format "SELECT docs.id, docs.source, docs.source_id, docs.ts, docs.title, substr(replace(replace(docs.content, char(10), ' '), char(13), ' '), 1, 240) AS snippet FROM docs_fts JOIN docs ON docs.id = docs_fts.rowid WHERE docs_fts MATCH %s ORDER BY docs_fts.rank LIMIT 10;"
                      (sql-quote (fts-query query)))]
      (shell! "sqlite3" "-json" db-path sql))))

(defn doctor []
  (load-dotenv)
  (let [checks [{:name "kb_home" :ok true :detail (str kb-home)}
                {:name "sqlite_db" :ok (exists? db-path) :detail db-path}
                {:name "config_json" :ok (exists? config-path) :detail (str config-path)}
                {:name "env_file" :ok (exists? env-path) :detail (str env-path)}
                {:name "rust_sidecar" :ok (have-rust?) :detail (str rust-bin)}
                {:name "sqlite3_cli" :ok (zero? (:exit (sh/sh "bash" "-lc" "command -v sqlite3 >/dev/null"))) :detail "sqlite3 on PATH"}]]
    (doseq [{:keys [name ok detail]} checks]
      (println (format "%s %-14s %s" (if ok "ok " "warn") name detail)))
    (println "ok  active_sources github,youtube")
    (when-not (exists? config-path)
      (println "hint config_json: create config.json from config.example.json before source ingestion"))
    (when-not (exists? env-path)
      (println "hint env_file: .env is optional for stats/search but required for API-backed YouTube or synthesis"))))

(defn stats []
  (if (have-rust?)
    (rust "stats" "--json")
    (sqlite-stats)))

(defn search [args]
  (let [[flags words] (split-with #(str/starts-with? % "--") args)
        private? (some #{"--private"} flags)
        query (str/join " " (remove #{"titles" "snippets" "full" "github" "youtube"} words))]
    (if (have-rust?)
      (let [mode (or (some->> args (partition 2 1) (some #(when (= "--mode" (first %)) (second %)))) "snippets")
            source (some->> args (partition 2 1) (some #(when (= "--source" (first %)) (second %))))
            base [(str rust-bin) "search" "--json" "--mode" mode]
            with-source (if source (conj base "--source" source) base)
            with-private (if private? (conj with-source "--private") with-source)]
        (apply shell! (concat with-private [query])))
      (lexical-search query))))

(defn timestamp []
  (.format (java.time.format.DateTimeFormatter/ISO_INSTANT) (java.time.Instant/now)))

(defn ingest-native [source script args]
  (load-dotenv)
  (let [started (timestamp)
        proc (apply run-proc babashka-bin script args)]
    (if (zero? (:exit proc))
      (let [upsert (p/shell {:in (:out proc) :out :string :err :string :continue true}
                            (str rust-bin) "upsert-jsonl" "--json" "--source" source "--run-started-at" started)]
        (when-not (str/blank? (:out upsert)) (print (:out upsert)))
        (when-not (str/blank? (:err upsert)) (binding [*out* *err*] (print (:err upsert))))
        (System/exit (:exit upsert)))
      (do
        (when-not (str/blank? (:err proc)) (binding [*out* *err*] (print (:err proc))))
        (shell! (str rust-bin) "record-run" source "--json" "--started-at" started "--failed" "1" "--error" (str/trim (or (:err proc) "connector failed")))
        (System/exit (:exit proc))))))

(defn ingest [args]
  (let [source (first args)]
    (when-not source (exit! 2 "usage: bb kb:ingest <github|youtube>"))
    (case source
      "github" (ingest-native "github" "scripts/github_ingest.bb" (rest args))
      "youtube" (ingest-native "youtube" "scripts/youtube_ingest.bb" (rest args))
      (exit! 2 (str "inactive source: " source ". Active sources are github,youtube.")))))

(defn ask [args]
  (let [query (str/join " " args)]
    (when (str/blank? query) (exit! 2 "usage: bb kb:ask <question>"))
    (let [res (run-proc (str rust-bin) "search" "--json" "--mode" "snippets" query)]
      (when-not (zero? (:exit res))
        (when-not (str/blank? (:err res)) (println-err (:err res)))
        (System/exit (:exit res)))
      (let [hits (json/parse-string (:out res) true)]
        (if (empty? hits)
          (println (json/generate-string {:ok true :answer "Insufficient evidence in the KB." :citations []}))
          (do
            (println "Answer requires synthesis; citation candidates below.")
            (doseq [[idx h] (map-indexed vector hits)]
              (println (format "[%s:%d] %s" (:source h) idx (or (:title h) "(untitled)")))
              (when-let [snippet (:snippet h)] (println (str "  " snippet))))))))))

(defn -main [& args]
  (let [[cmd & more] args]
    (case cmd
      "doctor" (doctor)
      "stats" (stats)
      "search" (search more)
      "ingest" (ingest more)
      "ask" (ask more)
      (exit! 2 "usage: bb scripts/kb.bb <doctor|stats|search|ingest|ask>"))))

(apply -main *command-line-args*)
