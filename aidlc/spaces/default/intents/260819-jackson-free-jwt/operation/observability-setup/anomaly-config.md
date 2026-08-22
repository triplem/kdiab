# Anomaly Detection Configuration — U1 Jackson-free JWT (#1606)

Consumes (all N/A — stages 3.3/3.4 skipped): `nfr-design/security-design.md`,
`nfr-design/reliability-design.md`, `nfr-design/performance-design.md`,
`infrastructure-design/monitoring-design.md`, `infrastructure-design/infrastructure-services.md`.

## Deterministic rules, not statistical baselines

There is no running-prod time-series to train an ML/statistical anomaly detector on (no running
prod — deployment-pipeline Q1). For #1606, "anomaly" = a **deterministic rule** over the rejection-reason
taxonomy that encodes what is abnormal for an auth-verification swap. These are the same conditions
`alarms.md` arms; this file records the detection intent + why each reason is (ab)normal.

## Anomaly rules

| Anomaly | Rule | Meaning |
|---|---|---|
| Provider-error anomaly | any `5xx` on an authed route (expected: only `401`) | Nimbus provider bug — a reject that should be `401` surfaced as `500`. Zero-tolerance. |
| Parity anomaly | `invalid-claims` rate steps above the java-jwt baseline (×3) | The exception-guarded claim mapping diverging → parity regression |
| Forgery anomaly | `bad-signature` sustained above a low floor | Token forgery attempt or JWKS/key mismatch |
| Config anomaly | `wrong-audience`/`wrong-issuer` step-change | Audience-mapper / realm / cross-service token misconfig |
| Correlated-lockout anomaly | 401 surge correlated across ≥ half the fleet | Fleet-wide wrong-reject (the auth blast radius) |

Explicitly **not** anomalies: steady `expired` and `no-token` rates (routine churn) — flagging them
would be noise.

## The #1606 baseline problem (and the honest answer)

A parity anomaly needs a baseline; the java-jwt baseline can only be measured on a running fleet, which
doesn't exist. So today the "anomaly detector" for parity is the **CI negative-path matrix** — it
proves the reason taxonomy is produced identically to java-jwt for every negative case before merge.
The live deterministic rules above activate when a running environment can supply the baseline
(forward hook), mirroring `dashboards.md`/`alarms.md`/`slo-config.md`.

## Status

**Design-ready, not active.** Deterministic rules defined; no statistical baseline claimed (a finite
library-swap has no prod time-series to model). CI parity stands in until a running env exists.
