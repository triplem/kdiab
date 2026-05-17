#!/usr/bin/env bash
# Hook: Stop
# 1. Writes session_stop entry to the per-session temp file (~/.claude/kdiab-sessions/).
# 2. Switches to main, appends the temp file to audit/agent-log.jsonl, commits.
# 3. Returns to the original branch. No push — user pushes when ready.
# Runs async so it never blocks the session shutdown.

INPUT=$(cat)
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"

SESSION_ID=$(echo "$INPUT" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(d.get('session_id', 'unknown'))
" 2>/dev/null || echo "unknown")

SESSION_DIR="${HOME}/.claude/kdiab-sessions"
TEMP_FILE="${SESSION_DIR}/${SESSION_ID}.jsonl"
AUDIT_FILE="${PROJECT_DIR}/audit/agent-log.jsonl"

TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || \
  python3 -c "from datetime import datetime,timezone; print(datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'))")

# Count actions logged for this session from the temp file
ACTIONS_COUNT=0
if [ -f "$TEMP_FILE" ]; then
  ACTIONS_COUNT=$(wc -l < "$TEMP_FILE" 2>/dev/null || echo 0)
fi

# Summarise git state at stop time
BRANCH=$(git -C "$PROJECT_DIR" rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")
LAST_COMMIT=$(git -C "$PROJECT_DIR" log -1 --format="%h %s" 2>/dev/null || echo "")

# Append session_stop to the temp file
ENTRY=$(python3 -c "
import json, sys
print(json.dumps({
    'ts': sys.argv[1],
    'agent': 'System',
    'session_id': sys.argv[2],
    'action': 'session_stop',
    'branch': sys.argv[3],
    'last_commit': sys.argv[4],
    'actions_this_session': int(sys.argv[5])
}, ensure_ascii=False))
" "$TIMESTAMP" "$SESSION_ID" "$BRANCH" "$LAST_COMMIT" "$ACTIONS_COUNT" 2>/dev/null)

if [ -n "$ENTRY" ]; then
    mkdir -p "$SESSION_DIR"
    echo "$ENTRY" >> "$TEMP_FILE"
fi

# Nothing to commit if the session temp file is empty or missing
[ -s "$TEMP_FILE" ] || exit 0

# ── Consolidate into the in-repo audit log on main ──────────────────────────

ORIGINAL_BRANCH="$BRANCH"
STASHED=false

# Stash tracked changes so we can switch branches cleanly
if [ -n "$(git -C "$PROJECT_DIR" status --porcelain 2>/dev/null)" ]; then
    git -C "$PROJECT_DIR" stash push -m "audit-stop-${SESSION_ID:0:8}" 2>/dev/null \
        && STASHED=true
fi

# Switch to main (no-op if already there)
if [ "$ORIGINAL_BRANCH" != "main" ]; then
    git -C "$PROJECT_DIR" checkout main 2>/dev/null || {
        # Can't switch — restore stash and exit gracefully
        $STASHED && git -C "$PROJECT_DIR" stash pop 2>/dev/null
        exit 0
    }
fi

# Pull latest main (ff-only; skip on conflict)
git -C "$PROJECT_DIR" pull --ff-only origin main 2>/dev/null || true

# Append session entries to the in-repo audit log
mkdir -p "$(dirname "$AUDIT_FILE")"
cat "$TEMP_FILE" >> "$AUDIT_FILE"

# Commit only if something changed
if ! git -C "$PROJECT_DIR" diff --quiet -- audit/agent-log.jsonl 2>/dev/null; then
    git -C "$PROJECT_DIR" add audit/agent-log.jsonl
    git -C "$PROJECT_DIR" commit -m "chore(audit): log session ${SESSION_ID:0:8}" 2>/dev/null || true
fi

# Truncate temp file so entries are not re-appended on the next stop hook run
> "$TEMP_FILE"

# Return to the original branch
if [ "$ORIGINAL_BRANCH" != "main" ]; then
    git -C "$PROJECT_DIR" checkout "$ORIGINAL_BRANCH" 2>/dev/null || true
fi

# Restore stashed changes
$STASHED && git -C "$PROJECT_DIR" stash pop 2>/dev/null || true

exit 0
