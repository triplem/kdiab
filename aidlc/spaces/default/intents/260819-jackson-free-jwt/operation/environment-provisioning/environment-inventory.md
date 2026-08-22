# Environment Inventory — U1 Jackson-free JWT (#1606)

Consumes `../deployment-pipeline/cd-config.md`. (`deployment-architecture` and
`infrastructure-services` from `infrastructure-design`/3.4 are **N/A — stage skipped**; no cloud
infra to design/provision. See `cd-config.md` upstream note.)

The "environment" for #1606 is the existing GitHub-native delivery surface plus each service's
runtime JWT configuration. Nothing here is provisioned by this stage — it all pre-exists. The table
records what exists and #1606's delta to each (which is empty everywhere except the build toolchain).

## Delivery surface (pre-existing, unchanged by #1606)

| Component | Role | #1606 delta |
|---|---|---|
| GitHub repo `triplem/kdiab` | Source of truth; branch protection on `main` | None |
| GitHub Actions runners (`ubuntu-latest`, JDK 21 temurin) | Build/test/scan/publish compute | None |
| GHCR `ghcr.io/<owner>/kdiab-*` | Container image registry (9 images), tags `latest`/`v{version}`/`sha-<short>` | None — same images republished from changed source |
| `docker-publish.yml` + `release.yml` | Gated deploy-on-merge publish + semantic-release | None (used unchanged — see `cd-config.md`) |
| Keycloak realm `config/keycloak-realm.json` | JWT issuer (RS256/JWKS), clients, audiences, test users | **None** — realm needs no change (ADR-023) |
| Running production environment | — | **Does not exist** (deployment-pipeline Q1); nothing to provision |

## Runtime JWT configuration (per service — pre-existing config keys)

From each `kdiab-<svc>/src/main/resources/application.conf` `jwt { }` block (verified on
kdiab-measures, representative of all 8 backends):

| Config key | Value / source | Path | #1606 delta |
|---|---|---|---|
| `domain` (issuer) | `${?JWT_DOMAIN}` → Keycloak realm URL | Production (RS256) | None |
| `jwksUrl` | `${?JWKS_URL}` → realm `…/protocol/openid-connect/certs` | Production (RS256) | None — Nimbus `JWKSource` reads the same URL |
| `audience` | per-service (`measure`, `profile`, …) | Both | None |
| `realm` | `${?JWT_REALM}` | Both | None |
| `secret` | `${?JWT_SECRET}` — **optional; unused in production** | Test only (HS256) | Behaviour delta: Nimbus enforces ≥32-byte min when `jwt.test=true`. **No config change** — prod never sets `jwt.test=true`. |

<!-- Text fallback: every service's prod JWT path is RS256 via domain+jwksUrl+audience against
Keycloak; JWT_SECRET is optional and unused in prod; the HS256 min-length change is test-scope only. -->

## Build toolchain (the one component with a #1606 delta)

| Component | Where | #1606 delta |
|---|---|---|
| `nimbus-jose-jwt` `10.0.1` | `gradle/libs.versions.toml` (version + module) | **Added** — the JWT verification library (ADR-023) |
| `testImplementation(nimbus-jose-jwt)` | `build-logic/.../kdiab.ktor-service.gradle.kts` | **Added** — test token minting via Nimbus `MACSigner`/`SignedJWT` |
| `com.auth0:java-jwt`, `com.auth0.jwk:jwks-rsa` | (removed via `ktor-server-auth-jwt` shed) | **Removed** from runtime + test |
| jackson force-pin (`jackson-core`, `jackson-databind`) | `kdiab.kotlin-base` `constraints{}` | **Removed** (handlebars pin retained) |

These deltas are already merged into the build config on the feature branch and verified in CI
(`../deployment-pipeline/cd-config.md` → `quality-gates.md`). No provisioning action needed — the
build environment self-provisions from the Gradle version catalog on the runner.

## Provisioning actions required for #1606

**None.** No environment is created, no secret is rotated, no realm is edited, no env var is added.
This stage is validation-only; see `validation-report.md`.
