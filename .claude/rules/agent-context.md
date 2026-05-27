# Rule: Agent Context Files (CLAUDE.md)

## Hierarchical CLAUDE.md (root + per-service)

Claude Code reads `CLAUDE.md` files hierarchically: when working inside a service directory it
loads **both** the root `CLAUDE.md` and the service's own `CLAUDE.md`. Per-service files ADD
focused context — they do not replace the shared root.

**Structure:**

```
CLAUDE.md                    ← shared: overview, stack, commands, architecture, conventions
kdiab-measures/CLAUDE.md     ← service-specific: package structure, schema, env vars, behaviours
kdiab-profiles/CLAUDE.md     ← service-specific: state machine, ADRs, validation rules
kdiab-analyze/CLAUDE.md      ← service-specific: analytics formulas, JWT forwarding detail
...
```

**What belongs in the root `CLAUDE.md`:**
- Project overview and service map (one-liners per service, port reference table)
- Common tech stack and shared build/run/test/lint commands
- Shared architecture (hexagonal layers, auth pattern, route pattern, domain conventions)
- Issue tracking, branch naming, commit conventions, quality gates
- Audit logging, Rules Index, Skills Index

**What belongs in a service `CLAUDE.md`:**
- Root package and full package structure
- Domain model / DB schema
- Service-specific env vars
- Behaviours unique to that service (state machines, access restrictions, safety guardrails)
- Key ADRs for that service

**Maintenance rule:** If a concept applies to two or more services, it belongs in the root. If it
applies to exactly one service, it belongs in that service's `CLAUDE.md`. Never duplicate content
across both levels.

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
