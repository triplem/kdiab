# Phase Boundary Verification — Ideation → Inception (#1606)

Governance traceability check per `stage-protocol-governance.md`, run at the approval-handoff gate.
Sources: intent-statement, scope-document, intent-backlog, competitive-analysis, feasibility-assessment,
constraint-register. (`team-assessment` and `wireframes` were skipped — solo maintainer, no UI.)

## Intent → Scope → Backlog Consistency

| Intent element (intent-statement.md) | Scope coverage (scope-document.md) | Backlog coverage (intent-backlog.md) | OK? |
|---|---|---|---|
| DoD: jackson off `runtimeClasspath` | In-scope #1 (drop `ktor-server-auth-jwt`), #8 (remove force-pin) | PU-2, PU-5 | ✅ |
| Replace java-jwt + jwks-rsa | In-scope #1 (both are its transitives) | PU-2 | ✅ |
| Adopt Nimbus | In-scope #3, #4 | PU-2, PU-3 | ✅ |
| Preserve UserPrincipal/JWKS/error exactly | In-scope #4 | PU-3 (+ PU-1 verifies) | ✅ |
| Both signing paths (RS256+HMAC) | In-scope #4 | PU-3 | ✅ |
| One atomic PR across 8 backends | Sequencing & Delivery (one PR) | Backlog note (single Unit) | ✅ |
| Full auth e2e + security review + dep proof + check + CI | In-scope #5, #8 | PU-1, PU-5 | ✅ |
| Config/realm change allowed if needed | In-scope #6 | PU-4 (conditional) | ✅ |

## All Scope Items Have Feasibility Backing

| Scope item | Feasibility backing (feasibility-assessment.md / constraint-register.md) | OK? |
|---|---|---|
| Drop `ktor-server-auth-jwt` removes jackson | `dependencyInsight` proof: sole jackson consumer on common + measures | ✅ |
| Nimbus supports RS256/JWKS+HMAC with hardening | Assumption A-2 (Nimbus `DefaultJWTProcessor`/`RemoteJWKSet`/`MACVerifier`) | ✅ |
| Route wiring unchanged (`auth-jwt` name stable) | Constraint TC-6; RAID A-3 | ✅ |
| Force-pin removal gated on sweep | RAID R-5/R-6; constraint TC-8 | ✅ |
| Realm config in scope | RC-1/RC-4 (no new regulatory control; token format unchanged) | ✅ |

## Verdict

**PASS.** Every intent element traces forward to a scope item and a proto-Unit; every scope item has
feasibility backing (much of it hard `dependencyInsight` evidence rather than assumption). No orphan
scope items, no unresolved contradiction (the ktor-server-auth-jwt / DoD conflict was resolved at
scope-definition Q1a). Cleared to proceed to Inception.

## Carried-forward open items

- Exact `jwt.*`/realm config mapping under Nimbus → Application Design (2.6).
- Platform-wide jackson sweep (all 8 services) → Build & Test (3.6); gates force-pin removal.
