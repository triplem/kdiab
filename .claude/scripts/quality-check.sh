#!/usr/bin/env bash
# Run all quality gates for the current project.
# Detects the tech stack and runs the appropriate tools.
# Usage: ./.claude/scripts/quality-check.sh

set -euo pipefail

ERRORS=0

check() {
  local name="$1"; shift
  echo "▶ Running: $name"
  if "$@"; then
    echo "  ✅ $name passed"
  else
    echo "  ❌ $name FAILED"
    ERRORS=$((ERRORS + 1))
  fi
}

# --- Kotlin / Gradle ---
if [ -f "gradlew" ]; then
  echo "Detected: Kotlin/JVM (Gradle)"
  # ./gradlew check runs: test + koverVerify (≥80% coverage) + detekt
  check "Check (tests + Kover coverage + detekt)"  ./gradlew check
  check "Build"  ./gradlew build -x test

  # Publish gate: verify maven-publish is wired when kdiab-common is touched
  if [ -f "build.gradle.kts" ] && grep -q "maven-publish" build.gradle.kts 2>/dev/null; then
    check "Publish (local)"  ./gradlew publishToMavenLocal
  fi
fi

# --- Node.js / TypeScript ---
if [ -f "package.json" ]; then
  echo "Detected: Node.js / TypeScript"
  check "Tests"        npm test
  check "Coverage"     npm run test:coverage
  check "Lint"         npm run lint
  check "Type check"   npx tsc --noEmit
  # npm run build is REQUIRED: noUnusedLocals in tsconfig causes TS6196/TS6133 errors
  # that only surface at build time — not caught by tsc --noEmit alone in all cases
  check "Build"        npm run build
  check "Audit"        npm audit --audit-level=high
fi

# --- .NET ---
if ls *.sln 2>/dev/null | grep -q '\.sln$'; then
  echo "Detected: .NET"
  check "Build"        dotnet build -c Release
  check "Tests"        dotnet test
  check "Format"       dotnet format --verify-no-changes
fi

# --- OpenAPI ---
if [ -f "openapi/openapi.yaml" ] || [ -f "openapi.yaml" ]; then
  SPEC="${OPENAPI_SPEC:-openapi/openapi.yaml}"
  [ -f "$SPEC" ] || SPEC="openapi.yaml"
  check "OpenAPI lint" npx @stoplight/spectral-cli lint "$SPEC"
fi

# --- SAST (universal) ---
if command -v semgrep &> /dev/null; then
  check "SAST (semgrep)" semgrep --config=auto --error --severity=ERROR src/ 2>/dev/null || \
    semgrep --config=auto --error --severity=ERROR .
fi

# --- TODOs ---
echo "▶ Checking for untracked TODOs..."
TODOS=$(grep -rn "TODO\|FIXME" src/ --include="*.kt" --include="*.ts" --include="*.cs" --include="*.java" 2>/dev/null | grep -v "#[0-9]" | wc -l)
if [ "$TODOS" -gt 0 ]; then
  echo "  ⚠️  Found $TODOS TODO/FIXME comments without issue references. Link them to tracker issues."
fi

echo ""
if [ "$ERRORS" -eq 0 ]; then
  echo "✅ All quality gates passed."
  exit 0
else
  echo "❌ $ERRORS quality gate(s) failed. Fix before opening PR."
  exit 1
fi
