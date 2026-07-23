(load-file "backup/common.bb")
(delete-recursive snapshot-dir)
(.mkdirs snapshot-dir)
(def copied (atom []))
(def skipped (atom []))
(doseq [inc include-paths]
  (let [src (io/file source-root inc)]
    (if-not (.exists src)
      (swap! skipped conj {:path inc :reason "missing"})
      (doseq [f (files-under src)]
        (let [rel (rel-path source-root f)]
          (if (denied-path? rel)
            (swap! skipped conj {:path rel :reason "denied"})
            (let [dst (io/file snapshot-dir rel)]
              (copy-file f dst)
              (swap! copied conj rel))))))))
(spit "reports/collect.json" (cheshire.core/generate-string {:copied (count @copied) :skipped @skipped :files @copied} {:pretty true}))
(println (str "Collected " (count @copied) " files into " (.getPath snapshot-dir)))
(when (seq @skipped)
  (println (str "Skipped " (count @skipped) " files/paths; see reports/collect.json")))
