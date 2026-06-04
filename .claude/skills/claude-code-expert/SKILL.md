---
name: claude-code-expert
description: Senior Claude Code expert advisor. Reviews and improves CLAUDE.md, settings.json, hooks, MCP servers, skills, and multi-agent orchestration against official documentation and current best practices. Invoke with a focus area (settings | hooks | mcp | skills | claude-md | agents | security) or omit for a full project audit.
argument-hint: "[settings | hooks | mcp | skills | claude-md | agents | security | full]"
arguments: [focus]
user-invocable: true
allowed-tools: Read Bash(ls *) Bash(find *) Bash(cat *) Bash(grep -r *) Bash(wc -l *)
---

## Focus: $focus

## Current project Claude Code state

**Settings:**
!`cat .claude/settings.json 2>/dev/null || echo "(no settings.json)"`

**Skills:**
!`ls .claude/skills/ 2>/dev/null || echo "(no skills)"`

**Hooks:**
!`ls .claude/hooks/ 2>/dev/null || echo "(no hooks)"`

**Rules:**
!`ls .claude/rules/ 2>/dev/null || echo "(no rules)"`

**Memory index:**
!`cat ~/.claude/projects/*/memory/MEMORY.md 2>/dev/null | head -40 || echo "(no memory)"`

**CLAUDE.md sections:**
!`grep "^## " CLAUDE.md 2>/dev/null || echo "(no CLAUDE.md)"`

---

## Knowledge Base — Claude Code Official Docs & Best Practices

### Configuration Architecture

**Three settings files, ordered by precedence (later overrides earlier):**

| File | Scope | Commit? | Use for |
|---|---|---|---|
| `~/.claude/settings.json` | Global / all projects | No | Personal defaults, global MCP servers |
| `.claude/settings.json` | Project / team | Yes | Shared permissions, hooks, MCP, env |
| `.claude/settings.local.json` | Project / personal | No (gitignore) | Personal overrides, local secrets |

**Full settings.json schema (key fields):**
```json
{
  "permissions": {
    "allow": ["Bash(git *)", "Bash(npm *)", "mcp__github__*"],
    "deny":  ["Bash(rm -rf /*)", "Bash(git push --force origin main)"],
    "defaultMode": "default"
  },
  "hooks": { ... },
  "mcpServers": { ... },
  "env": { "KEY": "value" },
  "model": "sonnet",
  "cleanupPeriodDays": 30,
  "respectGitignore": true
}
```

---

### CLAUDE.md Best Practices

**Single root file — never scatter per-service:**
- One `CLAUDE.md` at the monorepo root (add `## Service Details` subsections)
- Per-service files fragment context: agents miss cross-cutting decisions; files diverge
- Sub-service content: package structure, schema, state machine, formulas, env vars unique to that service

**Canonical section order:**
1. Project Overview (what the system does, service map)
2. Common Tech Stack
3. Commands (build, test, run, lint, deploy — one copy-pasteable block per task)
4. Architecture (hexagonal layers, auth pattern, data flow)
5. Service Details (per-service subsections)
6. Issue Tracking conventions
7. Branch Naming & Merge Policy
8. Commit Conventions
9. Quality Gates
10. Audit Logging
11. Rules Index (`.claude/rules/`)
12. Skills Index (`.claude/skills/`)

**Common omissions that hurt agents:**
- Missing test commands or incorrect working directories
- No description of domain-specific validation rules
- No "what NOT to do" section (anti-patterns)
- Missing access control rules (which roles can do what)

---

### Hooks System

**Seven hook event types:**

| Event | Fires | Use for |
|---|---|---|
| `SessionStart` | Session begins | Load context, git pull, print reminders |
| `PreToolUse` | Before any tool | Safety checks, commit message validation, deny dangerous commands |
| `PostToolUse` | After tool succeeds | Lint changed files, scan for secrets, validate OpenAPI |
| `PostToolUseFailure` | After tool fails | Alert, log failure context |
| `FileChanged` | File pattern match | `.env` change guard, OpenAPI auto-lint |
| `Stop` | Session ends | Session summary, cleanup |
| `PreCompact` | Before compaction | Prompt for what to preserve |

**Hook output protocol (JSON on stdout):**
```json
{
  "continue": false,           // block the tool call (PreToolUse only)
  "stopReason": "message",     // shown to user when continue=false
  "systemMessage": "warning",  // injected into context (all hooks)
  "decision": "block",         // PostToolUse block trigger
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "additionalContext": "text injected into model context"
  }
}
```

