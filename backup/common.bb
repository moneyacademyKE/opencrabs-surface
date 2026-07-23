(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.string :as str])

(def root (.getCanonicalFile (io/file ".")))
(def manifest (edn/read-string (slurp "manifest.edn")))
(defn expand-home [s]
  (if (str/starts-with? s "~/")
    (str (System/getProperty "user.home") (subs s 1))
    s))
(def source-root (.getCanonicalFile (io/file (expand-home (:source-root manifest)))))
(def snapshot-dir (.getCanonicalFile (io/file root (:snapshot-dir manifest))))
(def max-file-bytes (:max-file-bytes manifest 1048576))
(def include-paths (:include-paths manifest))
(def deny-fragments (:deny-path-fragments manifest))
(def deny-extensions (:deny-extensions manifest))
(def secret-patterns (map re-pattern (:secret-patterns manifest)))

(defn rel-path [base f]
  (let [bp (.toPath (.getCanonicalFile base))
        fp (.toPath (.getCanonicalFile f))]
    (str (.relativize bp fp))))

(defn posix [s] (str/replace s #"\\\\" "/"))

(defn denied-path? [rel]
  (let [p (posix rel)]
    (or (some #(str/includes? p %) deny-fragments)
        (some #(str/ends-with? p %) deny-extensions))))

(defn files-under [f]
  (let [f (io/file f)]
    (cond
      (not (.exists f)) []
      (.isFile f) [f]
      :else (filter #(.isFile %) (file-seq f)))))

(defn delete-recursive [f]
  (let [f (io/file f)]
    (when (.exists f)
      (doseq [x (reverse (file-seq f))]
        (.delete x)))))

(defn ensure-parent [f]
  (.mkdirs (.getParentFile (io/file f))))

(defn copy-file [src dst]
  (ensure-parent dst)
  (io/copy src dst)
  (.setLastModified (io/file dst) (.lastModified (io/file src))))

(defn sh! [& args]
  (let [p (apply babashka.process/process args)
        out @p]
    (when-not (zero? (:exit out))
      (throw (ex-info (str "command failed: " args) out)))
    out))
