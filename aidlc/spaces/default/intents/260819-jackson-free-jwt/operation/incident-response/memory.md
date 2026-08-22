# Incident Response — Stage Diary

Stage: incident-response (4.5) · Phase: Operation · Intent: 260819-jackson-free-jwt (#1606)
Lead: aidlc-operations-agent

## Interpretations
- 2026-08-21T16:50Z — Top incident class for #1606 = the Nimbus provider REGRESSING auth on the live platform: (a) wrong-REJECT → fleet-wide lockout (patients can't reach glucose/insulin data = a T1D SAFETY incident), (b) wrong-ACCEPT → cross-platform security hole. Both are P0-class. This mirrors the project.md incident-response learning (implemented change regressing a safety-critical T1D platform = rollback-first + domain-review gate + close-the-loop) — applied here to a real feature change, not a review deliverable.
- 2026-08-21T16:50Z — Response doctrine = ROLLBACK-FIRST. Never forward-fix an auth path under pressure. Rollback = the source-level git-revert path in ../deployment-pipeline/rollback-runbook.md.
- 2026-08-21T16:50Z — Detection today is DISCOVERY-based (CI/review/manual) because alarms are design-ready (no running prod — observability-setup). When a running env exists, alarms.md rules (AuthProviderErrors/InvalidClaimsSpike/BadSignatureSpike/FleetWide401Surge) drive detection. The incident PLAN holds regardless.

## Deviations
- 2026-08-21T16:50Z — Stage prose is SSM-runbook/AWS-shaped + assumes on-call rotation. Substituting: git-native rollback runbook, platform P0-P4 severity labels, solo-maintainer escalation (no rotation). Consumed reliability-design/security-design/deployment-architecture (3.3/3.4) are N/A-skipped; sourcing from ADR-023 + alarms.md + rollback-runbook.md.

## Tradeoffs
- 2026-08-21T16:50Z — Whether to hard-mandate /doctor-t1d-review + /patient-t1d-review before re-attempting a reverted auth change: adds friction but the platform is safety-critical and the change touches patient data access. Leaning mandatory-for-P0 — asking the user.

## Open questions
- 2026-08-21T16:50Z — (1) P0 auth-incident escalation: solo maintainer, or designate an external security/clinical advisor for sign-off (ADR-023 recommends establishing one)? (2) Mandatory domain-review gate (/doctor-t1d-review + /patient-t1d-review) before re-attempting a reverted auth change? Asked in questions file.
