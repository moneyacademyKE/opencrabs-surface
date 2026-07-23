(load-file "backup/common.bb")
(def apply? (some #{"--apply"} *command-line-args*))
(def files (files-under snapshot-dir))
(println (if apply? "Applying restore:" "Dry-run restore:"))
(println "Files:" (count files))
(doseq [f files]
  (let [rel (rel-path snapshot-dir f)
        dst (io/file source-root rel)]
    (println (if apply? "restore" "would restore") rel)
    (when apply?
      (copy-file f dst))))
(when-not apply?
  (println "No files changed. Re-run with --apply to restore."))
