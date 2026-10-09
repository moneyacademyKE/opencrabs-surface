---
name: morning-newspaper
description: Daily morning digest. Fetches each feed in the source list, extracts items newer than the last edition, extracts each article's text, and posts one message per article with a long grounded summary. Reports only what's new. Triggers on: morning newspaper, morning digest, daily paper, what's new this morning.
---

# Morning Newspaper

You are the morning editor. One edition per day. Only new items since the
previous edition — no reruns, no filler. Every article gets a long summary
(target 3000–3800 chars, hard cap 3900) posted as its own message.

## State (all under `~/.opencrabs/state/morning-newspaper/`)

- `sources.txt` — one feed per line: `url | name | section`. Section is one of
  `tech`, `biz`, `kenya`, `video`. Blank lines and `#` comments ignored.
  If missing on first run, create it with the Default sources below.
- `seen/<name>.txt` — the links already reported for that source, newest last.
- `editions.log` — one line per edition: `YYYY-MM-DD <items> items <elapsed>`.

## Default sources (seed `sources.txt` with these on first run)

```
https://hnrss.org/frontpage | Hacker News | tech
https://blog.rust-lang.org/feed.xml | Rust Blog | tech
https://feeds.arstechnica.com/arstechnica/index | Ars Technica | tech
https://stratechery.com/feed/ | Stratechery | biz
```

## The run

The fetch/extract pipeline lives in
`~/.opencrabs/state/morning-newspaper/run.clj`. Run it with Babashka; do not
improvise a fresh parser.

1. `bb ~/.opencrabs/state/morning-newspaper/run.clj`
   - Reads `sources.txt`, fetches every feed, keeps only links not in
     `seen/`, extracts each article's text into `digest/NN.txt` (numbered in
     edition order, plus `digest/index.md`), updates `seen/` and
     `editions.log`, and prints the index followed by a final `@@STATS@@`
     line.
   - `DRYRUN=1 bb ... run.clj` skips state writes but still writes `digest/`
     (it is scratch) — use that for testing extraction quality.
   - If `sources.txt` is missing, seed it from Default sources below and
     re-run (each source is capped at 10 new items per run).
2. Read `digest/index.md`, then process the `NN.txt` files IN ORDER, one at
   a time — read one file, summarize it, post it, then move to the next.
   Never batch-read all digests into context.
3. Per article, post ONE message to chat `-1004427473737`, thread `9479`,
   via `telegram_send` (activate via `tool_search "telegram"` if needed),
   markdown mode:

   ```
   [Source] Title
   https://link

   <summary>
   ```

   - Summary: target 3000–3800 chars, hard cap 3900 so the whole message
     stays under Telegram's 4096 limit. If it truly must run longer, split
     at a paragraph boundary into a second message.
   - If the digest file says `UNAVAILABLE`: post just the title + link with
     a one-line note (e.g. "paywalled / PDF / no text extractable"). NEVER
     summarize from the title alone.
4. Quiet morning (script prints only the quiet line): that line IS the whole
   reply. Post nothing else.
5. End the turn with the stats line as plain text (drop the `@@STATS@@`
   prefix) — it becomes the cron delivery footer.

## Summary rules

- Ground every sentence in the extracted text. No outside knowledge, no
  inference beyond what the text states, no invented quotes or numbers.
- Plain prose, 2–6 short paragraphs. Cover the whole piece (beginning,
  middle, end), not just the lede. No "This article discusses" preamble, no
  closing moral.
- If the extracted text is clearly truncated or paywalled, say so in one
  short line at the end.

## Rules

- Report only. Never fetch with credentials, never follow paywalled links
  beyond the HEAD the feed gives you.
- A source that fails 3 editions in a row gets flagged in the report:
  `name: down 3 mornings — consider removing from sources.txt`.
- This runs on a cron and lands in a chat: the per-article messages go via
  `telegram_send`; your final text is just the stats footer. No preamble, no
  closing pleasantries.
