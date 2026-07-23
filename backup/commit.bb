(load-file "backup/common.bb")
(load-file "backup/scan.bb")
(when-not (.exists (io/file root ".git"))
  @(babashka.process/process ["git" "init"] {:dir root :out :inherit :err :inherit}))
(let [msg (or (first *command-line-args*) "backup opencrabs surface")]
  @(babashka.process/process ["git" "add" "README.md" "manifest.edn" "bb.edn" ".gitignore" "backup" "snapshot"] {:dir root :out :inherit :err :inherit})
  @(babashka.process/process ["git" "commit" "-m" msg] {:dir root :out :inherit :err :inherit})
  (println "Committed local backup snapshot. Push to GitHub only after explicit approval."))
