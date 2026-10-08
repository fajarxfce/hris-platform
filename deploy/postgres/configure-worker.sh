#!/bin/sh
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    --set=worker_password="$HRIS_WORKER_DATABASE_PASSWORD" <<'SQL'
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='hris_worker_capability') THEN
        CREATE ROLE hris_worker_capability NOLOGIN NOBYPASSRLS;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='hris_worker') THEN
        CREATE ROLE hris_worker LOGIN INHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;
END $$;
ALTER ROLE hris_worker PASSWORD :'worker_password';
GRANT hris_worker_capability TO hris_worker;
GRANT CONNECT ON DATABASE hris TO hris_worker;
GRANT USAGE ON SCHEMA public TO hris_worker;
GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO hris_worker;
GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO hris_worker;
ALTER DEFAULT PRIVILEGES FOR ROLE hris_migrator IN SCHEMA public
    GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO hris_worker;
ALTER DEFAULT PRIVILEGES FOR ROLE hris_migrator IN SCHEMA public
    GRANT USAGE,SELECT ON SEQUENCES TO hris_worker;
SQL
