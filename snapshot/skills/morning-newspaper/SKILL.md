---
name: morning-newspaper
description: Daily morning digest. Fetches each feed in the source list, extracts items newer than the last edition, and composes a compact newspaper. Reports only what's new. Triggers on: morning newspaper, morning digest, daily paper, what's new this morning.
---

# Morning Newspaper

You are the morning editor. One edition per day, short and scannable. Only new
items since the previous edition — no reruns, no filler.

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

The whole pipeline lives in `~/.opencrabs/state/morning-newspaper/run.clj`.
Run it with Babashka; do not improvise a fresh parser.

1. `bb ~/.opencrabs/state/morning-newspaper/run.clj`
   - It reads `sources.txt`, fetches every feed, keeps only links not in
     `seen/`, resolves a summary per item, updates `seen/` and
     `editions.log`, and prints the edition.
   - `DRYRUN=1 bb ... run.clj` prints the edition without touching state —
     use that for testing.
   - If `sources.txt` is missing, seed it from Default sources below and
     re-run (first edition is capped at 10 items per source).
2. Output format: one or more messages separated by a line containing only
   `@@MSG@@`, then a final line starting `@@STATS@@`.
   - Post each message IN ORDER to the delivery topic with `telegram_send`
     (activate it via `tool_search "telegram"` if needed):
     chat `-1004427473737`, thread `9479`, markdown.
   - End the turn with the stats line as plain text (drop the `@@STATS@@`
     prefix) — it becomes the cron delivery footer.
3. Quiet morning (no `@@MSG@@`, just one line from the script): that line IS
   the whole reply. Post nothing else.
4. A source that fails is noted on the stats line as `unreachable: <name>`.
   Never drop a source for one bad morning.

## Format

Per item, two lines:

```
[Source Name] Title — https://link
One-line summary, max ~200 chars.
```

The summary is grounded: the feed's own excerpt (Ars, Stratechery, Rust
Blog), else the article page's `og:description`/`meta description`
(Hacker News — hnrss descriptions are metadata, never use them as
summaries). If neither yields text, the item is a single bare line.
**Never invent a summary from the title.**

## Rules

- Report only. Never fetch with credentials, never follow paywalled links
  beyond the HEAD the feed gives you.
- A source that fails 3 editions in a row gets flagged in the report:
  `name: down 3 mornings — consider removing from sources.txt`.
- This runs on a cron and lands in a chat: tight lines, no preamble, no
  closing pleasantries. The edition parts go via `telegram_send`; your final
  text is just the stats footer.
