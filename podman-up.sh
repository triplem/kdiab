#!/bin/bash
# Usage: podman-up.sh [OPTIONS] [podman compose up options]
#   Starts the full stack using .env.example as the default env file.
#   Override any value by setting it in the shell environment before running.
#
# Options:
#   -o, --observability   Include the otel compose override (collector, Jaeger)
#                         and set OTEL_*_EXPORTER=otlp automatically.
#
# Examples:
#   ./podman-up.sh --build
#   ./podman-up.sh --observability --build
#   ./podman-up.sh -o --build -d
#   POSTGRES_PASSWORD=mypassword ./podman-up.sh --build

# Docker image format is required for HEALTHCHECK support; OCI (Podman default) silently ignores it.
export BUILDAH_FORMAT=docker

COMPOSE_FILES="-f docker-compose.yml"
PASSTHROUGH_ARGS=()

for arg in "$@"; do
  case $arg in
    -o|--observability)
      COMPOSE_FILES="$COMPOSE_FILES -f docker-compose.otel.yml"
      export OTEL_TRACES_EXPORTER=otlp
      export OTEL_METRICS_EXPORTER=otlp
      export OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318
      ;;
    *)
      PASSTHROUGH_ARGS+=("$arg")
      ;;
  esac
done

# shellcheck disable=SC2086
exec podman compose --env-file .env.example $COMPOSE_FILES up "${PASSTHROUGH_ARGS[@]}"
