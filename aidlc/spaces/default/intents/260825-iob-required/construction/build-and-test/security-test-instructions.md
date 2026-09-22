# Security Test Instructions — #1563 activeIob required (DevSecOps view)

Consumes `../iob-required/code-generation/code-summary.md`,
`../iob-required/code-generation/code-generation-plan.md`.

## Security posture of the change

This is a **safety-hardening** change: it removes a silent-default failure mode on the single most
safety-critical dose input. It reduces risk rather than adding attack surface.

- **Input validation (A03).** `activeIob` is now validated at the boundary (`CalcMapper.toDomain`):
  omitted/null/negative → `BusinessValidationException` → 400. No value bypasses validation; there is no
  string interpolation or injection surface (numeric field).
- **Fail-closed.** Previously an omitted IOB silently became `0.0` (fail-dangerous). Now it is a hard
  400 (fail-closed) — including the case where the UI's `calcIOB` yields `NaN` (serialized as `null`),
  which is now rejected rather than dosed against a phantom zero.
- **No authn/authz change.** The route's existing `authenticate("auth-jwt")` + `checkReadAccess` are
  untouched; the guard runs after access control, so 401/403 still precede any body validation.
- **No secrets / PII.** No new logging; the IOB value (a clinical measure) is not logged. The
  secret-scan hook flagged only **pre-existing** test HMAC secrets (`jwt.test=true` fixtures) — false
  positives, not introduced here.

## Checks

```bash
cd kdiab-calc && ./gradlew detektMain   # security-relevant lint (passed)
```

CI additionally runs Semgrep / CodeQL / Trivy on the branch; no new HIGH/CRITICAL is expected from a
numeric input-validation guard. The full CI gate must be green before merge.
