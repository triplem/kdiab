# Risk & Sequencing Rationale — Jackson-free JWT Verification (#1606)

Traces to `../units-generation/unit-of-work.md`, `../units-generation/unit-of-work-dependency.md`,
`../units-generation/unit-of-work-story-map.md`, `../requirements-analysis/requirements.md`,
`../user-stories/stories.md`, `../application-design/components.md`,
`../refined-mockups/mockups.md` (skipped — no UI); governed by
`../practices-discovery/team-practices.md`.

## Why risk-first (T1 before the swap)

This is a **security-critical, behaviour-preserving** change. The dominant risk is a silent auth
regression (accepting a token that should be rejected, or vice-versa). The mitigation is to **pin the
current behaviour with characterization tests (T1) before touching the implementation**, then require
those exact tests to stay green after the Nimbus swap (T3). This turns "did we preserve behaviour?"
from a judgement call into a deterministic, re-runnable check. It strengthens the team's classic
test-after default specifically because of the safety profile.

## Sequencing rationale (T1 → T8)

| Step | Why here |
|---|---|
| T1 parity tests first | Safety net must exist before the swap; independent of the new code |
| T2 dep swap | Unblocks compilation of T3/T4; trivial |
| T3 Nimbus verifier | Core; validated by T1's matrix |
| T4 test-minter migration | Needed for the suites to compile once java-jwt leaves main |
| T5 realm/config | After T3 confirms whether any config change is needed (design says none) |
| T6 sweep + jackson-pin removal | After T3/T4 land, so the classpath is final before proving jackson dead |
| T7 ADR | After the design is realised |
| T8 release gate | Last — security review + whole-platform CI green |

## Top Risks & Mitigations (from the RAID log + design review)

| Risk | Mitigation | Owner step |
|---|---|---|
| Claim-shape parity (Nimbus throws where java-jwt returns null) | Exception-guarded mapper (ADR-023); T1 asserts present-but-non-array roles | T1, T3 |
| HMAC test-mode drops issuer/audience checks | One shared `DefaultJWTClaimsVerifier` for both paths | T3 |
| Force-pin removal re-opens handlebars CVE | Remove only the two jackson lines; sweep-gated | T6 |
| Surprise surviving jackson consumer | Sweep across 9 modules + `ktor-server-swagger`; fallback keeps pin | T6 |
| Test compilation breaks platform-wide | T4 migrates every minter; ordered right after T2 | T4 |
| Security regression slips through | Mandatory security review + full auth e2e as merge gate | T8 |

## Living-deliverable / value-first note

Not applicable — this is a single indivisible Bolt, not a phased deliverable. The only value-vs-order
deviation considered (and rejected) was splitting delivery, which the compile-boundary coupling forbids
(see `unit-of-work.md`). Sequencing is therefore pure risk-first within one Bolt.
