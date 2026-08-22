# Stakeholder Map — Jackson-free JWT Verification (#1606)

**Intent:** replace `com.auth0:java-jwt` (+ `com.auth0.jwk:jwks-rsa`) with a jackson-free
Nimbus-based JWT verification. Refs #1603.

---

## Key Stakeholders and Interests

| Stakeholder | Role in this change | Primary interest / concern |
|---|---|---|
| **Repo owner / lead maintainer** (`triplem`) | Decision-maker; approves design, reviews PR, merges | Epic #1603 closed cleanly; no auth regression; behaviour-identical swap; clean merge-commit with `Closes #1606`. |
| **Security / DevSecOps reviewer** | Gatekeeper on the security review | The new verification path is cryptographically correct (RS256 signature + JWKS key selection, HMAC test path), no validation weakened (audience, issuer, expiry, leeway); no unmitigated HIGH/CRITICAL; jackson attack surface actually eliminated. |
| **QA / test owner** | Gatekeeper on the auth e2e suite | Full auth e2e coverage exercises valid/expired/wrong-audience/missing-roles/bad-subject/HMAC-test tokens; parity with current behaviour proven, not assumed. |
| **Backend service owners** (all 8: measures, profiles, treatments, analyze, carbs, calc, nightscout, users) | Consumers of `kdiab-common` `configureSecurity()` | The shared change compiles and passes each service's own `check`; no service-specific auth config breaks; JWT-forwarding services (analyze, calc) still accept the same multi-audience token. |
| **Operators / deployers** | Affected if any `jwt.*` config key changes | Any config-key/env-var change is surfaced up front (docker-compose, `.env`, keycloak-realm) with a documented migration note; ideally none, but a bounded change is permitted (Q4). |
| **End users** (patients `sarah`/`mike`, doctors `dr_house`/`dr_cameron`, `admin`) | Indirect | Authentication continues to work with **no forced re-login** and no visible change — token format is unchanged (Keycloak-issued). |
| **Keycloak** (identity provider) | Unchanged external dependency | JWKS endpoint and token/claim contract stay exactly as-is; this change touches only the *verification* side, never issuance. |
| **CI / release pipeline** (`docker-publish.yml`, semantic-release) | Automated gate | All backends + kdiab-ui build green; `kdiab-common` publishes; no force-pin removal silently downgrades jackson into a known CVE. |

## Decision-makers vs. Influencers

**Decision-makers** (their approval is required to proceed / merge):
- **Lead maintainer (`triplem`)** — approves the intent, the application design (library choice,
  config-key decisions), and merges the PR only when all five verification gates are green.
- **Security review outcome** — a HIGH/CRITICAL finding blocks merge until mitigated (hard gate).
- **CI status** — green across every backend + kdiab-ui is a non-negotiable merge gate
  (project rule: never merge with a failing/pending check).

**Influencers** (shape the solution but do not hold the merge gate):
- **AI-DLC architect/developer/quality/devsecops agent perspectives** — inform the design,
  implementation, test strategy, and security posture across the workflow stages.
- **Epic #1603 history** — the prior runtimeClasspath findings (#1605, #1607, the jackson
  force-pin) constrain and inform the approach.
- **Existing auth e2e tests and `Security.kt` behaviour** — the current behaviour is the
  authoritative parity target.

## Communication Requirements

- **Config-key change (if any):** If the Nimbus migration alters any `jwt.*` key or env var,
  it MUST be documented before merge — updated `docker-compose`/`.env`/keycloak references plus
  an ops/user-guide "config change" note — and called out explicitly in the PR description.
  (Per the project rule for settings that affect operators; no forced user re-login applies here
  since token format is unchanged.)
- **PR traceability:** the PR references the issue (`Closes #1606`), follows Conventional Commits
  (`feat(auth): …`), and is merged with a **merge-commit, not squash** (team rule, preserves the
  `Closes #N` linkage). Epic #1603 is updated/closed once #1606 merges.
- **Security review artifact:** the security-review result is recorded and linked from the PR so
  the "no unmitigated HIGH/CRITICAL" gate is auditable.
- **Dependency proof:** the `gradle dependencyInsight` output showing jackson (and both Auth0
  libs) gone from `runtimeClasspath` is captured as merge evidence — the durable proof that
  epic #1603's goal is met.
- **No end-user comms needed:** because authentication behaviour and token format are unchanged,
  no user-facing announcement or re-login notice is required.
