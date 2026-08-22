# Incident Runbooks — U1 Jackson-free JWT (#1606)

Consumes `../observability-setup/alarms.md`, `../observability-setup/dashboards.md`. (`reliability-design`,
`security-design`, `deployment-architecture` from 3.3/3.4 are **N/A — skipped**.)

Concrete step-by-step runbooks for the `incident-plan.md` incident class. All rollbacks defer to
`../deployment-pipeline/rollback-runbook.md` for the exact git commands.

## RB-1: Wrong-reject / fleet lockout (P0 — T1D safety)

Trigger: `FleetWide401Surge` alarm, or users reporting "logged out everywhere" / apps can't sync.

1. **Declare P0.** Uncertain severity → P0 by default (patients cannot reach dosing data).
2. **Roll back first** — do not diagnose the provider live. Execute `rollback-runbook.md` § "Rollback
   procedure (primary)": `git revert -m 1 <#1606-merge-sha>` → PR → CI green → merge → CI republishes
   the java-jwt images.
3. **Restore consumers**: whoever runs the platform pulls the reverted images (prior immutable
   `sha-`/`v{version}` tags are also available for an immediate stopgap pull).
4. **Verify**: a valid Keycloak token is accepted again (the deferred auth smoke in `rollback-runbook.md`
   § "Smoke test" if a running env exists; otherwise CI green on the revert).
5. **Close the loop** (see RB-4).

## RB-2: Wrong-accept / token accepted that should be rejected (P0 — security)

Trigger: security review/CodeQL finding, or observation that a tampered/expired/wrong-audience token
was accepted.

1. **Declare P0 (security).** Treat as an active exposure.
2. **Roll back first** (same as RB-1 step 2). The java-jwt provider's accept/reject behaviour is the
   known-good baseline.
3. **Assess exposure**: use `log-queries.md` to review what was accepted (by `path`, `remote`,
   `correlationId`) during the exposure window; determine whether any patient data was accessed.
4. **Verify + close the loop** (RB-4). If patient data was exposed, follow the platform's data-breach
   obligations (out of scope of #1606 but referenced here).

## RB-3: Provider 5xx on the auth path (P1)

Trigger: `AuthProviderErrors` alarm (any `5xx` on an authed route).

1. Declare P1. Confirm via `dashboards.md` "Verification errors" panel / `log-queries.md` that it's
   the auth path (a `5xx` where a `401` was expected = provider bug).
2. Roll back per `rollback-runbook.md` (the contract is `401`-not-`500`; a `500` is a parity break).
3. Verify + close the loop.

## RB-4: Close-the-loop (all severities)

1. **Reopen #1606** — reuse the issue, do NOT file a duplicate (project practice).
2. **Annotate ADR-023** with the failure mode + a `Status: Superseded-by-revert` note.
3. **Add a preventive check** so the class can't recur (e.g. a new negative-path test for the exact
   failure, or a convention note). Re-run CI to prove it.
4. **Recommended (not gating)**: run `/doctor-t1d-review` (clinical safety) and `/patient-t1d-review`
   (data-access UX) before re-attempting the fix — maintainer judgement decides (`escalation-matrix.md`).
5. Re-attempt the jackson-free swap only after the above; it must re-pass the full merge gate + the
   ADR-023 manual security sign-off.

## RB-5: Supply-chain regression (P2)

Trigger: a jackson/java-jwt/jwks-rsa reappearance, or jackson downgrade to 2.21.3 (the AC-1/AC-8
guard). If discovered post-merge, treat as a defect: open an issue, fix the constraint, re-verify with
`dependencyInsight … --configuration runtimeClasspath`. Not a rollback of #1606 (that would re-add
jackson deliberately) — a forward-fix of the dependency graph.
