---
status: pending
last_updated: ""
depends_on: []
branch: ai-dlc/cleanup-claude-tooling/01-prune-obsolete-claude-tooling
discipline: devops
pass: ""
workflow: ""
ticket: ""
design_ref: ""
views: []
---

# unit-01-prune-obsolete-claude-tooling

## Description
Remove the obsolete `.claude/` tooling that the ai-dlc plugin and the actual stack
have made redundant, and bring root `CLAUDE.md`'s Rules Index and Skills Index back in
sync with what remains. One atomic cleanup: file deletions + one hook edit + doc index
rewrite. No application code is touched.

## Discipline
devops - repository tooling / AI-configuration maintenance.

## Domain Entities
Skills (`.claude/skills/<name>/`), rules (`.claude/rules/<name>.md`), the
`session-start.sh` hook, and the CLAUDE.md indexes. See intent.md "Remove / Keep
inventory" for the authoritative lists.

## Data Sources
- `.claude/skills/` — delete 25 skill directories (see list below).
- `.claude/rules/` — delete `java-style.md`, `spring-boot.md`.
- `.claude/hooks/session-start.sh` — remove the Angular `elif` in stack detection.
- `CLAUDE.md` — rewrite `## Rules Index` and `## Skills Index`.

## Technical Specification

### 1. Delete skills (25 directories under `.claude/skills/`)
`angular-patterns`, `gather-requirements`, `write-epics`, `write-stories`, `implement`,
`implement-epic`, `write-tests`, `create-pr`, `release`, `architect-review`,
`security-review`, `qa-review`, `devops-review`, `ux-review`, `performance-review`,
`operations-review`, `requirements-review`, `technical-writer-review`, `challenge`,
`challenge-all`, `pr-reviewer`, `learn`, `domain-model`, `create-adr`,
`claude-code-expert`.

Do NOT delete (keep): `doctor-t1d-review`, `patient-t1d-review`, `kotlin-patterns`,
`typescript-patterns`, `react-patterns`, `openapi-patterns`, `logging-kotlin`,
`logging-typescript`.

### 2. Delete rules (2 files under `.claude/rules/`)
`java-style.md`, `spring-boot.md`. Keep the other 13.

### 3. Edit `.claude/hooks/session-start.sh`
In the `package.json` stack-detection block, remove only the Angular branch:
```
  if find . -name "angular.json" -not -path "*/node_modules/*" 2>/dev/null | grep -q .; then
    STACK="Angular/TypeScript"
  elif grep -q '"react"' package.json 2>/dev/null; then
```
becomes:
```
  if grep -q '"react"' package.json 2>/dev/null; then
```
Leave the rest of the detector unchanged. Verify with `bash -n .claude/hooks/session-start.sh`.

### 4. Rewrite CLAUDE.md indexes
- **Rules Index**: list exactly the 13 kept rules. Remove `java-style.md`,
  `spring-boot.md`, and the dangling `react.md`, `angular.md`, `dotnet.md` lines.
- **Skills Index**: list exactly the 8 kept skills, grouped sensibly (T1D reviewers;
  code-pattern skills; logging skills). Remove every removed skill and the dangling
  `java-patterns`, `dotnet-patterns`, `spring-boot-patterns`, `logging-java` entries.
- Keep index wording/format consistent with the surrounding document.

## Success Criteria
- [ ] All 25 listed skill directories are gone; the 8 kept skills remain.
- [ ] `java-style.md` and `spring-boot.md` are gone; the other 13 rules remain.
- [ ] Angular `elif` removed from `session-start.sh`; `bash -n` passes; `"react"`
      detection still present.
- [ ] CLAUDE.md Rules Index == the 13 kept rules exactly (no removed/dangling entries).
- [ ] CLAUDE.md Skills Index == the 8 kept skills exactly (no removed/dangling entries).
- [ ] `grep -rn` over `.claude/` + `CLAUDE.md` finds no removed skill/rule name.
- [ ] `git diff --stat` touches only `.claude/`, `CLAUDE.md`, and `.gitignore`.

## Risks
- **Removing a skill still referenced elsewhere**: mitigated — discovery confirmed no
  hooks/settings/scripts reference the removed skills; verify with the final grep.
- **Breaking the session-start hook while editing**: mitigated — remove only the one
  `elif`, then run `bash -n` to confirm the script still parses.
- **Leaving CLAUDE.md indexes inconsistent**: mitigated — the grep criterion catches
  any stale index line.

## Boundaries
- Does NOT remove rules/skills for the live stack (Kotlin, TypeScript, React, OpenAPI,
  logging) or the T1D specialist reviewers.
- Does NOT touch the generic multi-stack Java/.NET branches of `session-start.sh`
  beyond the Angular `elif` (out of scope for this cleanup).
- Does NOT modify application code, build files, or CI workflows.

## Notes
- The `.gitignore` already gained `.ai-dlc/worktrees/` during elaboration; that line
  rides along in this branch's PR.
- Merge via PR to `main` with a merge commit (no squash), per project convention.
