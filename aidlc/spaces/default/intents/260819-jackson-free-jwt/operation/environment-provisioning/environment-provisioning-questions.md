# Environment Provisioning — Clarifying Questions (#1606 jackson-free JWT)

Operation phase · Standard depth · lead aidlc-operations-agent (support: devsecops, compliance).

## No new questions — rationale

Per `stage-protocol.md` §3, Operation-phase questions are *exceptional, not routine* — asked only
where operational parameters were not established earlier. For #1606 every relevant parameter was
established at **Deployment Pipeline (4.1)** and re-confirmed here by a **live read-only sweep**:

| Would-be question (from stage prose) | Already answered |
|---|---|
| Are all environments provisioned per Infra Design? | Infra Design (3.4) was **skipped** — no cloud infra (AWS forbidden). The environment is the existing GitHub-native delivery surface; nothing to provision. (deployment-pipeline Q1: no running prod.) |
| Are VPCs / subnets / security groups / NACLs correct? | N/A — no cloud network. |
| Are secrets in Secrets Manager / Parameter Store injected? | N/A — #1606 introduces **no new secret or env var**. Prod uses RS256/JWKS where `JWT_SECRET` is unused; the HS256 ≥32-byte rule is **test-scope only** (ADR-023). Confirmed by sweep: no `jwt.test=true` and no `JWT_SECRET` in any deployment/compose config. |
| Is cross-account / cross-VPC connectivity validated? | N/A — no cloud accounts/VPCs. |
| Any Keycloak realm change for the auth swap? | **No** — `config/keycloak-realm.json` needs no change (ADR-023). The realm's RS256/JWKS path is unchanged; the only `secret` in the realm is a client secret, not JWT signing material. |

The stage therefore runs as **validation only** (no provisioning mutation, no outward-facing change),
so there is no interaction-mode prompt. See `validation-report.md` for the evidence and the
capability-split readiness verdict, and `environment-inventory.md` for the surface inventory.

_If you want a specific environment check added (e.g. a live `gh`/GHCR probe, or a staging host
brought into scope), say so at the approval gate and it will be added._
