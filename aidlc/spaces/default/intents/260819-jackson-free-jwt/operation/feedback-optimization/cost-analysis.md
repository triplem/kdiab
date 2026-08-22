# Cost Analysis — U1 Jackson-free JWT (#1606)

Consumes `../deployment-execution/deployment-log.md`, `../observability-setup/dashboards.md`,
`../performance-validation/load-test-results.md`, `../observability-setup/slo-config.md`,
`../observability-setup/alarms.md`, `../incident-response/incident-plan.md`.

For #1606 the "cost" story is the *point* of the change — it is a net cost **reduction**, not a spend.
There is no cloud infrastructure cost (AWS forbidden; no running prod), so cost = **image footprint +
security-maintenance toil**.

## The optimization #1606 delivers

| Cost dimension | Before | After #1606 | Delta |
|---|---|---|---|
| Runtime CVE surface | jackson-databind/core (recurring HIGH Trivy — CVE-2026-54512/54513) + java-jwt + jwks-rsa | all removed from runtimeClasspath | **↓ fewer CRITICAL/HIGH** (`dashboards.md`/Trivy) |
| Image size | jackson stack (~2 MB) + auth0 libs on every backend image | replaced by nimbus-jose-jwt + one small transitive (json-smart) | **↓ modest per-image reduction** ×9 images |
| Security-maintenance toil | every jackson CVE = a force-pin bump across the monorepo | jackson gone → those CVEs no longer apply | **↓ recurring patching effort eliminated** |
| Owned-crypto risk | (n/a) | Nimbus is an audited JOSE lib (not hand-rolled — ADR-023) | neutral (deliberately avoided owned crypto) |

## Costs added (small, accepted)

- One new dependency (`nimbus-jose-jwt` 10.0.1) + its `json-smart` transitive — a jackson-free JSON
  parser, accepted at Feasibility.
- A small amount of owned wiring (provider + verifier config) → the one-time ADR-023 manual security
  review cost (already paid).

## No cloud cost

No AWS/cloud spend to analyze (project.md forbids AWS; `deployment-log.md` — no running prod). CI
compute is unchanged (same 9-module build).

## Verdict

**Net cost reduction.** #1606 removes a recurring CVE-remediation burden and shrinks the runtime
footprint of all nine images, for the price of one audited library + a one-time security review — the
intended payoff of epic #1603.
