#!/usr/bin/env bash
# Hook: FileChanged  (matcher: .env|.env.*|*.env)
# Warns when an .env file is modified and checks it is gitignored.

INPUT=$(cat)
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"

FILE_PATH=$(echo "$INPUT" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(d.get('file_path', ''))
" 2>/dev/null || echo "")

CHANGE_TYPE=$(echo "$INPUT" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(d.get('change_type', ''))
" 2>/dev/null || echo "")

[ -z "$FILE_PATH" ] && exit 0

WARNINGS=""

# Check if file is gitignored
cd "$PROJECT_DIR" 2>/dev/null || true
if git check-ignore -q "$FILE_PATH" 2>/dev/null; then
  IS_IGNORED="yes"
else
  IS_IGNORED="no"
fi

if [ "$IS_IGNORED" = "no" ]; then
  WARNINGS+="⚠️ SECURITY: ${FILE_PATH} is NOT in .gitignore. Secrets in this file will be committed to git history. Add it to .gitignore immediately.\n"
fi

# If file was created, remind about .env.example
if [ "$CHANGE_TYPE" = "create" ]; then
  EXAMPLE_FILE="${FILE_PATH}.example"
  if [ ! -f "$EXAMPLE_FILE" ]; then
    WARNINGS+="ℹ️ Consider creating ${FILE_PATH}.example with placeholder values so teammates know which variables are needed.\n"
  fi
fi

# Scan for obviously committed secrets (if file is readable)
if [ -f "$FILE_PATH" ]; then
  SECRET_LINES=$(grep -nE '(AKIA[0-9A-Z]{16}|-----BEGIN.*PRIVATE KEY|password\s*=\s*[^\$\{][^\s]{6,})' "$FILE_PATH" 2>/dev/null | head -5)
  if [ -n "$SECRET_LINES" ]; then
    WARNINGS+="⚠️ SECURITY: Potential hardcoded secrets found in ${FILE_PATH}:\n${SECRET_LINES}\nUse placeholder values like \${VAR_NAME} or descriptive comments instead of real values.\n"
  fi
fi

if [ -n "$WARNINGS" ]; then
  python3 -c "
import json, sys
msg = sys.argv[1]
print(json.dumps({
    'systemMessage': msg,
    'hookSpecificOutput': {
        'hookEventName': 'FileChanged',
        'additionalContext': msg
    }
}))
" "$WARNINGS"
fi

exit 0
