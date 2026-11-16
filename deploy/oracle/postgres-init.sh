#!/usr/bin/env bash
set -euo pipefail
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --no-psqlrc --set ON_ERROR_STOP=1 <<'SQL'
\getenv app_password DB_APP_PASSWORD
\getenv migration_password DB_MIGRATION_PASSWORD
SELECT format('CREATE ROLE supplyprint_migration LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE', :'migration_password') \gexec
SELECT format('CREATE ROLE supplyprint_app LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE', :'app_password') \gexec
ALTER DATABASE supplyprint OWNER TO supplyprint_migration;
REVOKE ALL ON DATABASE supplyprint FROM PUBLIC;
GRANT CONNECT ON DATABASE supplyprint TO supplyprint_app;
REVOKE CONNECT ON DATABASE postgres FROM PUBLIC;
ALTER SCHEMA public OWNER TO supplyprint_migration;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO supplyprint_app;
CREATE EXTENSION IF NOT EXISTS vector;
ALTER DEFAULT PRIVILEGES FOR ROLE supplyprint_migration IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO supplyprint_app;
ALTER DEFAULT PRIVILEGES FOR ROLE supplyprint_migration IN SCHEMA public GRANT USAGE,SELECT ON SEQUENCES TO supplyprint_app;
SQL
