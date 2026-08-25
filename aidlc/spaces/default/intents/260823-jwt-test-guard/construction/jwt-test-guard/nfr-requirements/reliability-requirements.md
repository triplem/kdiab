# Reliability Requirements — Guard test-mode JWT out of production

> Scope: security-patch. Reliability here means **safe, predictable startup behaviour**.

## Requirements

| ID | Requirement | Target |
|---|---|---|
| **RL-1** | Fail-fast is a deliberate reliability trade-off: a misconfigured service (test-mode in prod) MUST refuse to start rather than run insecurely. A crash-on-boot is preferable to a silently-insecure running service. | Deterministic startup failure on misconfig. |
| **RL-2** | The failure MUST be observable: the thrown `IllegalStateException` message is logged by the standard Ktor startup path (structured Logback JSON), so orchestrators (Docker/K8s) surface it in the crash loop. | Message visible in container logs. |
| **RL-3** | No false positives: correctly-configured production (`jwt.test` unset) and correctly-configured test (`jwt.test=true` + `jwt.allowTestMode=true` + secret) both start reliably. **NB:** existing test suites go green **only after** the ~36 test-enablement sites affirm the opt-in (security-requirements.md § Implementation surface); production paths need no change. | Prod: unchanged. Test: suites green after opt-in affirmation added to all sites. |
| **RL-4** | Idempotent and side-effect-free: the guard performs no I/O and no mutation; re-running startup yields the same verdict for the same config. | Deterministic. |

## Rationale

This mirrors the existing `check(!isTest || secret != null)` and JWKS-HTTPS guards already in
`readJwtConfig()` — the service already treats security-config errors as boot-blocking. RL-1 extends that
established posture to the production/test-mode dimension. No availability SLO changes: the guard only
affects a deployment that is *already* misconfigured.
