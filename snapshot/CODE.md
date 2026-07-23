# CODE.md — How You Write Code

> **Owns:** how you write code — standards, file organization, testing, security-first, and the Rust-First Policy.

*You build things. Build them right.*

---

## Philosophy

**Single binary. Run it. Delete the mess. Move on.**

You are not a framework junkie. You don't leave build artifacts rotting on disk. You compile, verify it works, clean up, and ship. If something needs to change — rebuild from scratch. Binaries are disposable. Source is sacred.

**Rust first. Always.** When choosing a language, Rust wins unless there's a concrete reason it can't (browser JS, platform SDK requirement, etc.). Native, safe, fast, single binary. No runtime dependencies. No "just install Node and Python and Java and..." — one binary, done.

**Small is beautiful.** Every line of code is a liability. Less code = less bugs = less maintenance. If you can solve it in 50 lines, don't write 200. If a dependency does it well, don't rewrite it. But if a dependency drags in half the internet, write it yourself.

---

## File Organization

### Hard Limits

- **No file over 500 lines.** If you're approaching that, you've already waited too long to split.
- **No file over 300 lines** without a damn good reason and a plan to refactor.
- **Target: 100-250 lines per file.** That's the sweet spot. Easy to read, easy to review, easy to test.

### Structure

Every module follows this pattern — **separate concerns into separate files:**

```
feature/
├── mod.rs          # Public API, re-exports (thin — just wiring)
├── types.rs        # Structs, enums, type aliases
├── handler.rs      # Request handling / business logic
├── utils.rs        # Helper functions (only if needed)
└── tests/
    ├── mod.rs      # Test module aggregator
    └── feature_test.rs
```

**Types live in `types.rs`.** Not scattered across handler files. Not inline in the module root. Dedicated file.

**Handlers handle.** They don't define types, don't contain utilities, don't hold test code. They receive, process, respond.

**One responsibility per file.** If you can't describe what a file does in one sentence, it does too much.

### When to Split

- File crosses 250 lines → think about splitting
- File crosses 400 lines → split now, no excuses
- You're adding a second "section" with its own types/logic → new file
- You find yourself scrolling to find things → too long

### Anti-Patterns

- **God files** — one file that does everything. Split it.
- **Copy-paste walls** — 10+ duplicated lines. Extract a function.
- **Inline type definitions** — types buried inside handler functions. Pull them out to `types.rs`.
- **"I'll refactor later"** — no you won't. Do it now while context is fresh.

---

## Testing

### Tests Live in Dedicated Files

**Tests go in a `tests/` directory, not at the bottom of source files.**

```
src/
├── feature/

│   ├── types.rs
│   ├── handler.rs
│   └── tests/          # ← tests here
│       ├── mod.rs
│       └── handler_test.rs
├── tests/              # ← or project-level test directory

│   └── feature_test.rs
```

- **Naming:** `*_test.rs` — always. Consistent, searchable, obvious.
- **Inline `#[cfg(test)]` only** for trivial assertions (under 30 lines). Anything beyond that → dedicated test file.
- **Shared test helpers** go in `tests/mod.rs` or a `test_utils` module. Never duplicate mock setups across test files.

### Test Every Build

**You don't get to say "it compiles, ship it."**

1. **Write the code.**
2. **Write tests for it** — in a dedicated test file.
3. **Run the full test suite.** Not just your new tests — everything. Regressions are real.
4. **If tests fail, fix before moving on.** No "I'll fix that later" — later is now.

### What to Test

- **Every public function** gets at least one test.
- **Every error path** gets a test. Happy path alone is not coverage.
- **Edge cases** — empty input, max values, Unicode, concurrent access.
- **Integration points** — where your code talks to external systems, mock and test the boundary.

### What NOT to Test

- Private implementation details that change often.
- Trivial getters/setters with no logic.
- Third-party library internals — trust or replace, don't test.

---

## Security-First

### Non-Negotiable

- **Validate all external input.** User input, API responses, file contents, environment variables — anything from outside your control gets validated before use.
- **No hardcoded secrets.** Not in source, not in tests, not in comments. Use environment variables, config files (chmod 600), or secret managers.
- **No `unwrap()` on user data** (Rust). Use `?`, `.unwrap_or()`, or proper error handling. Panics on bad input = denial of service.
- **Sanitize output.** HTML, SQL, shell commands — if user data touches these, escape it.
- **Principle of least privilege.** Don't request permissions you don't need. Don't read files you don't need. Don't open ports you don't need.

### Dependency Hygiene

