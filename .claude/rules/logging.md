# Rule: Logging Standards

These rules apply to all stacks. Stack-specific implementation is in the relevant logging skill.

## Mandatory Structured Fields

Every log entry in production must include:

| Field | Value | Source |
|---|---|---|
| `timestamp` | ISO 8601 UTC | Logger |
| `level` | trace/debug/info/warn/error | Logger |
| `service` | Service name | Env var / config |
| `traceId` | Request correlation ID | MDC / AsyncLocalStorage |
| `spanId` | Current span (if tracing) | MDC / OpenTelemetry |
| `message` | Human-readable event description | Code |
| `env` | production / staging / local | Env var |

Add contextual fields per event (e.g., `userId`, `orderId`, `durationMs`).

## Log Level Policy

| Level | When | Examples |
|---|---|---|
| `ERROR` | System cannot proceed; requires attention | DB connection failed, unhandled exception |
| `WARN` | Unexpected but recoverable; may indicate a problem | Retry triggered, deprecated API used, slow query |
| `INFO` | Normal significant events | User created, order placed, service started |
| `DEBUG` | Developer troubleshooting | Method entry/exit, intermediate values |
| `TRACE` | Very verbose; disabled in production | Every DB row processed, every HTTP header |

**Production default**: INFO.
**Local development**: DEBUG.
**Never enable TRACE in production** unless diagnosing a specific incident (enable temporarily with runtime config change, not a deploy).

## What to Log

### Always

- Service start / stop
- External dependency failures (DB down, HTTP 5xx from downstream)
- Auth events: login success, login failure, token refresh
- Business events that matter to the business: order placed, payment processed, user registered
- Start and end of long-running background jobs

### Never

- Passwords, secrets, tokens, API keys
- Full PII (name, email, phone, SSN, card number) — log IDs instead
- Full request/response bodies (except gated behind DEBUG + PII scrubbing)
- Binary data or base64-encoded blobs

## Correlation

Inject a `traceId` on every inbound request (read from `X-Trace-Id` header or generate). Propagate it:
- In HTTP calls to downstream services (`X-Trace-Id` header)
- In message headers to queues/topics
- In DB queries (via application-level metadata where supported)

Log the `traceId` on every log entry so a full request trace can be reconstructed from logs.

## Error Logging

When logging an error, always include the exception/stack trace:

```kotlin
logger.error(ex) { "Payment processing failed orderId=${order.id}" }
```

```typescript
logger.error({ err, orderId: order.id }, 'Payment processing failed');
```

```csharp
_logger.LogError(ex, "Payment processing failed {OrderId}", order.Id);
```

Log the exception **once** at the point where you have full context — do not re-log as it bubbles up.

## Performance Logging

For operations > 500ms:

```kotlin
val start = System.currentTimeMillis()
val result = expensiveOperation()
val durationMs = System.currentTimeMillis() - start
if (durationMs > 500) logger.warn { "Slow operation durationMs=$durationMs operationName=expensiveOperation" }
```

Add `durationMs` to all external calls (DB queries, HTTP calls, message sends).

## Log Aggregation

All services write JSON to stdout. Log aggregation is handled by the infrastructure layer (Loki, Elasticsearch, Datadog, CloudWatch). Agents do not configure log shipping — that is infrastructure.

## Sensitive Field Masking

Models that may appear in logs must mask sensitive fields in `toString()`:

```kotlin
data class PaymentRequest(val amount: BigDecimal, val cardNumber: String) {
    override fun toString() = "PaymentRequest(amount=$amount, cardNumber=****${cardNumber.takeLast(4)})"
}
```

Mark sensitive fields with `@JsonProperty(access = READ_ONLY)` or custom serialiser to prevent accidental serialisation.
