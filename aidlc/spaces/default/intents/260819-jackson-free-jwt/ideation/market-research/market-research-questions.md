# Market Research — Clarifying Questions (lean, build-vs-buy focus)

**Intent:** #1606 — jackson-free JWT verification. Refs #1603.
**Upstream:** derived from the intent statement (`../intent-capture/intent-statement.md`) — its DoD (jackson off `runtimeClasspath`) and its provisional nimbus lean frame the build-vs-buy question below.
**Note:** run lean per user choice — competitive/market-sizing parts are N/A for an internal refactor; the live question is **build-vs-buy** for the verification path.

> Fill in each `[Answer]:` tag. `X. Other` is always the final option.

---

## Q1 — Build-vs-buy conclusion: is "adopt nimbus-jose-jwt" firm, or should custom stay genuinely in play?

Context: intent-capture picked nimbus (Q2=A). But kdiab **already** ships `kotlinx.serialization` (used by `ContentNegotiation`), so a **custom** verifier could parse JWT/JWKS with it + JDK crypto (`java.security.Signature` RS256, `javax.crypto.Mac` HMAC) and add **zero** new runtime dependencies — fully jackson-free *and* json-smart-free. Nimbus instead adds `nimbus-jose-jwt` + `net.minidev:json-smart`.

- A. **Adopt nimbus-jose-jwt (firm)** — prefer mature, audited JOSE crypto; accept the `json-smart` transitive dep (still jackson-free, so DoD met). Document custom as the rejected alternative.
- B. **Keep both genuinely in play** — document a real build-vs-buy; let Feasibility / Application Design (ADR) make the final call on evidence (deps added, review burden, crypto risk)
- C. **Prefer custom (build)** — zero new deps using kotlinx.serialization + JDK crypto is the priority; document nimbus as the rejected alternative
- X. Other (please specify)

[Answer]: B — keep both genuinely in play; document a real build-vs-buy and let Feasibility / Application Design (ADR) make the final call on evidence. Refines intent-capture Q2 (nimbus was the initial lean; now build-vs-buy is open). **Mode:** guided (2026-08-19)

---

## Q2 — Which alternatives should I record in the competitive/library analysis? (select all that apply)

- A. **nimbus-jose-jwt** (Connect2id) — the reference JVM JOSE lib; RS256/JWKS via `RemoteJWKSet`, HMAC; jackson-free (json-smart)
- B. **Custom verifier** — JDK crypto + kotlinx.serialization; zero new deps
- C. **jjwt (io.jsonwebtoken)** with a non-jackson serializer module (`jjwt-gson` / `jjwt-orgjson`) — jackson-free but swaps in gson/orgjson
- D. **Ktor-native `jwks` + a jackson-free verifier** — keep as much of the ktor auth plumbing as possible
- X. Other (please specify)

[Answer]: A, B, D — record nimbus-jose-jwt, the custom verifier, and the ktor-native-jwks + jackson-free verifier options. **Not C** (jjwt — swapping in gson/orgjson isn't attractive vs the existing kotlinx.serialization). **Mode:** guided (2026-08-19)
