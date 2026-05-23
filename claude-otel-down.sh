#!/bin/bash
# Stops the Claude Code OTEL telemetry stack.
#
# Usage:
#   ./claude-otel-down.sh            # stop containers, keep volumes
#   ./claude-otel-down.sh --volumes  # stop containers and remove volumes

COMPOSE_FILE="docker-compose.claude-otel.yml"
REMOVE_VOLUMES=false

for arg in "$@"; do
  case $arg in
    --volumes|-v) REMOVE_VOLUMES=true ;;
  esac
done

if $REMOVE_VOLUMES; then
  docker compose -f "$COMPOSE_FILE" down -v
else
  docker compose -f "$COMPOSE_FILE" down
fi
