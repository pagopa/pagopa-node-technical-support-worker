-- =====================================================================================
-- Observability datasource: schema and application role bootstrap (PostgreSQL)
-- =====================================================================================
-- Run ONCE by a superuser/DBA against the target instance, BEFORE the Liquibase changelog.
-- It creates the dedicated schema and the least-privilege login role used by the second
-- datasource `quarkus.datasource.observability` (env OBSERVABILITY_DATASOURCE_*).
-- Idempotent: safe to re-run. Replace 'CHANGE_ME' with the real secret (or set it afterwards).

-- 1) Application login role. CREATE ROLE has no IF NOT EXISTS, so guard it explicitly.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'observability') THEN
    CREATE ROLE observability LOGIN PASSWORD 'password';
  END IF;
END
$$;

-- 2) Allow the role to connect to the current database. Many instances revoke the
--    default CONNECT grant from PUBLIC, which surfaces as
--    "permission denied for database <db>" at login. Granted dynamically so this
--    script works against whichever database it is run in (e.g. `nodo` in local dev).
DO $$
BEGIN
  EXECUTE format('GRANT CONNECT, TEMPORARY ON DATABASE %I TO observability', current_database());
END
$$;

-- 3) Dedicated schema owned by the application role.
CREATE SCHEMA IF NOT EXISTS observability AUTHORIZATION observability;

-- 4) Privileges on the schema and on objects created later (e.g. by Liquibase).
GRANT USAGE, CREATE ON SCHEMA observability TO observability;
GRANT ALL ON ALL TABLES IN SCHEMA observability TO observability;
GRANT ALL ON ALL SEQUENCES IN SCHEMA observability TO observability;
ALTER DEFAULT PRIVILEGES IN SCHEMA observability GRANT ALL ON TABLES TO observability;
ALTER DEFAULT PRIVILEGES IN SCHEMA observability GRANT ALL ON SEQUENCES TO observability;
