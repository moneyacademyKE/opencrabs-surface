(load-file "backup/common.bb")

(def msg (or (first *command-line-args*) "daily opencrabs surface backup"))

(load-file "backup/collect.bb")
(load-file "backup/scan.bb")

(when-not (.exists (io/file root ".git"))
  @(babashka.process/process ["git" "init"] {:dir root :out :inherit :err :inherit}))

@(babashka.process/process ["git" "add" "README.md" "manifest.edn" "bb.edn" ".gitignore" "backup" "snapshot" "reports"] {:dir root :out :inherit :err :inherit})

(def status (:out @(babashka.process/process ["git" "status" "--short"] {:dir root :out :string :err :string})))
(if (str/blank? status)
  (println "No sanitized backup changes to commit.")
  (do
    @(babashka.process/process ["git" "commit" "-m" msg] {:dir root :out :inherit :err :inherit})
    (println "Committed sanitized backup snapshot.")))

(let [result @(babashka.process/process ["bb" "backup:push"] {:dir root :out :inherit :err :inherit})]
  (when-not (zero? (:exit result))
    (System/exit (:exit result))))
