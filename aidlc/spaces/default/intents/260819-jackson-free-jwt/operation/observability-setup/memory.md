# Observability Setup — Stage Diary

Stage: observability-setup (4.4) · Phase: Operation · Intent: 260819-jackson-free-jwt (#1606)
Lead: aidlc-operations-agent

## Interpretations
- 2026-08-21T16:47Z — All 5 consumed artifacts (nfr-design/{performance,security,reliability}-design, infrastructure-design/{monitoring-design,infrastructure-services}) come from stages 3.3+3.4 which were SKIPPED. No hand-off exists. Sourcing observability design from the real platform substrate + the #1606 auth change instead.
- 2026-08-21T16:47Z — #1606's observability DELTA is narrow and specific: the enriched `TOKEN_REJECTED` log line (ADR-023b added `reason=<wire>` + proxy-aware `remote=`) and the rejection-reason taxonomy. Everything else observable (http metrics, traces, health) is unchanged platform behaviour. So the auth-signal is the spine of all 7 artifacts.
- 2026-08-21T16:47Z — Exact signal (Security.kt:222): `security_event=TOKEN_REJECTED reason=<wire> path=<path> method=<method> remote=<clientIp> correlationId=<callId>`. reason wire values: no-token, malformed, bad-signature, expired, wrong-audience, wrong-issuer, invalid-claims.

## Platform substrate (real, unchanged by #1606)
- kdiab-common plugins: Metrics.kt (Micrometer + Prometheus /metrics), Tracing.kt (OTEL span propagation), Logging.kt (X-Correlation-ID → MDC), Health.kt (/healthz, /readyz).
- Stack: docker-compose.otel.yml → config/otel (collector, Loki, Grafana datasource); Jaeger UI (host 16690). Backends export OTLP gRPC. Logback JsonEncoder (mdc.Correlation-ID).
- NOTE: the OTEL stack is dev/local-compose tooling; there is no running prod (deployment-pipeline Q1). So dashboards/alarms/SLO/anomaly are DESIGN-READY definitions that light up wherever the stack runs — not provisioned against a live prod.

## Deviations
- 2026-08-21T16:47Z — Stage prose is AWS-shaped (CloudWatch dashboards/alarms, X-Ray, Evidently). Substituting the platform's real analogues: Grafana/Loki dashboards, Prometheus/Micrometer alarms, Jaeger/OTEL tracing, Loki LogQL queries. Anomaly = deterministic threshold rules on the reason taxonomy, not statistical ML baselines (no prod time-series to train on).
- 2026-08-21T16:47Z — No new clarifying questions (Operation, all operational params established at 4.1–4.3; no running prod re-confirmed). questions file documents the rationale.

## Tradeoffs
- 2026-08-21T16:47Z — SLO: could set aspirational availability SLOs, but with no running prod there's no error budget to burn. Chose to define auth-correctness SLIs (verification decision-correctness, 401-on-valid rate) as the meaningful contract, framed as "activate when a running env exists," with CI parity as today's proxy.

## Open questions
- 2026-08-21T16:47Z — None. Substrate + auth signal fully known from code + ADR-023.
