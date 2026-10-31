-- Only internal provider credentials are stored here. Participant E2EE private
-- keys never enter HRIS. Ciphertext is authenticated to company/account/device/generation.
-- Reserve the last device revision for irreversible revocation.
ALTER TABLE collaboration_devices DROP CONSTRAINT collaboration_devices_version_check;
ALTER TABLE collaboration_devices ADD CHECK(version BETWEEN 0 AND 1000000);
ALTER TABLE collaboration_devices ADD CHECK(state='REVOKED' OR version<1000000);
CREATE TABLE collaboration_provider_sessions (
    company_id uuid NOT NULL,
    device_id uuid NOT NULL,
    account_id uuid NOT NULL,
    matrix_user_id varchar(255) NOT NULL CHECK(matrix_user_id ~ '^@[^[:space:]:]+:[^[:space:]]+$'),
    matrix_device_id varchar(128) NOT NULL CHECK(matrix_device_id ~ '^[A-Za-z0-9_-]{1,128}$'),
    generation bigint NOT NULL CHECK(generation BETWEEN 0 AND 1000000),
    token_encrypted varchar(4096) CHECK(token_encrypted IS NULL OR length(token_encrypted) BETWEEN 1 AND 4096),
    cleared_at timestamptz CHECK(cleared_at IS NULL OR isfinite(cleared_at)),
    created_at timestamptz NOT NULL CHECK(isfinite(created_at)),
    updated_at timestamptz NOT NULL CHECK(isfinite(updated_at) AND updated_at>=created_at),
    PRIMARY KEY(company_id,device_id),
    UNIQUE(matrix_user_id,matrix_device_id),
    CHECK(split_part(matrix_user_id,':',1)='@hris_'||replace(company_id::text,'-','')||'_'||replace(account_id::text,'-','')),
    CHECK(matrix_device_id='HRIS_'||replace(device_id::text,'-','')),
    CHECK((token_encrypted IS NULL)=(cleared_at IS NOT NULL)),
    CHECK(token_encrypted IS NULL OR generation<1000000),
    CHECK(cleared_at IS NULL OR (cleared_at>=created_at AND cleared_at<=updated_at)),
    FOREIGN KEY(company_id,device_id,account_id) REFERENCES collaboration_devices(company_id,id,account_id)
);

ALTER TABLE collaboration_provider_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_provider_sessions FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_provider_scope ON collaboration_provider_sessions
    USING(company_id=current_company_id() AND account_id=current_actor_id())
    WITH CHECK(company_id=current_company_id() AND account_id=current_actor_id());

CREATE FUNCTION protect_collaboration_provider_session() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Provider credential generation is retained' USING ERRCODE='23514';
    END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.generation<>0 OR NEW.created_at<>NEW.updated_at OR NEW.cleared_at IS NOT NULL THEN
            RAISE EXCEPTION 'Invalid initial provider credential' USING ERRCODE='23514';
        END IF;
    ELSE
        IF (NEW.company_id,NEW.device_id,NEW.account_id,NEW.matrix_user_id,NEW.matrix_device_id,NEW.created_at)
            IS DISTINCT FROM (OLD.company_id,OLD.device_id,OLD.account_id,OLD.matrix_user_id,OLD.matrix_device_id,OLD.created_at)
            OR NEW.generation<>OLD.generation+1 OR NEW.updated_at<OLD.updated_at
            OR OLD.cleared_at IS NOT NULL THEN
            RAISE EXCEPTION 'Invalid provider credential rotation' USING ERRCODE='23514';
        END IF;
    END IF;
    -- Credential retirement is irreversible. Retaining its generation prevents
    -- a delayed cleanup from deleting a newly-created generation zero (ABA).
    PERFORM 1 FROM collaboration_devices
        WHERE company_id=NEW.company_id AND id=NEW.device_id AND account_id=NEW.account_id
        AND ((NEW.cleared_at IS NULL AND state IN ('PROVISIONING','ACTIVE'))
            OR (NEW.cleared_at IS NOT NULL AND state='REVOKED')) FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Provider credential device is unavailable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_provider_session BEFORE INSERT OR UPDATE OR DELETE ON collaboration_provider_sessions
    FOR EACH ROW EXECUTE FUNCTION protect_collaboration_provider_session();
