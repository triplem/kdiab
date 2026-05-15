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
  podman compose down -v
else
  podman compose down
fi

podman rmi -f $(podman images | grep none | tr -s ' ' | cut -d ' ' -f 3)
podman rmi -f $(podman images | grep localhost | tr -s ' ' | cut -d ' ' -f 3)
