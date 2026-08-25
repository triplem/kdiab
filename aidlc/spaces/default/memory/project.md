# Project-Level Rules

> Project-specific overrides and corrections. Overrides aidlc-team.md
> and aidlc-org.md. Populated by practices-discovery and the
> self-learning loop.
>
> Use sparingly: most teams don't need a project layer. Reach for it
> only when this specific project deviates from team-wide practice in a
> stable, durable way (e.g., "this monorepo project rebases even though
> our team default is squash"; "this legacy project skips the test
> floor because the existing suite is unsalvageable and we accept
> that").

## Way of Working

<!-- Project-specific override. Example: -->
<!-- This monorepo project rebases instead of squash-merging because -->
<!-- the per-package commit history is the audit trail we depend on -->
<!-- for partial-rollback decisions. Override applies to this project -->
<!-- only. -->

## Walking Skeleton

<!-- Project-specific override. Example: -->
<!-- This project skips the walking skeleton because we're rewriting -->
<!-- an existing service in-place — there's no greenfield bootstrap -->
<!-- to gate. -->

## Testing Posture

<!-- Project-specific override. -->

## Deployment

<!-- Project-specific override. -->

## Code Style

<!-- Project-specific override. -->

## Tech Stack

<!-- Technology choices locked for this project. -->

- **Models**: runs on Anthropic's default models via the standard Claude
  Code provider (`opus[1m]`). This project does NOT use AWS Bedrock or any
  Amazon-hosted models; `.claude/settings.json` pins
  `CLAUDE_CODE_USE_BEDROCK=0` to force the default Anthropic provider.

## Decided

<!-- Decisions made in earlier stages that should not be re-asked. -->
<!-- Format: DECIDED: [decision] (Stage [slug], [date]) -->

DECIDED: No AWS Bedrock and no Amazon-hosted models — use the default
Anthropic provider only. The framework's shipped Bedrock model IDs
(`ANTHROPIC_DEFAULT_*_MODEL` = `global.anthropic.*`), `AWS_REGION`, and the
four AWS MCP servers (`aws-mcp`, `aws-pricing`, `aws-iac`, `aws-serverless`)
were stripped from this project's config (PR #1546, 2026-08-15).

## Scope Overrides

<!-- Custom scope rules for this project. -->

- **Default scope: `enterprise`** — new intents default to `enterprise`
  (all 32 stages, comprehensive depth, full audit trail). The deterministic
  lever is `AWS_AIDLC_DEFAULT_SCOPE=enterprise` in `.claude/settings.json`
  env, which the `/aidlc` orchestrator reads at intent birth (resolution
  order: existing state → `--scope` flag → this env var → hard default).
  Override per-intent with `/aidlc --scope <name>`. Note:
  `AWS_AIDLC_DEFAULT_SCOPE` is framework config (awslabs naming), unrelated
  to AWS Bedrock/models.

## Forbidden

<!-- Populated by practices-discovery affirmation gate. -->
<!-- Format: NEVER [behavior] (affirmed [date]) -->
<!-- Example: NEVER throw exceptions across service layer boundaries (affirmed 2026-05-17) -->

NEVER re-introduce AWS Bedrock, Amazon-hosted model IDs
(`ANTHROPIC_DEFAULT_*_MODEL` / `global.anthropic.*`), `AWS_REGION`, or the
AWS MCP servers when updating the AI-DLC framework. The `/aidlc-v2:aidlc`
installer re-adds them (merges into `settings.json`, creates `.mcp.json`);
strip them after any framework update (learned 2026-08-15).

NEVER re-introduce the `aidlc-aws-platform-agent` — this project has no AWS
infrastructure, so the AWS solutions-architect persona was removed (#1559,
2026-08-17). Its six stages were re-wired: `infrastructure-design` (3.4) lead →
`aidlc-architect-agent`; `environment-provisioning` (4.2) lead →
`aidlc-operations-agent`; support removed from `feasibility` (1.3),
`application-design` (2.6), `nfr-design` (3.3), `feedback-optimization` (4.7).
Like the Bedrock strip, the `/aidlc-v2:aidlc` installer re-adds the agent on
update — after any framework update, re-delete `.claude/agents/aidlc-aws-platform-agent.md`
+ its knowledge dir, re-apply the six re-wirings, and `bun .claude/tools/aidlc-graph.ts compile`.

- NEVER commit directly to `main` (git-hook enforced). (affirmed 2026-08-16)
- NEVER squash-merge — it drops the per-commit `Closes #N` issue linkage. (affirmed 2026-08-16)
- NEVER merge a PR with a failing or still-running CI check, and never bypass required checks with `--admin`. (affirmed 2026-08-16)
- NEVER hardcode credentials, secrets, or API keys, and never log secrets or raw PII. (affirmed 2026-08-16)
## Mandated

<!-- Populated by practices-discovery affirmation gate. -->
<!-- Format: ALWAYS [behavior] (affirmed [date]) -->
<!-- Example: ALWAYS use Result<T,E> for fallible operations in service layer (affirmed 2026-05-17) -->

- ALWAYS create a feature branch (`<type>/<issue>-<description>`) before staging any commit; never work on `main` directly. (affirmed 2026-08-16)
- ALWAYS merge PRs to `main` with a merge-commit (never squash) to preserve `Closes #N` issue linkage. (affirmed 2026-08-16)
- ALWAYS pass the full quality gate before opening or merging a PR: `./gradlew check` (tests + Detekt + Kover 80%) for Kotlin services, and `npm run build` + lint + test for kdiab-ui. (affirmed 2026-08-16)
- ALWAYS keep line coverage at or above 80% on new and modified code, across every scope. (affirmed 2026-08-16)
- ALWAYS wait for every GitHub Actions check to be green before merging to `main`. (affirmed 2026-08-16)
- ALWAYS reference a GitHub issue in commit messages and follow Conventional Commits (Angular preset). (affirmed 2026-08-16)
- ALWAYS delete both the remote and local feature branch immediately after a PR merges. (affirmed 2026-08-16)
- ALWAYS trace a downstream artifact's citations to the authoritative synthesized upstream artifact (e.g. intent-statement.md, scope-document.md), not the raw interview answer. When a synthesis re-letters or re-ranks a raw answer (e.g. a raw Q2 answer 'c, d, a, b' synthesized into 'Q2 = E, deprioritized'), the synthesized form is canonical for traceability tags. (learned 2026-08-16) <!-- cid:requirements-analysis:freetext-traceability-authoritative-artifact -->
- ALWAYS re-verify codekb-tracked issue/evidence state against the live repo (GitHub issue status, current config) before reporting it downstream. Reverse-engineering codekb is a point-in-time snapshot that goes stale or over-simplifies — e.g. it flagged issues #1082 and #894–#898 as open debt when they were already closed, and read an intentional exclusion-based coverage floor (vite lines:72 per ADR-015) as an unmet 80% gap. Never report a resolved gap as open. (learned 2026-08-16) <!-- cid:user-stories:freetext-codekb-issue-freshness -->
- ALWAYS validate a dependency-shedding change against the full RUNTIME classpath before merging. When an issue claims a library is on the classpath *solely* because of one consumer, confirm with `gradle dependencyInsight --dependency <lib> --configuration runtimeClasspath` (not just a build-file grep of declared deps) that no other transitive consumer keeps it, and NEVER remove a version force-pin without checking that conflict resolution won't silently downgrade to a CVE-vulnerable version. (learned 2026-08-19) <!-- cid:build-and-test:verify-runtimeclasspath-before-shedding-a-dep --> #1556 falsely claimed jackson came only from logback-jackson; the runtimeClasspath check found it also pulled by com.auth0:java-jwt (JWT) and ktor-server-openapi (Swagger), and dropping the force-pin would have downgraded jackson 2.21.4->2.21.3 (re-introducing CVE-2026-54512/54513). The finding split the work into epic #1603 (#1605 encoder swap shipped, #1607 swagger path removed, #1606 jwt path open). (learned 2026-08-19) <!-- cid:build-and-test:build-and-test:verify-runtimeclasspath-before-shedding-a-dep -->
- ALWAYS check whether TEST code depends on a library directly before shedding it — the build blast radius can extend to every module's test helpers even when the runtime change is one file. A dependency-shedding change's DoD may target only the RUNTIME classpath, but dropping the artifact from `implementation` can break TEST compilation elsewhere (e.g. #1606: every service's tests mint HMAC JWTs via `com.auth0.jwt.JWT.create()`, reached transitively through the main `ktor-server-auth-jwt`; dropping it breaks test compilation platform-wide unless test minting migrates to the replacement lib or the old lib is re-added as `testImplementation`). Grep test sources for the library's imports, not just main. (learned 2026-08-19) <!-- cid:requirements-analysis:test-code-dep-usage-when-shedding -->
- ALWAYS scope a version-force-pin removal to the exact constraint lines being retired; a `constraints { }` block may hold unrelated CVE pins. NEVER strip an unrelated pin while removing another — it silently re-opens that CVE. (e.g. #1606: `kdiab.kotlin-base` pins jackson-core, jackson-databind AND handlebars/CVE-2026-55760; removing "the jackson force-pin" must delete only the two jackson lines and keep the handlebars pin.) (learned 2026-08-19) <!-- cid:requirements-analysis:multi-pin-force-pin-scope-removal -->
- ALWAYS enumerate the full TEST-fixture blast radius before estimating a config-toggle guard change: when a security-patch ADDS a deny-by-default affirmation to a shared-library config toggle (e.g. `jwt.allowTestMode` guarding `jwt.test` in `kdiab-common` `readJwtConfig()`), the RUNTIME change may be one line but every legitimate test that enables the toggle must also affirm the new opt-in or it fails fast. Grep BOTH enablement mechanisms — `MapApplicationConfig(... "jwt.test" to "true" ...)` Kotlin builders across unit/integration/e2e for all services + `kdiab-common`, AND `src/test/resources/application.conf` HOCON `test = true` — before trusting an `effort=S`/"one-line" tag (#1588: guard is 1 line, propagation is ~36 sites incl. `SecurityConfigTest`'s positive-path test). This is the add-a-required-config mirror of [[test-code-dep-usage-when-shedding]]. (learned 2026-08-24) <!-- cid:nfr-requirements:enumerate-test-fixture-blast-radius-for-config-toggle-guard -->
- ALWAYS confirm the FILES a failing local `detektMain` flags are ones your change actually touched before treating the failure as your regression — CI Detekt (with the committed baseline) is authoritative. On this repo, local `detektMain` currently emits pre-existing `UnreachableCode` false-positives absent from the CI-passing baseline: ~21 on `kdiab-common` (`RateLimit.kt`/`AuditRoutes.kt`/`Tracing.kt`) + 2 on `kdiab-profiles` (`Application.kt` InjectDispatcher, `ProfileMapper.kt` UseOrEmpty). This is a local Detekt-version discrepancy (global CLAUDE.md documents the UnreachableCode FP class; #1579 tracks the analogous nightscout baseline). Prove cleanliness by grepping the detekt report for YOUR changed files, or stash-and-rerun to reproduce the identical failures on clean `main`. (learned 2026-08-25) <!-- cid:code-generation:local-detekt-preexisting-unreachablecode-fp-vs-ci -->
- ALWAYS recognise a `:compileKotlin` failure with `Unresolved reference` to upstream-generated types (e.g. `Profile`, `CreateProfileRequest`) plus `Failed to get the schema name: null`, in an `includeBuild` service that consumes `registerUpstreamSpec` clients (`kdiab-nightscout`, `kdiab-analyze`), as the flaky composite-build apiSpec codegen race **#1614** — NOT a real code break. Diagnose by inspecting `build/generated/upstream-<svc>/src/main/kotlin/.../models/`: the Gradle build cache can serve a WRONG upstream-`<svc>` generation (e.g. `upstream-profiles/models/` holding carbs/food types like `CreateFoodEntryRequest` instead of `Profile`/`CreateProfileRequest`), and a plain `clean` reuses the cached wrong output (`openApiGenerate`/`generate<Svc>Models` FROM-CACHE). FIX locally: `rm -rf <svc>/build/generated/upstream-<other>` then `./gradlew clean compileKotlin --rerun-tasks --no-build-cache --no-parallel`. The `Failed to get the schema name: null` line prints even on the subsequently-successful build → benign generator warning, not the cause. CI runs on a fresh checkout (no local stale cache), so #1614 does not bite there. (learned 2026-08-25) <!-- cid:build-and-test:nightscout-1614-stale-upstream-cache-diagnosis -->
## Corrections

<!-- Project-specific corrections from human feedback. -->
<!-- Format: NEVER/ALWAYS [behavior] (learned [date]) -->
- On a recommendations-only / assessment intent, Application Design models the review deliverable system (finding-record schema, docs/review document set, GitHub-issue projection, findings->deliverables pipeline) rather than production code — the deliverable architecture IS the thing being designed. (learned 2026-08-16) <!-- cid:application-design:appdesign-assessment-deliverable-system -->
- On review/assessment intents, number design-record ADRs ADR-RVW-NNN so they never collide with real kdiab platform (ADR-NNN) or service (ADR-{SVC}-NNN) ADRs the review may itself recommend. (learned 2026-08-16) <!-- cid:application-design:appdesign-adr-rvw-numbering -->
- In Units Generation, when a story's stated INVEST independence conflicts with an Application-Design data-flow edge, the unit DAG follows the authoritative story dependency and documents the refinement (e.g. quick-wins depends on the theme findings directly, not on the assembled backlog, honouring US-8's independence over the design's QuickWinsView->Backlog edge). (learned 2026-08-16) <!-- cid:units-generation:unitsgen-story-independence-over-design-edge -->
- Delivery Planning may place a deliverable-assembly Bolt after the Must-priority themes only (not after all themes it depends on in the unit DAG) when the deliverable is a living document that later Bolts extend — a value-first economic deviation from topological order, justified in risk-and-sequencing-rationale.md, that never violates the finding->deliverable data edge (each finding is appended only after its theme ships; only document-materialization timing moves). (learned 2026-08-16) <!-- cid:delivery-planning:delivery-living-deliverable-precedes-dag-deps -->
- The aidlc-architecture-reviewer-agent sub-agent (§12a reviewer) hangs in this environment — its transcript freezes at ~140B and it never self-appends its ## Review section (observed on application-design and units-generation). Invoke it with a BOUNDED wait (a background poll for the verdict, ~2-3 min cap), and if it stalls, STOP it: stopping surfaces the reviewer's full findings via its result message, which you then apply (§12a fix loop) and record on the primary artifact, alongside your own inline sensor/DAG/coverage verification. Never block the gate indefinitely on it — the reviewer is advisory and the human decides at the gate. (learned 2026-08-16) <!-- cid:application-design:reviewer-subagent-hangs-bounded-wait-and-stop -->
- REFINEMENT of the reviewer-hang rule: POLL before you stop. When a §12a reviewer runs in the background, use `TaskOutput(block:false)` to distinguish a genuinely stalled reviewer from one still actively verifying — do NOT stop a reviewer that is mid-verification (checking output lines/tool calls are advancing). The hang is agent-specific: `aidlc-architecture-reviewer-agent` froze on application-design/units-generation, but `aidlc-product-lead-agent` completed cleanly TWICE on this intent (requirements-analysis ~101s, user-stories ~131s) with thorough code-grounded findings. So: bounded wait + a non-blocking poll to check progress, stop only if truly stalled, and let a progressing reviewer finish. (learned 2026-08-19) <!-- cid:user-stories:reviewer-poll-before-stop-agent-specific-hang -->
- On a recommendations-only / assessment intent, at the OQ-1 park-vs-continue point the fastest path to the value-bearing deliverable is to jump past the per-unit design stages (functional-design, nfr-requirements, nfr-design, infrastructure-design) straight to code-generation and author the `docs/review/*.md` set INLINE — for these intents the docs ARE the "code" (application code goes to the workspace root), so code-generation writes markdown deliverables while the per-unit `code-generation-plan.md`/`code-summary.md` stay the record artifacts. The intermediate design stages add little because the deliverable system is already fully specified by the Inception ADRs (ADR-RVW-001..007). Note: the engine iterates code-generation over units in the unit-DAG's TOPOLOGICAL order (all theme units before the assembly units), not the delivery-planning Bolt sequence — so assemble the backlog once after every theme unit, not incrementally. (learned 2026-08-16) <!-- cid:code-generation:codegen-assessment-jump-to-docs-inline -->
- On a review/assessment intent, build-and-test verifies the `docs/review/*.md` deliverable (the "code") and MUST run two cross-document checks that the markdown-shape sensors (required-sections/upstream-coverage) cannot catch: (1) a finding->deliverable TRACEABILITY check — every actionable finding (theme-doc canonical block minus the named positive verdicts) appears in the assembled backlog table AND the roadmap AND the queued-issue set, and the row counts match the "N actionable findings" headline; (2) a PHASE-AUTHORITY consistency check — each finding's `Phase` agrees across theme-doc, backlog column, and roadmap band. When a phase drift is found, align to the ROADMAP band (the single source of truth per ADR-RVW-006), not to the majority. These caught FIND-SEC-002 dropped from a backlog that still claimed "30 actionable findings", and CLIN-010/CLIN-013 stamped Mid in theme+backlog while the roadmap+quick-wins placed them Near. Also assert the recommendations-only invariant (no `.kt/.kts/.ts/.tsx/.sql/.ya?ml/.json` source changed outside `aidlc/` + `docs/review/`). (learned 2026-08-16) <!-- cid:build-and-test:review-traceability-and-phase-authority-checks -->
- On a review/assessment intent, ci-pipeline packages the build-and-test deliverable-integrity checks into a dependency-free stdlib `docs/review/verify.py` (10 checks: presence, schema, contiguity, severity-discipline, evidence-format, backlog-traceability, phase-authority, dead-links, readme-numbers, no-secrets; exit 1 on any fail) plus a path-filtered (`docs/review/**`) GitHub Actions workflow `review-verify.yml` that runs it on PR + push. Match the repo's supply-chain convention (pin `actions/checkout` by SHA + version comment; use the runner's pre-installed python3, no `setup-python` pin). The RECURRING gate deliberately OMITS the one-time recommendations-only authoring invariant — once a finding is materialized, its implementation PR legitimately changes kdiab source, so baking "no code changed" into ongoing CI would be wrong. Installing the workflow is review TOOLING (a CI job + checker over `docs/review/`), not a change to the kdiab system under review, so the recommendations-only posture (scoped to kdiab app/service code) stays intact. (learned 2026-08-16) <!-- cid:ci-pipeline:review-verify-py-and-workflow-packaging -->
- On a review/assessment intent, the Operation deployment-pipeline stage designs GitHub-native delivery of the docs/review deliverable rather than cloud CD: a docs-PR promotion path (Working=local, Staged=PR with review-verify.yml, Published=merged on main), a gh issue projection, and ROADMAP Near/Mid/Long as a phased-but-pull rollout, with `python3 docs/review/verify.py` exit 0 as the docs rollback smoke test and rollback = revert+annotate/supersede (never silent-delete). Infra-heavy strategies (blue/green, canary-by-metric, AppConfig/CloudWatch-Evidently feature flags) are N/A and are replaced by their GitHub-native analogues (the deferred queued-issue set is the dark-launch mechanism). infrastructure-design (3.4) is skipped for these intents, so source cd-config from ci-config.md + quality-gates.md + the deliverable, not from a deployment-architecture hand-off. Extends the ci-pipeline/build-and-test review-intent series. (learned 2026-08-16) <!-- cid:deployment-pipeline:deployment-pipeline-review-delivery-model -->
- On a review/assessment intent (AWS forbidden per project rules), the Operation environment-provisioning stage has no cloud infra to provision — the 'environment' is the GitHub-native delivery surface: repo (triplem/kdiab), Actions runner (ubuntu-latest/python3), review-verify.yml + verify.py, gh CLI auth, the review label taxonomy, and the main branch-protection rule. Validate it with a LIVE read-only sweep (python3 docs/review/verify.py exit 0, gh auth status, gh label list, gh api .../branches/main/protection) rather than describing it abstractly, and produce a readiness verdict split by capability (publish-deliverable / materialize-findings / enforce-gate). The only provisioning mutation is creating the review label taxonomy (review, quick-win, area:*, severity:*) via gh label create --force (idempotent); gate it on explicit user authorization because it is outward-facing. Epic + sub-issues + branch protection stay deferred to Deployment Execution (4.3) / maintainer. Note the working clone may have only a local git remote, so target the canonical GitHub repo via gh -R. (learned 2026-08-16) <!-- cid:environment-provisioning:environment-provisioning-github-delivery-surface -->
- On a review/assessment intent, deployment-execution actually publishes the docs/review deliverable via a real feature-branch -> commit (Refs the intent's tracking issue) -> push -> PR to triplem/kdiab (scope the PR to docs/review/** + review-verify.yml only; exclude the aidlc/ record — it is mid-workflow and audit shards must not be committed on a feature branch). Enforce publish-before-materialize: the GitHub epic/sub-issues MUST wait until the deliverable is merged so theme-doc links resolve. Smoke test = the 'Verify review deliverable integrity' check green; note CodeQL Analyze(actions) fires even on a docs-only PR whenever a .github/workflows file changes, and Analyze(javascript-typescript) runs its matrix regardless of paths — both must be green (CLEAN/MERGEABLE) before merge. Leave the merge itself to the maintainer (merge-commit, never squash). (learned 2026-08-16) <!-- cid:deployment-execution:deployment-execution-publish-before-materialize -->
- The commit-guard PreToolUse hook (.claude/hooks/commit-guard.sh) matches any Bash command STRING containing 'git commit' (grep 'git[[:space:]]+commit') and denies it when the current branch is main/master. This false-positives when a heredoc/echo's CONTENT merely mentions 'git commit' (e.g. writing a diary that says 'auto-commit/push') while on main. Avoid it: write files with the Write tool (not cat/echo/heredoc via Bash), and run real git commits only after checking out a feature branch. On ai-dlc/* branches the hook skips Conventional-Commit enforcement; on any branch it also validates the -m subject against the Conventional-Commits pattern. (learned 2026-08-16) <!-- cid:deployment-execution:deployment-execution-commit-guard-heredoc-falsepositive -->
- On a review/assessment intent, observability-setup has no runtime telemetry — map its 6 artifacts to the deliverable's health/currency/progress lifecycle: dashboards = GitHub epic native sub-issue burn-down + review-verify.yml run history + a weekly currency report; SLOs = 100% integrity green on main + evidence-linkage + a currency-drift-detection-latency window; log-queries = gh/git (issue burn-down, gh run list, git log baseline..HEAD per cited file); tracing = the recommendation lifecycle (finding -> backlog row -> roadmap band -> sub-issue -> branch -> PR Closes #N -> merge), correlated by finding ID + issue number; anomalies = deterministic verify.py/monitor.py checks, NOT statistical baselines (a finite prose deliverable has no time-series). The install (when chosen) is a stdlib docs/review/monitor.py (currency + burn-down, false-positive-guarded so only real top-level-repo-dir paths are tracked) + a scheduled review-monitor.yml that upserts a SINGLE idempotent drift issue; keep it advisory (never blocks a PR) with just-in-time per-band re-verification as the authoritative currency gate. (learned 2026-08-16) <!-- cid:observability-setup:observability-setup-deliverable-lifecycle -->
- On a review/assessment intent, incident-response's top incident class is an IMPLEMENTED recommendation regressing the live platform (not the review docs breaking, which is only a P2/P3 integrity/currency defect). On a safety-critical platform (T1D) a clinical-safety regression is P0 = rollback-first (git revert immediately; never forward-fix a dose path under pressure) + a mandatory domain-review gate (/doctor-t1d-review, plus /patient-t1d-review for UX) before any re-attempt + close-the-loop (supersede/annotate the originating finding, add a preventive rule to docs/review/CONVENTIONS.md so the class can't recur, re-run verify.py). Reuse the platform's existing P0-P4 severity labels with a safety-first 'uncertain -> P0' default, and recommend establishing an external clinical advisor for P0 sign-off since a solo maintainer has no on-call. (learned 2026-08-16) <!-- cid:incident-response:incident-response-implemented-recommendation-regression -->
- kdiab has NO continuously-running production environment today — the delivery pipeline terminates at GHCR image publish (`docker-publish.yml` gated deploy-on-merge → immutable `latest`/`v{version}`/`sha-<short>` tags; `release.yml` semantic-release). Operation stages (deployment-pipeline, environment-provisioning, observability-setup, incident-response, feedback-optimization) must model a PUBLISH-TO-REGISTRY pipeline, not a deploy-to-environment one: rollback is source-level `git revert` + CI republish (no live redeploy — nothing is continuously running); verification authority is CI (tests + Kover ≥80% + Trivy + CodeQL + manual security sign-off must be green BEFORE publish), so there is no post-deploy smoke stage and no metric-based rollback trigger. Do NOT invent canary/blue-green/CloudWatch-Evidently/AppConfig/metric-trigger machinery that has no target; instead document the publish-only reality plus clearly-labelled FORWARD HOOKS (deploy workflow, auth accept/reject smoke test, metric rollback trigger, redeploy-prior-immutable-tag fast path) for if/when a running prod is introduced. (learned 2026-08-21) (learned 2026-08-21) <!-- cid:deployment-pipeline:no-running-prod-publish-only-pipeline -->
