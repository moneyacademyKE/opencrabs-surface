use anyhow::{Context, Result, anyhow};
use clap::{Parser, Subcommand, ValueEnum};
use rusqlite::{Connection, OptionalExtension, params};
use serde::{Deserialize, Serialize};
use serde_json::json;
use std::fs::File;
use std::io::{self, BufRead, BufReader};
use std::path::PathBuf;

const SCHEMA: &str = r#"
CREATE TABLE IF NOT EXISTS docs (
    id INTEGER PRIMARY KEY,
    source TEXT NOT NULL,
    source_id TEXT NOT NULL,
    ts TEXT,
    title TEXT,
    content TEXT,
    metadata TEXT,
    embedding BLOB,
    visibility TEXT NOT NULL DEFAULT 'internal',
    origin TEXT NOT NULL DEFAULT 'system',
    UNIQUE(source, source_id)
);
CREATE VIRTUAL TABLE IF NOT EXISTS docs_fts USING fts5(
    title, content, content='docs', content_rowid='id'
);
CREATE TABLE IF NOT EXISTS ingest_runs (
    id INTEGER PRIMARY KEY,
    source TEXT NOT NULL,
    started_at TEXT NOT NULL,
    finished_at TEXT NOT NULL,
    status TEXT NOT NULL,
    attempted INTEGER NOT NULL DEFAULT 0,
    inserted INTEGER NOT NULL DEFAULT 0,
    updated INTEGER NOT NULL DEFAULT 0,
    unchanged INTEGER NOT NULL DEFAULT 0,
    failed INTEGER NOT NULL DEFAULT 0,
    error TEXT,
    metadata TEXT
);
CREATE TRIGGER IF NOT EXISTS docs_ai AFTER INSERT ON docs BEGIN
    INSERT INTO docs_fts(rowid, title, content) VALUES (new.id, new.title, new.content);
END;
CREATE TRIGGER IF NOT EXISTS docs_ad AFTER DELETE ON docs BEGIN
    INSERT INTO docs_fts(docs_fts, rowid, title, content) VALUES ('delete', old.id, old.title, old.content);
END;
CREATE TRIGGER IF NOT EXISTS docs_au AFTER UPDATE ON docs BEGIN
    INSERT INTO docs_fts(docs_fts, rowid, title, content) VALUES ('delete', old.id, old.title, old.content);
    INSERT INTO docs_fts(rowid, title, content) VALUES (new.id, new.title, new.content);
END;
"#;

#[derive(Parser)]
#[command(name = "kb", about = "OpenCrabs knowledge-base sidecar")]
struct Cli {
    #[arg(long, env = "KB_DB")]
    db: Option<PathBuf>,
    #[command(subcommand)]
    command: Command,
}

#[derive(Subcommand)]
enum Command {
    Doctor {
        #[arg(long)]
        json: bool,
    },
    InitDb {
        #[arg(long)]
        json: bool,
    },
    Stats {
        #[arg(long)]
        json: bool,
        #[arg(long, default_value_t = 5)]
        recent_runs: usize,
    },
    ExportSchema,
    UpsertJsonl {
        #[arg(value_name = "FILE")]
        file: Option<PathBuf>,
        #[arg(long)]
        json: bool,
        #[arg(long)]
        source: Option<String>,
        #[arg(long)]
        run_started_at: Option<String>,
        #[arg(long)]
        error: Option<String>,
    },
    RecordRun {
        source: String,
        #[arg(long)]
        json: bool,
        #[arg(long, default_value_t = 0)]
        attempted: i64,
        #[arg(long, default_value_t = 0)]
        inserted: i64,
        #[arg(long, default_value_t = 0)]
        updated: i64,
        #[arg(long, default_value_t = 0)]
        unchanged: i64,
        #[arg(long, default_value_t = 0)]
        failed: i64,
        #[arg(long)]
        started_at: Option<String>,
        #[arg(long)]
        error: Option<String>,
        #[arg(long)]
        metadata: Option<String>,
    },
    Search {
        query: String,
        #[arg(long, default_value_t = 10)]
        limit: usize,
        #[arg(long)]
        json: bool,
        #[arg(long, default_value = "snippets")]
        mode: OutputMode,
        #[arg(long)]
        source: Option<String>,
        #[arg(long)]
        private: bool,
    },
}

#[derive(Clone, Debug, ValueEnum)]
enum OutputMode {
    Titles,
    Snippets,
    Full,
}

