#!/bin/bash
# Usage: podman-up.sh [--pgadmin|-p] [podman compose up options]
#
#   Starts the full stack (app + OTEL observability) using .env.example as the
#   default env file. Override any value by setting it in the shell environment
#   before running.
#
# Options:
#   --pgadmin, -p   Also start pgAdmin (opt-in; disabled by default)
#
# Examples:
#   ./podman-up.sh --build
#   ./podman-up.sh --build -d
#   ./podman-up.sh --pgadmin --build
#   POSTGRES_PASSWORD=mypassword ./podman-up.sh --build

# Docker image format is required for HEALTHCHECK support; OCI (Podman default) silently ignores it.
export BUILDAH_FORMAT=docker

COMPOSE_FILES=(-f docker-compose.yml -f docker-compose.otel.yml)
PASS_ARGS=()

for arg in "$@"; do
    case "$arg" in
        --pgadmin|-p)
            COMPOSE_FILES+=(-f docker-compose.pgadmin.yml)
            ;;
        *)
            PASS_ARGS+=("$arg")
            ;;
    esac
done

# Forward telemetry from all backends to the otel-collector defined in docker-compose.otel.yml.
# Shell env vars take precedence over --env-file values, so these override the
# OTEL_TRACES_EXPORTER:-none defaults in docker-compose.yml.
export OTEL_TRACES_EXPORTER=otlp
export OTEL_METRICS_EXPORTER=otlp
export OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318

exec podman compose --env-file .env.example "${COMPOSE_FILES[@]}" up "${PASS_ARGS[@]}"
