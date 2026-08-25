# Deployment Log — jwt-test-guard (#1588 / FIND-SEC-001)

Publish-only pipeline: "deployment" = publish the change to GitHub as a reviewed PR that, on merge,
triggers the GHCR image publish + semantic release. No environment deploy (no running prod).

## Actions taken (2026-08-25)

| Step | Detail |
|---|---|
| Branch | `fix/1588-guard-test-jwt-production` created from `main` HEAD (`88428807`) |
| Commit | `0e10626b` — `fix(security): guard test-mode JWT out of production` — **36 code files** (+84/−3); `Closes #1588` |
| Scope guard | Only app-source `.kt`/`.conf` staged; the 7 `aidlc/` workflow records were **excluded** (committed separately via the established `chore(aidlc)` flow, cf. #1619) |
| Push | `origin` → `github.com/triplem/kdiab` |
| PR | **#1642** → base `main` (https://github.com/triplem/kdiab/pull/1642) |
| Issue | #1588 assigned to `@me`, labelled `In Progress` |
| Merge | **NOT performed** — left to the maintainer after CI is green (team rule: merge-commit, never squash; no merge on failing/pending checks) |

## Commit message (Conventional Commits, Angular preset)
`fix(security): …` → semantic-release **patch** bump. Footer `Closes #1588` links the review finding.

## Post-merge (maintainer, automatic)
On merge to `main`: `docker-publish.yml` republishes all 9 backend images to GHCR (immutable tags),
`ci-common-publish.yml` republishes the kdiab-common JAR, `release.yml` cuts the patch release.

## Follow-up bookkeeping (separate from this PR)
The `aidlc/` workflow records for this intent (codekb JWT-detail refresh, `project.md` learnings,
the `260823-jwt-test-guard` record, `intents.json`, state) are to be committed to `main` via a
`chore(aidlc)` commit/PR — never on this feature branch (mid-workflow audit shards must not ride a
code branch).
