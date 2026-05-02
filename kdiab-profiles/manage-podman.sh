#!/bin/bash

# Manage Podman environments for kdiab-profiles

show_help() {
    echo "Usage: ./manage-podman.sh [COMMAND]"
    echo ""
    echo "Commands:"
    echo "  build    - Build all podman images using podman-compose"
    echo "  start    - Start the podman-compose environment (detached mode)"
    echo "  stop     - Stop the podman-compose environment"
    echo "  cleanup  - Stop the environment and clean up all generated podman images and volumes"
    echo "  help     - Show this help message"
}

if [ $# -eq 0 ]; then
    show_help
    exit 1
fi

case "$1" in
    build)
        echo "=> Building images with podman-compose (backend and frontend are built inside Docker)..."
        podman-compose build
        ;;
    start)
        echo "=> Starting podman-compose..."
        podman-compose up -d
        ;;
    stop)
        echo "=> Stopping podman-compose..."
        podman-compose down
        ;;
    cleanup)
        echo "=> Stopping environment and removing volumes..."
        podman-compose down -v

        echo "=> Cleaning up generated podman images for kdiab-profiles..."
        # Find images tagged with 'kdiab-profiles' or 'localhost/kdiab-profiles' and remove them
        podman images | grep "kdiab-profiles" | awk '{print $3}' | xargs -r podman rmi -f
        podman image prune -f
        echo "=> Cleanup complete."
        ;;
    *)
        show_help
        exit 1
        ;;
esac