#[derive(Debug, Deserialize)]
struct DocIn {
    source: String,
    source_id: String,
    ts: Option<String>,
    title: Option<String>,
    content: Option<String>,
    metadata: Option<serde_json::Value>,
    embedding: Option<Vec<f32>>,
    visibility: Option<String>,
    origin: Option<String>,
}

#[derive(Debug, Serialize)]
struct SourceStats {
    source: String,
    n: i64,
    embedded: i64,
    newest: Option<String>,
}

#[derive(Debug, Serialize)]
struct IngestRun {
    id: i64,
    source: String,
    started_at: String,
    finished_at: String,
    status: String,
    attempted: i64,
    inserted: i64,
    updated: i64,
    unchanged: i64,
    failed: i64,
    error: Option<String>,
    metadata: Option<String>,
}

#[derive(Debug, Serialize)]
struct StatsPayload {
    sources: Vec<SourceStats>,
    recent_runs: Vec<IngestRun>,
}

#[derive(Debug, Serialize)]
struct SearchHit {
    id: i64,
    source: String,
    source_id: String,
    ts: Option<String>,
    title: Option<String>,
    snippet: Option<String>,
    content: Option<String>,
    visibility: String,
    origin: String,
    metadata: serde_json::Value,
}

#[derive(Debug, Serialize)]
struct UpsertSummary {
    source: String,
    attempted: i64,
    inserted: i64,
    updated: i64,
    unchanged: i64,
    failed: i64,
    started_at: String,
    finished_at: String,
    status: String,
    error: Option<String>,
}

fn main() -> Result<()> {
    let cli = Cli::parse();
    let db_path = cli.db.unwrap_or_else(default_db_path);
    match cli.command {
        Command::Doctor { json } => doctor(&db_path, json),
        Command::InitDb { json } => {
            let conn = connect(&db_path)?;
            init_db(&conn)?;
            if json {
                println!(
                    "{}",
                    serde_json::to_string_pretty(&json!({"ok": true, "db": db_path}))?
                );
            } else {
                println!("initialized {}", db_path.display());
            }
            Ok(())
        }
        Command::Stats { json, recent_runs } => {
            let conn = connect(&db_path)?;
            init_db(&conn)?;
            let payload = StatsPayload {
                sources: stats(&conn)?,
                recent_runs: recent_runs_query(&conn, recent_runs)?,
            };
            if json {
                println!("{}", serde_json::to_string_pretty(&payload)?);
            } else {
                for r in &payload.sources {
                    println!(
                        "{:<8} {:>5} docs  {} embedded  newest {}",
                        r.source,
                        r.n,
                        r.embedded,
                        r.newest.clone().unwrap_or_default()
                    );
                }
                if !payload.recent_runs.is_empty() {
                    println!("\nrecent ingest runs:");
                    for r in &payload.recent_runs {
                        println!(
                            "{:<8} {:<7} attempted={} inserted={} updated={} unchanged={} failed={} {}",
                            r.source,
                            r.status,
                            r.attempted,
                            r.inserted,
                            r.updated,
                            r.unchanged,
                            r.failed,
                            r.finished_at
                        );
                    }
                }
            }
            Ok(())
        }
        Command::ExportSchema => {
            print!("{}", SCHEMA);
            Ok(())
        }
        Command::UpsertJsonl {
            file,
            json,
            source,
            run_started_at,
            error,
        } => {
            let conn = connect(&db_path)?;
            init_db(&conn)?;
            let summary = upsert_jsonl(&conn, file, source, run_started_at, error)?;
            if json {
                println!("{}", serde_json::to_string_pretty(&summary)?);
            } else {
                println!(
                    "source={} status={} attempted={} inserted={} updated={} unchanged={} failed={}",
                    summary.source,
                    summary.status,
                    summary.attempted,
                    summary.inserted,
                    summary.updated,
                    summary.unchanged,
                    summary.failed
                );
            }
            Ok(())
        }
        Command::RecordRun {
            source,
            json,
            attempted,
            inserted,
            updated,
            unchanged,
            failed,
            started_at,
            error,
            metadata,
        } => {
            let conn = connect(&db_path)?;
            init_db(&conn)?;
            let started = started_at.unwrap_or_else(now_iso);
            let finished = now_iso();
            let status = run_status(failed, &error);
            record_ingest_run(
                &conn,
                &source,
                &started,
                &finished,
                &status,
                attempted,
                inserted,
                updated,
                unchanged,
                failed,
                error.as_deref(),
                metadata.as_deref(),
            )?;
            let payload = json!({"source": source, "status": status, "attempted": attempted, "inserted": inserted, "updated": updated, "unchanged": unchanged, "failed": failed, "started_at": started, "finished_at": finished, "error": error});
            if json {
                println!("{}", serde_json::to_string_pretty(&payload)?);
            } else {
                println!("{}", payload);
            }
            Ok(())
        }
        Command::Search {
            query,
            limit,
            json,
            mode,
            source,
            private,
        } => {
            let conn = connect(&db_path)?;
            init_db(&conn)?;
            if matches!(mode, OutputMode::Full) && !private {
                return Err(anyhow!(
                    "refusing full-content output without --private; use --mode snippets or --mode titles"
                ));
            }
            let hits = search(&conn, &query, limit, &mode, source.as_deref())?;
            if json {
                println!("{}", serde_json::to_string_pretty(&hits)?);
            } else {
                for h in hits {
                    println!(
                        "[{}] {} {} {}",
                        h.source,
                        h.id,
                        h.ts.unwrap_or_default(),
                        h.visibility
                    );
                    println!("  {}", h.title.unwrap_or_default());
                    if let Some(snippet) = h.snippet {
                        println!("  {}", snippet);
                    }
                    if let Some(content) = h.content {
                        println!("  {}", content);
                    }
                    println!();
                }
            }
            Ok(())
        }
    }
}

