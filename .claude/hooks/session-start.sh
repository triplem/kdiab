#!/usr/bin/env bash
# Hook: SessionStart
# Injects project context into Claude's system prompt at the start of every session.
# Detects tech stack, git state, and SDLC phase so Claude doesn't need to ask.

INPUT=$(cat)  # consume stdin

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"
cd "$PROJECT_DIR" 2>/dev/null || true

# --- Tech stack detection ---
STACK=""
BUILD_TOOLS=""

if [ -f "build.gradle.kts" ] || [ -f "settings.gradle.kts" ]; then
  STACK="Kotlin/JVM"
  BUILD_TOOLS="Gradle (Kotlin DSL)"
elif [ -f "build.gradle" ] || [ -f "settings.gradle" ]; then
  if find . -name "*.kt" -not -path "*/build/*" 2>/dev/null | grep -q .; then
    STACK="Kotlin/JVM"
  else
    STACK="Java/JVM"
  fi
  BUILD_TOOLS="Gradle (Groovy DSL)"
elif [ -f "pom.xml" ]; then
  if find . -name "*.kt" -not -path "*/target/*" 2>/dev/null | grep -q .; then
    STACK="Kotlin/JVM"
  else
    STACK="Java/JVM"
  fi
  BUILD_TOOLS="Maven"
elif [ -f "package.json" ]; then
  if grep -q '"react"' package.json 2>/dev/null; then
    STACK="React/TypeScript"
  else
    STACK="Node.js/TypeScript"
  fi
  BUILD_TOOLS="npm"
elif find . -name "*.csproj" -not -path "*/bin/*" -not -path "*/obj/*" 2>/dev/null | grep -q .; then
  STACK=".NET/C#"
  BUILD_TOOLS="dotnet CLI"
elif find . -name "*.sln" 2>/dev/null | grep -q .; then
  STACK=".NET/C#"
  BUILD_TOOLS="dotnet CLI"
fi

# --- Git state ---
BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")
DIRTY=""
if [ -n "$(git status --porcelain 2>/dev/null)" ]; then
  DIRTY=" (uncommitted changes)"
fi

# Detect story ID from branch name (feature/42-slug → #42)
STORY_ID=""
if echo "$BRANCH" | grep -qE '^(feature|fix|bug|chore|refactor|docs)/[0-9]+'; then
  STORY_ID=$(echo "$BRANCH" | sed 's|^[^/]*/\([0-9]*\)-.*|\1|')
fi

# --- SDLC phase detection ---
PHASE="Unknown"
if [ -f "docs/requirements.md" ]; then
  if grep -q "APPROVED" "docs/requirements.md" 2>/dev/null; then
    PHASE="Requirements approved"
  else
    PHASE="Requirements draft (not yet approved)"
  fi
fi
[ -f "docs/epics-index.md" ]   && PHASE="Epic generation complete"
[ -d "docs/adr" ] && ls docs/adr/*.md 2>/dev/null | grep -q . && PHASE="ADRs present"
[ -n "$STORY_ID" ] && PHASE="Implementation (story #${STORY_ID})"

# --- Last audit entry (from most recent session file in ~/.claude/kdiab-sessions/) ---
LAST_ACTION=""
_SESS_DIR="${HOME}/.claude/kdiab-sessions"
_LAST_FILE=$(ls -t "${_SESS_DIR}/"*.jsonl 2>/dev/null | head -1)
if [ -n "$_LAST_FILE" ] && [ -f "$_LAST_FILE" ]; then
  LAST_ACTION=$(tail -1 "$_LAST_FILE" | \
    python3 -c "import sys,json; d=json.load(sys.stdin); print(f\"{d.get('agent','?')}: {d.get('action','?')}\")" 2>/dev/null || true)
fi

# --- Build context string ---
CONTEXT="## Session Context\n"
[ -n "$STACK" ]       && CONTEXT+="- Tech stack: ${STACK} (${BUILD_TOOLS})\n"
CONTEXT+="- Branch: ${BRANCH}${DIRTY}\n"
[ -n "$STORY_ID" ]    && CONTEXT+="- Active story: #${STORY_ID} (from branch name)\n"
CONTEXT+="- SDLC phase: ${PHASE}\n"
[ -n "$LAST_ACTION" ] && CONTEXT+="- Last agent action: ${LAST_ACTION}\n"

# Reminder about workflow rules
CONTEXT+="\nApply all rules from .claude/rules/ automatically. "
CONTEXT+="Use the retry loop (Ralph Principle) before escalating to human. "
[ -n "$STORY_ID" ] && CONTEXT+="The current story is #${STORY_ID} — load its details from the issue tracker before starting work."

# --- Write session_start audit entry ---
SESSION_ID=$(echo "$INPUT" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(d.get('session_id', 'unknown'))
" 2>/dev/null || echo "unknown")

# Write to per-session temp file outside the repo.
SESSION_DIR="${HOME}/.claude/kdiab-sessions"
AUDIT_FILE="${SESSION_DIR}/${SESSION_ID}.jsonl"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || \
  python3 -c "from datetime import datetime,timezone; print(datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'))")

AUDIT_ENTRY=$(python3 -c "
import json, sys
print(json.dumps({
    'ts': sys.argv[1],
    'agent': 'System',
    'session_id': sys.argv[2],
    'action': 'session_start',
    'branch': sys.argv[3],
    'story_id': sys.argv[4],
    'phase': sys.argv[5]
}, ensure_ascii=False))
" "$TIMESTAMP" "$SESSION_ID" "$BRANCH" "${STORY_ID:-}" "${PHASE:-}" 2>/dev/null)

if [ -n "$AUDIT_ENTRY" ]; then
    mkdir -p "$SESSION_DIR"
    echo "$AUDIT_ENTRY" >> "$AUDIT_FILE"
fi

python3 -c "
import json, sys
context = sys.argv[1]
print(json.dumps({
    'hookSpecificOutput': {
        'hookEventName': 'SessionStart',
        'additionalContext': context
    }
}))
" "$CONTEXT" 2>/dev/null || echo '{}'

exit 0
