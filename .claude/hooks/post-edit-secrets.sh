#!/usr/bin/env bash
# Hook: PostToolUse:Write|Edit
# Scans every written/edited file for hardcoded secrets and sensitive patterns.
# Injects a warning as additionalContext so Claude fixes the issue immediately.

INPUT=$(cat)

FILE_PATH=$(echo "$INPUT" | python3 -c "
import sys, json
d = json.load(sys.stdin)
inp = d.get('tool_input', {})
# Write uses 'file_path', Edit uses 'file_path'
print(inp.get('file_path', ''))
" 2>/dev/null || echo "")

[ -z "$FILE_PATH" ] && exit 0
[ ! -f "$FILE_PATH" ] && exit 0

# Skip binary files and common non-code files
case "$FILE_PATH" in
  *.png|*.jpg|*.jpeg|*.gif|*.ico|*.woff|*.ttf|*.eot|*.pdf|*.zip|*.jar|*.class)
    exit 0 ;;
esac

# Secret patterns to detect
FINDINGS=""

check_pattern() {
  local LABEL="$1"
  local PATTERN="$2"
  local LINE
  LINE=$(grep -n -iE "$PATTERN" "$FILE_PATH" 2>/dev/null | head -3)
  if [ -n "$LINE" ]; then
    FINDINGS+="  [$LABEL] $LINE\n"
  fi
}

# High-confidence secret patterns
check_pattern "API_KEY"         '(api_key|apikey)\s*[:=]\s*["\x27][A-Za-z0-9_\-]{16,}'
check_pattern "PASSWORD"        '(password|passwd|pwd)\s*[:=]\s*["\x27][^"\x27\$\{]{6,}'
check_pattern "SECRET"          '(secret|secret_key|client_secret)\s*[:=]\s*["\x27][A-Za-z0-9_\-]{8,}'
check_pattern "TOKEN"           '(token|access_token|auth_token)\s*[:=]\s*["\x27][A-Za-z0-9_\-\.]{16,}'
check_pattern "AWS_KEY"         'AKIA[0-9A-Z]{16}'
check_pattern "PRIVATE_KEY"     '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY'
check_pattern "GITHUB_TOKEN"    'gh[pousr]_[A-Za-z0-9_]{36,}'
check_pattern "STRIPE_KEY"      'sk_(live|test)_[A-Za-z0-9]{24,}'
check_pattern "CONNECTION_STR"  '(jdbc|mongodb(\+srv)?|redis|amqp)://[^"\x27\s]*:[^"\x27\s@]{3,}@'

if [ -n "$FINDINGS" ]; then
  MSG="⚠️ POTENTIAL SECRET DETECTED in ${FILE_PATH}:\n${FINDINGS}\nAction required: Remove the hardcoded value and replace with an environment variable or secrets manager reference. Never commit secrets to source control."

  python3 -c "
import json, sys
msg = sys.argv[1]
print(json.dumps({
    'systemMessage': msg,
    'hookSpecificOutput': {
        'hookEventName': 'PostToolUse',
        'additionalContext': msg
    }
}))
" "$MSG"
fi

exit 0