- **Audit before adding.** Check the crate/package: maintainer reputation, recent activity, dependency tree, security advisories.
- **Minimal dependencies.** Every dependency is an attack surface. If you can write it in 20 lines, don't add a crate for it.
- **Pin versions.** Lock files exist for a reason. No floating versions in production.
- **No yanked/deprecated packages.** Check before adding.

### Code Patterns

- **Fail explicitly.** Return errors, don't swallow them. `let _ = dangerous_thing()` is a bug.
- **No shell injection.** Never pass unsanitized strings to `Command::new()` or `exec()` or `system()`. Use argument arrays.
- **Constant-time comparison** for secrets (tokens, passwords, API keys).
- **Bound all buffers.** No unbounded reads from network or files. Set limits.

---

## Build Workflow

### The Loop

```
1. Write code
2. Build → single binary
3. Run binary, verify it works
4. Run tests, verify coverage
5. Delete build artifacts (target/, dist/, node_modules/, __pycache__/, etc.)
6. If changes needed → go to 1
```

### Language-Specific

**Rust (preferred):**
```bash
cargo clippy --all-features          # lint — NOT cargo check
cargo test --all-features            # test everything
cargo build --release --all-features # release binary
# clean when done: cargo clean
```

**Go:**
```bash
go vet ./...
go test ./...
go build -o binary .
# clean: rm binary
```

**Python (when unavoidable):**
```bash
python3 -m pytest tests/
python3 -m py_compile script.py
# no build artifacts, but clean __pycache__/
```


```bash
npm test
npm run build
# clean: rm -rf dist/ node_modules/
```

### Clean Up After Yourself

Build artifacts are temporary. Don't commit them. Don't leave them. The repo should be source code and nothing else.

---

## Rust

- **Do NOT use unwraps or anything that can panic in Rust code, handle errors.** Obviously in tests unwraps and panics are fine!
- In Rust code prefer using `crate::` to `super::`; don't use `super::`. If you see a lingering `super::` from someone else clean it up.
- Avoid `pub use` on imports unless you are re-exposing a dependency so downstream consumers do not have to depend on it directly.
- Skip global state via `lazy_static!`, `Once`, or similar; prefer passing explicit context structs for any shared state.
- **No mock tests.** Unit tests and e2e tests hit real implementations (real DB, real structs). Mocks hide bugs — if the mock passes but prod breaks, the test was worthless.
- All tests live in `src/tests/` as dedicated `*_test.rs` files — not inline, not scattered.

### Rust-First Policy

This is the single home for the Rust-first rule (AGENTS.md and BOOT.md point here).

When searching for new integrations, libraries, or adding new features, **always prioritize Rust-based crates** over wrappers, FFI bindings, or other-language alternatives. Performance is non-negotiable — native Rust keeps the stack lean, safe, and fast. Only fall back to non-Rust solutions when no viable crate exists.

---

## Architecture Principles

### Read Before Writing

**Understand the existing codebase before adding code.** Read the module structure. Follow the patterns already established. Don't introduce a new convention when one exists.

### Match Existing Patterns

If the project uses X pattern, use X pattern. Consistency beats "better" in isolation. If you genuinely think the existing pattern is wrong, flag it — don't silently introduce a competing pattern.

### No Premature Abstraction

Three similar lines of code is fine. Don't create a `GenericHandlerFactoryBuilder` for two use cases. Abstract when you have 3+ concrete cases that genuinely share logic.

### Error Handling

- **Return errors up the call stack.** Let the caller decide what to do.
- **Log at the boundary, not deep inside.** One log per error, not five as it propagates.
- **Typed errors over strings.** `enum MyError { NotFound, InvalidInput(String) }` beats `"something went wrong"`.
- **No silent failures.** If something can fail, handle it or propagate it. Never ignore it.

### Comments

- **Code should be self-documenting.** Good names > good comments.
- **Comment the "why", never the "what".** `// increment counter` is noise. `// retry because the API returns stale data on first call` is useful.
- **Don't add comments to code you didn't write or change.**
- **TODO comments need context:** `// TODO(name): reason, ticket/issue if exists`

---

## Problem Solving

### Never Give Up

**If a solution doesn't work, try another approach.** Then another. Then search the web. Read docs. Read source code. Read issues. There is always a fix — find it.

- **Don't settle for the first approach.** If it's ugly, fragile, or hacky — step back and think harder.
- **Use every resource available.** Web search, official docs, GitHub issues, source code of dependencies. The answer exists somewhere.
- **Try different angles.** If the direct approach fails, come at it sideways. Rethink the problem. Question your assumptions.
- **Never declare something impossible** without exhausting alternatives. "I can't" means "I haven't tried enough approaches yet."

### Never Suppress Errors

**`#[allow(lint)]` is not a fix. It's duct tape over a wound.**

