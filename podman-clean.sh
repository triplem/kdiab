#!/bin/bash
# Usage: podman-clean.sh [-v|--volumes]
#   -v, --volumes   also remove named volumes (postgres_data)
#
# Only tears down the kdiab application stack. The Claude Code OTEL stack
# (docker-compose.claude-otel.yml) is intentionally excluded — manage it
# separately with: podman compose -f docker-compose.claude-otel.yml down

REMOVE_VOLUMES=false

for arg in "$@"; do
  case $arg in
    -v|--volumes) REMOVE_VOLUMES=true ;;
  esac
done

# Scope down to the kdiab app stack only. Without explicit -f flags,
# podman-compose stops ALL containers sharing the project name (derived from
# the working directory), which includes the Claude OTEL containers.
KDIAB_FILES=(-f docker-compose.yml -f docker-compose.otel.yml)

if $REMOVE_VOLUMES; then
  podman compose --env-file .env.example "${KDIAB_FILES[@]}" down -v
else
  podman compose --env-file .env.example "${KDIAB_FILES[@]}" down
fi

# Remove locally-built images only (localhost/ prefix).
# Never touch images from external registries (quay.io, docker.io).
podman images --format '{{.ID}} {{.Repository}}' \
  | awk '$2 ~ /^localhost\// {print $1}' \
  | xargs -r podman rmi -f

# Remove dangling images (untagged build leftovers).
podman images --filter dangling=true -q | xargs -r podman rmi -f
