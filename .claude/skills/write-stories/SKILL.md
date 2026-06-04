---
name: write-stories
description: Decompose an approved epic into user stories with full acceptance criteria. Runs in plan mode with deep thinking. Use after an epic is approved in the issue tracker.
argument-hint: <epic-id>
arguments: epic_id
disable-model-invocation: true
effort: max
allowed-tools: Read Write Bash(cat docs/*)
---

ultrathink

## Epic to decompose: $epic_id

!`cat docs/epics-index.md 2>/dev/null | grep -A5 "$epic_id" || echo "Fetch epic $epic_id from issue tracker"`

## Instructions

You are in **plan mode**. No code is written here — only specifications.

Spend significant thinking time on:
- Domain model implications
- API contract surface (spec-first for every API change)
- Data flow through the system
- Edge cases and failure modes

### 1 — Generate stories

Each story follows this format (see `templates/story.md`):

**Title:** `As a <role>, I want to <capability> so that <benefit>`

Required sections:
- Context (link to epic + requirement IDs)
- Acceptance Criteria (Given/When/Then — at least: happy path, not-found, validation error)
- Technical Notes (API sketch, data model changes, integration points)
- Definition of Done
- Dependencies (other story IDs)
- Estimate: S=1d / M=3d / L=5d — anything larger must be split

### 2 — API-first check

For any story that touches an API:
1. Draft the OpenAPI spec change first (see `/openapi-patterns`)
2. Attach snippet to the story body
3. `/challenge ArchitectAgent "Review API contract for story $epic_id"`

### 3 — Trace to requirements

Every story must trace to at least one requirement ID from `docs/requirements.md`.
Every story must have at least one acceptance criterion that can be automated.

### 4 — Challenge loop

For each story:
- `/challenge ImplementAgent "Is story $epic_id implementable without ambiguity?"`
- `/challenge TestAgent "Can all acceptance criteria for story $epic_id be automated?"`
- Incorporate feedback. Repeat up to 3 times. Escalate to human if unresolved.

### 5 — Push to tracker

Create issues linked to epic `$epic_id`. Always add `--assignee "@me"` to every `gh issue create` call (see `github-issue-management.md`). Label: `story,pending-approval`.

### 6 — Poll for approval

Poll for `approved` label. On approval → `/implement <story-id>` for each (parallelise independent stories).

## Output

- Stories in issue tracker linked to epic `$epic_id`
- Each story body references requirements and contains automated test criteria

On approval → `/implement <story-id>` (parallel where no dependencies)
