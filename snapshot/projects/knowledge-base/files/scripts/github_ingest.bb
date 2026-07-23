#!/usr/bin/env bb
(ns github-ingest
  (:require [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.string :as str]))

(defn exit! [code msg]
  (binding [*out* *err*] (println msg))
  (System/exit code))

(defn run [& args]
  (let [res (apply p/shell {:out :string :err :string :continue true} args)]
    (when-not (zero? (:exit res))
      (exit! (:exit res) (str "command failed: " (str/join " " args) "\n" (:err res))))
    (:out res)))


(defn ensure-gh-auth []
  (let [res (p/shell {:out :string :err :string :continue true} "gh" "auth" "status")]
    (when-not (zero? (:exit res))
      (exit! 2 (str "gh auth is required for GitHub ingest. Run: gh auth login\n" (:err res))))))


(defn gh-json [& args]
  (json/parse-string (apply run "gh" "api" args) true))

(defn doc-json [m]
  (json/generate-string m))

(defn repo-doc [repo]
  {:source "github"
   :source_id (str "repo:" (:full_name repo))
   :ts (:pushed_at repo)
   :title (str "Repo: " (:full_name repo))
   :content (format "%s\nLanguage: %s\nUpdated: %s" (or (:description repo) "") (or (:language repo) "") (or (:pushed_at repo) ""))
   :metadata {:repo (:full_name repo) :kind "repo"}
   :visibility "internal"
   :origin "team"})

(defn readme-doc [repo]
  (try
    (let [readme (gh-json (str "repos/" (:full_name repo) "/readme"))
          text (String. (.decode (java.util.Base64/getMimeDecoder) (:content readme)) "UTF-8")]
      {:source "github"
       :source_id (str "readme:" (:full_name repo))
       :ts (:pushed_at repo)
       :title (str "README: " (:full_name repo))
       :content (subs text 0 (min 20000 (count text)))
       :metadata {:repo (:full_name repo) :kind "readme"}
       :visibility "internal"
       :origin "team"})
    (catch Exception _ nil)))

(defn issue-docs [repo limit]
  (try
    (for [issue (gh-json (str "repos/" (:full_name repo) "/issues") "-X" "GET" "-f" "state=all" "-f" (str "per_page=" limit))]
      {:source "github"
       :source_id (str (if (:pull_request issue) "pr:" "issue:") (:full_name repo) ":" (:number issue))
       :ts (or (:updated_at issue) (:created_at issue))
       :title (str (:full_name repo) " #" (:number issue) ": " (:title issue))
       :content (or (:body issue) "")
       :metadata {:repo (:full_name repo) :kind (if (:pull_request issue) "pull_request" "issue") :number (:number issue) :state (:state issue) :url (:html_url issue)}
       :visibility "internal"
       :origin "team"})
    (catch Exception e
      (binding [*out* *err*] (println "issues skipped" (:full_name repo) (.getMessage e)))
      [])))

(defn commit-docs [repo limit]
  (try
    (for [c (gh-json (str "repos/" (:full_name repo) "/commits") "-X" "GET" "-f" (str "per_page=" limit))]
      (let [commit (:commit c)]
        {:source "github"
         :source_id (str "commit:" (:full_name repo) ":" (:sha c))
         :ts (get-in commit [:author :date])
         :title (str (:full_name repo) ": " (subs (first (str/split-lines (or (:message commit) ""))) 0 (min 100 (count (first (str/split-lines (or (:message commit) "")))))))
         :content (or (:message commit) "")
         :metadata {:repo (:full_name repo) :kind "commit" :sha (subs (:sha c) 0 10) :author (get-in commit [:author :name])}
         :visibility "internal"
         :origin "team"}))
    (catch Exception e
      (binding [*out* *err*] (println "commits skipped" (:full_name repo) (.getMessage e)))
      [])))

(defn -main [& args]
  (ensure-gh-auth)
  (let [opts (set args)
        max-repos (or (some->> args (partition 2 1) (some #(when (= "--repos" (first %)) (Integer/parseInt (second %))))) 10)
        issues-per-repo (or (some->> args (partition 2 1) (some #(when (= "--issues" (first %)) (Integer/parseInt (second %))))) 20)
        commits-per-repo (or (some->> args (partition 2 1) (some #(when (= "--commits" (first %)) (Integer/parseInt (second %))))) 10)
        repos (gh-json "user/repos" "-X" "GET" "-f" "sort=pushed" "-f" (str "per_page=" max-repos))]
    (when (empty? repos) (exit! 1 "gh returned no repos; check gh auth status"))
    (doseq [repo repos
            doc (concat [(repo-doc repo)]
                        (keep identity [(readme-doc repo)])
                        (issue-docs repo issues-per-repo)
                        (when-not (contains? opts "--no-commits") (commit-docs repo commits-per-repo)))]
      (println (doc-json doc)))))

(apply -main *command-line-args*)