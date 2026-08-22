# Deployment Log — U1 Jackson-free JWT (#1606)

Consumes `../deployment-pipeline/cd-config.md`, `../deployment-pipeline/deployment-strategy.md`,
`../environment-provisioning/environment-inventory.md`,
`../../construction/build-and-test/build-test-results.md`.

## Execution status: NOT YET EXECUTED (document-only, by decision)

Per the stage decision (Q1 = A, document-only / dry-run), **no git or GitHub action was taken by the
conductor.** The deployment (publishing the 9 jackson-free images to GHCR) has **not** occurred and
must not be inferred as done. This log records the actual state and the maintainer-driven runbook to
execute it.

### State at stage time (live check, 2026-08-21)

| Fact | Value |
|---|---|
| Branch | `feature/1606-jackson-free-jwt` |
| Core provider commit | `7d767d2a feat(auth): jackson-free JWT verification via Nimbus custom provider (core)` |
| Remaining uncommitted code changes | ~37 non-`aidlc/` files (test minters, build config, ADR-023, etc.) |
| Branch pushed? | **No** (no upstream) |
| PR open? | **No** |
| CI run? | **No** (branch unpushed) |
| Security sign-off (ADR-023)? | Pending (build-and-test review PASS; human countersign required) |
| Images published? | **No** |

## Why the conductor did not execute

The deployment for #1606 = **merge to `main`**, which is a hard gate and maintainer-owned
(`deployment-strategy.md` § Verification authority; `cd-config.md` § Approval workflow):

- All GitHub Actions must be green (no `--admin` bypass) — team practice / project rule.
- ADR-023 mandates a **manual security sign-off** on the safety-sensitive Nimbus auth path.
- Merge is a **merge-commit** (never squash) with `Closes #1606`, done by the maintainer.
- The change is safety-sensitive (T1D platform, fleet-wide auth) — outward-facing and not reversible
  without a follow-up revert. The conductor does not take it unilaterally.

Additionally the work is incomplete (37 uncommitted files), so even PR-open would be premature without
the author finishing and running the gate.

## Maintainer execution runbook (the deployment, when you run it)

1. **Finish + commit** the remaining #1606 changes on the feature branch. Keep the `aidlc/` record
   **out** of feature-branch commits (audit shards must not be committed on a feature branch — project
   rule); commit only application/build/doc files. Use Conventional Commits.
2. **Run the full gate locally** (`build-test-results.md` recorded these green pre-flight):
   ```bash
   ./gradlew check          # 9 modules: tests + Detekt + Kover ≥80%
   ```
3. **Push** the branch and **open the PR** with issue linkage:
   `feat(auth): jackson-free JWT verification via Nimbus (#1606)` — body `Closes #1606. Refs #1603.`
4. **Wait for all GitHub Actions green.** Expect Trivy CRITICAL/HIGH to **improve** (jackson/java-jwt/
   jwks-rsa gone). If kdiab-analyze/nightscout `:check` fails on `upstream-*` client refs, that's the
   known flaky `#1614` `apiSpec` race — **re-run the job**, it is not a #1606 regression
   (`cd-config.md`).
5. **Obtain the ADR-023 manual security sign-off** on the Nimbus provider (a human countersignature on
   the auth path).
6. **Merge** (merge-commit, never squash). On merge to `main`, `docker-publish.yml` runs its
   all-checks-green gate then **publishes the 9 jackson-free images** to GHCR (`latest`, `v{version}`,
   `sha-<short>`); `release.yml` tags the version.
7. **Post-merge cleanup**: delete the remote **and** local feature branch (project rule).
8. **If anything regresses**: follow `../deployment-pipeline/rollback-runbook.md` (source-level
   `git revert -m 1` → CI republish).

## Deployment target reminder

There is no running production environment (`environment-inventory.md` → deployment-pipeline Q1), so
"deployment complete" = images published to GHCR. No live rollout, no server restart, no traffic
shift follows. Consumption of the published images is ad hoc/downstream.
