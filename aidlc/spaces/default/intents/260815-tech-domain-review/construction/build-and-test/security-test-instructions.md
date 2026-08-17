# Security Test Instructions — Deliverable Safety & Recommendations-Only Invariant

> **DevSecOps support lens (aidlc-devsecops-agent).** For a review deliverable there is no running
> surface to pen-test; the security concerns are (1) the review must not itself *leak* secrets or
> special-category patient PII into committed Markdown, (2) evidence links must not embed sensitive
> values, and (3) the recommendations-only invariant — the review must not have altered code, config,
> auth, or data. This also cross-checks that `security.md`'s own findings are sound. Driven by the
> `security.md` / U4 `code-generation-plan.md` + `code-summary.md` and the platform
> `.claude/rules/security.md`.

## Test 1 — recommendations-only invariant (no code/config touched)

The single most important security property of a review: it changed nothing executable. Only
`docs/review/*.md` and the `aidlc/` workspace record may differ from `main`.

```bash
cd /home/triplem/projects/kdiab-bkp
# No source/config/schema file may be modified or added by this review
git status --porcelain | grep -E '\.(kt|kts|ts|tsx|js|java|sql|ya?ml|json|Dockerfile|properties)$' \
  | grep -vE '^..?\s+aidlc/' \
  && echo "DEFECT — a source/config file was modified" \
  || echo "PASS — recommendations-only: no source/config/schema file changed"
```

Expected: `PASS`. (A review that edits code violates its own charter — C-1 / FR-1.1: flag concerns,
pair rewrites with an incremental alternative, do **not** author corrected code this run.)

## Test 2 — no secrets committed in the review docs

The deliverable must not contain credentials, tokens, private keys, or connection strings — even as
"example" evidence. Evidence is a symbol path, never a secret value.

```bash
grep -rnEi '(BEGIN [A-Z ]*PRIVATE KEY|aws_secret|password\s*[:=]\s*\S|bearer [A-Za-z0-9._-]{20,}|xox[baprs]-|ghp_[A-Za-z0-9]{20,})' \
  docs/review/ && echo "DEFECT — possible secret in docs" \
  || echo "PASS — no secret patterns in review docs"
```

Expected: `PASS`. (References to the *concept* of the test-mode HMAC secret in FIND-SEC-001 are fine —
the check targets literal secret values, not the word "secret".)

## Test 3 — no special-category patient PII leaked as evidence

kdiab handles GDPR Art-9 health data. Evidence links cite code symbols, not patient rows. Assert no
real-patient identifiers (emails, the seeded patient names as data, glucose rows) appear as evidence.

```bash
# Evidence lines must be code-symbol / config-key / issue cites — never a data value
grep -rnE '^- Evidence:' docs/review/*.md | grep -viE '`[^`]+\.(kt|kts|ts|tsx|sql|ya?ml|json|properties)[^`]*`|changeSet|#[0-9]+|config|realm|vite\.config' \
  && echo "REVIEW — evidence line not obviously a code/config/issue cite (manual check)" \
  || echo "PASS — every evidence line is a code/config/issue citation"
```

Expected: `PASS`. (Test-account names like `sarah`/`mike` may appear as *context* in prose but must not
be presented as evidence rows — the mention is documentation of the seed fixture, not leaked PII.)

## Test 4 — security.md findings are internally sound (devsecops sanity)

Cross-check the security theme doc's own claims against live `main` so the review doesn't ship a stale
or wrong security assertion.

- **FIND-SEC-001** (test-JWT HMAC toggle unguarded): confirm the `jwt.test` / HMAC256 path exists in
  `kdiab-common/.../plugins/Security.kt` — the finding is real only if the toggle is present.
- **FIND-SEC-002** (doctor-access revocation lag): confirm `allowed_patients` is a JWT claim consumed
  by `UserPrincipal.canAccess` — the revocation-latency reasoning depends on it being token-embedded.
- **FIND-SEC-003** (ABAC core clean — positive verdict): confirm `canAccess` performs self/admin/doctor
  checks; a positive verdict must be as evidence-backed as a concern.

```bash
grep -q 'canAccess' kdiab-common/src/main/kotlin/org/javafreedom/kdiab/common/plugins/Security.kt \
  && grep -qiE 'jwt\.?test|HMAC' kdiab-common/src/main/kotlin/org/javafreedom/kdiab/common/plugins/Security.kt \
  && echo "PASS — SEC-001/002/003 anchors resolve in Security.kt" \
  || echo "REVIEW — re-anchor a security finding"
```

Expected: `PASS`.

## Acceptance

The deliverable passes security testing when: the recommendations-only invariant holds (Test 1, the
gate), no secrets or Art-9 PII are committed (Tests 2–3), and the `security.md` findings' anchors
resolve on live `main` (Test 4). Any Test 1 failure is a hard block — it means the review breached its
own non-modification contract.
