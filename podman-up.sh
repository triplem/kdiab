#!/bin/bash
# Usage: podman-up.sh [podman compose up options]
#   Starts the full stack using .env.example as the default env file.
#   Override any value by setting it in the shell environment before running.
#
# Examples:
#   ./podman-up.sh --build
#   ./podman-up.sh --build -d
#   POSTGRES_PASSWORD=mypassword ./podman-up.sh --build

exec podman compose --env-file .env.example up "$@"
