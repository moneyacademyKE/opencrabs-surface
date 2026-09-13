#!/usr/bin/env bb
;; score_claims.clj — the immutable evaluator. Reads results.jsonl (13 pages of
;; measurements) and scores each SKILL.md claim: CONFIRMED / REFUTED / REFINED.
;; Karpathy rule: this file is the judge; the skill being judged cannot touch it.

(require '[cheshire.core :as json]
         '[clojure.string :as str])

(def pages (map #(json/parse-string % true) (str/split-lines (slurp "/tmp/results.jsonl"))))
(def n (count pages))

(defn lines-matching [page re]
  (filter #(re-find re (:t %)) (:lines page)))

(defn any-line [page re] (boolean (seq (lines-matching page re))))

(defn hex-dist [h1 h2]
  (let [p (fn [h i] (Integer/parseInt (subs h (+ 1 (* i 2)) (+ 3 (* i 2))) 16))]
    (Math/sqrt (+ (Math/pow (- (p h1 0) (p h2 0)) 2)
                  (Math/pow (- (p h1 1) (p h2 1)) 2)
                  (Math/pow (- (p h1 2) (p h2 2)) 2)))))

(defn band-has-color? [page target tol]
  (let [swatches (mapcat (fn [[_band sws]] sws) (:bands page))]
    (boolean
     (some (fn [{:keys [hex share]}]
             (and (> share 2) (< (hex-dist hex target) tol)))
           swatches))))

(defn caps-ratio [page]
  (let [ls (:lines page)
        big (filter #(> (:h %) 0.08) ls)
        caps (filter #(= (:t %) (str/upper-case (:t %))) big)]
    (if (empty? big) nil (/ (count caps) (count big)))))

(def verdicts
  {:H1-masthead-top-30pct
   (let [support (filter #(any-line % #"(?i)club of nairobi south|since 1963") pages)
         in-band (filter (fn [p] (every? #(< (:y %) 0.33) (lines-matching p #"(?i)club of nairobi south|since 1963"))) support)]
     {:support (count in-band) :of (count support)
      :detail (str (count support) " pages have masthead text; " (count in-band) " fully within top 33%")})

   :H2-footer-order
   (let [with-when (filter #(any-line % #"(?i)(monday|tuesday|wednesday|thursday|friday|saturday|sunday)") pages)
         ordered (filter (fn [p]
                           (let [when-y (some-> (lines-matching p #"(?i)(monday|tuesday|wednesday|thursday|friday|saturday|sunday)") first :y)
                                 time-y (some-> (lines-matching p #"(?i)FROM \d") first :y)
                                 enq-y  (some-> (lines-matching p #"(?i)ENQUIR") first :y)]
                             (and when-y time-y enq-y (< when-y time-y enq-y) (> when-y 0.60))))
                         with-when)]
     {:support (count ordered) :of (count with-when)
      :detail (str (count with-when) " pages have a weekday footer line; " (count ordered) " have WHEN<FROM<ENQUIRIES in bottom band")})

   :H3-dot-time-style
   (let [times (mapcat #(lines-matching % #"(?i)FROM \S+") pages)
         dotted (filter #(re-find #"\d\.\d{2}" (:t %)) times)]
     {:support (count dotted) :of (count times)
      :detail (str "time strings: " (pr-str (map :t times)))})

   :H4-headline-caps
   (let [ratios (keep caps-ratio pages)]
     {:support (count (filter #(>= % 0.99) ratios)) :of (count ratios)
      :detail (str "pages with big text fully caps: " (count (filter #(>= % 0.99) ratios)) "/" (count ratios))})

   :H5-palette-blue-gold
   (let [blue (count (filter #(band-has-color? % "#0060b0" 90) pages))
         gold (count (filter #(band-has-color? % "#f0b000" 90) pages))]
     {:support blue :of n :detail (str "blue-ish present: " blue "/" n ", gold-ish present: " gold "/" n)})

   :H6-scrim-darkness
   (let [dark-mid (count (filter #(< (get-in % [:lum :mid]) 0.45) pages))
         light-mid (count (filter #(> (get-in % [:lum :mid]) 0.75) pages))]
     {:support (+ dark-mid light-mid) :of n
      :detail (str "dark-mid pages (scrim mode): " dark-mid ", light-mid pages (bio mode): " light-mid ", neither: " (- n dark-mid light-mid))})

   :H7-hierarchy-ratio
   ;; Run 002 harness fix (labeled, separate from skill edits): the detail
   ;; printer multiplied ratios by 10 for rounding and never divided back —
   ;; "16-38x" was really 1.6-3.8x. Found by probing a NEW artifact (the v3
   ;; poster measured 2.25x) against the deck claim. The loop working.
   (let [ratios (keep (fn [p] (let [hs (map :h (:lines p))]
                                (when (>= (count hs) 4)
                                  (/ (apply max hs) (double (/ (reduce + hs) (count hs))))))) pages)]
     {:support (count ratios) :of n
      :detail (str "max/mean line-height ratios (true): " (pr-str (map #(/ (Math/round (* % 10.0)) 10.0) ratios)))})

   :H8-headline-max-2-lines
   (let [big-counts (map #(count (filter (fn [l] (> (:h l) 0.08)) (:lines %))) pages)]
     {:support (count (filter #(<= % 2) big-counts)) :of n
      :detail (str "big-line counts per page: " (pr-str big-counts))})

   :H9-square-canvas
   {:support (count (filter #(and (= 1080 (:w %)) (= 1080 (:h %))) pages)) :of n
    :detail "all pages 1080x1080?"}

   :H10-min-text-size
   (let [mins (map #(if (seq (:lines %)) (apply min (map :h (:lines %))) 1) pages)
         viol (count (filter #(< % 0.017) mins))]
     {:support (- n viol) :of n
      :detail (str "min line-height per page (canvas frac): " (pr-str (map #(Math/round (* % 1000.0)) mins)))})})

(doseq [[k v] verdicts]
  (let [verdict (cond (>= (:support v) (* 0.85 (:of v))) "CONFIRMED"
                      (>= (:support v) (* 0.5 (:of v)))  "REFINED"
                      :else                               "REFUTED")]
    (println (format "%-28s %-10s %d/%d  %s" (name k) verdict (:support v) (:of v) (:detail v)))))

;; ============================================================
;; Run 002 extensions (2026-09-13) — harness evolution, explicitly labeled.
;; H1-H10 above are byte-identical to Run 001. New hypotheses below score
;; patterns observed but never measured. First sight = [dominant k/n], never law.
;; ============================================================

(defn footer-y [page re]
  (some-> (lines-matching page re) first :y))

(defn rgb [hex]
  (map #(Integer/parseInt (subs hex (+ 1 (* % 2)) (+ 3 (* % 2))) 16) [0 1 2]))

(defn color-class [hex]
  (let [[r g b] (rgb hex)
        mx (max r g b) mn (min r g b)]
    (cond
      (< (- mx mn) 40)                    :neutral
      (and (> r 150) (> g 100) (< b 100)) :gold
      (and (> b (+ r 30)) (> b 80))       :blue-family
      :else                               :novelty)))

(def verdicts-002
  {:H11-weekday-anchor-band
   (let [ys (keep #(footer-y % #"(?i)(monday|tuesday|wednesday|thursday|friday|saturday|sunday)") pages)
         in (filter #(and (>= % 0.70) (<= % 0.85)) ys)]
     {:support (count in) :of (count ys)
      :detail (str "weekday ys: " (pr-str (map #(Math/round (* % 100.0)) ys)))})

   :H12-contact-closes-footer
   (let [ys (keep #(footer-y % #"(?i)ENQUIR") pages)
         in (filter #(>= % 0.88) ys)]
     {:support (count in) :of (count ys)
      :detail (str "enquiries ys: " (pr-str (map #(Math/round (* % 100.0)) ys)))})

   :H13-headline-band
   (let [big-ys (mapcat (fn [p] (map :y (filter #(> (:h %) 0.08) (:lines p)))) pages)
         in (filter #(and (>= % 0.28) (<= % 0.62)) big-ys)]
     {:support (count in) :of (count big-ys)
      :detail (str "big-line ys: " (pr-str (sort (map #(Math/round (* % 100.0)) big-ys))))})

   :H14-single-novelty-accent
   (let [counts (map (fn [p]
                       (let [swatches (mapcat (fn [[_band sws]] sws) (:bands p))
                             novel (filter (fn [{:keys [hex share]}]
                                             (and (> share 3) (= :novelty (color-class hex))))
                                           swatches)]
                         (count (distinct (map :hex novel)))))
                     pages)]
     {:support (count (filter #(<= % 1) counts)) :of n
      :detail (str "novelty-accent counts per page: " (pr-str counts))})

   :H15-big-text-centered
   (let [big (mapcat (fn [p] (filter #(> (:h %) 0.08) (:lines p))) pages)
         centered (filter #(< (Math/abs (- (+ (:x %) (/ (:w %) 2.0)) 0.5)) 0.12) big)]
     {:support (count centered) :of (count big)
      :detail (str "big lines: " (count big) ", centered within 0.12: " (count centered))})

   :H16-date-numeral-class
   (let [has (filter (fn [p] (some #(and (re-find #"^\d{1,2}$" (:t %)) (> (:h %) 0.08)) (:lines p))) pages)]
     {:support (count has) :of n
      :detail (str "pages with a huge standalone date digit: " (count has))})})

(doseq [[k v] verdicts-002]
  (let [verdict (cond (>= (:support v) (* 0.85 (:of v))) "CONFIRMED"
                      (>= (:support v) (* 0.5 (:of v)))  "REFINED"
                      :else                               "REFUTED")]
    (println (format "%-28s %-10s %d/%d  %s" (name k) verdict (:support v) (:of v) (:detail v)))))
