#!/usr/bin/env bb
(ns youtube-ingest
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]))

(def api "https://www.googleapis.com/youtube/v3")

(defn exit! [code msg]
  (binding [*out* *err*] (println msg))
  (System/exit code))

(defn env [k] (System/getenv k))

(defn get-json [path params]
  (let [key (env "YOUTUBE_API_KEY")]
    (when (str/blank? key)
      (exit! 2 "YOUTUBE_API_KEY is required for YouTube ingest; set it only when youtube source is enabled."))
    (let [resp (http/get (str api "/" path) {:query-params (assoc params "key" key)
                                             :throw false})
          body (json/parse-string (:body resp) true)]
      (when-let [err (:error body)]
        (exit! 1 (str "YouTube API error: " (or (:message err) err))))
      body)))

(defn doc-json [m]
  (json/generate-string m))

(defn opt-val [args flag default]
  (or (some->> args (partition 2 1) (some #(when (= flag (first %)) (second %)))) default))

(defn fetch-videos [uploads max-videos]
  (loop [items [] page nil]
    (if (>= (count items) max-videos)
      (take max-videos items)
      (let [params (cond-> {"part" "snippet,contentDetails"
                            "playlistId" uploads
                            "maxResults" (str (min 50 (- max-videos (count items))))}
                     page (assoc "pageToken" page))
            data (get-json "playlistItems" params)
            next-items (concat items (:items data))]
        (if-let [next-page (:nextPageToken data)]
          (recur next-items next-page)
          next-items)))))

(defn -main [& args]
  (let [handle (opt-val args "--handle" nil)
        max-videos (Integer/parseInt (opt-val args "--max" "30"))]
    (when (str/blank? handle)
      (exit! 2 "usage: bb scripts/youtube_ingest.bb --handle @channel [--max 30]"))
    (let [ch (get-in (get-json "channels" {"part" "contentDetails,snippet" "forHandle" handle}) [:items 0])]
      (when-not ch
        (exit! 1 (str "No YouTube channel found for handle " handle)))
      (let [uploads (get-in ch [:contentDetails :relatedPlaylists :uploads])
            channel-name (get-in ch [:snippet :title])]
        (doseq [item (fetch-videos uploads max-videos)]
          (let [sn (:snippet item)
                vid (get-in item [:contentDetails :videoId])]
            (println
             (doc-json
              {:source "youtube"
               :source_id vid
               :ts (:publishedAt sn)
               :title (:title sn)
               :content (or (:description sn) "")
               :metadata {:channel channel-name
                          :url (str "https://youtu.be/" vid)
                          :has_transcript false}}))))))))

(apply -main *command-line-args*)
