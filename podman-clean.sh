#!/bin/bash
# Usage: podman-clean.sh [-v|--volumes]
#   -v, --volumes   also remove named volumes (postgres_data)

REMOVE_VOLUMES=false

for arg in "$@"; do
  case $arg in
    -v|--volumes) REMOVE_VOLUMES=true ;;
  esac
done

if $REMOVE_VOLUMES; then
  podman compose --env-file .env.example down -v
else
  podman compose --env-file .env.example down
fi

# Remove locally-built images only (localhost/ prefix).
# Never touch images from external registries (quay.io, docker.io).
podman images --format '{{.ID}} {{.Repository}}' \
  | awk '$2 ~ /^localhost\// {print $1}' \
  | xargs -r podman rmi -f

# Remove dangling images (untagged build leftovers).
podman images --filter dangling=true -q | xargs -r podman rmi -f
