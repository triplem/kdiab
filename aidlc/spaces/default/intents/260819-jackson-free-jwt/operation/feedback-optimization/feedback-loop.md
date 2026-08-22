# Feedback Loop — U1 Jackson-free JWT (#1606)

Consumes `../incident-response/incident-plan.md`, `../performance-validation/load-test-results.md`,
`../observability-setup/slo-config.md`, `../observability-setup/dashboards.md`,
`../observability-setup/alarms.md`, `../deployment-execution/deployment-log.md`.

Insights from #1606 that flow back to Ideation / the backlog for the next cycle.

## Insights → next cycle

| # | Insight (from #1606) | Feed to |
|---|---|---|
| 1 | **A versioned Gradle `constraints{}` force-pin materializes a dependency even with no real consumer.** Removing java-jwt didn't clear jackson — the force-pin itself kept it resolved (ADR-023 Consequences). Reusable lesson for any future dependency-shedding. | Practices / a `project.md`-style Gradle note; the epic #1603 record |
| 2 | **DRY follow-up**: a shared `kdiab-common` Nimbus test-fixture was rejected in ADR-023 as more composite-build refactoring than #1606 warranted. Each service's test minter is now duplicated Nimbus code. | Backlog — a future DRY refactor issue |
| 3 | **`#1615` — require `exp` presence** (deferred hardening; the shared `DefaultJWTClaimsVerifier` could enforce `exp` presence, not just validity). Already an open issue. | Existing issue `#1615` |
| 4 | **Anti-drift CI guard**: no standing CI check asserts jackson stays off the runtimeClasspath (`drift-report.md`). Adding one would make the #1603 win permanent. | Backlog — supply-chain guard issue |
| 5 | **Operability gap**: the whole Operation phase was design-ready-only because there is no running production environment. If kdiab gains a running deployment, the deferred hooks (deploy workflow, auth smoke, live SLIs, armed alarms) become actionable. | Ideation — "introduce a running environment" is a latent epic |

## Loop closure for #1606 itself

- The change is verified, SLO-ready (`slo-report.md`), cost-positive (`cost-analysis.md`), and
  drift-free (`drift-report.md`).
- **Remaining to close #1606**: finish the ~37 uncommitted files → PR → CI green → ADR-023 manual
  security sign-off → maintainer merge → auto-publish (the `deployment-log.md` runbook). The workflow
  has produced the operational scaffolding; the merge is the maintainer's.

## Optimization recommendations (prioritized)

1. **Now**: complete + merge #1606 per the `deployment-log.md` runbook (the CVE win isn't realized
   until the images publish).
2. **Soon**: file the anti-drift CI guard (insight #4) — cheap, makes #1603 durable.
3. **Later**: DRY test-fixture refactor (#2) and `exp`-presence hardening (`#1615`).
