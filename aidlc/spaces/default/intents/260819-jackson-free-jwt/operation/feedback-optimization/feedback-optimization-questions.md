# Feedback & Optimization — Clarifying Questions (#1606 jackson-free JWT)

Operation phase · Standard depth · lead aidlc-operations-agent. Final stage.

## No new questions — rationale

The feedback items and the optimization story are already identified in prior artifacts:

- **Optimization** = the change itself (jackson/java-jwt/jwks-rsa removed → smaller image + reduced CVE
  surface), quantified from `../deployment-execution/deployment-log.md` + the epic #1603 premise.
- **SLO** = the auth-correctness SLIs from `../observability-setup/slo-config.md`; no live telemetry
  (no running prod), so CI parity is the proxy.
- **Drift** = supply-chain re-creep, per the AC-1/AC-8 guard in `quality-gates.md`.
- **Feedback to Ideation** = the DRY test-fixture follow-up (rejected in ADR-023 as out-of-scope) and
  `#1615` (require `exp` presence — already an open issue), both pre-identified.

Nothing requires a new decision. _If you want a specific follow-up issue actually filed now, say so at
the gate._
