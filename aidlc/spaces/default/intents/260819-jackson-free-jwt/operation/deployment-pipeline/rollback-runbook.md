# Rollback Runbook — U1 Jackson-free JWT (#1606)

Consumes `../../construction/ci-pipeline/ci-config.md`,
`../../construction/ci-pipeline/quality-gates.md`. (`deployment-architecture` / `cicd-pipeline`
from 3.4 are **N/A — stage skipped**; see `cd-config.md`.)

Rollback for #1606 is **source-level**: the change is contained and reverts cleanly (ADR-023
Consequences: "Reversible via a single `git revert` — the change is contained to `Security.kt`, the
two build files, per-service test minters, and this ADR"). Because there is no continuously-running
production environment today (deployment questions Q1), there is **no live redeploy step and no
metric-based rollback trigger** — rollback is triggered by *discovery* and executed at the source.
This runbook is written to hold whether the defect is caught pre-merge, post-merge-pre-consumption,
or after someone has pulled the images.

## When to roll back (trigger)

There is no automated observable trigger (Q4 — nothing is running to emit one). Roll back on
**discovery** of any of:

- A defect found by the **manual security review** or **CodeQL** after merge (e.g. a none-alg,
  issuer/audience-skip, or claim-parsing gap in the Nimbus provider).
- A **parity break** surfaced by tests or manual verification: a valid Keycloak token wrongly rejected
  (`401` where `com.auth0:java-jwt` accepted) or an invalid token wrongly accepted.
- A **supply-chain regression**: jackson / `java-jwt` / `jwks-rsa` reappearing on any runtime
  classpath, or jackson silently downgrading to the CVE-vulnerable `2.21.3` (the AC-1/AC-8 guard in
  `quality-gates.md`).
- Any **CI red** on `main` after merge that is a real #1606 regression (distinguish from the known
  flaky `#1614` `apiSpec` race noted in `ci-config.md` — that is a re-run, not a rollback).

> Severity note (T1D safety context): a wrong-**reject** locks every user out of every service; a
> wrong-**accept** is a cross-platform security hole. Treat either as high severity — revert first,
> diagnose second. Do not forward-fix an auth defect under pressure.

## Rollback procedure (primary — source-level revert + republish)

This is the Q2 decision: `git revert` the merge, let CI rebuild and republish clean images. No live
redeploy (nothing is running).

1. **Identify the merge commit** of the #1606 PR on `main`:
   ```bash
   git checkout main && git pull
   git log --oneline --merges --grep "#1606"        # find the merge commit SHA
   ```
2. **Create a revert branch** (never commit to `main` directly — team practice / hook-enforced):
   ```bash
   git checkout -b revert/1606-jackson-free-jwt
   git revert -m 1 <merge-commit-sha>               # -m 1 = keep main's first parent
   ```
   `git revert -m 1` restores `com.auth0:java-jwt`, the old `ktor-server-auth-jwt` `jwt {}` provider,
   the jackson force-pin, and the per-service test minters in one commit. If the auth path has moved
   since merge, resolve conflicts favouring the pre-#1606 `Security.kt`.
3. **Verify locally before opening the PR** (the same gate #1606 had to pass — `quality-gates.md`):
   ```bash
   ./gradlew check          # tests + Detekt + Kover ≥80% across all 9 modules
   ```
   Confirm the revert compiles and the auth accept/reject tests pass on the restored `java-jwt` path.
4. **Open the revert PR** with a Conventional Commit subject and issue linkage:
   `revert(auth): jackson-free JWT verification (#1606)` — body `Reverts #1606. <reason>. Refs #1603.`
5. **Let CI run and the human gate apply.** All checks green + merge-commit (never squash). On merge to
   `main`, `docker-publish.yml` re-runs its gate and **republishes all nine images to GHCR** built from
   the reverted source — new `latest`, a new `v{version}` (semantic-release: `revert` → patch bump),
   and a fresh `sha-<short>`. The images are now back on `java-jwt`.
6. **Confirm the supply-chain state of the republished images** (the revert intentionally re-adds
   jackson — that is the accepted trade-off of undoing #1606):
   ```bash
   # jackson/java-jwt are EXPECTED back after a revert; confirm the force-pin restored a SAFE jackson
   ./gradlew :kdiab-common:dependencyInsight --dependency jackson-databind --configuration runtimeClasspath
   ```
   Ensure jackson resolves to the pinned safe version (≥ 2.21.4), **not** the CVE-vulnerable `2.21.3`.
7. **Close the loop**: reopen #1606 (do not file a duplicate — reuse the issue), annotate ADR-023 with
   the revert reason and a `Status: Superseded-by-revert` note, and record the incident.

## Recovering a specific prior artifact (no rebuild)

Every prior release remains in GHCR under immutable tags. If a consumer needs the pre-#1606 image
*right now* without waiting for a revert PR to build, pull the last good tag directly:

```bash
# list available tags for a service, pick the last pre-#1606 v{version} or sha-<short>
docker pull ghcr.io/<owner>/kdiab-measures:<prior-v{version}-or-sha>
```

This is a stopgap for a local/self-hosted consumer, not a substitute for the source-level revert —
`latest` still points at the #1606 images until the revert republishes.

## Smoke test (deferred — ready for when a running environment exists)

No post-deploy smoke runs today (Q3 — no deploy target). This is the auth smoke test to wire as the
deploy health gate the moment a running environment is introduced (see `cd-config.md` forward hooks).
Per service, against a valid Keycloak token and a tampered/expired one:

```bash
BASE=http://<service-host>:<port>/api/v1
# 1) POSITIVE — a valid token must be accepted
curl -sS -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $VALID_TOKEN" "$BASE/<authed-endpoint>"
#    expect: 2xx
# 2) NEGATIVE — a tampered/expired token must be rejected with the standard body
curl -sS -w '\n%{http_code}\n' -H "Authorization: Bearer $TAMPERED_TOKEN" "$BASE/<authed-endpoint>"
#    expect: 401 + ErrorResponse JSON body; a TOKEN_REJECTED log line with reason=<…>
```

Run against the canary (`kdiab-measures`) first, then the rest. A single failure of either the
positive or negative assertion = do not proceed to the fleet; roll back. When wired, this replaces
"deployment is not done until smoke passes" for the auth path.

## Roles

Solo-maintainer footprint. The maintainer executes the revert; ADR-023 recommends an **external
clinical/security advisor countersignature** for the safety-sensitive auth path (consistent with the
platform's P0 incident practice). No on-call rotation exists.