fn default_db_path() -> PathBuf {
    dirs::home_dir()
        .unwrap_or_else(|| PathBuf::from("."))
        .join(".opencrabs/projects/knowledge-base/files/nsm_kb.sqlite")
}

fn connect(path: &PathBuf) -> Result<Connection> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)
            .with_context(|| format!("create db parent {}", parent.display()))?;
    }
    Connection::open(path).with_context(|| format!("open sqlite db {}", path.display()))
}

fn init_db(conn: &Connection) -> Result<()> {
    conn.execute_batch(SCHEMA).context("initialize schema")?;
    ensure_column(
        conn,
        "docs",
        "visibility",
        "TEXT NOT NULL DEFAULT 'internal'",
    )?;
    ensure_column(conn, "docs", "origin", "TEXT NOT NULL DEFAULT 'system'")?;
    Ok(())
}

fn ensure_column(conn: &Connection, table: &str, column: &str, definition: &str) -> Result<()> {
    let mut stmt = conn.prepare(&format!("PRAGMA table_info({})", table))?;
    let names = stmt
        .query_map([], |r| r.get::<_, String>(1))?
        .collect::<std::result::Result<Vec<_>, _>>()?;
    if !names.iter().any(|n| n == column) {
        conn.execute(
            &format!("ALTER TABLE {} ADD COLUMN {} {}", table, column, definition),
            [],
        )?;
    }
    Ok(())
}

fn now_iso() -> String {
    chrono::Utc::now().to_rfc3339()
}

fn doctor(db_path: &PathBuf, as_json: bool) -> Result<()> {
    let db_exists = db_path.exists();
    let conn = connect(db_path)?;
    let fts5_ok = conn
        .execute(
            "CREATE VIRTUAL TABLE IF NOT EXISTS __kb_fts_probe USING fts5(x)",
            [],
        )
        .and_then(|_| conn.execute("DROP TABLE __kb_fts_probe", []))
        .is_ok();
    let schema_ready = init_db(&conn).is_ok();
    let payload = json!({"db": db_path, "db_exists": db_exists, "sqlite_open": true, "fts5": fts5_ok, "schema_ready": schema_ready, "active_sources": ["github", "youtube"]});
    if as_json {
        println!("{}", serde_json::to_string_pretty(&payload)?);
    } else {
        println!("ok   db           {}", db_path.display());
        println!(
            "{} sqlite_open  true",
            if payload["sqlite_open"].as_bool().unwrap_or(false) {
                "ok  "
            } else {
                "fail"
            }
        );
        println!(
            "{} fts5         {}",
            if fts5_ok { "ok  " } else { "fail" },
            fts5_ok
        );
        println!(
            "{} schema_ready {}",
            if schema_ready { "ok  " } else { "fail" },
            schema_ready
        );
        println!("ok   active_sources github,youtube");
    }
    Ok(())
}

