# Code Summary — U4 security-review

**Unit:** U4 · **Bolt:** B7 · **Status:** complete. **Deliverable:** `docs/review/security.md`.

## Findings produced (7)
| ID | Severity | Dimension | Summary |
|---|---|---|---|
| FIND-SEC-001 | High | Auth | Test-mode HMAC JWT toggle has no production guard → platform-wide forge risk |
| FIND-SEC-002 | Medium | Auth | Doctor access is JWT-embedded → revocation lag = token lifetime |
| FIND-SEC-003 | (verdict) | Auth | ABAC canAccess / JWKS / audience core is sound |
| FIND-SEC-004 | High | MDR/SaMD | Dose calc likely SaMD under MDR — flag classification, don't certify |
| FIND-SEC-005 | Medium | GDPR | Erasure (Art 17) vs MDR 7-yr no-purge retention must be reconciled |
| FIND-SEC-006 | Medium | GDPR | Verify encryption-at-rest / lawful basis / IP-log retention for Art-9 data |
| FIND-SEC-007 | Low | Headers | Good baseline; add frame-ancestors/base-uri/form-action to CSP |

## Key decisions
- **FR-2.2 respected:** every regulatory item (MDR/SaMD, erasure, Art-9 safeguards) is explicitly
  *flagged, not certified* — the review is not a conformity assessment.
- **AR-001 cross-referenced, not re-filed** (FR-D.5, project "reuse issues" rule) — and the accepted-risks
  process itself credited as a positive.
- **Highest security finding (SEC-001)** is a small-effort fix; highest regulatory (SEC-004 SaMD) is the
  strategic flag. Both capped at High (non-safety severity rule, ADR-RVW-004).
- **Positives recorded:** clean ABAC core (SEC-003), PII/health-data separation via Keycloak, audit log.

## Test coverage summary
No tests (recommendations-only).

## Deviations from plan
None.
