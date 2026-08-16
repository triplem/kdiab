# Quick Wins

> **Unit U8.** The subset of the [backlog](./BACKLOG.md) that is **effort = S** (a single ~1-day
> maintainer burst), **high value**, and **independently shippable** (NFR-2, FR-D.2). These are the
> "do this week" items — each is one feature-branch-per-issue change with its own green-CI merge.
> Full evidence is in the linked theme docs; this is a filtered view, not new findings.

## Top quick wins (high value, small effort)

| Priority | ID | Area | Sev | What to do | Doc |
|---|---|---|---|---|---|
| 1 | FIND-SEC-001 | security | High | Refuse to start in test-JWT (HMAC) mode outside dev/test — closes a platform-wide auth-forge risk with a one-line guard | [security](./security.md) |
| 2 | FIND-CLIN-002 | clinical-safety | Med | Validate `glucoseUnit` against `{mg/dL, mmol/L}` (trimmed) and reject unknown — stops silent BG mis-scaling | [clinical-safety](./clinical-safety.md) |
| 3 | FIND-CLIN-013 | clinical-safety | Med | Add a soft implausible-dose plausibility guard at the treatments boundary (warn on bolus > ceiling) | [clinical-safety](./clinical-safety.md) |
| 4 | FIND-CLIN-010 | clinical-safety | Med | Switch the estimate to GMI (`3.31 + 0.02392·mean`) and relabel "GMI (estimated A1c)" | [clinical-safety](./clinical-safety.md) |
| 5 | FIND-DEBT-008 | tech-debt | Med | Remove `suppressWarnings.set(true)` in `kdiab-analyze` and triage the surfaced warnings | [tech-debt](./tech-debt.md) |

Doing rows 1–5 in one week measurably improves safety (unit + dose-plausibility + metric labelling),
security (auth hardening), and code health — five independent PRs, no coordination.

## Also quick, lower value (effort = S, Low severity)

Fold these in opportunistically alongside a nearby change:

| ID | Area | Sev | What to do |
|---|---|---|---|
| FIND-CLIN-006 | clinical-safety | Low | Reject physiologically impossible `currentBg` at the boundary |
| FIND-CLIN-005 | clinical-safety | Low | Round the dose to a pump increment (0.05 U) instead of 0.01 U |
| FIND-SEC-007 | security | Low | Add `frame-ancestors`/`base-uri`/`form-action` to the CSP |
| FIND-MOD-003 | modernization | Low | Stamp a single platform version across modules (release config) |

## Not quick (do not attempt as a burst)

For contrast, these high-value items are **not** quick wins — they are Medium/Large effort and belong in
the [roadmap](./ROADMAP.md), not here: FIND-CLIN-001 (IOB, M), FIND-CLIN-014 (stacking detection, L),
FIND-SEC-004 (MDR/SaMD determination, L), FIND-DEBT-005 (wire 4 backends into api:generate, M),
FIND-MOD-002 (service consolidation, L).

## Empty-result honesty

This list is genuinely filtered — the Low-severity rows are separated from the high-value ones rather
than padded together, and large-effort items are explicitly excluded even where their value is high. A
quick win is small *and* worthwhile, not merely small.
