# Code-Generation Plan — U4 security-review

**Unit:** U4 · **Story:** US-4 · **Priority:** Should · **Bolt:** B7. **Deliverable:** `docs/review/security.md`.

## Steps
- [x] Step 1 — Read auth core: `kdiab-common/plugins/Security.kt` (JWKS/HMAC test mode, buildPrincipal,
  canAccess), `SecurityHeaders.kt`, `RouteUtils.kt` (checkRead/WriteAccess). *(→ FR-2.1 auth)*
- [x] Step 2 — Assess GDPR special-category (retention vs erasure, encryption, PII placement, IP logging)
  and MDR/SaMD posture (dose calc). *(→ FR-2.1 GDPR/MDR)*
- [x] Step 3 — Read `docs/security/accepted-risks.md` (A-4 prior art) → cross-reference AR-001, don't re-file. *(→ FR-D.5)*
- [x] Step 4 — Author `docs/review/security.md`; flag regulatory obligations, do NOT certify (FR-2.2). *(→ US-4, FR-2.2)*
- [x] Step 5 — Write plan + summary.

## Story-to-step traceability
| Step | Anchor |
|---|---|
| 1 | FR-2.1 auth hardening / ABAC |
| 2 | FR-2.1 GDPR special-category + MDR/SaMD |
| 3 | A-4 (prior art), FR-D.5 (no duplicate) |
| 4 | US-4, FR-2.2 (flag not certify), NFR-1 |

## Coverage (FR-2.1 / FR-2.2)
GDPR (SEC-005/006), auth (SEC-001/002/003/007), MDR/SaMD (SEC-004). All regulatory items flagged, not
certified (FR-2.2). AR-001 cross-referenced, not re-filed. ✓
