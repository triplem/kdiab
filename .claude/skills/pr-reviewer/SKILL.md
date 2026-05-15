---
name: pr-reviewer
description: Review a GitHub pull request against all project rules and quality gates. Produces a structured report with severity-classified findings and posts an optional review comment on the PR. Use after a feature branch is ready for merge.
argument-hint: <pr-number>
arguments: pr_number
allowed-tools: Read Bash(gh *) Bash(git *) Bash(cat .claude/rules/*) Bash(grep -r *)
---

## Pull Request to review: $pr_number

!`gh pr view $pr_number --json number,title,body,headRefName,baseRefName,files,additions,deletions 2>/dev/null || echo "PR $pr_number not found"`

!`gh pr diff $pr_number 2>/dev/null | head -2000`

!`cat .claude/rules/*.md 2>/dev/null | head -500`

## Instructions

You are a senior engineer conducting a pre-merge code review. Apply all project rules systematically. Be specific — vague feedback is not useful.

### 1 — Understand the change

- Read the PR description and acceptance criteria
- Identify the scope: new feature / bugfix / refactor / chore
- List all changed files

### 2 — Checklist review

For each category, scan the diff and report findings:

#### Security (`security.md`)
- [ ] No secrets, credentials, or tokens committed
- [ ] No SQL/command/path injection vectors
- [ ] Auth checks present on all new endpoints (deny by default)
- [ ] User input validated at boundaries
- [ ] No PII logged in plaintext

#### Architecture (`solid-principles.md`)
- [ ] SRP: classes and functions have a single reason to change
- [ ] DIP: services depend on interfaces, not concrete classes
- [ ] No domain logic in adapters/routes
- [ ] No infrastructure types leaking into domain layer

#### API design (`api-design.md`, `openapi.md`)
- [ ] OpenAPI spec updated for every API change
- [ ] All new responses documented (including 4xx/5xx)
- [ ] HTTP methods and status codes correct
- [ ] Pagination on list endpoints

#### Testing (`test-pyramid.md`, `quality-gates.md`)
- [ ] Unit tests cover new business logic (≥ 80% coverage)
- [ ] Edge cases and error paths tested
- [ ] No tests skipped or commented out to make CI pass
- [ ] Integration tests added/updated if persistence or HTTP changes

#### Logging (`logging.md`)
- [ ] Structured log fields for every significant event
- [ ] No secrets or full PII in log messages
- [ ] Correct log level (ERROR for unrecoverable, INFO for business events)

#### Code style (`kotlin-style.md` / `typescript-style.md`)
- [ ] No magic numbers/strings — named constants used
- [ ] No unnecessary comments — code is self-documenting
- [ ] Functions ≤ 20 lines; parameters ≤ 3
- [ ] Conventional Commits format on commit messages (`commit-conventions.md`)

#### Error handling
- [ ] Domain exceptions used (not generic `RuntimeException`)
- [ ] Exceptions not swallowed silently
- [ ] Partial-write operations have compensating rollback

### 3 — Classify each finding

Use this severity scale:

| Severity | Meaning |
|---|---|
| **BLOCKER** | Must be fixed before merge — security hole, data loss, broken core path |
| **MAJOR** | Should be fixed before merge — correctness bug, missing test, design smell |
| **MINOR** | Nice to fix — style, naming, missing edge case unlikely to hit |
| **NIT** | Optional polish — trivial formatting, minor readability |

### 4 — Format the review report

```markdown
## PR Review: #$pr_number — <title>

**Verdict**: APPROVE | REQUEST CHANGES | COMMENT

### Summary
<2–3 sentence overall assessment>

### Findings

#### BLOCKER
- [ ] `<file>:<line>` — <description> [rule: <rule-name>]

#### MAJOR
- [ ] `<file>:<line>` — <description>

#### MINOR
- [ ] `<file>:<line>` — <description>

#### Positives
- <what was done well — always include at least one>
```

If there are zero BLOCKER and zero MAJOR findings → **APPROVE**.
If there are any BLOCKER findings → **REQUEST CHANGES**.
If there are only MAJOR findings → reviewer's call; default **REQUEST CHANGES**.

### 5 — Post review (optional)

If the user confirms, post the review as a GitHub PR comment:

```bash
gh pr comment $pr_number --body "$(cat <<'REVIEW'
<formatted review report>
REVIEW
)"
```

### 6 — Log

```json
{"ts":"<ISO>","agent":"PrReviewerAgent","action":"pr_review","pr":$pr_number,"verdict":"<APPROVE|REQUEST_CHANGES>","blockers":<count>,"majors":<count>,"minors":<count>}
```

Append to `audit/agent-log.jsonl`.

## Rules

- Never approve a PR with an unmitigated BLOCKER
- Cite the specific rule file and section for each finding (e.g. `security.md:A03`)
- At least one positive finding per review — acknowledge what was done well
- If the diff is > 500 lines, focus on the highest-risk areas first
