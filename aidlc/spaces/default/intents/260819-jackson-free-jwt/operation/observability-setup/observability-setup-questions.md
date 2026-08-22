# Observability Setup — Clarifying Questions (#1606 jackson-free JWT)

Operation phase · Standard depth · lead aidlc-operations-agent.

## No new questions — rationale

Per `stage-protocol.md` §3, Operation questions are exceptional. Everything this stage needs is
already fixed:

- **Target**: established across 4.1–4.3 — there is **no running production environment**; the OTEL
  stack (`docker-compose.otel.yml`) is local/dev tooling. So the deliverables are **design-ready**
  observability definitions (queries, thresholds, dashboard specs) that light up wherever the stack
  runs, not artifacts provisioned against a live prod.
- **Substrate**: known from code — `kdiab-common` `Metrics.kt` (Micrometer/Prometheus), `Tracing.kt`
  (OTEL), `Logging.kt` (correlation-id MDC), `Health.kt` (`/healthz`,`/readyz`), Loki/Grafana/Jaeger.
- **#1606 signal**: known exactly from `Security.kt` + ADR-023b — the enriched
  `security_event=TOKEN_REJECTED reason=<wire> path=… method=… remote=… correlationId=…` line and the
  rejection-reason taxonomy (`no-token, malformed, bad-signature, expired, wrong-audience,
  wrong-issuer, invalid-claims`).

The upstream design hand-offs this stage nominally consumes (`performance-design`, `security-design`,
`reliability-design`, `monitoring-design`, `infrastructure-services`) come from stages 3.3/3.4 which
were **skipped** — so the design is sourced from the platform substrate + the auth change instead.

_If you want a specific dashboard/alert wired into a running Grafana/Prometheus now (i.e. bring a
running environment into scope), say so at the gate._
