# Deployment Pipeline — Stage Diary

Stage: deployment-pipeline (4.1) · Phase: Operation · Intent: 260819-jackson-free-jwt (#1606)

## Interpretations
- 2026-08-21T16:22:53Z — This is a brownfield dependency-swap (jackson-free JWT), not a new deployable; interpreting "deployment pipeline" the same way ci-pipeline did — as a decision doc over the EXISTING deploy-on-merge pipeline (docker-publish.yml → GHCR) rather than authoring new CD. Context: platform already deploys-on-merge; AWS/cloud CD is forbidden per project.md.
- 2026-08-21T16:22:53Z — Read the real pipeline: docker-publish.yml triggers on workflow_run(completed, main) of the 9 CIs, gates on all check-runs green, then publishes 9 images to GHCR tagged latest + v{version} (semver) + sha-<short>. release.yml runs semantic-release. So the "environment promotion" is: PR CI → merge → gated publish → GHCR. There is no visible cloud staging/prod split in-repo.

## Deviations
- 2026-08-21T16:22:53Z — Stage prose Step 3 lists AWS-flavoured questions (CloudWatch Evidently / AppConfig feature flags, blue/green, canary-by-metric). Substituting GitHub-native analogues: image-tag rollback, deploy-on-merge, no feature-flag layer (auth verification can't be dark-launched — it's a fleet-wide correctness change). Rationale: project.md forbids AWS; the deployable surface is GHCR images.

## Tradeoffs
- 2026-08-21T16:22:53Z — Rollback approach: (a) git revert the merge + let CI republish vs. (b) redeploy the previous immutable image tag directly. For a safety-sensitive auth path an outage must be closed fast, so leaning "redeploy prior tag first (fast restore), revert PR second (clean history)" as the recommended runbook default — pending user confirmation.

## Open questions
- 2026-08-21T16:22:53Z — Does merge-to-main auto-deploy to a running production host/orchestrator, or does the pipeline stop at GHCR publish (images pulled manually)? Determines whether the rollback runbook targets a live redeploy or image republish. Asked in questions file. RESOLVED below.

## Resolved (user answers, guided, 2026-08-21)
- 2026-08-21T16:24Z — Q1=C: NO continuously-running production environment. GHCR publish is the terminal step; images consumed ad hoc (self-hosting/demos/local compose). This is the load-bearing answer — it collapses Q3/Q4 to N/A.
- 2026-08-21T16:24Z — Q2=B-minus-redeploy: rollback = `git revert` merge + CI rebuild/republish clean images. NO live redeploy step (nothing continuously running). Prior immutable tags remain in GHCR for anyone who wants them.
- 2026-08-21T16:24Z — Q3=N/A: no post-deploy smoke test (no deploy target). Verification authority for #1606 is CI (tests + Kover + Trivy + CodeQL + manual security review) — must be green before publish.
- 2026-08-21T16:24Z — Q4=none: no running env → no metric-based rollback trigger. Rollback is discovery-triggered (defect found in review/CI/manual test), not observable-triggered.
- 2026-08-21T16:24Z — Consistency check PASSED: all four answers converge on "publish-to-registry pipeline, not deploy-to-environment pipeline." No contradiction. Artifacts must NOT invent canary/blue-green/metric-trigger apparatus that doesn't exist; instead document the publish-only reality + forward hooks for when a running prod is introduced.

## Sensor note
- 2026-08-21T16:28Z — upstream-coverage sensor logged **Pass: false** only on memory.md and deployment-pipeline-questions.md — expected: those are the diary + question file, not upstream-citing deliverables. Confirmed by grep that the three real deliverables (cd-config/deployment-strategy/rollback-runbook) each reference all four consumed artifacts (ci-config, quality-gates, deployment-architecture, cicd-pipeline — the latter two cited as N/A/skipped-3.4). required-sections satisfied (each deliverable has ≥2 H2). Advisory sensor; coverage verified manually.
