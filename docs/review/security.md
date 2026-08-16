# Security & Compliance Review

> **Theme: security** (area code `SEC`, non-safety → severity caps at High).
> Findings follow [`CONVENTIONS.md`](./CONVENTIONS.md). Assessment per FR-2.1 (GDPR special-category,
> auth hardening/ABAC, MDR/SaMD) and FR-2.2 (**flag obligations, do not certify**). Prior art
> (`docs/security/accepted-risks.md` AR-001) is built on, not re-derived (A-4, FR-D.5).

## Verdicts

| Dimension | Verdict | Finding |
|---|---|---|
| Auth core (ABAC/JWKS/audience) | Sound | FIND-SEC-003 |
| Test-mode JWT toggle | **Concern** | FIND-SEC-001 |
| Doctor access revocation latency | Concern | FIND-SEC-002 |
| Security headers | Good; minor hardening | FIND-SEC-007 |
| MDR / SaMD posture | **Flag** | FIND-SEC-004 |
| GDPR erasure vs MDR retention | **Flag** | FIND-SEC-005 |
| GDPR special-category safeguards | **Flag** | FIND-SEC-006 |

### Findings

#### FIND-SEC-001 — Test-mode symmetric-JWT toggle has no production guard
- Severity: High · Effort: S · Confidence: High · Phase: Near · Area: security
- Evidence: `kdiab-common/.../plugins/Security.kt#configureSecurity` (`isTest` from `jwt.test`; `Algorithm.HMAC256(jwtSecret)` verifier)
- Patient-safety impact: n/a (data-security). Impact: whole-platform auth bypass risk.
- Finding: when `jwt.test=true`, every service verifies tokens with **HMAC256 (symmetric)** using a shared `jwt.secret` instead of Keycloak JWKS (RS256). The only guard is `check(!isTest || jwtSecret != null)` — it ensures a secret exists, but nothing prevents `jwt.test=true` from being set in production. A misconfig (or a leaked `JWT_SECRET`) then lets anyone forge a valid token for any user/role across all services.
- Finding severity rationale: capped at High (non-safety), but this is the highest-impact security finding — it is a platform-wide authentication downgrade.
- Recommendation: refuse to start in test-JWT mode outside an explicit dev/test environment (e.g. gate on a build flag or a `KDIAB_ENV!=production` assertion), and never ship `jwt.test=true` in a production image/config.
- Incremental alternative: n/a (small, self-contained guard).

