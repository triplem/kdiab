<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations
- 2026-08-25T00:00:00Z — per the project.md learned rule (cid:deployment-pipeline:no-running-prod-publish-only-pipeline), kdiab has NO continuously-running prod; the pipeline terminates at GHCR image publish. Modelled a PUBLISH-TO-REGISTRY pipeline (docker-publish.yml → ghcr.io, release.yml semantic-release, ci-common-publish.yml for the changed kdiab-common JAR), NOT a deploy-to-environment one. No canary/blue-green/metric-trigger machinery invented; documented publish-only reality + labelled forward hooks.

## Deviations
- 2026-08-25T00:00:00Z — consumes (ci-config.md, quality-gates.md from ci-pipeline; infrastructure-design artifacts) do not exist — those stages are skipped in security-patch scope. Grounded the CD model in the actual existing workflows (.github/workflows/docker-publish.yml, release.yml, ci-common-publish.yml) instead.

## Tradeoffs
- 2026-08-25T00:00:00Z — no user question raised: for a one-guard security fix riding the established publish-only pipeline there is no genuine deployment-design decision. The one operationally-significant item (the new JWT_ALLOW_TEST_MODE env var must never be set in prod) is a runbook NOTE, not a decision — documented rather than asked.

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
