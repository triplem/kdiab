#!/usr/bin/env bash
# Hook: PostToolUse:Write|Edit
# Runs Spectral linting after any write to an OpenAPI spec file.
# Injects violations as additionalContext so Claude fixes them immediately.

INPUT=$(cat)
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"

FILE_PATH=$(echo "$INPUT" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(d.get('tool_input', {}).get('file_path', ''))
" 2>/dev/null || echo "")

[ -z "$FILE_PATH" ] && exit 0

# Only run on openapi files
BASENAME=$(basename "$FILE_PATH")
case "$BASENAME" in
  openapi.yaml|openapi.yml|openapi.json) ;;
  *) exit 0 ;;
esac

[ ! -f "$FILE_PATH" ] && exit 0

cd "$PROJECT_DIR" 2>/dev/null || true

OUTPUT=""

if command -v spectral &>/dev/null; then
  OUTPUT=$(spectral lint "$FILE_PATH" 2>&1 | head -40)
elif command -v npx &>/dev/null; then
  OUTPUT=$(npx --yes @stoplight/spectral-cli lint "$FILE_PATH" 2>&1 | head -40)
fi

if [ -z "$OUTPUT" ]; then
  exit 0
fi

# Check for errors/warnings
if echo "$OUTPUT" | grep -qiE "(error|warning|✖|✗)"; then
  python3 -c "
import json, sys
output = sys.argv[1]
filepath = sys.argv[2]
print(json.dumps({
    'hookSpecificOutput': {
        'hookEventName': 'PostToolUse',
        'additionalContext': 'Spectral OpenAPI lint results for ' + filepath + ':\n' + output + '\n\nFix all errors before proceeding. Warnings should also be addressed unless there is a documented reason to suppress them.'
    }
}))
" "$OUTPUT" "$FILE_PATH"
fi

exit 0