#### FIND-SEC-002 — Doctor→patient access is JWT-embedded; revocation lags by token lifetime
- Severity: Medium · Effort: M · Confidence: High · Phase: Mid · Area: security
- Evidence: `kdiab-common/.../plugins/Security.kt#buildPrincipal` (`allowed_patients` claim) + `UserPrincipal.canAccess`
- Patient-safety impact: n/a (access-control / privacy).
- Finding: a doctor's `allowedPatients` set is baked into the access token. Revoking a doctor's access to a patient does not take effect until the token expires and is refreshed — a stale token keeps read access to special-category health data. Acceptable only if access tokens are short-lived.
- Recommendation: keep the Keycloak access-token TTL short (≤15 min per the project's own `security.md` guidance — verify the realm config) and document the revocation-latency window; for high-sensitivity revocation, consider a real-time allow-list check.
- Incremental alternative: verifying/shortening the token TTL is the low-cost first step; a live check is the larger follow-up.

#### FIND-SEC-003 — Authorization core is sound (verdict, no concern)
- Severity: Low · Effort: S · Confidence: High · Phase: Near · Area: security · Patient-safety impact: n/a
- Evidence: `kdiab-common/.../plugins/Security.kt` (`buildPrincipal` audience check + roles-required + `Uuid` subject parse; `canAccess` = self OR admin OR doctor-with-allow-list; JWKS HTTPS enforced for non-local)
- Finding: the ABAC model is clean and correct — deny-by-default (null principal when audience/roles/subject are invalid), self/admin/doctor-scoped access in one place (`canAccess`), audience validation, and an HTTPS-enforced JWKS provider. This is a well-factored authz core; centralizing `canAccess` in `kdiab-common` keeps it consistent across all services.
- Recommendation: no change.

#### FIND-SEC-004 — Dose calculator likely qualifies as SaMD under EU MDR (regulatory flag)
- Severity: High · Effort: L · Confidence: Medium · Phase: Mid · Area: security
- Evidence: `kdiab-calc/.../application/service/DoseCalculationService.kt#calculateDose` (recommends insulin doses); `kdiab-calc/CLAUDE.md` (disclaimer "recommended dose, not a prescription")
- Patient-safety impact: n/a to code correctness, but high regulatory exposure. **Flag, not certification (FR-2.2).**
- Finding: software that recommends an insulin dose is clinical decision support that drives a high-risk therapy and very likely meets the EU MDR definition of **Software as a Medical Device** (potentially Class IIa/IIb). The "recommendation, not prescription" disclaimer is a mitigation but does not by itself remove MDR scope. This must be assessed before any use beyond the maintainer's personal/self-hosted use.
- Recommendation: obtain a regulatory determination of whether `kdiab-calc` (and the metric displays) are SaMD and, if so, the MDR class and conformity route. Do **not** treat this review as that determination.
- Incremental alternative: in the interim, make the personal-use / not-a-medical-device boundary explicit in-product and in docs, so the current use is defensible while the determination is pending.

#### FIND-SEC-005 — GDPR erasure (Art 17) vs MDR 7-year no-purge retention must be reconciled (flag)
- Severity: Medium · Effort: M · Confidence: High · Phase: Mid · Area: security
- Evidence: `kdiab-measures/CLAUDE.md` & `kdiab-treatments/CLAUDE.md` ("EU MDR: never purge rows"); `kdiab-common/.../domain/model/AuditLog.kt`
- Patient-safety impact: n/a (compliance).
- Finding: the platform never purges measurement/treatment rows (MDR retention), which is in direct tension with a GDPR erasure request for special-category data. The retention obligation can lawfully override erasure, but only with a documented lawful basis and a defined process. That reconciliation is not documented.
- Recommendation: document the lawful basis for retention over erasure (MDR obligation), the retention period, and an erasure-request handling process (e.g. pseudonymize vs retain). **Flag only — not a certification.**
- Incremental alternative: n/a (a policy/doc deliverable).

#### FIND-SEC-006 — GDPR special-category safeguards to verify (flag)
- Severity: Medium · Effort: M · Confidence: Medium · Phase: Mid · Area: security
- Evidence: `kdiab-common/.../plugins/SecurityHeaders.kt` (TLS/HSTS present); `Security.kt` challenge log records `remote=<host>` (an IP = personal data); no DB-encryption-at-rest evidence in code (infra concern)
- Patient-safety impact: n/a (privacy).
- Finding: three special-category safeguards need confirmation: (1) encryption at rest for the PostgreSQL health data (not visible in the reviewed code — an infra setting); (2) a lawful basis / DPA for processing Art-9 data; (3) IP logging on token rejection is security-legitimate but is personal data and should have a retention limit. Positives already in place: PII (name/email) lives in Keycloak, not the health stores (data minimization); an `AuditLog` supports accountability.
- Recommendation: confirm DB encryption at rest, record the Art-9 lawful basis, and bound security-log IP retention. **Flag only.**
- Incremental alternative: encryption at rest (Postgres/volume-level) is the highest-value first confirmation.

#### FIND-SEC-007 — Security headers are good; minor CSP hardening (verdict + low)
- Severity: Low · Effort: S · Confidence: High · Phase: Mid · Area: security · Patient-safety impact: n/a
- Evidence: `kdiab-common/.../plugins/SecurityHeaders.kt` (CSP `default-src 'self'`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, conditional HSTS)
- Finding: a solid baseline. Minor hardening: add `frame-ancestors 'none'`, `base-uri 'self'`, and `form-action 'self'` to the CSP for defense-in-depth on modern browsers.
- Recommendation: extend the CSP as above; no structural change.

## Cross-reference (FR-D.5, no re-filing)

`docs/security/accepted-risks.md` **AR-001** (libxml2 in the `nginx:alpine` UI image, accepted as
unreachable) is already tracked with a justified acceptance and a review date — cross-referenced here,
**not** re-filed. The existence of that process is itself a positive signal.

## Section coverage (FR-2.1 / FR-2.2)

- **FR-2.1** GDPR special-category (SEC-005, SEC-006), auth hardening (SEC-001, SEC-002, SEC-003, SEC-007),
  MDR/SaMD (SEC-004). ✓
- **FR-2.2** all regulatory items (SEC-004 MDR/SaMD, SEC-005 erasure/retention, SEC-006 Art-9 safeguards)
  are **flagged, not certified**. ✓
