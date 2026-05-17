#!/usr/bin/env bash
# Hook: PostToolUse:Bash|Write|Edit
# Appends one audit entry per significant tool use to ~/.claude/kdiab-sessions/<session_id>.jsonl.
# Runs async so it never blocks Claude.

INPUT=$(cat)
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"

SESSION_ID=$(echo "$INPUT" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(d.get('session_id', 'unknown'))
" 2>/dev/null || echo "unknown")

SESSION_DIR="${HOME}/.claude/kdiab-sessions"
AUDIT_FILE="${SESSION_DIR}/${SESSION_ID}.jsonl"

TOOL_NAME=$(echo "$INPUT" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(d.get('tool_name', 'unknown'))
" 2>/dev/null || echo "unknown")

# Extract meaningful detail per tool type
DETAIL=$(echo "$INPUT" | python3 -c "
import sys, json
d = json.load(sys.stdin)
inp = d.get('tool_input', {})
tool = d.get('tool_name', '')
if tool == 'Bash':
    cmd = inp.get('command', '')
    # Only log meaningful commands — skip pure read-only ones
    skip = ['cat ', 'head ', 'tail ', 'echo ', 'ls ', 'wc ', 'grep -', 'find .', 'sha256sum', 'python3 -c']
    if any(cmd.strip().startswith(s) for s in skip):
        print('__SKIP__')
    else:
        print(cmd[:300])
elif tool in ('Write', 'Edit'):
    print(inp.get('file_path', ''))
else:
    print('')
" 2>/dev/null || echo "")

# Skip noisy low-value commands
[ "$DETAIL" = "__SKIP__" ] && exit 0
[ -z "$DETAIL" ] && exit 0

ACTION=$(echo "$TOOL_NAME" | tr '[:upper:]' '[:lower:]')
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || \
  python3 -c "from datetime import datetime,timezone; print(datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'))")

ENTRY=$(python3 -c "
import json, sys
print(json.dumps({
    'ts': sys.argv[1],
    'agent': 'Claude',
    'session_id': sys.argv[2],
    'action': sys.argv[3],
    'detail': sys.argv[4]
}, ensure_ascii=False))
" "$TIMESTAMP" "$SESSION_ID" "$ACTION" "$DETAIL" 2>/dev/null)

if [ -n "$ENTRY" ] && [ -n "$AUDIT_FILE" ]; then
    mkdir -p "$(dirname "$AUDIT_FILE")"
    echo "$ENTRY" >> "$AUDIT_FILE"
fi

exit 0
