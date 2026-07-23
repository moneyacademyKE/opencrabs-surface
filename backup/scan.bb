(load-file "backup/common.bb")
(def findings (atom []))
(defn add! [path kind detail] (swap! findings conj {:path path :kind kind :detail detail}))
(doseq [f (files-under snapshot-dir)]
  (let [rel (rel-path snapshot-dir f)]
    (when (denied-path? rel)
      (add! rel "denied-path" "path matches deny policy"))
    (when (> (.length f) max-file-bytes)
      (add! rel "oversized" (str (.length f) " bytes > " max-file-bytes)))
    (try
      (let [text (slurp f)]
        (doseq [pat secret-patterns]
          (when (re-find pat text)
            (add! rel "secret-pattern" (str pat)))))
      (catch Exception _
        (add! rel "unreadable-or-binary" "snapshot should contain text/source artifacts only")))))
(spit "reports/scan.json" (cheshire.core/generate-string {:ok (empty? @findings) :findings @findings} {:pretty true}))
(if (empty? @findings)
  (println "Scan passed: no denied paths, oversized files, binaries, or secret-looking patterns found.")
  (do
    (println (str "Scan failed: " (count @findings) " finding(s); see reports/scan.json"))
    (doseq [f @findings] (println (:kind f) (:path f) "-" (:detail f)))
    (System/exit 1)))