fn stats(conn: &Connection) -> Result<Vec<SourceStats>> {
    let mut stmt = conn.prepare("SELECT source, COUNT(*) AS n, SUM(embedding IS NOT NULL) AS embedded, MAX(ts) AS newest FROM docs GROUP BY source ORDER BY source")?;
    let rows = stmt.query_map([], |r| {
        Ok(SourceStats {
            source: r.get(0)?,
            n: r.get(1)?,
            embedded: r.get(2)?,
            newest: r.get(3)?,
        })
    })?;
    rows.collect::<std::result::Result<Vec<_>, _>>()
        .map_err(Into::into)
}

fn recent_runs_query(conn: &Connection, limit: usize) -> Result<Vec<IngestRun>> {
    let mut stmt = conn.prepare("SELECT id, source, started_at, finished_at, status, attempted, inserted, updated, unchanged, failed, error, metadata FROM ingest_runs ORDER BY id DESC LIMIT ?")?;
    let rows = stmt.query_map(params![limit as i64], |r| {
        Ok(IngestRun {
            id: r.get(0)?,
            source: r.get(1)?,
            started_at: r.get(2)?,
            finished_at: r.get(3)?,
            status: r.get(4)?,
            attempted: r.get(5)?,
            inserted: r.get(6)?,
            updated: r.get(7)?,
            unchanged: r.get(8)?,
            failed: r.get(9)?,
            error: r.get(10)?,
            metadata: r.get(11)?,
        })
    })?;
    rows.collect::<std::result::Result<Vec<_>, _>>()
        .map_err(Into::into)
}

fn embedding_bytes(vec: Option<Vec<f32>>) -> Option<Vec<u8>> {
    vec.map(|v| v.into_iter().flat_map(|f| f.to_le_bytes()).collect())
}

fn visibility_for(d: &DocIn) -> String {
    d.visibility
        .clone()
        .unwrap_or_else(|| match d.source.as_str() {
            "youtube" => "public".into(),
            "github" => "internal".into(),
            _ => "internal".into(),
        })
}

fn origin_for(d: &DocIn) -> String {
    d.origin.clone().unwrap_or_else(|| match d.source.as_str() {
        "youtube" => "public_web".into(),
        "github" => "team".into(),
        _ => "system".into(),
    })
}

fn upsert_doc(conn: &Connection, d: &DocIn) -> Result<&'static str> {
    let meta = serde_json::to_string(&d.metadata.clone().unwrap_or_else(|| json!({})))?;
    let existing: Option<(i64, String)> = conn
        .query_row(
            "SELECT id, COALESCE(content, '') FROM docs WHERE source=? AND source_id=?",
            params![d.source, d.source_id],
            |r| Ok((r.get(0)?, r.get(1)?)),
        )
        .optional()?;
    let content = d.content.clone().unwrap_or_default();
    let title = d.title.clone().unwrap_or_default();
    let visibility = visibility_for(d);
    let origin = origin_for(d);
    match existing {
        None => {
            conn.execute("INSERT INTO docs (source, source_id, ts, title, content, metadata, embedding, visibility, origin) VALUES (?,?,?,?,?,?,?,?,?)",
                         params![d.source, d.source_id, d.ts, title, content, meta, embedding_bytes(d.embedding.clone()), visibility, origin])?;
            Ok("inserted")
        }
        Some((id, old_content)) if old_content != content => {
            conn.execute("UPDATE docs SET ts=?, title=?, content=?, metadata=?, embedding=?, visibility=?, origin=? WHERE id=?",
                         params![d.ts, title, content, meta, embedding_bytes(d.embedding.clone()), visibility, origin, id])?;
            Ok("updated")
        }
        Some(_) => Ok("unchanged"),
    }
}