**Skill-level hook overrides:** Skills can declare their own hooks in frontmatter that add to (not replace) the global hooks:
```yaml
hooks:
  PostToolUse:
    - matcher: "Write|Edit"
      hooks:
        - type: command
          command: "${CLAUDE_PROJECT_DIR}/.claude/hooks/post-edit-lint.sh"
          timeout: 30
```

---

### Skills System

**Skill file anatomy:**
```yaml
---
name: my-skill                    # /my-skill invocation name
description: One-line summary     # shown in /help and agent descriptions
argument-hint: "<arg>"            # shown in completions
arguments: [arg_name]             # available as $arg_name in body
user-invocable: true              # false = only callable by other agents
effort: high                      # low | medium | high (affects spinner)
allowed-tools: Read Write Edit Bash(git *) Bash(npm *)   # scope sandbox
disable-model-invocation: true    # suppress auto-start; skill is a prompt only
hooks:
  PostToolUse:
    - matcher: "Write|Edit"       # skill-scoped hooks
      hooks:
        - type: command
          command: "..."
---

## Body content here

# A line that starts with a bang-backtick pattern runs at load time.
# The six injections at the top of this skill are intentional examples.

$arg_name is substituted from arguments.
```

**Runtime injection (bang prefix):**
- A line at the TOP LEVEL of the skill body beginning with `!` followed by a backtick-quoted shell command is executed at skill invocation time; its stdout is injected into the prompt
- Keeps skills dynamic: load current git status, issue details, file contents, build output
- Examples (written in actual skill SKILL.md files, on their own lines):
  - `!` + backtick + `git status --short` + backtick
  - `!` + backtick + `gh issue view $issue_id` + backtick

**Skill composition patterns:**
- Skills can reference other skills: `After implementation → /write-tests $story_id`
- Skills can spawn sub-agents: one lead + N workers on same branch via worktrees
- Skills chain naturally: `/implement` → `/write-tests` → `/create-pr`

**Good skill vs bad skill:**
| Good | Bad |
|---|---|
| Single clear purpose | Does everything at once |
| Arguments for variable parts | Hardcoded values |
| Runtime context via `!` | Stale static context |
| Explicit output format | Vague "do your best" |
| Scoped `allowed-tools` | No tool restriction |
| `user-invocable: false` for internal skills | All skills public |

---

### MCP (Model Context Protocol)

**Built-in MCP servers (install via npx):**
```json
{
  "mcpServers": {
    "github":     { "command": "npx", "args": ["-y", "@modelcontextprotocol/server-github"], "env": {"GITHUB_PERSONAL_ACCESS_TOKEN": "${GITHUB_TOKEN}"} },
    "filesystem": { "command": "npx", "args": ["-y", "@modelcontextprotocol/server-filesystem", "${PROJECT_ROOT}"] },
    "postgres":   { "command": "npx", "args": ["-y", "@modelcontextprotocol/server-postgres", "${DATABASE_URL}"] },
    "slack":      { "command": "npx", "args": ["-y", "@modelcontextprotocol/server-slack"], "env": {"SLACK_BOT_TOKEN": "${SLACK_BOT_TOKEN}"} },
    "linear":     { "command": "npx", "args": ["-y", "@modelcontextprotocol/server-linear"], "env": {"LINEAR_API_KEY": "${LINEAR_API_KEY}"} }
  }
}
```

**Permission scoping for MCP tools:**
```json
"allow": [
  "mcp__github__*",              // all GitHub tools
  "mcp__github__create_issue",   // specific tool only
  "mcp__filesystem__read_file",  // read-only filesystem
]
```

**MCP vs Bash tool choice:**
- Use MCP when: structured API access, credentials not in env, pagination handled, rate limiting needed
- Use Bash when: simple CLI invocation, no MCP server exists, piping/chaining needed

**Security rule:** Never pass credentials in skill prompts or `!` commands. Put them in `mcpServers.env` using `${ENV_VAR}` interpolation. This keeps secrets out of context windows and audit logs.

---

### Multi-Agent Orchestration

**Worktree pattern (parallel agents, same branch):**
```bash
# Agent A (implementer)
git worktree add ../worktree-42-impl feature/42-my-feature

# Agent B (test writer — runs in parallel)
git worktree add ../worktree-42-tests feature/42-my-feature
```
Both agents commit to the same branch. Coordinate via rebase, not merge.

**Worktree cleanup — always use double-force for Claude-locked trees:**
```bash
git worktree remove -f -f /path/to/worktree  # single -f fails on locked trees
git branch | grep "worktree-" | xargs -r git branch -D
git fetch --prune
```

**Agent isolation principles:**
- Each agent reads its own copy of CLAUDE.md (same file, independent context)
- Independent circuit breakers / state (no shared in-memory state)
- Coordinate via git, issue tracker, or file system (NOT shared variables)
- Credentials passed via settings.json env vars, not prompt injection

