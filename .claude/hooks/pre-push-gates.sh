#!/usr/bin/env bash
# Hook: PreToolUse:Bash(git push *)  (skill-scoped: active during /release)
# Runs all quality gates before allowing a push. Blocks if any gate fails.
# This is the last line of defence before code reaches the remote.

INPUT=$(cat)
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"

COMMAND=$(echo "$INPUT" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(d.get('tool_input', {}).get('command', ''))
" 2>/dev/null || echo "")

deny() {
  python3 -c "
import json, sys
print(json.dumps({
    'hookSpecificOutput': {
        'hookEventName': 'PreToolUse',
        'permissionDecision': 'deny',
        'permissionDecisionReason': sys.argv[1]
    }
}))
" "$1"
  exit 0
}

# Only run for git push commands — the 'if' field in skill hooks may not filter correctly
if ! echo "$COMMAND" | grep -qE '^\s*git push'; then
  exit 0
fi

cd "$PROJECT_DIR" 2>/dev/null || true

FAILURES=""

# --- Kotlin / Gradle ---
if [ -f "gradlew" ]; then
  if ! ./gradlew test -q 2>/dev/null; then
    FAILURES+="- Tests failed (./gradlew test)\n"
  fi
  if ! ./gradlew koverVerify -q 2>/dev/null; then
    FAILURES+="- Coverage below 80% threshold (./gradlew koverVerify)\n"
  fi
  if ! ./gradlew detekt -q 2>/dev/null; then
    FAILURES+="- Detekt violations found (./gradlew detekt)\n"
  fi
  if ! ./gradlew ktlintCheck -q 2>/dev/null; then
    FAILURES+="- ktlint violations found (./gradlew ktlintCheck)\n"
  fi
fi

# --- Node.js / TypeScript ---
if [ -f "package.json" ]; then
  if ! npm test --silent 2>/dev/null; then
    FAILURES+="- Tests failed (npm test)\n"
  fi
  if npm run lint --if-present --silent 2>/dev/null | grep -qiE "(error|warning)"; then
    FAILURES+="- Lint violations found (npm run lint)\n"
  fi
  if ! npx tsc --noEmit --silent 2>/dev/null; then
    FAILURES+="- TypeScript type errors found (tsc --noEmit)\n"
  fi
fi

# --- .NET ---
if ls *.sln 2>/dev/null | grep -q '\.sln$'; then
  if ! dotnet build -c Release -q 2>/dev/null; then
    FAILURES+="- Build failed (dotnet build)\n"
  fi
  if ! dotnet test -q 2>/dev/null; then
    FAILURES+="- Tests failed (dotnet test)\n"
  fi
fi

# --- SAST (universal) ---
if command -v semgrep &>/dev/null; then
  SAST_OUT=$(semgrep --config=auto --error --severity=ERROR src/ 2>&1 | grep -E "(CRITICAL|HIGH|ERROR)" | head -10)
  if [ -n "$SAST_OUT" ]; then
    FAILURES+="- SAST findings (semgrep):\n${SAST_OUT}\n"
  fi
fi

if [ -n "$FAILURES" ]; then
  deny "Quality gate failures detected. Fix before pushing:\n\n${FAILURES}\nRun .claude/scripts/quality-check.sh for full details."
fi

exit 0
