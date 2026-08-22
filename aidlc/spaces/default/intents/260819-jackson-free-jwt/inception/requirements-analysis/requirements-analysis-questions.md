# Requirements Analysis — Clarifying Questions (lean)

**Intent:** #1606 — jackson-free JWT verification. Refs #1603.
**Upstream:** `../../ideation/intent-capture/intent-statement.md`, `../../ideation/scope-definition/scope-document.md`, `../../../codekb/kdiab-bkp/architecture.md`, `../../../codekb/kdiab-bkp/business-overview.md`, `../../../codekb/kdiab-bkp/code-structure.md`, `../practices-discovery/team-practices.md`.

> Most requirements derive directly from the approved intent + scope. Two genuine requirements-level
> decisions remain. `X. Other` is always the final option.

---

## Q1 — Is a performance/latency parity NFR required, or is functional behaviour-parity sufficient?

The current path caches JWKS (size/TTL) + rate-limits + `acceptLeeway`. Nimbus `RemoteJWKSet` caches similarly.

- A. **Functional parity is sufficient** — require identical accept/reject outcomes + preserved caching config; no explicit latency budget (recommended — behaviour-preserving change, no perf regression expected)
- B. **Add a latency-parity NFR** — require per-request auth overhead within ~X% of current (specify budget)
- C. **Add a startup NFR** — require no meaningful change to service startup time / JWKS warm-up
- X. Other (please specify)

[Answer]: A — **functional parity sufficient**; no explicit latency budget. Preserve JWKS caching/rate-limit/leeway config; no perf regression expected. **Mode:** guided (2026-08-19)

---

## Q2 — How strict should the negative-path test matrix be? (drives acceptance criteria + test volume)

- A. **Full matrix (recommended)** — valid, expired, not-yet-valid (nbf), wrong-audience, wrong-issuer, bad signature, missing/blank roles, malformed-UUID subject, missing Authorization header, malformed Bearer, HMAC test-mode valid+invalid — each asserted identical to today
- B. **Core matrix** — valid, expired, wrong-audience, missing-roles, bad-signature, HMAC test-mode only
- C. Let the test-design stage (Build & Test 3.6) decide the matrix depth
- X. Other (please specify)

[Answer]: A — **full negative-path matrix**: valid, expired, nbf, wrong-audience, wrong-issuer, bad-signature, missing/blank roles, malformed-UUID subject, missing Authorization header, malformed Bearer, HMAC test-mode valid+invalid — each asserted identical to today. **Mode:** guided (2026-08-19)
