Implement beads issue $ARGUMENTS in the current worktree.

This command is the **worker** half of the parallel workflow. It is designed to be called directly for a single issue, or embedded as the agent prompt by `/parallel`.

---

## Steps

### 1. Claim the issue
```bash
bd update $ARGUMENTS --claim
```

### 2. Read the OpenSpec
```bash
bd show $ARGUMENTS
```
Read the `design` field carefully. It contains:
- **Goal** — what you are building
- **Context** — current code state
- **Interface Changes** — any OpenAPI or domain model changes
- **Implementation Plan** — ordered steps with exact file paths
- **Test Plan** — what to run
- **Acceptance Criteria** — how to know you are done

If the design field is empty, stop and report: "No OpenSpec found for $ARGUMENTS — run /spec $ARGUMENTS first."

### 3. Implement — follow the Implementation Plan in order

For each step in the plan:
1. **Read the file first** before editing — never edit blindly
2. Make the change described
3. Confirm the change compiles (if possible to check incrementally)

Obey all project conventions:
- Kotlin backend: `kotlin.uuid.Uuid`, `kotlinx.datetime`, domain exceptions, no framework types in `domain/` or `application/`
- Frontend: CSS custom properties (`var(--token)`), not hardcoded colours; `$ARGUMENTS` in `className` over inline styles
- OpenAPI changes: update `api/openapi.yaml` first, then regenerate both backend stubs and frontend client

### 4. Run the Test Plan

Execute each item from the spec's Test Plan section. Common quality gates:

```bash
# Backend
./gradlew :backend:check          # tests + Detekt + Kover (≥80% coverage)

# Frontend
cd kdiab-<service>/frontend
npm run build                      # type-check + Vite build
npm run test                       # Vitest

# Full stack (if changes span services)
./build.sh --check
```

If a quality gate fails: fix the issue, do not skip or bypass (`--no-verify` is forbidden).

### 5. Verify Acceptance Criteria

Check each item in the spec's Acceptance Criteria section. Report any that are not satisfied.

### 6. Commit

```bash
git add <changed files — specific, not -A>
git commit -m "$(cat <<'EOF'
<type>: <concise title matching issue>

Implements $ARGUMENTS per OpenSpec.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

`<type>` follows Conventional Commits: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`.

### 7. Close the issue
```bash
bd close $ARGUMENTS --reason="Implemented per OpenSpec. Quality gates green."
```

### 8. Report

State clearly:
- Files changed (list them)
- Tests run and their outcome
- Whether all Acceptance Criteria are met
- The commit hash
