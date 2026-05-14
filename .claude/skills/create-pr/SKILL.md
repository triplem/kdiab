---
name: create-pr
description: Open a pull request with a guided review summary. Use when all quality gates pass on a feature branch.
argument-hint: <story-id> <branch-name>
arguments: [story_id, branch]
disable-model-invocation: true
allowed-tools: Bash(git *) Bash(gh *) Bash(glab *)
---

## Branch diff summary

!`git diff main...$branch --stat 2>/dev/null | tail -20`

## Commits on branch

!`git log main...$branch --oneline 2>/dev/null`

## Instructions

### 1 — Pre-flight

Run `./.claude/scripts/quality-check.sh`. If any gate is red → abort and fix first.

### 2 — Classify changed files

Categorise every changed file:
- **🔴 High**: business logic, security, data migrations, auth
- **🟡 Medium**: API changes, configuration, integration points
- **🟢 Low**: tests, generated code, documentation

### 3 — Build PR description

Use `templates/pr-description.md`. Fill in:
- Story link + title
- 3 bullet summary
- Reviewer focus table (🔴🟡🟢)
- Test evidence (counts + coverage %)
- How to test locally (copy-pasteable commands)
- Breaking changes (if any)

### 4 — Open PR via MCP

**GitHub**: `mcp__github__create_pull_request` with `head: "$branch"`, `base: "main"`, labels `["pending-review"]`
**GitLab**: `mcp__gitlab__create_merge_request` with `squash: true`, `remove_source_branch: true`

PR title format: `<type>(<scope>): <story title> (#$story_id)`

### 5 — Comment on story

Add comment on issue `$story_id`: "PR opened: <PR_URL> — awaiting review."

Log to `audit/human-decisions.jsonl`:
```json
{"ts":"<ISO>","agent":"ReviewAgent","action":"pr_opened","story_id":"$story_id","pr_url":"<URL>"}
```

### 6 — Poll for outcome

Poll every 5 minutes.
- **Approved** → `/release <pr-id>`
- **Changes requested** → read comments, fix in feature branch, re-push, re-check gates

## Output

- PR in tracker (labelled `pending-review`)
- Comment on story issue