fn upsert_jsonl(
    conn: &Connection,
    file: Option<PathBuf>,
    source: Option<String>,
    run_started_at: Option<String>,
    run_error: Option<String>,
) -> Result<UpsertSummary> {
    let reader: Box<dyn BufRead> = match file {
        Some(path) => Box::new(BufReader::new(
            File::open(&path).with_context(|| format!("open {}", path.display()))?,
        )),
        None => Box::new(BufReader::new(io::stdin())),
    };
    let mut attempted = 0;
    let mut inserted = 0;
    let mut updated = 0;
    let mut unchanged = 0;
    let mut failed = 0;
    let mut inferred_source = source.unwrap_or_else(|| "unknown".into());
    for (idx, line) in reader.lines().enumerate() {
        let line = line?;
        if line.trim().is_empty() {
            continue;
        }
        attempted += 1;
        match serde_json::from_str::<DocIn>(&line)
            .map_err(|e| anyhow!("line {}: {}", idx + 1, e))
            .and_then(|d| {
                inferred_source = d.source.clone();
                upsert_doc(conn, &d).map(str::to_string)
            }) {
            Ok(status) if status == "inserted" => inserted += 1,
            Ok(status) if status == "updated" => updated += 1,
            Ok(_) => unchanged += 1,
            Err(e) => {
                failed += 1;
                eprintln!("{}", e);
            }
        }
    }
    if run_error.is_some() {
        failed += 1;
    }
    let started = run_started_at.unwrap_or_else(now_iso);
    let finished = now_iso();
    let status = run_status(failed, &run_error);
    record_ingest_run(
        conn,
        &inferred_source,
        &started,
        &finished,
        &status,
        attempted,
        inserted,
        updated,
        unchanged,
        failed,
        run_error.as_deref(),
        None,
    )?;
    Ok(UpsertSummary {
        source: inferred_source,
        attempted,
        inserted,
        updated,
        unchanged,
        failed,
        started_at: started,
        finished_at: finished,
        status,
        error: run_error,
    })
}

fn run_status(failed: i64, error: &Option<String>) -> String {
    if error.is_some() {
        "fail".into()
    } else if failed > 0 {
        "partial".into()
    } else {
        "success".into()
    }
}

fn record_ingest_run(
    conn: &Connection,
    source: &str,
    started_at: &str,
    finished_at: &str,
    status: &str,
    attempted: i64,
    inserted: i64,
    updated: i64,
    unchanged: i64,
    failed: i64,
    error: Option<&str>,
    metadata: Option<&str>,
) -> Result<()> {
    conn.execute(
        "INSERT INTO ingest_runs (source, started_at, finished_at, status, attempted, inserted, updated, unchanged, failed, error, metadata) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
        params![source, started_at, finished_at, status, attempted, inserted, updated, unchanged, failed, error, metadata],
    )?;
    Ok(())
}

fn fts_query(query: &str) -> String {
    query
        .split_whitespace()
        .filter(|s| !s.trim().is_empty())
        .map(|s| format!("\"{}\"", s.replace('"', "")))
        .collect::<Vec<_>>()
        .join(" OR ")
}

