You are the **orchestrator** for parallel agent execution across isolated git worktrees.

Arguments: $ARGUMENTS
(Pass space-separated beads issue IDs, or leave empty to use all issues from `bd ready` that have an OpenSpec in their design field.)

---

## Steps

### 1. Determine the issue list

If `$ARGUMENTS` is empty:
```bash
bd ready
```
Collect all issue IDs listed.

If `$ARGUMENTS` has issue IDs: use those directly.

### 2. Verify each issue has an OpenSpec

For each issue ID, run `bd show <id>`. Read the `design` field. An issue is **ready to implement** if and only if the design field contains a populated OpenSpec (starts with `# OpenSpec:`).

For any issue **without** a spec, print a warning:
```
⚠ <id> has no OpenSpec — run /spec <id> first. Skipping.
```
Remove it from the list.

### 3. Sanity check

If the resulting list is empty, stop and tell the user. If the list has more than 6 issues, warn that running more than 6 parallel agents may exhaust system resources and ask for confirmation.

### 4. Spawn parallel agents — ONE MESSAGE, ALL AGENTS

**Critical:** Issue ALL Agent tool calls in a **single response message**. Do not wait between them — that would serialize execution. Each agent gets:
- `isolation: "worktree"` — ensures a clean git branch per agent
- A self-contained prompt that includes the full OpenSpec from the beads issue (paste the design field content inline — agents cannot run `bd show` reliably from inside a worktree)

Agent prompt template (fill in per issue):

```
Implement beads issue <id>: "<issue title>"

## Your OpenSpec
<paste full design field content here>

## Instructions
1. Run: bd update <id> --claim
2. Implement every step in the Implementation Plan above, in order.
   - Read each target file before editing it.
   - Follow the project conventions: hexagonal layer boundaries, domain exceptions, kotlin.uuid.Uuid in domain code, CSS custom properties in frontend.
3. Run the Test Plan steps.
4. If the quality gate fails, fix the issue before continuing.
5. Commit using Conventional Commits:
   git commit -m "feat/fix/refactor: <concise title>

   Implements <id>: <one-line summary>

   Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
6. Run: bd close <id> --reason="Implemented per OpenSpec in isolated worktree"
7. Report: which files changed, which tests passed, whether the quality gate is green.
```

### 5. Collect and summarise results

When all agents complete, summarise:
- ✅ `<id>` — done, worktree branch: `<branch>`
- ❌ `<id>` — failed: `<reason>` — worktree kept for inspection

For any failed agent, tell the user the worktree branch name so they can inspect the partial work with `git worktree list`.
