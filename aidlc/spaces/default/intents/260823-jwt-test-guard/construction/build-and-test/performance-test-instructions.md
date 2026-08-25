# Performance Test Instructions — jwt-test-guard (#1588)

## Applicability: none required

Per `nfr-requirements/.../performance-requirements.md` (PR-1/PR-2), the guard is a **startup-time**
configuration check inside `readJwtConfig()`. It:
- adds **0 ms** to the per-request JWT verification hot path (verifier selection and Nimbus
  verification are untouched), and
- adds a negligible boolean config read (< 1 ms) to process boot.

There is no throughput, latency, or resource-utilisation dimension to benchmark for this change, so no
performance test is authored. This is consistent with FIND-DEBT-009 (the platform has no perf/load
tier yet) — that gap is tracked separately (#1584) and is out of scope here.

## If a smoke check is desired
Confirm a correctly-configured service still boots within its normal startup budget:
```bash
cd kdiab-common && ./gradlew test --tests "org.javafreedom.kdiab.*SecurityConfigTest*"  # startup path exercised
```
Steady-state auth performance is unchanged from the reverse-engineering `technology-stack.md` baseline.
