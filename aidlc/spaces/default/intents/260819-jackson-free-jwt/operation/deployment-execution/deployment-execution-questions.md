# Deployment Execution — Clarifying Questions (#1606 jackson-free JWT)

Operation phase · Standard depth · lead aidlc-pipeline-deploy-agent (support: developer).

For #1606 the "deployment" is the merge of the PR to `main`, which triggers `docker-publish.yml` to
publish the 9 jackson-free images to GHCR (no separate live deploy — no running prod). That merge is
a hard gate: **all GitHub Actions green + ADR-023 manual security sign-off + maintainer-owned
merge-commit**. Current state (live check): the branch has the core provider committed but **~37 code
changes are still uncommitted**, the branch is **unpushed**, and **no PR exists**. So there is exactly
one decision this stage needs.

---

## Q1. What deployment-execution action should I take now?

- A. **Document-only (dry-run).** Produce the execution runbook + record the honest NOT-YET-EXECUTED
     status; take **no** git/GitHub action. You (maintainer) drive commit → push → PR → security
     sign-off → merge yourself when ready. (Safest; respects incomplete work + maintainer-owned merge.)
- B. **Prepare the PR (no merge).** I commit the remaining non-`aidlc/` #1606 changes on the feature
     branch, run `./gradlew check`, push the branch, and open a PR (`Refs #1606`, `Refs #1603`) so CI
     runs and the security review can proceed. I do **not** merge. (Excludes the `aidlc/` record from
     the commit per project rules.)
- C. **You'll drive everything manually** — I produce the docs only and take no action, same as A but
     with no runbook hand-holding.
- X. Other (please specify)

[Answer]: A — Document-only (dry-run). Take no git/GitHub action; the maintainer drives commit → push → PR → security sign-off → merge. (Guided, 2026-08-21)

---

## Q2. (Only if A or C) Confirm the deferred verification posture is acceptable

There is no running environment, so no live post-deploy smoke test / health check runs as part of this
stage. Verification authority stays with **CI** (unit/integration/e2e + Kover + Trivy + CodeQL + the
ADR-023 manual security sign-off), which must be green before the maintainer merges. The live auth
accept/reject smoke test stays **deferred** (specified in `../deployment-pipeline/rollback-runbook.md`
§ "Smoke test (deferred)") for if/when a running prod exists.

- A. Acceptable — CI is the verification authority; live smoke stays deferred.
- B. Not acceptable — I want a live verification step defined/executed now (specify environment).
- X. Other (please specify)

[Answer]: A — entailed by Q1=A. Document-only inherently accepts that no live verification runs now; CI (+ ADR-023 sign-off) is the verification authority and the live auth smoke stays deferred. Consistent with the no-running-prod posture affirmed at 4.1/4.2. (Recorded 2026-08-21)
