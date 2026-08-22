# Personas — Jackson-free JWT Verification (#1606)

For an internal, behaviour-preserving auth change the "users" are the maintainer, the security/QA
gatekeepers, operators, and — indirectly — the platform's end users. These personas are the actors
behind the requirements in `../requirements-analysis/requirements.md`. Grounded in
`../../../codekb/kdiab-bkp/business-overview.md` and
`../../../codekb/kdiab-bkp/component-inventory.md`; governed by
`../practices-discovery/team-practices.md`.

## Primary Personas

### P1 — Platform Maintainer (`triplem`)
Solo maintainer + AI-DLC agents. Implements the change, owns the PR, runs the quality gate, merges.
**Goals:** close epic #1603 (jackson gone); keep authentication behaviour identical; one clean atomic
PR; no regression across the 8 backend services + `kdiab-common` (9 Gradle modules; the change lives in `kdiab-common`). **Frustrations:** silent auth regressions; a force-pin edit
re-opening a CVE; test compilation breaking across services.

### P2 — Security / QA Reviewer
Represents the mandated security review + the auth e2e gate. **Goals:** no weakened validation
(signature/issuer/audience/expiry); no unmitigated HIGH/CRITICAL; the negative-path matrix proves
parity. **Frustrations:** `MACVerifier` dropping issuer/audience checks; unverified "no jackson" claims.

### P3 — Operator / Deployer
Runs the platform (docker-compose / prod). **Goals:** no surprise config churn; if `jwt.*`/realm
config must change, it's documented and called out; deploy-on-merge stays green. **Frustrations:**
undocumented config-key changes; a forced end-user re-login with no notice.

## Indirect Persona

### P4 — End User (patient `sarah`/`mike`, doctor `dr_house`, `admin`)
Uses the T1D platform daily via kdiab-ui. **Goal for #1606:** *nothing changes* — stays logged in,
tokens keep working, no visible difference. **Success = they never notice the change happened.** No
forced re-login; token format (Keycloak RS256) unchanged.
