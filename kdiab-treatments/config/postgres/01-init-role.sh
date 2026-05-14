#!/bin/bash
set -e

# Create a dedicated least-privilege role for kdiab-treatments.
# The service role has DML-only access (no DDL).
# ALTER DEFAULT PRIVILEGES ensures tables created by future Liquibase migrations
# are automatically accessible to the service role without manual GRANT steps.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "${POSTGRES_DB:-kdiab-treatments}" <<-EOSQL
    CREATE USER kdiab_treatments WITH PASSWORD '${TREATMENTS_DB_PASSWORD}';
    GRANT CONNECT ON DATABASE "${POSTGRES_DB:-kdiab-treatments}" TO kdiab_treatments;
    GRANT USAGE ON SCHEMA public TO kdiab_treatments;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO kdiab_treatments;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO kdiab_treatments;
EOSQL