fn search(
    conn: &Connection,
    query: &str,
    limit: usize,
    mode: &OutputMode,
    source: Option<&str>,
) -> Result<Vec<SearchHit>> {
    if query.trim().is_empty() {
        return Ok(vec![]);
    }
    let fts = fts_query(query);
    let source_clause = if source.is_some() {
        " AND docs.source=?"
    } else {
        ""
    };
    let sql = format!(
        "SELECT docs.id, docs.source, docs.source_id, docs.ts, docs.title, substr(replace(replace(COALESCE(docs.content, ''), char(10), ' '), char(13), ' '), 1, 300) AS snippet, COALESCE(docs.content, ''), COALESCE(docs.visibility, 'internal'), COALESCE(docs.origin, 'system'), COALESCE(docs.metadata, '{{}}')
         FROM docs_fts JOIN docs ON docs.id = docs_fts.rowid
         WHERE docs_fts MATCH ?{} ORDER BY docs_fts.rank LIMIT ?", source_clause);
    let mut stmt = conn
        .prepare(&sql)
        .context("prepare lexical FTS5 search; is docs_fts available?")?;
    let map_row = |r: &rusqlite::Row<'_>| -> rusqlite::Result<SearchHit> {
        let metadata_raw: String = r.get(9)?;
        let metadata = serde_json::from_str(&metadata_raw).unwrap_or_else(|_| json!({}));
        Ok(SearchHit {
            id: r.get(0)?,
            source: r.get(1)?,
            source_id: r.get(2)?,
            ts: r.get(3)?,
            title: r.get(4)?,
            snippet: if matches!(mode, OutputMode::Snippets) {
                Some(r.get(5)?)
            } else {
                None
            },
            content: if matches!(mode, OutputMode::Full) {
                Some(r.get::<_, String>(6)?)
            } else {
                None
            },
            visibility: r.get(7)?,
            origin: r.get(8)?,
            metadata,
        })
    };
    let rows = if let Some(src) = source {
        stmt.query_map(params![fts, src, limit as i64], map_row)?
            .collect::<std::result::Result<Vec<_>, _>>()?
    } else {
        stmt.query_map(params![fts, limit as i64], map_row)?
            .collect::<std::result::Result<Vec<_>, _>>()?
    };
    Ok(rows)
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::NamedTempFile;

    fn temp_conn() -> Connection {
        let conn = Connection::open_in_memory().unwrap();
        init_db(&conn).unwrap();
        conn
    }

    #[test]
    fn empty_db_search_returns_empty() {
        let conn = temp_conn();
        let hits = search(&conn, "needle", 10, &OutputMode::Snippets, None).unwrap();
        assert!(hits.is_empty());
    }

    #[test]
    fn duplicate_upserts_report_unchanged_then_updated() {
        let conn = temp_conn();
        let doc = DocIn {
            source: "test".into(),
            source_id: "1".into(),
            ts: Some("2026-01-01T00:00:00Z".into()),
            title: Some("Alpha".into()),
            content: Some("first content".into()),
            metadata: Some(json!({"k":"v"})),
            embedding: None,
            visibility: None,
            origin: None,
        };
        assert_eq!(upsert_doc(&conn, &doc).unwrap(), "inserted");
        assert_eq!(upsert_doc(&conn, &doc).unwrap(), "unchanged");
        let changed = DocIn {
            content: Some("second content".into()),
            ..doc
        };
        assert_eq!(upsert_doc(&conn, &changed).unwrap(), "updated");
    }

    #[test]
    fn lexical_search_finds_inserted_doc() {
        let conn = temp_conn();
        let doc = DocIn {
            source: "test".into(),
            source_id: "1".into(),
            ts: None,
            title: Some("Rust sidecar".into()),
            content: Some("Lexical search works without embeddings".into()),
            metadata: None,
            embedding: None,
            visibility: None,
            origin: None,
        };
        upsert_doc(&conn, &doc).unwrap();
        let hits = search(&conn, "sidecar", 10, &OutputMode::Snippets, None).unwrap();
        assert_eq!(hits.len(), 1);
        assert_eq!(hits[0].source, "test");
        assert_eq!(hits[0].visibility, "internal");
    }

    #[test]
    fn malformed_jsonl_is_counted_as_failed_and_records_run() {
        let conn = temp_conn();
        let path = NamedTempFile::new().unwrap();
        std::fs::write(path.path(), "not-json\n").unwrap();
        let summary = upsert_jsonl(
            &conn,
            Some(path.path().to_path_buf()),
            Some("test".into()),
            None,
            None,
        )
        .unwrap();
        assert_eq!(summary.attempted, 1);
        assert_eq!(summary.failed, 1);
        assert_eq!(recent_runs_query(&conn, 1).unwrap()[0].status, "partial");
    }

    #[test]
    fn missing_fts5_table_has_clear_error() {
        let conn = temp_conn();
        conn.execute("DROP TABLE docs_fts", []).unwrap();
        let err = search(&conn, "anything", 10, &OutputMode::Snippets, None)
            .unwrap_err()
            .to_string();
        assert!(err.contains("FTS5") || err.contains("docs_fts"));
    }

    #[test]
    fn embedding_is_little_endian_float_blob_compatible() {
        let bytes = embedding_bytes(Some(vec![1.0, 2.5])).unwrap();
        assert_eq!(bytes.len(), 8);
        assert_eq!(f32::from_le_bytes(bytes[0..4].try_into().unwrap()), 1.0);
        assert_eq!(f32::from_le_bytes(bytes[4..8].try_into().unwrap()), 2.5);
    }

    #[test]
    fn full_mode_requires_policy_at_cli_but_function_returns_content() {
        let conn = temp_conn();
        let doc = DocIn {
            source: "youtube".into(),
            source_id: "v1".into(),
            ts: None,
            title: Some("Public video".into()),
            content: Some("full public transcript".into()),
            metadata: None,
            embedding: None,
            visibility: None,
            origin: None,
        };
        upsert_doc(&conn, &doc).unwrap();
        let hits = search(&conn, "transcript", 10, &OutputMode::Full, Some("youtube")).unwrap();
        assert_eq!(hits[0].visibility, "public");
        assert_eq!(hits[0].content.as_deref(), Some("full public transcript"));
    }
}
