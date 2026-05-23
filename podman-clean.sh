#!/bin/bash
# Usage: podman-clean.sh [-v|--volumes] [-o|--observability]
#   -v, --volumes       also remove named volumes (postgres_data, grafana_data, prometheus_data)
#   -o, --observability include the otel compose override during teardown

REMOVE_VOLUMES=false
COMPOSE_FILES="-f docker-compose.yml"

for arg in "$@"; do
  case $arg in
    -v|--volumes) REMOVE_VOLUMES=true ;;
    -o|--observability) COMPOSE_FILES="$COMPOSE_FILES -f docker-compose.otel.yml" ;;
  esac
done

if $REMOVE_VOLUMES; then
  # shellcheck disable=SC2086
  podman compose --env-file .env.example $COMPOSE_FILES down -v
else
  # shellcheck disable=SC2086
  podman compose --env-file .env.example $COMPOSE_FILES down
fi

# Remove locally-built images only (localhost/ prefix).
# Never touch images from external registries (quay.io, docker.io).
podman images --format '{{.ID}} {{.Repository}}' \
  | awk '$2 ~ /^localhost\// {print $1}' \
  | xargs -r podman rmi -f

# Remove dangling images (untagged build leftovers).
podman images --filter dangling=true -q | xargs -r podman rmi -f
