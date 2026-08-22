# Dashboards — U1 Jackson-free JWT (#1606)

Consumes (all N/A — stages 3.3/3.4 skipped): `nfr-design/performance-design.md`,
`nfr-design/security-design.md`, `nfr-design/reliability-design.md`,
`infrastructure-design/monitoring-design.md`, `infrastructure-design/infrastructure-services.md`.
Sourced from the platform substrate (`Metrics.kt`, Loki/Grafana, Jaeger) + the #1606 auth signal.

Design-ready Grafana panel specs (no running prod to provision against — deployment-pipeline Q1).
They render wherever the OTEL/Grafana stack runs (local compose today; a prod later).

## "JWT Verification (#1606)" dashboard — new panels

| Panel | Source | Query / metric | Why (for #1606) |
|---|---|---|---|
| Auth rejection rate by reason | Loki | `log-queries.md` Q2 (rate by `reason`) | The core #1606 view — reason taxonomy is the new signal |
| `invalid-claims` rate (parity canary) | Loki | `log-queries.md` Q4 | Divergence from the java-jwt baseline = parity regression |
| `bad-signature` rate (security canary) | Loki | `log-queries.md` Q3 | Any sustained rate = forgery / key issue |
| 401 responses by service | Prometheus (Micrometer http metric, `status="401"`) | `sum by (service)(rate(http_server_requests_seconds_count{status="401"}[5m]))` | Fleet view of rejection volume |
| Auth-path latency (p50/p95/p99) | Prometheus | `histogram_quantile` on the authed-route timer | Confirm Nimbus verification adds no latency regression |
| Verification errors (5xx on auth path) | Prometheus | `rate(http_server_requests_seconds_count{status=~"5.."}[5m])` on authed routes | A 5xx (not 401) on the auth path = provider bug (must be 0) |

## Reused platform panels (unchanged by #1606)

Service health (`/healthz`,`/readyz`), request throughput, JVM/GC, and the existing Grafana/Loki
dashboards continue to apply — #1606 changes none of them. The dashboard above is additive.

## Provisioning status

**Design-ready, not provisioned.** No running Grafana/prod exists to install these into as a
standing dashboard. They are ready to import (`config/otel/grafana/provisioning/…`) the moment a
running environment exists — this is the observability-setup forward hook, parallel to
`deployment-pipeline` `cd-config.md`.
