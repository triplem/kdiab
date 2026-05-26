#!/bin/bash
# Usage: podman-up.sh [--pgadmin|-p] [--optional|-o] [podman compose up options]
#
#   Starts the full stack (app + OTEL observability) using .env.example as the
#   default env file. Override any value by setting it in the shell environment
#   before running.
#
# Options:
#   --pgadmin, -p    Also start pgAdmin (opt-in; disabled by default)
#   --optional, -o   Also start optional services: kdiab-nightscout and kdiab-carbs
#                    food database backend, and enables the Food Database tab in
#                    the kdiab-ui build (requires --build on first use).
#
# Examples:
#   ./podman-up.sh --build
#   ./podman-up.sh --build -d
#   ./podman-up.sh --pgadmin --build
#   ./podman-up.sh --optional --build
#   POSTGRES_PASSWORD=mypassword ./podman-up.sh --build

# Docker image format is required for HEALTHCHECK support; OCI (Podman default) silently ignores it.
export BUILDAH_FORMAT=docker

COMPOSE_FILES=(-f docker-compose.yml -f docker-compose.otel.yml)
COMPOSE_PROFILES=()
PASS_ARGS=()

for arg in "$@"; do
    case "$arg" in
        --pgadmin|-p)
            COMPOSE_FILES+=(-f docker-compose.pgadmin.yml)
            ;;
        --optional|-o)
            COMPOSE_PROFILES+=(optional)
            export VITE_FOOD_DATABASE_ENABLED=true
            ;;
        *)
            PASS_ARGS+=("$arg")
            ;;
    esac
done

if [ ${#COMPOSE_PROFILES[@]} -gt 0 ]; then
    export COMPOSE_PROFILES=$(IFS=,; echo "${COMPOSE_PROFILES[*]}")
fi

# Forward telemetry from all backends to the otel-collector defined in docker-compose.otel.yml.
# Shell env vars take precedence over --env-file values, so these override the
# OTEL_TRACES_EXPORTER:-none defaults in docker-compose.yml.
# gRPC (port 4317) avoids the okhttp HTTP/2 ConnectionShutdownException that occurs on port 4318.
export OTEL_TRACES_EXPORTER=otlp
export OTEL_METRICS_EXPORTER=otlp
export OTEL_LOGS_EXPORTER=otlp
export OTEL_EXPORTER_OTLP_PROTOCOL=grpc
export OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317

exec podman compose --env-file .env.example "${COMPOSE_FILES[@]}" up "${PASS_ARGS[@]}"
