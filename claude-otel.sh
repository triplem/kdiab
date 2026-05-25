#!/bin/bash
# Starts the Claude Code OTEL observability stack, then launches Claude with
# OpenTelemetry telemetry enabled. Traces, metrics, and logs from Claude are
# forwarded to the local collector and viewable in Jaeger and Grafana.
#
# Usage:
#   ./claude-otel.sh              # start OTEL stack + open Claude
#   ./claude-otel.sh --resume     # pass any claude argument through
#   ./claude-otel.sh -p 'prompt'  # non-interactive mode
#
# The OTEL stack is started in detached mode and keeps running after Claude
# exits. Stop it manually when no longer needed:
#   podman compose -f docker-compose.claude-otel.yml down

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.claude-otel.yml"
MAX_WAIT_SECS=60

# ---------------------------------------------------------------------------
# Detect container runtime
# ---------------------------------------------------------------------------
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    COMPOSE_CMD="docker compose"
elif command -v podman >/dev/null 2>&1 && podman compose version >/dev/null 2>&1; then
    COMPOSE_CMD="podman compose"
elif command -v podman-compose >/dev/null 2>&1; then
    COMPOSE_CMD="podman-compose"
else
    echo "ERROR: neither 'docker compose' nor 'podman compose' is available." >&2
    echo "Install Docker or Podman with the compose plugin to use this script." >&2
    exit 1
fi

# ---------------------------------------------------------------------------
# Start OTEL stack (idempotent — safe if already running)
# ---------------------------------------------------------------------------
echo "Starting Claude Code OTEL stack (${COMPOSE_CMD})..."
$COMPOSE_CMD -f "$COMPOSE_FILE" up -d

# ---------------------------------------------------------------------------
# Wait for the OTEL collector to accept connections on port 4317 (gRPC).
# The collector image is distroless so we cannot use a container-side healthcheck
# (no wget/curl/sh). Instead we test TCP reachability from the host — port 4317
# is already exposed and is the endpoint Claude Code will use.
# ---------------------------------------------------------------------------
echo "Waiting for OTEL collector to accept connections on port 4317..."
elapsed=0
until (echo >/dev/tcp/localhost/4317) 2>/dev/null; do
    if [ $elapsed -ge $MAX_WAIT_SECS ]; then
        echo "ERROR: OTEL collector did not become reachable within ${MAX_WAIT_SECS}s" >&2
        echo "--- collector logs ---" >&2
        $COMPOSE_CMD -f "$COMPOSE_FILE" logs claude-otel-collector 2>&1 | tail -20 >&2
        exit 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
done
echo "OTEL collector is ready (${elapsed}s)."

# ---------------------------------------------------------------------------
# Print service URLs
# ---------------------------------------------------------------------------
cat <<'EOF'

  Claude Code OTEL stack:
    Jaeger UI    http://localhost:16686
    Prometheus   http://localhost:9090
    Grafana      http://localhost:3000   (admin / admin)

  OTEL signals enabled:  traces · metrics · logs
  Collector endpoint:    localhost:4317 (gRPC)

EOF

# ---------------------------------------------------------------------------
# Launch Claude with OTEL env vars
# ---------------------------------------------------------------------------
CLAUDE_VERSION="$(claude --version 2>/dev/null | awk '{print $1}' || echo unknown)"

exec env \
    OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:4317" \
    OTEL_EXPORTER_OTLP_PROTOCOL="grpc" \
    OTEL_TRACES_EXPORTER="otlp" \
    OTEL_METRICS_EXPORTER="otlp" \
    OTEL_LOGS_EXPORTER="otlp" \
    OTEL_SERVICE_NAME="claude-code" \
    OTEL_RESOURCE_ATTRIBUTES="deployment.environment=local,service.version=${CLAUDE_VERSION}" \
    claude "$@"
