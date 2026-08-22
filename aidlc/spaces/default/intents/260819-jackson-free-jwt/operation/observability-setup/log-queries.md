# Log Queries — U1 Jackson-free JWT (#1606)

Consumes (all N/A — stages 3.3/3.4 skipped): `nfr-design/security-design.md`,
`nfr-design/reliability-design.md`, `nfr-design/performance-design.md`,
`infrastructure-design/monitoring-design.md`, `infrastructure-design/infrastructure-services.md`.
Sourced instead from `Security.kt` + ADR-023b and the platform Loki stack (`config/otel/loki`).

The auth log is #1606's primary observable delta. This is the star artifact of the stage.

## The signal

Emitted by `kdiab-common/plugins/Security.kt` on every rejected request (structured, one line):

```
security_event=TOKEN_REJECTED reason=<wire> path=<path> method=<method> remote=<clientIp> correlationId=<callId>
```

`reason` wire values (the taxonomy ADR-023b introduced): `no-token`, `malformed`, `bad-signature`,
`expired`, `wrong-audience`, `wrong-issuer`, `invalid-claims`. The Logback `JsonEncoder` wraps this as
`formattedMessage`, with `Correlation-ID` under `mdc` (per `.claude/rules/logging.md`).

## LogQL queries (Loki / Grafana)

> `{service}` label is set per backend; adjust to your Loki label scheme. These run today against the
> local compose OTEL stack and against any future running environment unchanged.

```logql
# 1. All auth rejections, all services
{job=~"kdiab-.*"} |= "security_event=TOKEN_REJECTED"

# 2. Rejection rate by reason (the key #1606 breakdown)
sum by (reason) (
  count_over_time({job=~"kdiab-.*"} |= "TOKEN_REJECTED"
    | regexp `reason=(?P<reason>[a-z-]+)` [5m])
)

# 3. SECURITY-CRITICAL: bad-signature spike (possible token forgery / wrong key)
{job=~"kdiab-.*"} |= "TOKEN_REJECTED" | regexp `reason=(?P<reason>[a-z-]+)` | reason="bad-signature"

# 4. PARITY-CRITICAL: invalid-claims (the exception-guarded claim path — a spike vs. the java-jwt
#    baseline would indicate a parity regression, not normal churn)
{job=~"kdiab-.*"} |= "TOKEN_REJECTED" | regexp `reason=(?P<reason>[a-z-]+)` | reason="invalid-claims"

# 5. CONFIG drift: wrong-audience / wrong-issuer (misrouted token or realm/audience misconfig)
{job=~"kdiab-.*"} |= "TOKEN_REJECTED" | regexp `reason=(?P<reason>[a-z-]+)` | reason=~"wrong-audience|wrong-issuer"

# 6. Trace one rejection end-to-end by correlation id
{job=~"kdiab-.*"} | json | mdc_Correlation_ID="<id>"
```

## Interpreting the reasons (runbook context)

| reason | Normal? | Investigate when |
|---|---|---|
| `expired` | Yes — routine token churn | Sudden fleet-wide spike (clock skew, Keycloak outage) |
| `no-token` | Yes — unauthenticated probes | Spike on an authed endpoint (client regression) |
| `malformed` | Occasional | Spike (a client sending garbage / truncated tokens) |
| `bad-signature` | **Rare** | ANY sustained rate → possible forgery or JWKS/key rotation issue |
| `wrong-audience` / `wrong-issuer` | Rare | Spike → audience-mapper or realm misconfig (or a cross-service token misuse) |
| `invalid-claims` | **Rare** | Spike vs. pre-#1606 baseline → **parity regression** in the Nimbus claim mapping → consider rollback |

## Baseline note (#1606-specific)

Because #1606 swaps the verification library, `invalid-claims` and `bad-signature` rates are the
**parity canaries**: they must match the `com.auth0:java-jwt` baseline. There is no running prod to
measure today (design-ready); the CI negative-path matrix (`build-and-test`) is the standing proof of
parity until a running environment can supply the live baseline.
