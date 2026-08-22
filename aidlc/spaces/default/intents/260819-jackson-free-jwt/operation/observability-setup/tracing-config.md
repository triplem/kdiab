# Tracing Configuration — U1 Jackson-free JWT (#1606)

Consumes (all N/A — stages 3.3/3.4 skipped): `nfr-design/performance-design.md`,
`nfr-design/reliability-design.md`, `nfr-design/security-design.md`,
`infrastructure-design/monitoring-design.md`, `infrastructure-design/infrastructure-services.md`.
Sourced from the platform substrate (`Tracing.kt`, OTEL collector, Jaeger).

## Existing tracing (unchanged by #1606)

The platform already traces: `kdiab-common` `Tracing.kt` propagates OpenTelemetry spans; every backend
exports OTLP gRPC to the collector (`config/otel/otel-collector-config.yaml`); Jaeger UI at host port
16690. Correlation is via `X-Correlation-ID` → MDC (`Logging.kt`) and OTEL trace context. **#1606
adds no new service, span, or exporter** — the auth verification happens inside the existing request
span of each service.

## What #1606 changes in a trace

- The verification step (Nimbus `JWKSource` fetch / `MACVerifier`) executes within the inbound request
  span, same position the old `java-jwt` provider occupied. No new span is required.
- On rejection, the `correlationId` in the `TOKEN_REJECTED` log line (`log-queries.md`) ties the log
  to the trace — so a rejected request is traceable end-to-end (log → correlation id → trace) exactly
  as before. This log↔trace correlation is the recommendation "lifecycle" for an auth event.
- **Do not** add token contents, `sub`, or claim values as span attributes (PII / secret hygiene —
  `.claude/rules/logging.md`). Only the non-sensitive `reason` and `path` belong in telemetry.

## Optional enrichment (design-ready, not required)

If deeper auth visibility is later wanted on a running environment, add a span event (not a new span)
`auth.reject` with attribute `reason=<wire>` on the request span — cheap, PII-free, and it lets Jaeger
filter traces by rejection reason. Deferred; the log-based `reason` breakdown (`log-queries.md`)
already covers the need without touching the hot path.

## Status

**No change required.** Tracing works as-is; #1606 is fully observable through the existing spans plus
the correlation-id-linked auth log. The optional span event is a forward hook only.
