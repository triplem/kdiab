---
workflow: default
git:
  change_strategy: unit
  auto_squash: false
announcements: [changelog]
passes: []
active_pass: ""
iterates_on: ""
created: 2026-08-15T10:29:20Z
status: active
epic: "1537"
quality_gates: []
---

# Prune obsolete `.claude/` tooling superseded by the ai-dlc plugin

## Problem
The project adopted the ai-dlc plugin (`/ai-dlc:*`) for its SDLC workflow, but the
repo still carries the previous hand-rolled tooling in `.claude/`:
- ~25 custom skills that are now redundant (an Angular pattern skill for a stack with
  no Angular; SDLC skills like `write-epics`/`implement` replaced by ai-dlc; generic
  specialist-review skills replaced by ai-dlc review; meta skills).
- 2 rules for stacks the monorepo does not use (`java-style.md`, `spring-boot.md` —
  the backends are Kotlin/Ktor).
- An Angular detection branch in `session-start.sh` for a stack that isn't present.
- CLAUDE.md's Rules Index and Skills Index list files that no longer exist (dangling
  entries for `react.md`/`angular.md`/`dotnet.md` rules and `java-patterns`/
  `dotnet-patterns`/`spring-boot-patterns`/`logging-java` skills), plus entries for
  everything being removed.

This clutters the skill list, misleads agents about available tooling, and lets the
CLAUDE.md indexes drift from reality.

## Solution
Delete the obsolete skills, rules, and the Angular hook branch, then rewrite
CLAUDE.md's Rules Index and Skills Index so they list exactly what remains. This is a
pure repository/AI-configuration cleanup — no application code, build, or runtime
behaviour changes.

## Domain Model
This intent operates on repository configuration artifacts, not application entities.

### Entities
- **Skill**: a directory under `.claude/skills/<name>/` invoked via `/<name>`.
- **Rule**: a markdown file under `.claude/rules/<name>.md`, auto-applied guidance.
- **Hook**: a script under `.claude/hooks/` (here: `session-start.sh` stack detection).
- **Index**: the `## Rules Index` and `## Skills Index` sections of root `CLAUDE.md`
  that enumerate the above.

### Remove / Keep inventory
- **Remove — 25 skills**: `angular-patterns`; `gather-requirements`, `write-epics`,
  `write-stories`, `implement`, `implement-epic`, `write-tests`, `create-pr`,
  `release`; `architect-review`, `security-review`, `qa-review`, `devops-review`,
  `ux-review`, `performance-review`, `operations-review`, `requirements-review`,
  `technical-writer-review`, `challenge`, `challenge-all`, `pr-reviewer`; `learn`,
  `domain-model`, `create-adr`, `claude-code-expert`.
- **Keep — 8 skills**: `doctor-t1d-review`, `patient-t1d-review`, `kotlin-patterns`,
  `typescript-patterns`, `react-patterns`, `openapi-patterns`, `logging-kotlin`,
  `logging-typescript`.
- **Remove — 2 rules**: `java-style.md`, `spring-boot.md`.
- **Keep — 13 rules**: `agent-context`, `api-design`, `branching-strategy`,
  `commit-conventions`, `github-issue-management`, `kotlin-style`, `logging`,
  `openapi`, `quality-gates`, `security`, `solid-principles`, `test-pyramid`,
  `typescript-style`.
- **Hook**: remove only the Angular `elif` branch from `session-start.sh` stack
  detection; keep React/Node (and other) detection intact.
- **CLAUDE.md**: Rules Index and Skills Index rewritten to the kept sets; all dangling
  entries removed.

### Data Sources
- `.claude/skills/`, `.claude/rules/`, `.claude/hooks/session-start.sh`, `CLAUDE.md`
  (all read/written via the filesystem).

### Data Gaps
- None. Discovery confirmed the stack has no Angular/Java/Spring/.NET, and no
  hooks/settings/scripts reference the skills/rules being removed.

## Success Criteria
- [ ] The 25 listed skill directories are deleted from `.claude/skills/`; the 8 kept
      skills remain present.
- [ ] `.claude/rules/java-style.md` and `.claude/rules/spring-boot.md` are deleted;
      the other 13 rule files remain present.
- [ ] The Angular `elif` branch is removed from `.claude/hooks/session-start.sh`,
      `bash -n .claude/hooks/session-start.sh` passes, and React/Node detection is
      still present.
- [ ] CLAUDE.md Rules Index lists exactly the 13 remaining rules — no entries for
      `java-style`, `spring-boot`, `react.md`, `angular.md`, or `dotnet.md`.
- [ ] CLAUDE.md Skills Index lists exactly the 8 remaining skills — no entries for any
      removed skill or the dangling `java-patterns`/`dotnet-patterns`/
      `spring-boot-patterns`/`logging-java` skills.
- [ ] `grep -rn` across `.claude/` and `CLAUDE.md` returns no reference to any removed
      skill or rule name.
- [ ] No application code changed; `./gradlew` build and `npm run build` are unaffected
      (spot-check: `git diff --stat` touches only `.claude/`, `CLAUDE.md`, `.gitignore`).

## Context
- Adopted ai-dlc plugin provides the SDLC workflow (`elaborate`, `execute`, `review`,
  `reflect`, etc.), making the hand-rolled SDLC and generic-review skills redundant.
- Kept the two T1D-specialist reviewers (`doctor-t1d-review`, `patient-t1d-review`)
  because they encode project-specific clinical-safety perspectives not provided by
  ai-dlc, and the code-pattern/logging skills because they match the live stack.
- Delivery: per-unit PR to `main` (project blocks direct commits to `main`; merge via
  PR with a merge commit, no squash).
