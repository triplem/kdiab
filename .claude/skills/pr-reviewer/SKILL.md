---
name: pr-reviewer
description: Review a GitHub pull request against all project rules, post findings as a PR comment, then fix every finding (BLOCKER → MAJOR → MINOR → NIT) on the PR branch and push.
argument-hint: <pr-number>
arguments: pr_number
allowed-tools: Read Write Edit Bash(gh *) Bash(git *) Bash(cat .claude/rules/*) Bash(grep -r *) Bash(./gradlew *) Bash(npm *) Bash(find *) Bash(ls *)
---

## Pull Request to review: $pr_number

!`gh pr view $pr_number --json number,title,body,headRefName,baseRefName,files,additions,deletions 2>/dev/null || echo "PR $pr_number not found"`

!`gh pr diff $pr_number 2>/dev/null | head -5000`

!`cat .claude/rules/*.md 2>/dev/null | head -5000`

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

#### NIT
- [ ] `<file>:<line>` — <description>

#### Positives
- <what was done well — always include at least one>
```

If there are zero BLOCKER and zero MAJOR findings → **APPROVE**.
If there are any BLOCKER findings → **REQUEST CHANGES**.
If there are only MAJOR findings → reviewer's call; default **REQUEST CHANGES**.

### 5 — Post review comment

Always post the formatted report as a PR comment — do not wait for confirmation:

```bash
gh pr comment $pr_number --body "$(cat <<'REVIEW'
<formatted review report>
REVIEW
)"
```

### 6 — Fix all findings

Fix every finding from step 3 in severity order: BLOCKER → MAJOR → MINOR → NIT.

#### 6a — Check out the PR branch

```bash
gh pr checkout $pr_number
```

#### 6b — Apply fixes

Work through each finding. For each one:
1. Edit the file at the indicated location
2. Apply the minimal change that resolves the finding
3. If a finding requires a new file (e.g. missing test), create it

After completing all fixes for a severity group, run the relevant quality gates before moving to the next group:

```bash
# After BLOCKER + MAJOR fixes — run full check
./gradlew check --no-daemon          # Kotlin services
npm run build && npm test            # kdiab-ui

# After MINOR + NIT fixes — linter only
./gradlew detektMain --no-daemon     # Kotlin
npm run lint                         # TypeScript
```

If a gate fails, fix the regression before continuing.

#### 6c — Commit the fixes

```bash
git add <file1> <file2> ...   # stage only the files changed in step 6b
git commit -m "fix(<scope>): address PR #$pr_number review findings"
```

Group all fixes into one commit per severity group (e.g. one commit for BLOCKERs+MAJORs, one for MINORs+NITs) or a single commit if the total change is small.

#### 6d — Push and post follow-up comment

```bash
git push
```

Then post a follow-up comment listing each finding as resolved:

```bash
gh pr comment $pr_number --body "$(cat <<'FIXED'
## Review findings addressed

All findings from the review have been fixed:

#### BLOCKER
- [x] `<file>:<line>` — <description> — fixed in <commit-sha>

#### MAJOR
- [x] `<file>:<line>` — <description> — fixed in <commit-sha>

#### MINOR
- [x] `<file>:<line>` — <description> — fixed in <commit-sha>

#### NIT
- [x] `<file>:<line>` — <description> — fixed in <commit-sha>
FIXED
)"
```

If there were zero findings, skip step 6 entirely.

## Rules

- Never approve a PR with an unmitigated BLOCKER
- Cite the specific rule file and section for each finding (e.g. `security.md:A03`)
- At least one positive finding per review — acknowledge what was done well
- If the diff is > 500 lines, focus on the highest-risk areas first
- Always post the review comment — never skip step 5
- Always attempt to fix every finding — never skip step 6 when findings exist
- If a fix would require understanding beyond the diff (e.g. missing business context), make a best-effort attempt and note the assumption in the commit message
