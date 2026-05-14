---
name: logging-kotlin
description: Structured logging with kotlin-logging and Logback for Kotlin/JVM services. Apply when writing or reviewing Kotlin logging code.
when_to_use: Apply when adding logging to Kotlin code, configuring Logback, or reviewing log statements.
user-invocable: false
paths: "**/*.kt,**/logback*.xml"
---

Apply these logging patterns in all Kotlin code. See `reference.md` for Logback config examples.

## Setup

```kotlin
// build.gradle.kts
implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")
implementation("net.logstash.logback:logstash-logback-encoder:8.0")

// In class
private val logger = KotlinLogging.logger {}
```

Never `LoggerFactory.getLogger(...)` directly.

## Structured fields — always

```kotlin
// WRONG — unstructured
logger.info { "User ${user.id} logged in" }

// RIGHT — structured key=value
logger.info { "user_login userId=${user.id.value} ip=$ip" }
```

## Levels

`ERROR` — system cannot proceed, always attach exception.
`WARN` — recoverable unexpected, may need attention.
`INFO` — normal business events (user created, payment processed).
`DEBUG` — off in production.

## Error logging

```kotlin
logger.error(ex) { "payment_failed orderId=${order.id}" }
```

Log exceptions **once** at the point you have full context. Do not re-log as it bubbles up.

## MDC correlation

Inject `traceId` from incoming request header. Log it on every entry. See `reference.md` for the filter implementation.

## Never log

Passwords · tokens · secrets · PII (email, name, phone) · full request bodies in production.

Override `toString()` on any class that may appear in logs and contains sensitive fields:
```kotlin
data class LoginRequest(val email: Email, val password: String) {
    override fun toString() = "LoginRequest(email=$email, password=***)"
}
```
