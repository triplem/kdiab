# Rule: Quality Gates

These gates are mandatory before any PR can be opened. Agents must not skip any gate.

## Gate Checklist

### 1. All Tests Pass

```bash
# Kotlin/Gradle
./gradlew test

# TypeScript
npm test

# .NET
dotnet test
```

No test may be skipped, ignored, or commented out to make the gate pass.

### 2. Unit Test Coverage ≥ 80%

```bash
# Kotlin — JaCoCo
./gradlew test jacocoTestCoverageVerification
# Configuration in build.gradle.kts: minimum = "0.80".toBigDecimal()

# TypeScript — Jest / Vitest
npm run test:coverage
# jest.config.ts: coverageThreshold: { global: { lines: 80, branches: 80 } }

# .NET
dotnet test --collect:"XPlat Code Coverage"
# Use reportgenerator; fail if line coverage < 80%
```

Coverage is measured on **new and modified code only** in brownfield projects (use diff coverage tools: `diff-cover`, `jacoco-diff`).

### 3. Linting Passes

```bash
# Kotlin
./gradlew detekt ktlintCheck

# TypeScript / React / Angular
npm run lint        # ESLint
npm run type-check  # tsc --noEmit

# .NET
dotnet format --verify-no-changes
```

No linting warning may be silenced with `@Suppress`, `eslint-disable`, or `#pragma warning` without a comment explaining why.

### 4. SAST Scan — No Unmitigated HIGH or CRITICAL

```bash
# Universal (open source)
semgrep --config=auto --error --severity=ERROR src/

# Kotlin: also run detekt security ruleset
./gradlew detekt -PdetektConfigPath=config/detekt-security.yml

# Node.js: npm audit
npm audit --audit-level=high
```

A finding may only be accepted (not fixed) if:
- It is a false positive, AND
- A comment on the finding explains why, AND
- It is tracked as a known-issue in `docs/security/accepted-risks.md`.

### 5. OpenAPI Spec Valid (if changed)

```bash
spectral lint openapi/openapi.yaml
```

No breaking changes without a major version bump.

### 6. No Untracked TODOs

```bash
grep -rn "TODO\|FIXME\|HACK\|XXX" src/ --include="*.kt" --include="*.ts" --include="*.cs"
```

Every TODO must be linked to a tracker issue: `// TODO(#42): extract to service`.

### 7. Build Succeeds

```bash
./gradlew build -x test    # Kotlin
npm run build              # TypeScript/React/Angular
dotnet build -c Release    # .NET
```

### 8. Docker Build Succeeds (if applicable)

```bash
docker build -t ${IMAGE_NAME}:ci .
```

## Automated Enforcement

All gates run in CI on every push to a feature branch. PR cannot be merged until CI is green.

`.github/workflows/ci.yml` (or equivalent) must include all gates as required status checks.

## Gate Failure Handling

1. Agent reads the failure output.
2. Attempts to fix.
3. Re-runs the specific gate.
4. If unable to fix after 3 attempts → blocks PR with `ci-blocked` label and notifies human with:
   - Exact failure output
   - What was attempted
   - Proposed options

Never override or bypass a failing gate without human explicit approval.
