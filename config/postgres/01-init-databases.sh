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

# Create a dedicated least-privilege role for each service database.
# Each role can only CONNECT to its own database and perform DML (no DDL).
# ALTER DEFAULT PRIVILEGES ensures tables created by future Liquibase migrations
# are automatically accessible to the service role without manual GRANT steps.

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "kdiab-measures" <<-EOSQL
    CREATE USER kdiab_measures WITH PASSWORD '${MEASURES_DB_PASSWORD}';
    GRANT CONNECT ON DATABASE "kdiab-measures" TO kdiab_measures;
    GRANT USAGE ON SCHEMA public TO kdiab_measures;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO kdiab_measures;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO kdiab_measures;
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "kdiab-profiles" <<-EOSQL
    CREATE USER kdiab_profiles WITH PASSWORD '${PROFILES_DB_PASSWORD}';
    GRANT CONNECT ON DATABASE "kdiab-profiles" TO kdiab_profiles;
    GRANT USAGE ON SCHEMA public TO kdiab_profiles;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO kdiab_profiles;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO kdiab_profiles;
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "kdiab-treatments" <<-EOSQL
    CREATE USER kdiab_treatments WITH PASSWORD '${TREATMENTS_DB_PASSWORD}';
    GRANT CONNECT ON DATABASE "kdiab-treatments" TO kdiab_treatments;
    GRANT USAGE ON SCHEMA public TO kdiab_treatments;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO kdiab_treatments;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO kdiab_treatments;
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "kdiab-carbs" <<-EOSQL
    CREATE USER kdiab_carbs WITH PASSWORD '${CARBS_DB_PASSWORD}';
    GRANT CONNECT ON DATABASE "kdiab-carbs" TO kdiab_carbs;
    GRANT USAGE ON SCHEMA public TO kdiab_carbs;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO kdiab_carbs;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO kdiab_carbs;
EOSQL
