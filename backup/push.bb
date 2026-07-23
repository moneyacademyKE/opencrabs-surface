(load-file "backup/common.bb")
(load-file "backup/scan.bb")

(defn trim [s] (str/trim (or s "")))
(def remote-name (or (first *command-line-args*) "origin"))
(def branch (trim (:out @(babashka.process/process ["git" "branch" "--show-current"] {:dir root :out :string :err :string}))))
(def remotes (trim (:out @(babashka.process/process ["git" "remote"] {:dir root :out :string :err :string}))))

(when-not (.exists (io/file root ".git"))
  (println "Push blocked: backup workspace is not a git repo. Run bb backup:init first.")
  (System/exit 1))

(when-not (some #{remote-name} (str/split-lines remotes))
  (println (str "Push blocked: no git remote named '" remote-name "'."))
  (println "Configure a private GitHub remote first, e.g. gh repo create opencrabs-surface --private --source=. --remote=origin")
  (System/exit 1))

(when (str/blank? branch)
  (println "Push blocked: cannot determine current git branch.")
  (System/exit 1))

(println (str "Pushing sanitized OpenCrabs surface backup to " remote-name "/" branch "..."))
(let [result @(babashka.process/process ["git" "push" remote-name branch] {:dir root :out :inherit :err :inherit})]
  (when-not (zero? (:exit result))
    (println "Push failed.")
    (System/exit (:exit result))))
(println "Push complete.")
