# Rule: Agent Context Files (CLAUDE.md)

## Single Root CLAUDE.md

Maintain **one** `CLAUDE.md` at the monorepo root. Do **not** create per-service or per-directory `CLAUDE.md` files.

**Why**: Per-service files fragment context across the tree. Agents working in one service miss cross-cutting decisions stored elsewhere. Files diverge and become stale independently. A single root file is always found, always current, and gives every agent the full picture immediately.

**How to organise multi-service content**: Add a `## Service Details` section in the root with subsections per service. Each subsection covers what is unique to that service (package structure, domain model schema, state machine, formulas, env vars). Common patterns (architecture, conventions, commands) stay in the shared sections above.

```
# CLAUDE.md (root)
## Architecture        ← shared
## Commands           ← shared
## Service Details    ← per-service subsections
  ### kdiab-measures
  ### kdiab-profiles
  ...
## Issue Tracking     ← shared
```

**Anti-pattern**:
```
kdiab-measures/CLAUDE.md   ← DO NOT create these
kdiab-profiles/CLAUDE.md   ← DO NOT create these
```

If a service-specific file is found, merge its content into the root `## Service Details` section and delete it.

## Hook Audit Logging

Use a **PostToolUse hook** for real-time agent action logging — do not rely on Stop hook transcript parsing.

**Why**: The Stop hook receives a session transcript, but tool_use entries are nested inside `content` arrays rather than top-level objects. Parsing them is fragile and always produces empty results. A PostToolUse hook fires immediately after each tool call with structured `tool_input` data that is trivial to extract.

**Pattern**:
- PostToolUse hook on `Bash|Write|Edit` → append one JSONL entry per meaningful action
- Skip read-only commands (`cat`, `grep`, `find`, `ls`, etc.) via an explicit skip list
- Stop hook counts session actions by grepping the log, then appends a `session_stop` summary entry
- SessionStart hook appends a `session_start` entry

```json
{"ts":"...","agent":"Claude","session_id":"...","action":"bash","detail":"git commit -m ..."}
{"ts":"...","agent":"System","session_id":"...","action":"session_stop","branch":"main","actions_this_session":12}
```
