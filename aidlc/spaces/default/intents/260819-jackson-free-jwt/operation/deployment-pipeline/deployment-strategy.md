# Deployment Strategy — U1 Jackson-free JWT (#1606)

Consumes `../../construction/ci-pipeline/ci-config.md`,
`../../construction/ci-pipeline/quality-gates.md`. (`deployment-architecture` and `cicd-pipeline`
from `infrastructure-design`/3.4 are **N/A — stage skipped**; no cloud infra to design. See
`cd-config.md` for the full upstream note.)

## Strategy: publish immutable images on merge; no live rollout stage

The deployment strategy for #1606 is dictated by one fact (deployment questions Q1): **there is no
continuously-running production environment**. The strategy is therefore not "how do we roll new
running instances out safely" but "how do we publish trustworthy artifacts and keep every prior
artifact recoverable."

- **Unit of deployment**: the container image. Nine images (`kdiab-measures`, `-profiles`,
  `-treatments`, `-analyze`, `-carbs`, `-calc`, `-nightscout`, `-users`) + `kdiab-ui` (unchanged by
  #1606 — no UI code changed, per `quality-gates.md`). #1606 changes all eight backends + `kdiab-common`
  (the shared `Security.kt` Nimbus provider), so all eight backend images are rebuilt.
- **Trigger**: merge to `main` → `docker-publish.yml` (see `cd-config.md`).
- **Rollout style**: **all-at-once publish** to GHCR. Because there is no running fleet, there is no
  rolling/canary/blue-green *rollout*; "all-at-once" describes the publish, which is inherently safe —
  publishing an image mutates no running system.
- **Tags per image**: `latest` (moving, default branch), `v{version}` (immutable semver), `sha-<short>`
  (immutable commit). The immutable tags are the recoverability guarantee — every prior release stays
  pullable.

## Why all-at-once publish is safe for a fleet-wide auth change

An auth-library swap changes a property shared by all nine modules. Three facts make publishing all
nine images together safe:

1. **Behavioural parity is proven before publish, not after.** ADR-023's whole design goal is an
   *identical* accept/reject decision, `UserPrincipal` extraction, and `401` body versus
   `com.auth0:java-jwt`. `quality-gates.md` records this verified in CI (9/9 modules green, Kover
   ≥80%, security review PASS). The gate that protects users is CI correctness, which runs *before*
   the publish gate.
2. **Mixed-version fleets are safe.** The JWT wire format is unchanged — still a standard RS256 token
   verified against the same Keycloak JWKS. A partially-updated deployment (some images jackson-free,
   some not) validates the same tokens identically, because each service verifies independently. So
   even when a running environment exists later, images can be rolled one-at-a-time with no
   cross-service token-compatibility risk.
3. **No configuration or data change ships with the images.** Per ADR-023 Consequences, production
   uses RS256/JWKS where `jwt.secret` is unused; the new HS256 ≥32-byte minimum is **test-scope only**.
   No `keycloak-realm.json`, environment variable, or schema change is required. The images are
   drop-in replacements.

## Blast radius (documented, because this is auth)

Although nothing auto-deploys today, the strategy records the blast radius so a future rollout is done
consciously: a regression in the Nimbus provider is **fleet-wide** — every service uses the same
`kdiab-common` `configureSecurity()`. A wrong-reject bug locks every user out of every service; a
wrong-accept bug is a security hole across the platform. This is why (a) the manual security sign-off
is a hard merge gate, and (b) the future rollout hook in `cd-config.md` specifies a canary service
(kdiab-measures) with an auth accept/reject smoke test before fleet-wide rollout.

## Verification authority (replaces post-deploy smoke, today)

With no deploy target (Q3), there is no post-deploy smoke test. The verification authority for #1606
is **CI**, and it is already in place (`quality-gates.md`):

| Signal | Gate | Status |
|---|---|---|
| Behavioural parity (accept/reject matrix, `UserPrincipal`, `401` body) | unit + integration + e2e `:check` | ✅ 9/9 green |
| Coverage | `:koverVerify` ≥ 80% | ✅ green |
| Supply-chain goal (jackson/java-jwt/jwks-rsa absent from runtimeClasspath) | `dependencyInsight` + Trivy | ✅ verified (AC-1/AC-8) |
| SAST on the new auth wiring | CodeQL | ⏳ CI (security review PASS) |
| Human auth sign-off | ADR-023 manual security review | required before merge |

The full auth accept/reject smoke test is specified in `rollback-runbook.md` § "Smoke test (deferred)"
so it is ready to wire the moment a running environment exists.

## Summary

| Dimension | #1606 decision |
|---|---|
| Deploy target | GHCR image registry (no running prod) |
| Rollout style | All-at-once immutable publish; mixed-version-safe when a fleet later exists |
| Config/data change | None (RS256/JWKS unaffected; HS256 min is test-only) |
| Verification | CI (parity + coverage + supply-chain + SAST + manual security sign-off) |
| Rollback | Source-level `git revert` + republish — see `rollback-runbook.md` |