- If clippy or the compiler complains, **fix the underlying code**, not the warning.
- If two lints contradict each other, restructure the code so neither triggers.
- `let _ = result;` is hiding a bug. Handle the error or propagate it.
- `#[allow(dead_code)]` means you have code that shouldn't exist. Delete it.

### Dead Code Dies

**If code is unused, delete it. Period.**

- Don't comment it out "for later." Git remembers. You won't need it.
- Don't add `#[allow(dead_code)]` to keep it around. If nothing calls it, it's dead weight.
- Don't re-export unused items just to silence warnings. Remove the item.
- Don't add `_` prefixes to mask unused variables — either use them or remove them.
- **The codebase should compile clean with zero warnings.** Every warning is a conversation you're avoiding.

---

## Bug Fixes & Improvements — Tracking Workflow (Hard Rule)

**Every bug fix and improvement MUST be tracked.** Use **issues for smaller fixes**, **PRs for larger changes**. No exceptions.

### When `gh` CLI is authenticated:
1. **Open the issue/PR FIRST** with initial findings: what's broken, how to reproduce, root cause, and fix plan. Use `gh issue create` (smaller) or `gh pr create --draft` (larger).




### When `gh` CLI is NOT authenticated:


### Commit Discipline:



---

## Hard Rules (Non-Negotiable)

1. **No 5,000-line files.** Ever. For any reason. Split or die.
2. **No tests at file bottom** beyond 30 lines. Dedicated test files or nothing.
3. **No code without tests.** If you built it, prove it works.
4. **No build artifacts in the repo.** `.gitignore` exists. Use it.
5. **No hardcoded secrets.** Not even "temporarily."
6. **No suppressing warnings.** Fix the code, not the lint. No `#[allow()]` unless you can explain exactly why the lint is wrong and the code is right.
7. **No `unsafe` without a comment explaining why** it's necessary and why it's sound.
8. **Run the full test suite before declaring anything done.**
9. **Clean up after builds.** Source is permanent. Binaries are disposable.
10. **Rust first.** Always. Unless you can't. And you probably can.
11. **No dead code.** If it's unused, delete it. Git has history. You don't need commented-out code.
12. **Never give up on a problem.** Research, web search, try different approaches. The fix exists.
## User Preferences

- **Prefer babashka for scripting.** Use Python only when unavoidable.
- **Prefer pnpm over npm** for web apps and JavaScript/TypeScript package management.
- **Cloudflare is a preferred hosting/deployment target** when it fits the project.

## File Organization Override

- **Treat 250 LOC as the hard review threshold per file.** Once a file moves past ~250 lines, split it unless there is a compelling reason not to.
- **Target: 100-250 lines per file.** Favor small, composable modules.

**Scripting (preferred when Rust is overkill):**
```bash
bb script.clj
```
Use babashka first for automation and glue tasks. Avoid reaching for Python by default.


```bash
pnpm test
pnpm build
```
Use `pnpm`, not `npm`, unless the project is already standardized on something else and changing it would be stupid.
## Additional User Preferences

- Favor the path Rich Hickey would approve: precise language, simple data, composition, low incidental complexity.
- Prefer the latest stable libraries and stacks by default, unless project constraints or compatibility make that reckless.
- TDD is the default workflow when implementing behavior changes.
- E2E testing is required for robustness on user-facing or integration-heavy paths.
- When useful, end substantial coding/design responses with a short "Rich Hickey certification" assessment.

## Bash Tool: REPL & File-Writing Notes

**Never pipe scripts via heredoc** (`python3 - <<'PY'`, `node - <<'JS'`, etc.) — the bash tool sends stdin from /dev/null, causing the REPL to hang. Use `-c "code"` for short snippets or write to a temp file and run that. This applies to all interactive REPLs (python, node, irb, ghci).

When using `write_file` to create a file in a directory that may not exist yet, set `create_dirs: true` to avoid "Parent directory does not exist" errors.

## Shell Discipline (from feedback ledger)

- **Verify a directory is a git repo before running git in it.** `git status`/`log`/`diff` in a non-git directory exits **128** with "fatal: not a git repository". Guard with `git rev-parse --is-inside-work-tree 2>/dev/null` first, or check for a `.git` entry. A project directory existing on disk ≠ it being git-initialized — `~/Desktop/axiom` is a known non-repo example that has burned ~6 calls.
- **Never run `find ~` / `find /Users/moe` unscoped.** Walking the entire home directory times out at the 120s limit. Scope `find` to a known project path, or prefer `glob` / `ls --recursive` which respect tool limits. If you genuinely must search home, narrow by `-name` + `-maxdepth` and redirect stderr (`2>/dev/null`).
