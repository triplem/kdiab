#!/usr/bin/env bash
# build.sh — build all kdiab services
#
# Usage:
#   ./build.sh                 # build everything (backends + frontends), skip tests
#   ./build.sh --check         # build + run all tests, detekt, kover
#   ./build.sh --backend-only  # backends only
#   ./build.sh --frontend-only # frontends only
#   ./build.sh --docker        # build all Docker images (service + liquibase images)
#   ./build.sh --no-parallel   # sequential (useful when disk/RAM is tight)
#
# Flags can be combined, e.g.: ./build.sh --check --docker
# Exit code is non-zero if any service fails to build.

set -euo pipefail

SERVICES=(kdiab-measures kdiab-profiles kdiab-treatments kdiab-bff)

# ── Flags ─────────────────────────────────────────────────────────────────────
BUILD_BACKEND=true
BUILD_FRONTEND=true
BUILD_DOCKER=false
GRADLE_TASK=":backend:build"
PARALLEL=true

for arg in "$@"; do
  case "$arg" in
    --backend-only)  BUILD_FRONTEND=false ;;
    --frontend-only) BUILD_BACKEND=false  ;;
    --check)         GRADLE_TASK=":backend:check" ;;
    --docker)        BUILD_DOCKER=true ;;
    --no-parallel)   PARALLEL=false ;;
    --help|-h)
      sed -n '3,13p' "$0" | sed 's/^# *//'
      exit 0
      ;;
    *)
      echo "Unknown option: $arg  (try --help)"
      exit 1
      ;;
  esac
done

# ── Helpers ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'

log()  { echo -e "${CYAN}▶ $*${NC}"; }
ok()   { echo -e "${GREEN}✓ $*${NC}"; }
fail() { echo -e "${RED}✗ $*${NC}"; }

ROOT="$(cd "$(dirname "$0")" && pwd)"
LOGDIR="$ROOT/.build-logs"
mkdir -p "$LOGDIR"

# Runs a build step, streaming output to a log file.
# Prints a single summary line; on failure dumps the tail of the log.
run_step() {
  local label="$1"; shift
  local logfile="$LOGDIR/${label//\//-}.log"

  log "$label"
  if "$@" >"$logfile" 2>&1; then
    ok "$label"
    return 0
  else
    fail "$label — see $logfile"
    tail -30 "$logfile" | sed 's/^/    /'
    return 1
  fi
}

# ── Build backends ─────────────────────────────────────────────────────────────
build_backend() {
  local svc="$1"
  run_step "$svc/backend" \
    bash -c "cd '$ROOT/$svc' && ./gradlew $GRADLE_TASK --no-daemon --console=plain"
}

# ── Build frontends ────────────────────────────────────────────────────────────
build_frontend() {
  local svc="$1"
  run_step "$svc/frontend" \
    bash -c "cd '$ROOT/$svc/frontend' && npm ci --silent && npm run build"
}

# ── Parallel runner ────────────────────────────────────────────────────────────
# Launches all jobs in background, collects exit codes, returns 1 if any failed.
run_parallel() {
  local -a pids=() labels=()
  local job_type="$1"; shift   # "backend" or "frontend"

  for svc in "${SERVICES[@]}"; do
    if [[ "$job_type" == "backend" ]]; then
      build_backend "$svc" &
    else
      build_frontend "$svc" &
    fi
    pids+=($!)
    labels+=("$svc/$job_type")
  done

  local rc=0
  for i in "${!pids[@]}"; do
    wait "${pids[$i]}" || { fail "${labels[$i]} failed"; rc=1; }
  done
  return $rc
}

# ── Build Docker images ────────────────────────────────────────────────────────
# All buildable images in the root docker-compose.yml (excludes pulled images:
# postgres, keycloak, pgadmin, pg-seed).
DOCKER_SERVICES=(
  liquibase-measures liquibase-profiles liquibase-treatments
  measures-backend   measures-frontend
  profiles-backend   profiles-frontend
  treatments-backend treatments-frontend
  bff-backend        bff-frontend
)

build_docker() {
  # Detect available compose tool: docker compose v2, podman compose, or docker-compose v1
  if docker compose version &>/dev/null 2>&1; then
    COMPOSE_CMD="docker compose"
  elif podman compose version &>/dev/null 2>&1; then
    COMPOSE_CMD="podman compose"
  elif command -v docker-compose &>/dev/null; then
    COMPOSE_CMD="docker-compose"
  else
    fail "No compose tool found (tried: docker compose, podman compose, docker-compose)"
    return 1
  fi

  # --parallel is a docker compose flag; podman compose handles concurrency internally
  local parallel_flag=""
  if $PARALLEL && [[ "$COMPOSE_CMD" == "docker compose" ]]; then
    parallel_flag="--parallel"
  fi

  run_step "docker images" \
    bash -c "cd '$ROOT' && $COMPOSE_CMD build $parallel_flag ${DOCKER_SERVICES[*]}"
}

# ── Main ───────────────────────────────────────────────────────────────────────
START=$(date +%s)
FAILED=0

if $BUILD_BACKEND; then
  echo ""
  echo -e "${YELLOW}═══ Backends ($GRADLE_TASK) ═══${NC}"
  if $PARALLEL; then
    run_parallel backend || FAILED=1
  else
    for svc in "${SERVICES[@]}"; do
      build_backend "$svc" || FAILED=1
    done
  fi
fi

if $BUILD_FRONTEND; then
  echo ""
  echo -e "${YELLOW}═══ Frontends ═══${NC}"
  if $PARALLEL; then
    run_parallel frontend || FAILED=1
  else
    for svc in "${SERVICES[@]}"; do
      build_frontend "$svc" || FAILED=1
    done
  fi
fi

if $BUILD_DOCKER; then
  echo ""
  echo -e "${YELLOW}═══ Docker images ═══${NC}"
  build_docker || FAILED=1
fi

# ── Summary ───────────────────────────────────────────────────────────────────
ELAPSED=$(( $(date +%s) - START ))
echo ""
if [[ $FAILED -eq 0 ]]; then
  ok "All builds succeeded in ${ELAPSED}s"
else
  fail "One or more builds failed (${ELAPSED}s) — logs in $LOGDIR/"
  exit 1
fi
