---
name: write-epics
description: Decompose an approved requirements document into epics with acceptance criteria and push them to the issue tracker. Use after requirements.md is approved.
disable-model-invocation: true
effort: high
allowed-tools: Read Write Bash(cat docs/requirements.md)
---

## Current requirements

!`cat docs/requirements.md 2>/dev/null || echo "docs/requirements.md not found — run /gather-requirements first"`

## Instructions

### 1 — Decompose into epics

Read the requirements above. Group into logical epics (one per major functional area). Cross-cutting concerns (auth, logging, observability) get their own epics.

For each epic use `templates/epic.md`. Include:
- Goal statement (user-value focused, 1–2 sentences)
- Acceptance criteria (Given/When/Then)
- Linked requirement IDs
- Complexity: S / M / L / XL
- Dependencies on other epics

Epics larger than L must be split.

### 2 — Scan for ADR triggers

For each epic: does it require an unresolved technology decision or schema decision?
If yes → invoke `/create-adr "<topic>"` before proceeding with that epic.

### 3 — Challenge

Invoke `/challenge StoryAgent "Review epic decomposition: are these independently deliverable and do they cover all requirements?"`.
Incorporate feedback. Re-challenge until ACCEPT.

### 4 — Push to issue tracker

Use the MCP server configured in `$ISSUE_TRACKER` (see `mcp/<tracker>-mcp.md`).
Label each epic `epic,pending-approval`.

### 5 — Plan milestones

Group epics into milestones (M1, M2, …) by:
- Dependencies (dependency first)
- Business priority (from requirements doc)
- Risk (high-risk early)

Write `docs/milestones.md`.

### 6 — Poll for approval

Poll the tracker every 5 minutes for `approved` label on all epics.
On approval → invoke `/write-stories <epic-id>` for each.

## Output

- Epics in issue tracker (`epic`, `pending-approval`)
- `docs/milestones.md`
- `docs/epics-index.md`

On all epics approved → `/write-stories <epic-id>` for each approved epic.
