# Performance Validation — Clarifying Questions (#1606 jackson-free JWT)

Operation phase · Standard depth · lead aidlc-quality-agent.

## No new questions — rationale

- **Scope of the perf question is self-evident**: a JWT-library swap can only affect **auth-path
  latency**; nothing else changes. The acceptance criterion for a swap is **no measurable regression
  vs. the `com.auth0:java-jwt` baseline** — there is no separate performance NFR (nfr-requirements 3.2
  was skipped), so no target to negotiate.
- **No running prod** (established 4.1–4.3): no live load test can be executed; results are recorded as
  N/A-live with a design-ready plan + CI-observed evidence.
- Consumed `performance-requirements`, `scalability-requirements`, `performance-design`,
  `scalability-design` (from 3.2/3.3) are **N/A — skipped**; the analysis is sourced from `Security.kt`
  + ADR-023.

_If you want the load test actually executed against a running instance (bring an environment into
scope), say so at the gate._
