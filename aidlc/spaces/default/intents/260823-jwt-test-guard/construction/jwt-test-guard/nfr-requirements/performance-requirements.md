# Performance Requirements — Guard test-mode JWT out of production

> Scope: security-patch. The change is a **startup-time configuration check** inside
> `readJwtConfig()`; it runs once per process during `configureSecurity()` installation.

## Requirements

| ID | Requirement | Target |
|---|---|---|
| **PR-1** | The guard adds no per-request overhead. It executes only at application startup, not on the token-verification hot path. | 0 ms added to request-time JWT verification. |
| **PR-2** | Startup cost is a boolean/config read and a comparison — negligible. | < 1 ms added to service boot. |

## Rationale

Token verification (`HmacTokenVerifier` / `JwksTokenVerifier`) is untouched, so steady-state auth
throughput and latency are unchanged from the values captured in the reverse-engineering
`technology-stack.md` / `architecture.md`. No benchmarking is required for this change.
