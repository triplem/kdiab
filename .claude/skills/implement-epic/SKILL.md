---
name: implement-epic
description: Implement all open stories under an epic in dependency order. Discovers sub-issues via GitHub API, resolves blockers, groups into parallel waves, and delegates each story to /implement. Use when an epic is approved and all its stories are ready for implementation.
argument-hint: <epic-id>
arguments: epic_id
disable-model-invocation: true
effort: max
allowed-tools: Read Bash(gh *) Bash(git *)
---

## Epic to implement: $epic_id

!`gh issue view $epic_id --json number,title,state,labels 2>/dev/null || echo "Epic $epic_id not found"`

## Sub-issues

!`gh api graphql -f query='
{
  repository(owner: "triplem", name: "kdiab") {
    issue(number: '$epic_id') {
      title
      subIssues(first: 50) {
        nodes {
          number
          title
          state
          labels(first: 10) { nodes { name } }
          body
        }
      }
    }
  }
}' 2>/dev/null || echo "sub-issues query failed — falling back to issue body"`

## Instructions

### 1 — Validate epic

- Confirm epic `$epic_id` exists and is open.
- If the epic is closed → exit: "Epic #$epic_id is already closed. Nothing to implement."
- If no sub-issues found → exit: "Epic #$epic_id has no linked sub-issues. Run /write-stories $epic_id first."

### 2 — Collect implementable stories

Filter the sub-issues list to the **implementable set**:

- **Include**: open issues (`state: OPEN`) with label `story`
- **Skip** (log each skip):
  - Closed/merged issues → `"#NNN already closed — skipping"`
  - Issues labelled `epic`, `adr`, `docs`, `documentation`, `pending-approval` → `"#NNN is not an implementation story — skipping"`
  - Issues labelled `BLOCKED` → `"#NNN is externally blocked — skipping"`

If the implementable set is empty → exit: "No open story issues found under epic #$epic_id."

### 3 — Parse dependencies

For each story in the implementable set, scan its body for dependency declarations:

Patterns to detect (case-insensitive):
```
Depends on #NNN
Blocked by #NNN
Dependency: #NNN
Dependencies: #NNN, #NNN
- [ ] Depends on #NNN
```

Build an adjacency map: `blockers[story_number] = set of story numbers it must wait for`.

Only track dependencies on stories **within the implementable set** — ignore dependencies on already-closed issues (treat them as satisfied).

### 4 — Detect circular dependencies

Run Kahn's algorithm on the adjacency map:
1. Find all stories with an empty `blockers` set → initial wave
2. Remove them, decrement counts for their dependents
3. Repeat until empty

If stories remain after the algorithm completes (their count never reached zero), a cycle exists.

On cycle detected:
- Report: "Circular dependency detected among stories: #A → #B → #A. Cannot proceed."
- Label each story in the cycle with `BLOCKED`.
- Append to `audit/agent-log.jsonl`:
  ```json
  {"ts":"<ISO>","agent":"ImplementEpicAgent","action":"cycle_detected","epic":$epic_id,"cycle":[<story numbers>]}
  ```
- Exit without implementing anything.

### 5 — Build execution waves

The result of step 4 is an ordered list of waves, where:
- **Wave 1**: stories with no unmet dependencies
- **Wave N**: stories whose dependencies are all in waves 1..N-1

Example:
```
Stories: #10, #11, #12, #13
Dependencies: #11 depends on #10, #12 depends on #10, #13 depends on #11 and #12

Wave 1: [#10]          ← no dependencies
Wave 2: [#11, #12]     ← both depend only on #10 (completed in wave 1)
Wave 3: [#13]          ← depends on #11 and #12 (completed in wave 2)
```

### 6 — Execute waves

For each wave **in order**:

#### 6a — Log wave start

```json
{"ts":"<ISO>","agent":"ImplementEpicAgent","action":"wave_start","epic":$epic_id,"wave":<N>,"stories":[<numbers>]}
```

#### 6b — Dispatch stories in the wave

Stories within a wave have no mutual dependencies and **must be run in parallel** via separate worktrees.

For each story in the wave, create an isolated worktree and invoke `/implement`:

```bash
# Each story gets its own worktree on its own feature branch
git worktree add ../.claude/worktrees/epic-$epic_id-story-NNN feature/NNN-<slug>
```

Then dispatch:
```
/implement NNN
```

All dispatches in the wave happen concurrently. Wait for **all** to complete before advancing to the next wave.

#### 6c — Check outcomes

After each wave completes, check each story's PR:
```bash
gh pr list --state open --search "Closes #NNN"
```

If a story's `/implement` failed (no PR created, branch still dirty):
- Label the story `BLOCKED`
- Log:
  ```json
  {"ts":"<ISO>","agent":"ImplementEpicAgent","action":"story_failed","epic":$epic_id,"story":NNN,"wave":<N>}
  ```
- **Do not cancel the remaining waves** — continue with unblocked stories. Only skip stories that depend on the failed one.

#### 6d — Log wave complete

```json
{"ts":"<ISO>","agent":"ImplementEpicAgent","action":"wave_complete","epic":$epic_id,"wave":<N>,"completed":[<numbers>],"failed":[<numbers>]}
```

### 7 — Final summary

After all waves have run, output a summary table:

```
## Epic #$epic_id — Implementation Summary

| Story | Title | Wave | Outcome | PR |
|---|---|---|---|---|
| #10 | ... | 1 | ✓ merged | #NNN |
| #11 | ... | 2 | ✓ open | #NNN |
| #12 | ... | 2 | ✗ failed | — |
| #13 | ... | 3 | ⏭ skipped (depends on failed #12) | — |
```

### 8 — Log session end

```json
{"ts":"<ISO>","agent":"ImplementEpicAgent","action":"epic_complete","epic":$epic_id,"waves":<total>,"implemented":<count>,"failed":<count>,"skipped":<count>}
```

Append to `audit/agent-log.jsonl`.

## Rules

- Never implement stories out of dependency order — a story must not start until all its declared blockers are complete
- Never duplicate logic from `/implement` — always delegate; this skill only orchestrates
- A failed story in wave N does **not** cancel wave N+1 unless stories in wave N+1 depend on the failed story
- Always clean up worktrees after each story completes: `git worktree remove ../.claude/worktrees/epic-$epic_id-story-NNN`
- If the epic has > 20 open stories, ask the human to confirm before proceeding (risk of overwhelming the system)
