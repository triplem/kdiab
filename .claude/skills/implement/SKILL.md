---
name: implement
description: Implement a user story on a dedicated feature branch using worktrees. Multiple agents may work in parallel. Use when a story is approved and ready for implementation.
argument-hint: <story-id>
arguments: story_id
disable-model-invocation: true
effort: high
allowed-tools: Read Write Edit Bash(git *) Bash(./gradlew *) Bash(npm *) Bash(dotnet *) Bash(find *) Bash(grep *)
hooks:
  PostToolUse:
    - matcher: "Write|Edit"
      hooks:
        - type: command
          command: "${CLAUDE_PROJECT_DIR}/.claude/hooks/post-edit-lint.sh"
          timeout: 30
          statusMessage: "Linting changed file..."
---

## Story to implement: $story_id

## Git status

!`git status --short`

## Instructions

### 1 — Prepare

- Fetch story details from issue tracker (MCP)
- Read `docs/requirements.md`, linked epic, any ADRs
- Inspect codebase for existing patterns to follow
- Read rules: `solid-principles`, `quality-gates`, tech-stack rules

Determine branch name: `<type>/$story_id-<slug>`

### 2 — Branch and worktree

```bash
git checkout main && git pull
git checkout -b <branch-name>
# For parallel agents:
git worktree add ../worktree-$story_id-impl <branch-name>
```

### 3 — Detect story type

Check the story's labels. If any of `docs`, `documentation`, or `adr` are present, follow **§ 3-D (Documentation)**. Otherwise follow **§ 3-C (Code)**.

### 3-C — Code implementation loop (Ralph Principle)

For each acceptance criterion:
1. Write the code
2. Run linter — fix all findings
3. Run existing tests — must not regress
4. Self-review against SOLID + language rules
5. If blocked:
   - Search codebase for prior solutions
   - Re-attempt
   - `/challenge ArchitectAgent "Blocked on <issue>, tried <approach>"`
   - After 3 retries → label story `BLOCKED`, notify human with problem + 2–3 options

### 3-D — Documentation implementation

For ADRs, developer references, operations guides, and user guides:

1. Determine the output path from `github-issue-management.md` (ADR naming, service vs. platform path)
2. Write the AsciiDoc file(s) — no code changes
3. Commit immediately with `docs(<scope>): <summary>`

Skip steps 4 (API contract), 6 (TestAgent), and all linter/test quality gates — documentation stories only require the file to exist and be committed.

### 4 — API contract (if API changes)

Update `openapi/openapi.yaml` before writing handlers.
Run `spectral lint openapi/openapi.yaml`. Verify no breaking changes (`oasdiff`).

### 5 — Commit each logical unit

**Skip this step for documentation stories** — step 3-D handles the commit directly.

```
<type>(<scope>): <summary>

<body>

Closes #$story_id
```

Only commit when: linter passes AND unit tests for new code pass.

### 6 — Signal TestAgent

After implementation complete on worktree, invoke:
`/write-tests $story_id <branch-name>`

TestAgent works in a parallel worktree and commits tests to the same branch.

**Skip this step for documentation stories.**

### 7 — Quality gate

See `quality-checklist.md` for the full gate list. All gates must pass before opening a PR.

**For documentation stories**: only verify the file exists at the correct path and the build is not broken. Skip coverage, linter, and SAST gates.

### 8 — Create PR

Once all gates pass → `/create-pr $story_id <branch-name>`

## Output

- Feature branch with conventional commits
- All quality gates passing
- PR opened via `/create-pr`