**State machine for multi-agent workflows (pattern from production systems):**
```
todo → in_progress → review → gate_approved → done
                   ↓               ↓
                 rework ←──────────┘   (human rejects)
```
- **agent** states: run a Claude Code agent
- **gate** states: pause for human approval
- **terminal** states: cleanup and mark complete
- Rework loops preserve implementation progress — never discard on reviewer feedback

**Concurrency control:**
- `max_concurrent_agents` global cap (prevents resource starvation)
- Per-state caps for fairness across projects
- One story = one feature branch (multiple worktrees on same branch are fine)

---

### Permissions Deep-Dive

**Bash permission syntax:**
```json
"Bash(git *)"           // any git subcommand
"Bash(npm run *)"       // npm run only
"Bash(find . -name *)"  // find with filter
"Bash(grep -r * src/)"  // grep scoped to src/
```
Wildcard `*` matches zero or more characters. No regex; prefix matching only after the first `*`.

**What to always deny:**
```json
"deny": [
  "Bash(rm -rf /*)",                    // destroy root filesystem
  "Bash(git push --force origin main)", // force push main
  "Bash(git push --force origin master)",
  "Bash(curl * | bash)",                // remote code execution
  "Bash(eval *)"                        // arbitrary eval
]
```

**What to always allow (reduces friction without risk):**
```json
"allow": [
  "Bash(git *)",       // all git (force push blocked separately)
  "Bash(gh *)",        // GitHub CLI
  "Bash(find *)",      // file discovery
  "Bash(grep *)",      // search
  "Bash(cat *)",       // read files
  "Bash(ls *)",        // list dirs
  "Bash(wc *)"         // word count
]
```

---

### Security Hygiene

