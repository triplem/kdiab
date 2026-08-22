# Drift Report — U1 Jackson-free JWT (#1606)

Consumes `../observability-setup/alarms.md`, `../observability-setup/dashboards.md`,
`../observability-setup/slo-config.md`, `../deployment-execution/deployment-log.md`,
`../performance-validation/load-test-results.md`, `../incident-response/incident-plan.md`.

The drift that matters for #1606 is **supply-chain drift** — jackson (or java-jwt/jwks-rsa) creeping
back onto the runtime classpath and silently undoing the change. Plus behavioural (reason-taxonomy)
drift once a running env exists.

## Supply-chain drift (the primary watch)

| Drift | Detection | Response |
|---|---|---|
| jackson-databind/core reappears on `runtimeClasspath` | `./gradlew :<mod>:dependencyInsight --dependency jackson-databind --configuration runtimeClasspath` (the AC-1/AC-8 check) — ideally a CI guard | Investigate the new consumer; re-shed or pin jackson-free |
| jackson **downgrades** to the CVE-vulnerable 2.21.3 | same insight — assert resolved version | Restore a safe pin; never accept 2.21.3 |
| java-jwt / jwks-rsa reappears | dependencyInsight on those modules | Trace the transitive; remove |
| handlebars pin drops (unrelated CVE-2026-55760) | grep the `kotlin-base` constraints block | Keep the handlebars pin — it is deliberately retained (ADR-023c) |

**Recommendation (design-ready)**: add a lightweight CI guard (or a scheduled job) that runs the
`dependencyInsight` assertions and fails if jackson/java-jwt reappears — the standing anti-drift
control, parallel to how observability-setup's rules are design-ready.

## Behavioural drift (when a running env exists)

The `alarms.md` reason-taxonomy rules (`InvalidClaimsSpike`, `BadSignatureSpike`) are the behavioural
anti-drift signal: a divergence of the live reason distribution from the java-jwt baseline is drift in
the verification behaviour. Deferred with the rest of the live observability.

## Current drift status

**No drift.** As of this stage: jackson/java-jwt/jwks-rsa absent from all runtime classpaths (verified,
`quality-gates.md` AC-1/AC-8); handlebars pin retained; reason taxonomy as designed. The change has not
regressed since verification.
