---
name: create-pr
description: Open a pull request with a guided review summary, then on merge: delete local/remote branches, close linked issues, and close the parent epic if all its stories are done.
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
- **Merged** → proceed to step 7
- **Approved (not yet merged)** → `/release <pr-id>`, then proceed to step 7 once merged
- **Changes requested** → read comments, fix in feature branch, re-push, re-check gates

### 7 — Post-merge cleanup

Run this step once the PR shows `state: MERGED`.

#### 7a — Delete branches

```bash
git checkout main && git pull
git push origin --delete $branch
git branch -D $branch
```

If the remote branch was already deleted (GitHub "delete on merge" enabled), skip the `push --delete` silently.

#### 7b — Close linked issues

Collect every issue the PR closes:

```bash
gh pr view $pr_number --json closingIssuesReferences \
  --jq '.closingIssuesReferences[].number'
```

Also include `$story_id` explicitly in case it was not declared with `Closes #` in the PR body.

Close each issue:

```bash
gh issue close <number> --comment "Closed by merged PR #$pr_number."
```

Skip issues that are already closed.

#### 7c — Check parent epic

For each issue closed in step 7b, look up its parent epic:

```bash
gh api graphql -f query='
{
  repository(owner: "triplem", name: "kdiab") {
    issue(number: <number>) {
      parent {
        number
        title
        state
        subIssues(first: 50) {
          nodes { number state }
        }
      }
    }
  }
}'
```

If the issue has a parent epic **and** every sub-issue in `subIssues.nodes` has `state: CLOSED`:

```bash
gh issue close <epic-number> --comment "All stories complete — closing epic."
```

If different issues belong to different epics, check each epic independently.

## Output

- PR in tracker (labelled `pending-review`)
- Comment on story issue
- On merge: branches deleted, linked issues closed, epic closed if all stories done
