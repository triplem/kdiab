#!/usr/bin/env bash
# Hook: PostToolUse:Write|Edit  (skill-scoped: active during /implement)
# Runs the appropriate linter on the edited file and injects findings back
# as additionalContext so Claude fixes them in the same turn.

INPUT=$(cat)
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"

FILE_PATH=$(echo "$INPUT" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(d.get('tool_input', {}).get('file_path', ''))
" 2>/dev/null || echo "")

[ -z "$FILE_PATH" ] && exit 0
[ ! -f "$FILE_PATH" ] && exit 0

inject_context() {
  python3 -c "
import json, sys
msg = sys.argv[1]
print(json.dumps({
    'hookSpecificOutput': {
        'hookEventName': 'PostToolUse',
        'additionalContext': 'Linter output for ' + sys.argv[2] + ':\n' + msg
    }
}))
" "$1" "$2"
}

cd "$PROJECT_DIR" 2>/dev/null || true

OUTPUT=""
EXT="${FILE_PATH##*.}"

case "$EXT" in
  kt)
    # Run detekt on the subproject that owns this file.
    if [ -f "./gradlew" ]; then
      # Derive subproject from path: the first path segment under PROJECT_DIR is the Gradle subproject.
      REL_PATH="${FILE_PATH#$PROJECT_DIR/}"
      SUBPROJECT=$(echo "$REL_PATH" | cut -d'/' -f1)
      if ./gradlew ":${SUBPROJECT}:tasks" -q 2>/dev/null | grep -q "detektMain"; then
        OUTPUT=$(./gradlew ":${SUBPROJECT}:detektMain" -q 2>&1 | grep -v "^>" | head -30)
      fi
    fi
    ;;
  java)
    # Checkstyle via Maven or Gradle on single file
    if [ -f "pom.xml" ]; then
      OUTPUT=$(mvn checkstyle:check -Dcheckstyle.includes="$(basename "$FILE_PATH")" -q 2>&1 | grep -E "(ERROR|WARNING|violation)" | head -20)
    elif [ -f "build.gradle" ] || [ -f "build.gradle.kts" ]; then
      OUTPUT=$(./gradlew checkstyleMain -q 2>&1 | grep -v "^>" | head -20)
    fi
    ;;
  ts|tsx)
    if [ -f "package.json" ] && command -v npx &>/dev/null; then
      OUTPUT=$(npx eslint --no-eslintrc -c .eslintrc.json "$FILE_PATH" 2>&1 | head -30)
      [ -z "$OUTPUT" ] && OUTPUT=$(npx eslint "$FILE_PATH" 2>&1 | head -30)
    fi
    ;;
  js|jsx)
    if [ -f "package.json" ] && command -v npx &>/dev/null; then
      OUTPUT=$(npx eslint "$FILE_PATH" 2>&1 | head -30)
    fi
    ;;
  cs)
    if command -v dotnet &>/dev/null; then
      OUTPUT=$(dotnet format --verify-no-changes --include "$FILE_PATH" 2>&1 | head -20)
    fi
    ;;
esac

if [ -n "$OUTPUT" ] && echo "$OUTPUT" | grep -qiE "(error|warning|violation|lint)"; then
  inject_context "$OUTPUT" "$FILE_PATH"
fi

exit 0
