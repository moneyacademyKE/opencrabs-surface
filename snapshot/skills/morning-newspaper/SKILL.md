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

1. Read `sources.txt`. If it doesn't exist, seed it from Default sources and
   continue (first edition will be large; cap it at 10 items per source).
2. For each source:
   a. Fetch the feed (`curl -sL --max-time 15`). On failure: note
      `name: unreachable` and move on — never drop a source for one bad morning.
   b. Extract `<item>` (or `<entry>`) titles, links, and dates. Links are the
      identity — dedupe on them, ignore dates you can't parse.
   c. Keep only links not in `seen/<name>.txt`. No file yet → first edition:
      keep the 5 newest, mark the rest seen silently.
3. Compose the edition, grouped by section in this order: `kenya`, `tech`,
   `biz`, `video`. Per item: one line, `[name] title` + the link. Cap the
   whole edition at ~35 lines; if over, keep the newest and end with
   `+N more <section> items`.
4. If every source came back empty or unreachable: reply with one line —
   `Quiet morning — 0 new items across N sources.` Still append to the log.
5. After a successful edition: append the new links to each `seen/<name>.txt`
   (trim any file over 2000 lines from the top), append `editions.log`.

## Rules

- Report only. Never fetch with credentials, never follow paywalled links
  beyond the HEAD the feed gives you.
- A source that fails 3 editions in a row gets flagged in the report:
  `name: down 3 mornings — consider removing from sources.txt`.
- This runs on a cron and lands in a chat: tight lines, no preamble, no
  closing pleasantries. The edition IS the message.
