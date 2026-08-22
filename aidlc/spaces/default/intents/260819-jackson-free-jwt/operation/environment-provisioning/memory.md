# Environment Provisioning — Stage Diary

Stage: environment-provisioning (4.2) · Phase: Operation · Intent: 260819-jackson-free-jwt (#1606)
Lead: aidlc-operations-agent · Support: aidlc-devsecops-agent, aidlc-compliance-agent

## Interpretations
- 2026-08-21T16:35Z — Stage prose is AWS-shaped (VPCs, subnets, security groups, Secrets Manager, IaC provisioning). AWS is forbidden (project.md) and — established at deployment-pipeline (4.1) — there is NO running production environment. Interpreting "environment provisioning" as VALIDATION of the existing GitHub-native delivery surface + confirmation that #1606 needs no config/secret provisioning, done as a LIVE READ-ONLY SWEEP (per the project.md environment-provisioning learning), not abstract prose or actual mutation.
- 2026-08-21T16:35Z — For a dependency-swap with no schema/config change, the "environment" = GitHub repo + Actions runners + GHCR registry + Keycloak realm + per-service application.conf. All pre-exist; #1606's delta to them is empty.

## Deviations
- 2026-08-21T16:35Z — No provisioning MUTATION performed (the review-intent precedent's only mutation was creating a label taxonomy, gated on authorization). #1606 is a feature intent with nothing outward-facing to provision — the stage is pure validation. No user-authorization gate needed because no mutation is proposed.
- 2026-08-21T16:35Z — Zero new clarifying questions. Per stage-protocol §3, Operation questions are exceptional and only where operational parameters weren't established earlier; they were all established at deployment-pipeline (4.1) and re-confirmed by the live sweep. questions file documents the no-questions rationale rather than asking rhetorical questions.

## Tradeoffs
- 2026-08-21T16:35Z — Could have hit the live GitHub/GHCR API (gh auth, gh api) to prove the delivery surface. Chose repo-local read-only evidence (workflow files exist + run history is the CI-pipeline stage's concern) to avoid network/auth prompts; the surface's existence is already proven by docker-publish.yml + the merged prior intents. Recorded gh-based deeper checks as optional in the validation report.

## Live sweep results (read-only, 2026-08-21)
- jwt.test=true: ABSENT from all docker-compose*.yml / config/ — production never enables the HS256/test path.
- JWT_SECRET: not set in any compose env; application.conf has `secret = ${?JWT_SECRET}` (optional, unused in prod — kdiab-profiles conf documents this explicitly).
- Prod JWT config = RS256/JWKS path (domain/issuer, jwksUrl, audience, realm) — exactly what ADR-023 says is unaffected. No realm/env change.
- Keycloak realm `secret` at line 699 is a CLIENT secret (users-service-secret), not JWT signing material — unrelated to #1606.
- Nimbus 10.0.1 present: libs.versions.toml (version + module) + testImplementation in kdiab.ktor-service convention plugin. Build env has what #1606 needs.
- swagger.enabled=false by default (consistent with #1607 swagger/jackson removal).
- CONCLUSION: #1606 requires ZERO environment/config provisioning. Readiness = GREEN across all capabilities.

## Open questions
- 2026-08-21T16:35Z — None. Every operational parameter established at deployment-pipeline (4.1) and confirmed by the live sweep.
