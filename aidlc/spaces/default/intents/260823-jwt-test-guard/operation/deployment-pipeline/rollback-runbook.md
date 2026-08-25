# Rollback Runbook — jwt-test-guard (#1588)

> Publish-only pipeline ⇒ rollback is **source-level**: `git revert` + CI republish. There is no live
> environment to redeploy (project.md: no-running-prod).

## When would you roll back?
This change only *adds* a startup guard; it cannot break a correctly-configured service (production
never sets `jwt.test`). The realistic rollback trigger is narrow: a deployment that legitimately relied
on `jwt.test=true` **without** having added `JWT_ALLOW_TEST_MODE` now fails to boot. The correct fix is
almost always **forward** (set `JWT_ALLOW_TEST_MODE=true` in that non-prod environment), not rollback.

## Rollback procedure (source-level)
1. Identify the merge commit for this change on `main` (`git log --grep '#1588'`).
2. Revert it: `git checkout main && git pull && git revert -m 1 <merge-sha>` (or revert the squash/merge
   commit as appropriate; team uses **merge-commits**, so `-m 1`).
3. Open a PR with the revert; let the full CI gate go green; merge.
4. On merge, `docker-publish.yml` republishes all 9 backend images **without** the guard, and
   semantic-release cuts a new patch. The immutable prior tags (`v{version}`, `sha-<short>`) remain
   available for anyone pinning them.

## Forward hook (if a running prod is later introduced)
Fast rollback would become "redeploy the prior immutable GHCR tag" (`v{prev}` / `sha-<prev>`) — no
rebuild needed. Documented placeholder only; not built today.

## Operational NOTE — `JWT_ALLOW_TEST_MODE` (carry into any ops/deployment guide)
- **Production/staging: never set `JWT_TEST` or `JWT_ALLOW_TEST_MODE`.** Leaving both unset yields the
  secure Keycloak-JWKS verifier (unchanged default).
- **CI/test only:** `JWT_TEST=true` now REQUIRES `JWT_ALLOW_TEST_MODE=true` (and a `JWT_SECRET`) or the
  service fails fast at startup — by design.
- If a service crash-loops at boot with `jwt.test=true is not permitted unless jwt.allowTestMode=true`,
  the environment is misconfigured: unset `JWT_TEST` for prod, or (non-prod only) add
  `JWT_ALLOW_TEST_MODE=true`. The message contains the remediation; it never logs the secret.
