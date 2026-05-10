#!/bin/bash
set -e

# Create a database for each kdiab service.
# The default postgres user and password come from the postgres service environment.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE "kdiab-measures";
    CREATE DATABASE "kdiab-profiles";
    CREATE DATABASE "kdiab-treatments";
    CREATE DATABASE "kdiab-carbs";
EOSQL
