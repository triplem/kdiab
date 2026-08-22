# Environment Validation Report — U1 Jackson-free JWT (#1606)

Consumes `../deployment-pipeline/cd-config.md`. (`deployment-architecture` and
`infrastructure-services` from `infrastructure-design`/3.4 are **N/A — stage skipped**; no cloud
infra exists to validate. This report validates the GitHub-native delivery surface + runtime JWT
config instead — see `environment-inventory.md`.)

Method: **live read-only sweep** of the repo on the feature branch (2026-08-21). No mutation. The
verdict is split by capability so a partial-green result would be visible; here every capability is
green.

## Sweep evidence

| Check | Command (read-only) | Result |
|---|---|---|
| Production never enables HS256/test path | grep `jwt.?test` across `docker-compose*.yml`, `config/` | **none found** — prod uses RS256 only |
| No new secret injected | grep `JWT_SECRET` across compose env | **not set** — `secret = ${?JWT_SECRET}` optional, unused in prod |
| Prod JWT path intact | inspect `kdiab-measures/…/application.conf` `jwt{}` | RS256 via `domain`+`jwksUrl`+`audience` — unchanged |
| Keycloak realm unaffected | grep `HS256`/`hmac`/`secret` in `keycloak-realm.json` | only a **client** secret present; no JWT signing material to change |
| Build env has Nimbus | grep `nimbus` in `libs.versions.toml`, `build-logic/` | `10.0.1` version+module + `testImplementation` wired |
| Swagger/jackson path closed | `swagger.enabled` default | `false` (consistent with #1607) |

## Readiness verdict (by capability)

| Capability | Ready? | Basis |
|---|---|---|
| **Build & publish** (produce the 9 jackson-free images) | ✅ GREEN | Runner (JDK 21) + Gradle catalog self-provision Nimbus; `docker-publish.yml` gate + publish jobs exist and are unchanged (`cd-config.md`). |
| **Auth runtime configuration** (services verify JWTs after the swap) | ✅ GREEN | Prod RS256/JWKS config unchanged; Nimbus `JWKSource` reads the same `jwksUrl`. No env/realm/secret change (ADR-023). |
| **Enforce the merge gate** (block a bad auth change) | ✅ GREEN | Branch protection on `main` + `docker-publish.yml` all-checks-green gate + ADR-023 manual security sign-off (`cd-config.md` § Approval workflow). |

No capability is amber/red; no remediation is required before this stage's gate.

## DevSecOps perspective (support: aidlc-devsecops-agent)

- **No new attack surface at the environment layer.** #1606 adds no endpoint, port, secret, or IAM
  grant. The only new material is a library (Nimbus, an audited JOSE implementation — ADR-023
  rejected a hand-rolled verifier precisely to avoid owned crypto).
- **Secret hygiene preserved.** `JWT_SECRET` remains optional and unused in production; no secret is
  added to source, compose, or CI. The Nimbus ≥32-byte HS256 minimum is a *hardening* that only
  applies to the test path, and test secrets were already lengthened in Construction.
- **The security-critical wiring is reviewed, not provisioned.** The provider/verifier config is
  covered by CodeQL + the mandatory ADR-023 manual review (a merge gate, not an environment gate);
  this stage confirms there is nothing further to lock down at the environment level.

## Compliance perspective (support: aidlc-compliance-agent)

- **No data classification or data-flow change.** #1606 changes *how* a JWT is verified, not *what*
  identity data flows. `UserPrincipal` extraction (`sub`, `roles`, `allowed_patients`, `timezone`) is
  preserved byte-for-byte (ADR-023) — no PII surface change, no new processing, no consent/retention
  impact.
- **Auditability intact.** The `TOKEN_REJECTED` log line is retained and *enriched* (structured
  `reason` + proxy-aware `remote`) — a net improvement to A09 security-logging without logging tokens
  or PII.
- **Change is reversible and evidenced.** Single `git revert` (ADR-023) + full CI evidence trail
  (`quality-gates.md`) satisfy the change-control expectation for a safety-sensitive auth path.

## Optional deeper checks (not required; offered at the gate)

If desired, a live GitHub/GHCR probe can be added as follow-up evidence (all read-only):
`gh auth status`, `gh api repos/triplem/kdiab/branches/main/protection`, and a GHCR tag listing to
confirm prior immutable image tags are present for the `rollback-runbook.md` stopgap path. Omitted by
default to avoid network/auth prompts — the surface's existence is already proven by the merged prior
intents and the in-repo workflows.

## Conclusion

#1606 requires **no environment provisioning**. The delivery surface and runtime JWT configuration are
ready as-is; the only change consumed by the environment is the rebuilt image set, which the existing
pipeline (`cd-config.md`) publishes. Readiness: **GREEN** across build-&-publish, auth-runtime-config,
and merge-gate-enforcement.
