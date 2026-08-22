<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-08-20T21:30:00Z — Test strategy = Standard (feature scope). Generated unit + integration instructions per Standard, PLUS security-test-instructions (ADR-023 mandates a manual security review; #1606 is auth/safety-sensitive) and a performance-test-instructions marked N/A (NFR-2 explicitly has no latency/startup budget). This "additional type at Standard strategy" is the stage's sanctioned escape hatch ("context demands it").
- 2026-08-20T21:35:00Z — Ran the full release gate `./gradlew check` (test+integrationTest+e2eTest+Detekt+Kover≥80%) PER MODULE (composite includeBuild — no root aggregate). Result: all 9 backends GREEN. Security review of the Nimbus provider: PASS (algorithm-confusion/none-alg blocked by pinned key selectors; issuer+audience+exp enforced; HMAC≥32B; JWKS-HTTPS; no secret/PII logging; fail-closed on malformed claims).

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-08-20T21:40:00Z — The stage names `test-results.md` but the engine's `produces` list uses `build-test-results.md`; wrote the engine-resolved name (`build-test-results.md`) so the artifact path matches the workflow record.

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-08-20T21:42:00Z — Ran the gate with `--no-build-cache` in a batch script (to dodge the known nightscout apiSpec cache-poison) but WITHOUT `--rerun-tasks`; analyze+nightscout then failed on stale generated `upstream-treatments` client code. Re-ran those two with `clean check --rerun-tasks` → both GREEN. Lesson: `--no-build-cache` disables cache PULL only, not incremental up-to-date skipping; forcing regen needs `--rerun-tasks`. Reported the true (green) result + the flaky-race caveat rather than the poisoned batch result.

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-08-20T21:45:00Z — Two out-of-#1606 follow-ups surfaced by the gate: (1) the pre-existing flaky composite-build `apiSpec` generation race (analyze/nightscout `upstream-*` client) — recommend disambiguating the `apiSpec` variant selection; (2) optional auth hardening — require `exp` presence platform-wide (stricter than java-jwt parity; needs all issuers + test minters to set exp). Both belong in separate issues; #1606's own gate is green.
- 2026-08-20T21:46:00Z — Maintainer gates remain per ADR-023 / team practice before merge: a human security sign-off on the Nimbus provider, and all GitHub Actions green on the PR (Trivy/CodeQL/SBOM expected to improve with jackson gone).
