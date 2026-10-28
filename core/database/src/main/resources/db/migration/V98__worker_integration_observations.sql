-- Ephemeral process observations only; no provider addresses, credentials or business data.
CREATE TABLE worker_integration_observations (
    instance_id uuid PRIMARY KEY,
    first_seen_at timestamptz NOT NULL DEFAULT clock_timestamp() CHECK(isfinite(first_seen_at)),
    last_seen_at timestamptz NOT NULL DEFAULT clock_timestamp() CHECK(isfinite(last_seen_at)),
    mail_enabled boolean NOT NULL,
    push_enabled boolean NOT NULL,
    storage_enabled boolean NOT NULL,
    scanner_enabled boolean NOT NULL,
    CHECK(last_seen_at>=first_seen_at)
);
ALTER TABLE worker_integration_observations ENABLE ROW LEVEL SECURITY;
ALTER TABLE worker_integration_observations FORCE ROW LEVEL SECURITY;
CREATE POLICY worker_integration_read ON worker_integration_observations FOR SELECT
    USING(current_company_id() IS NULL AND current_actor_id() IS NOT NULL);
CREATE POLICY worker_integration_insert ON worker_integration_observations FOR INSERT
    TO hris_worker_capability WITH CHECK(current_company_id() IS NULL);
CREATE POLICY worker_integration_update ON worker_integration_observations FOR UPDATE
    TO hris_worker_capability USING(current_company_id() IS NULL) WITH CHECK(current_company_id() IS NULL);
CREATE POLICY worker_integration_delete ON worker_integration_observations FOR DELETE
    TO hris_worker_capability USING(current_company_id() IS NULL);
GRANT SELECT,INSERT,UPDATE,DELETE ON worker_integration_observations TO hris_worker_capability;

CREATE FUNCTION protect_worker_integration_observation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF (NEW.instance_id,NEW.first_seen_at,NEW.mail_enabled,NEW.push_enabled,NEW.storage_enabled,NEW.scanner_enabled)
        IS DISTINCT FROM (OLD.instance_id,OLD.first_seen_at,OLD.mail_enabled,OLD.push_enabled,OLD.storage_enabled,OLD.scanner_enabled)
        OR NEW.last_seen_at<OLD.last_seen_at THEN
        RAISE EXCEPTION 'Worker startup observations are immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_worker_integration_observation BEFORE UPDATE ON worker_integration_observations
    FOR EACH ROW EXECUTE FUNCTION protect_worker_integration_observation();
