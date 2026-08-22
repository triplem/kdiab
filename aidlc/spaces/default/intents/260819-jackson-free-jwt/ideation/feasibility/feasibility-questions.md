# Feasibility — Clarifying Questions (lean; technical uncertainty already resolved by evidence)

**Intent:** #1606 — jackson-free JWT verification. Refs #1603.
**Upstream:** `../intent-capture/intent-statement.md`, `../market-research/build-vs-buy.md`, `../market-research/competitive-analysis.md`, `../market-research/market-trends.md`.

> The main technical uncertainty is already resolved by dependency-graph evidence (jackson enters
> only via `ktor-server-auth-jwt`; DoD achievable by dropping it). These questions cover the
> remaining human/decision inputs. `X. Other` is always the final option.

---

## Q1 — Build-vs-buy: the evidence is now in. Lock it, or still defer to the ADR?

Evidence: **nimbus** adds `json-smart` (+ `accessors-smart` → `asm`) = ~3 new jackson-free transitive deps, mature audited crypto, least owned code. **Custom** adds **0** deps (kotlinx.serialization + JDK crypto), but we own all verification logic (highest security-review burden). Both drop `ktor-server-auth-jwt` and need a custom Ktor `AuthenticationProvider`.

- A. **Lock nimbus-jose-jwt now** — the audited-crypto / low-review-burden trade wins for a safety-sensitive auth path; the 3 added deps are acceptable (all jackson-free)
- B. **Lock custom now** — zero-new-deps wins; accept the higher review burden and own the crypto
- C. **Still defer to the Application Design ADR (2.6)** — record both with the evidence; decide there
- X. Other (please specify)

[Answer]: A — **lock nimbus-jose-jwt now**. Audited crypto + low review burden wins for a safety-sensitive auth path; the ~3 added deps are all jackson-free so the DoD holds. The ADR (2.6) documents this decision rather than re-opening it. **Mode:** guided (2026-08-19)

---

## Q2 — Compliance / regulatory constraints on the auth mechanism? (compliance-agent perspective)

kdiab is a T1D self-management platform (not a certified medical device), auth is Keycloak/OIDC, no card data (no PCI). The change is behaviour-preserving.

- A. **None beyond the security review already mandated** — no new regulatory control applies; the existing auth-event logging (`security_event=TOKEN_REJECTED`) and "no secrets/PII in logs" rule are the relevant controls and must be preserved
- B. There **is** a specific constraint I should register (e.g. a medical-data / audit obligation) — I'll specify under Other
- X. Other (please specify)

[Answer]: A — none beyond the mandated security review. Preserve auth-event logging (`TOKEN_REJECTED`) and the no-secrets/PII-in-logs rule. **Mode:** guided (2026-08-19)

---

## Q3 — Any organizational blocker, change-freeze, or timeline constraint to register in the RAID log?

- A. **None** — proceed; no change-freeze, no hard deadline (this closes epic #1603 at the team's own pace)
- B. There is a constraint (deadline / freeze / dependency on another PR) — I'll specify under Other
- X. Other (please specify)

[Answer]: A — none; proceed at the team's own pace. **Mode:** guided (2026-08-19)
