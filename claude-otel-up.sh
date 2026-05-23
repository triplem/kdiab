#!/bin/bash
# Starts the Claude Code OTEL telemetry stack.
# Separate from the kdiab observability stack — no shared ports or networks.
#
# Usage: ./claude-otel-up.sh
#
# After the stack is healthy, Jaeger is at http://localhost:16696,
# Prometheus at http://localhost:9095, and Grafana at http://localhost:3015.

set -e

COMPOSE_FILE="docker-compose.claude-otel.yml"

echo "Starting Claude Code OTEL stack..."
docker compose -f "$COMPOSE_FILE" up -d

echo "Waiting for services to become healthy..."

TIMEOUT=60
ELAPSED=0
INTERVAL=3

while [ "$ELAPSED" -lt "$TIMEOUT" ]; do
  # Count containers that are not yet healthy (running but health != healthy)
  UNHEALTHY=$(docker compose -f "$COMPOSE_FILE" ps --format json 2>/dev/null \
    | grep -c '"Health":"starting"' 2>/dev/null || echo 0)

  if [ "$UNHEALTHY" -eq 0 ]; then
    break
  fi

  sleep "$INTERVAL"
  ELAPSED=$((ELAPSED + INTERVAL))
done

echo ""
echo "Claude Code OTEL stack is up:"
echo "  Jaeger UI    http://localhost:16696"
echo "  Prometheus   http://localhost:9095"
echo "  Grafana      http://localhost:3015  (admin / admin)"
echo "  OTLP HTTP    http://localhost:4328"
echo ""
echo "Set the following env var to send Claude Code telemetry to this stack:"
echo "  export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4328"
