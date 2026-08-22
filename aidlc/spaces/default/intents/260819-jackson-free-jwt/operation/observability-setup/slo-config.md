# SLO Configuration — U1 Jackson-free JWT (#1606)

Consumes (all N/A — stages 3.3/3.4 skipped): `nfr-design/reliability-design.md`,
`nfr-design/performance-design.md`, `nfr-design/security-design.md`,
`infrastructure-design/monitoring-design.md`, `infrastructure-design/infrastructure-services.md`.

For a library-swap with no running prod there is no live availability to budget. The meaningful SLIs
for #1606 are **auth-correctness** SLIs — the contract the change must not break — defined here and
activatable when a running environment exists. Today CI parity is the proxy.

## SLIs / SLOs (auth-correctness — the #1606 contract)

| SLI | Definition | SLO target | Measured by |
|---|---|---|---|
| Verification correctness | valid tokens accepted ∧ invalid tokens rejected, identical to java-jwt | 100% (no wrong-accept, no wrong-reject) | Negative-path test matrix (CI) today; live parity when a prod exists |
| Auth error rate | `5xx` on the auth path (should be 0 — rejects are `401`, not `500`) | ≤ 0.0% (any `5xx` is a defect) | `alarms.md` `AuthProviderErrors` |
| `invalid-claims` parity | live `invalid-claims` rate within tolerance of the java-jwt baseline | within baseline×3 | `log-queries.md` Q4 |
| Auth-path latency | p95 verification overhead vs. pre-#1606 | no regression (Nimbus JWKS is cached/rate-limited per ADR-023) | Prometheus timer |

## Error budget

With no running prod there is **no availability error budget to burn** — the change ships on
correctness proof, not on a reliability budget. When a running environment exists, the correctness SLI
(100%) has effectively a **zero error budget for wrong-accept** (a security hole is never acceptable)
and a small budget for wrong-reject before the `FleetWide401Surge`/`InvalidClaimsSpike` alarms fire
and trigger the rollback runbook.

## Activation status

**Defined, not active.** These SLIs activate against live telemetry once a running environment exists
(forward hook). Until then, the CI negative-path matrix + the AC-1/AC-8 supply-chain check
(`build-and-test`/`quality-gates.md`) are the standing evidence that the correctness SLO holds.
