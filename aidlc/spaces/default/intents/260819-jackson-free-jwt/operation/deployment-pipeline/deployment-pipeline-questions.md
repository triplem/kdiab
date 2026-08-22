# Deployment Pipeline — Clarifying Questions (#1606 jackson-free JWT)

Operation phase · Standard depth. Most deployment mechanics are already fixed by the existing
platform (`docker-publish.yml` deploy-on-merge → GHCR, `release.yml` semantic-release, `git revert`
reversibility per ADR-023). These questions resolve only the operational unknowns that shape the
deployment-strategy and rollback-runbook artifacts. Answer A–E or X (Other).

---

## Q1. What actually happens after images are published to GHCR on merge to `main`?

This determines whether the rollback runbook targets a *live redeploy* or an *image republish*.

- A. A running production host/orchestrator (docker-compose / podman / other) continuously pulls and
     runs the new `latest` images automatically — merge effectively deploys to prod.
- B. A production environment exists but is updated **manually** (an operator pulls a chosen tag and
     restarts) — GHCR publish is the artifact, the pull/restart is a separate human step.
- C. There is **no continuously-running production environment** today; GHCR publish is the end of the
     pipeline and the images are consumed ad hoc (self-hosting, demos, local compose).
- D. A/B split: some services auto-deploy, others are manual.
- X. Other (please specify)

[Answer]: C — There is no continuously-running production environment today. GHCR publish is the end of the pipeline; images are consumed ad hoc (self-hosting, demos, local compose). (Guided, 2026-08-21)

---

## Q2. Which rollback path do you want as the runbook's primary procedure for an auth regression?

(An auth regression on this change = the Nimbus provider wrongly rejecting valid tokens, or wrongly
accepting invalid ones, across the fleet. Both are fleet-wide.)

- A. **Redeploy the previous immutable image tag first** (fast service restore using the prior
     `sha-<short>` / `v{version}` tag), then open a `git revert` PR for a clean permanent fix.
     (Recommended — closes an auth outage fastest.)
- B. **`git revert` the merge commit + let CI rebuild/republish**, then redeploy — single clean path,
     but slower to restore service (full CI + publish cycle).
- C. Both documented as equal peers; operator chooses per situation.
- X. Other (please specify)

[Answer]: B, minus the redeploy — since there is no auto/live deployment, rollback is `git revert` the merge + let CI rebuild and republish clean images to GHCR. There is no "redeploy" step because nothing is continuously running. (Guided, 2026-08-21)

---

## Q3. What is the concrete post-deploy smoke signal that JWT verification is healthy in the new images?

"Deployment is not done until smoke passes." For an auth change the smoke test must exercise both the
accept and reject paths.

- A. Positive + negative pair per service: an authenticated request with a **valid** Keycloak token
     returns 2xx, AND a request with a **tampered/expired** token returns `401` with the standard
     `ErrorResponse` body (+ `TOKEN_REJECTED` log). Run against a canary service first (e.g.
     kdiab-measures), then the rest.
- B. Existing cross-service e2e suite (`e2e.yml`) is the smoke signal — a single forwarded token
     accepted by every upstream is sufficient proof.
- C. Health endpoint 200 only (no auth-specific assertion).
- D. A + B combined (per-service accept/reject pair **and** the cross-service e2e token-forwarding
     path).
- X. Other (please specify)

[Answer]: N/A right now — there is no auto/live deploy, so there is no post-deploy smoke stage. Verification for #1606 lives in CI (unit/integration/e2e + Kover + Trivy + CodeQL + the manual security review), which must be green before publish. (Guided, 2026-08-21)

---

## Q4. What is the rollback trigger — the observable that says "roll back now"?

- A. Spike in `401`/`403` on the auth path for tokens that were previously valid (verification
     wrongly rejecting), OR any `500` on the auth path — either crosses threshold → roll back.
- B. Above, plus a manual "auth smoke failed on canary" trigger before fleet-wide rollout.
- C. Error-rate/latency SLO breach on any service (generic, not auth-specific).
- D. Manual/operator judgement only — no automated trigger (small self-hosted footprint).
- X. Other (please specify)

[Answer]: None — there is no running environment emitting observables, so there is no metric-based rollback trigger. Rollback is triggered by discovery (a defect found in review/CI/manual testing), acted on via the source-level `git revert` path in Q2. (Guided, 2026-08-21)