**Top 5 common mistakes:**
1. **Secrets in CLAUDE.md** — never put API keys, passwords, or tokens in CLAUDE.md (it's committed). Use `mcpServers.env` with `${ENV_VAR}` references.
2. **No deny list for destructive commands** — always deny `rm -rf /*`, force push to main, `eval`, `curl | bash`.
3. **Per-service CLAUDE.md** — fragments context, causes agents to miss security rules from other services.
4. **Overly broad `Bash(*)`** — negates the permission sandbox. Always scope to specific tool families.
5. **Audit log gaps** — if PostToolUse hook isn't running on `Bash|Write|Edit`, you have blind spots in agent actions.

**Post-edit secret scanning pattern:**
```bash
#!/bin/bash
# post-edit-secrets.sh — runs on every Write|Edit
FILE=$(jq -r '.tool_input.file_path // .tool_response.filePath // empty' 2>/dev/null)
[ -z "$FILE" ] && exit 0
gitleaks detect --source "$FILE" --no-git --redact 2>/dev/null && exit 0
echo '{"decision":"block","reason":"Potential secret detected — review before saving"}'
```

---

### Latest Claude Code Features (2025–2026)

**Extended thinking:** `alwaysThinkingEnabled: true` in settings.json — Claude reasons through complex problems before acting. Use for architecture reviews, security analysis, and multi-step planning.

**Custom sub-agents:** The `Agent` tool spawns specialized sub-agents mid-conversation. Each sub-agent can have its own model, tools, and isolation mode (`isolation: "worktree"` creates a clean git worktree).

**Background agents:** `run_in_background: true` on Agent calls — lead agent continues working while sub-agents run in parallel. Notified on completion.

**Session continuity across surfaces:**
- `claude --teleport` — pull a web/iOS session into the local terminal
- `/desktop` — hand off terminal session to Desktop app for visual review
- Remote Control — continue local session from phone/browser

**Scheduled tasks (`/loop` and `ScheduleWakeup`):**
- `/loop <prompt>` — self-pacing iteration (Claude picks delay based on what it's waiting for)
- `ScheduleWakeup` tool — wake agent in N seconds, fire same prompt; survives context compression
- Cache efficiency: delays under 300 s keep Anthropic prompt cache warm (5-min TTL); over 300 s pay a cache miss

**Plan mode (`EnterPlanMode` / `ExitPlanMode`):**
- `/plan` — enter plan mode; Claude shows design before touching any code
- Use for: complex refactors, breaking API changes, multi-service migrations
- Combine with `AskUserQuestion` to gather requirements before finalizing

**Model override per-skill:** Skills can specify model in frontmatter (not yet widely documented but supported):
```yaml
model: claude-opus-4-7   # use Opus for high-stakes skills
```

**Memory system (auto):** Claude Code builds a persistent memory file in `.claude/projects/<hash>/memory/`. Writing to `MEMORY.md` index + individual `.md` files persists learnings across sessions. Types: `user`, `feedback`, `project`, `reference`.

---

## Instructions

You are a senior Claude Code expert applying the knowledge base above to the project loaded at the top of this prompt.

**If `$focus` is empty or "full", audit all areas. Otherwise focus on the named area.**

### 1 — Read current state

Load and read:
- `.claude/settings.json` — full file
- `.claude/skills/` — all SKILL.md files (list names and check descriptions)
- `.claude/hooks/` — all hook scripts (check they implement the patterns above)
- `.claude/rules/` — all rule files (check completeness)
- `CLAUDE.md` — full file (check against canonical section list)

### 2 — Apply expert checklist

#### CLAUDE.md
- [ ] Single root file (no per-service CLAUDE.md anywhere)
- [ ] All 12 canonical sections present
- [ ] Commands section: all key commands present and copy-pasteable
- [ ] Architecture section: layering, auth pattern, data flow described
- [ ] Service Details present for every service in the monorepo
- [ ] Rules Index links match actual files in `.claude/rules/`
- [ ] Skills Index links match actual files in `.claude/skills/`
- [ ] No secrets or tokens in the file

#### settings.json
- [ ] `permissions.allow` scoped (not `Bash(*)`)
- [ ] `permissions.deny` includes `rm -rf /*` and force push to main/master
- [ ] MCP servers declared for all external systems in use (GitHub, issue tracker)
- [ ] `env` block declares project-level constants agents need
- [ ] PreToolUse hook for safety / commit guard
- [ ] Stop hook for session summary
- [ ] FileChanged hook if `.env` files exist in the repo

#### Hooks
- [ ] `safety-bash.sh` — blocks dangerous patterns (`rm -rf`, eval, curl|bash)
- [ ] `commit-guard.sh` — validates Conventional Commits format
- [ ] `post-edit-secrets.sh` — runs gitleaks or similar on changed files
- [ ] `session-start.sh` — injects project context (branch, story, recent log)
- [ ] All hook scripts are executable (`chmod +x`)

#### Skills
- [ ] All SDLC lifecycle phases covered (requirements → epics → stories → implement → test → PR → release)
- [ ] All specialist reviews have skills (security, QA, architect, DevOps, UX, operations, technical writer)
- [ ] Each skill has a clear `description` (used by agents to select the right skill)
- [ ] Internal-only skills have `user-invocable: false`
- [ ] Skills use runtime injection (bang-backtick lines) for dynamic context, not stale static text
- [ ] `allowed-tools` scoped to what the skill actually needs

#### MCP
- [ ] GitHub MCP server present (for issue tracking, PR management)
- [ ] Credentials use `${ENV_VAR}` syntax, never hardcoded values
- [ ] MCP tools are in `permissions.allow` (e.g., `mcp__github__*`)
- [ ] Additional MCP servers for issue tracker if not GitHub (Linear, Jira, etc.)

#### Multi-agent / Security
- [ ] Worktree cleanup uses double-force (`-f -f`)
- [ ] No credentials in CLAUDE.md or skill prompt bodies
- [ ] Deny list covers the top 5 dangerous patterns
- [ ] Audit log covers all Write/Edit/Bash actions (no blind spots)

### 3 — Classify findings

| Severity | Meaning |
|---|---|
| **CRITICAL** | Security hole, credential exposure, data loss risk |
| **HIGH** | Missing capability that blocks normal SDLC flow |
| **MEDIUM** | Suboptimal pattern that causes friction or gaps |
| **LOW** | Polish — better naming, missing edge case, readability |

### 4 — Produce the report

```markdown
## Claude Code Expert Audit — <focus area or "Full">

**Overall health**: 🟢 EXCELLENT | 🟡 GOOD | 🟠 NEEDS WORK | 🔴 CRITICAL ISSUES

### Executive summary
<2–3 sentences on the state of the Claude Code setup and the most important action to take>

### Findings

#### CRITICAL
- `<file>` — <description> [docs ref: <section>]

#### HIGH
- `<file>` — <description>

#### MEDIUM
- `<file>` — <description>

#### LOW
- `<file>` — <description>

### Positive patterns
- <what is already well done — always list at least one>

### Recommended next actions (in priority order)
1. <specific action with example code/config>
2. ...
```

### 5 — Offer to fix

For each HIGH or CRITICAL finding, ask the user:
> "Shall I fix `<finding>` now? I can update `<file>` directly."

If the user says yes, apply the fix using Edit/Write tools, then re-check the finding is resolved.

## Output

- Structured report with classified findings
- Actionable recommendations with example code
- Offer to auto-fix HIGH/CRITICAL items
