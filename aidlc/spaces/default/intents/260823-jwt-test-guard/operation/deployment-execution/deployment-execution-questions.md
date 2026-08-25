# Deployment Execution — Questions (#1588)

## Q1 — Publish scope & action (asked, answered)
Because pushing + opening a PR is outward-facing, the publish action was confirmed with the user
before execution.

- **A. Yes — branch, push & open PR** *(chosen)* — create `fix/1588-guard-test-jwt-production`, commit
  the 36 code files (aidlc/ records excluded), push to origin, open PR to triplem/kdiab (base main);
  leave the merge to the maintainer after CI is green.
- B. Commit locally, don't push.
- C. Stop — don't touch git.

[Answer]: A

## Q2 — Commit / PR title (asked, answered)

- **A. `fix(security): guard test-mode JWT out of production`** *(chosen)* — type=fix, scope=security →
  semantic-release patch bump; footer `Closes #1588`.
- B. `fix(common): deny-by-default guard for jwt.test mode`.

[Answer]: A

## Result
Branch pushed, PR **#1642** opened, #1588 assigned + `In Progress`. Merge deferred to the maintainer
after the full CI gate is green.
