-- Reserve the last revision for revocation, including an exhausted active device.
ALTER TABLE collaboration_space_devices DROP CONSTRAINT collaboration_space_devices_version_check;
ALTER TABLE collaboration_space_devices ADD CONSTRAINT collaboration_space_device_version_bounds
    CHECK(version BETWEEN 0 AND 1000000 AND (state='REVOKED' OR version<1000000));
ALTER TABLE collaboration_space_devices ADD CONSTRAINT collaboration_space_provider_owner_reference
    UNIQUE(space_id,id,account_id,origin_native_session_id,matrix_user_id,matrix_device_id);

-- Owned security metadata and terminal cleanup must survive group eligibility
-- loss. This is not content admission. Live inserts/updates still require eligible
-- group scope; only irreversible revocation may proceed after offboarding.
DROP POLICY collaboration_space_device_read ON collaboration_space_devices;
CREATE POLICY collaboration_space_device_read ON collaboration_space_devices FOR SELECT
    USING(account_id=current_actor_id());
DROP POLICY collaboration_space_device_update ON collaboration_space_devices;
CREATE POLICY collaboration_space_device_update ON collaboration_space_devices FOR UPDATE
    USING(current_company_id() IS NULL AND account_id=current_actor_id())
    WITH CHECK(current_company_id() IS NULL AND account_id=current_actor_id()
        AND (state='REVOKED' OR EXISTS(SELECT 1 FROM collaboration_space_directory s WHERE s.space_id=collaboration_space_devices.space_id)));

-- New authenticated envelope namespace. Do not copy or reinterpret old company
-- credentials. Native parent provenance is retained without a parent-session FK.
CREATE TABLE collaboration_space_provider_sessions (
    space_id uuid NOT NULL,
    device_id uuid NOT NULL,
    account_id uuid NOT NULL,
    native_session_id uuid NOT NULL,
    matrix_user_id varchar(255) NOT NULL,
    matrix_device_id varchar(80) NOT NULL,
    generation bigint NOT NULL CHECK(generation BETWEEN 0 AND 1000000),
    token_encrypted varchar(4096) CHECK(token_encrypted IS NULL OR length(token_encrypted) BETWEEN 1 AND 4096),
    cleared_at timestamptz CHECK(cleared_at IS NULL OR isfinite(cleared_at)),
    created_at timestamptz NOT NULL CHECK(isfinite(created_at)),
    updated_at timestamptz NOT NULL CHECK(isfinite(updated_at) AND updated_at>=created_at),
    token_expires_at timestamptz NOT NULL CHECK(isfinite(token_expires_at)),
    PRIMARY KEY(space_id,device_id),
    UNIQUE(matrix_user_id,matrix_device_id),
    FOREIGN KEY(space_id,device_id,account_id,native_session_id,matrix_user_id,matrix_device_id)
        REFERENCES collaboration_space_devices(space_id,id,account_id,origin_native_session_id,matrix_user_id,matrix_device_id),
    CHECK((token_encrypted IS NULL)=(cleared_at IS NOT NULL)),
    CHECK(token_encrypted IS NULL OR generation<1000000),
    CHECK(cleared_at IS NULL OR (cleared_at>=created_at AND cleared_at<=updated_at)),
    CHECK(token_expires_at>created_at AND (token_encrypted IS NULL OR
        (token_expires_at>updated_at AND token_expires_at<=updated_at+interval '10 minutes')))
);
ALTER TABLE collaboration_space_provider_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_space_provider_sessions FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_space_provider_read ON collaboration_space_provider_sessions FOR SELECT
    USING(current_company_id() IS NULL AND account_id=current_actor_id());
CREATE POLICY collaboration_space_provider_insert ON collaboration_space_provider_sessions FOR INSERT
    WITH CHECK(current_company_id() IS NULL AND account_id=current_actor_id()
        AND EXISTS(SELECT 1 FROM collaboration_space_directory s WHERE s.space_id=collaboration_space_provider_sessions.space_id));
CREATE POLICY collaboration_space_provider_update ON collaboration_space_provider_sessions FOR UPDATE
    USING(current_company_id() IS NULL AND account_id=current_actor_id())
    WITH CHECK(current_company_id() IS NULL AND account_id=current_actor_id()
        AND (cleared_at IS NOT NULL OR EXISTS(SELECT 1 FROM collaboration_space_directory s WHERE s.space_id=collaboration_space_provider_sessions.space_id)));

CREATE FUNCTION protect_collaboration_space_provider_session() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Organization credential generation is retained' USING ERRCODE='23514';
    ELSIF TG_OP='INSERT' THEN
        IF NEW.generation<>0 OR NEW.created_at<>NEW.updated_at OR NEW.cleared_at IS NOT NULL THEN
            RAISE EXCEPTION 'Invalid initial organization credential' USING ERRCODE='23514';
        END IF;
    ELSE
        IF (NEW.space_id,NEW.device_id,NEW.account_id,NEW.native_session_id,NEW.matrix_user_id,NEW.matrix_device_id,NEW.created_at)
            IS DISTINCT FROM (OLD.space_id,OLD.device_id,OLD.account_id,OLD.native_session_id,OLD.matrix_user_id,OLD.matrix_device_id,OLD.created_at)
            OR NEW.generation<>OLD.generation+1 OR NEW.updated_at<OLD.updated_at
            OR OLD.cleared_at IS NOT NULL THEN
            RAISE EXCEPTION 'Invalid organization credential rotation' USING ERRCODE='23514';
        END IF;
        IF NEW.cleared_at IS NOT NULL AND NEW.token_expires_at IS DISTINCT FROM OLD.token_expires_at THEN
            RAISE EXCEPTION 'Retired organization credential expiry is retained' USING ERRCODE='23514';
        END IF;
    END IF;
    -- Live writes also require a current bound native parent; cleanup can operate
    -- after that parent was purged, but only after irreversible device revocation.
    PERFORM 1 FROM collaboration_space_devices d
        WHERE d.space_id=NEW.space_id AND d.id=NEW.device_id AND d.account_id=NEW.account_id
          AND d.created_at<=NEW.created_at AND d.updated_at<=NEW.updated_at
          AND ((NEW.cleared_at IS NOT NULL AND d.state='REVOKED' AND d.revoked_at<=NEW.cleared_at) OR
            (NEW.cleared_at IS NULL AND d.state IN ('PROVISIONING','ACTIVE') AND EXISTS(
                SELECT 1 FROM collaboration_space_device_sessions b
                JOIN native_sessions s ON s.id=b.native_session_id AND s.account_id=b.account_id
                JOIN accounts a ON a.id=s.account_id
                WHERE b.space_id=d.space_id AND b.device_id=d.id AND b.account_id=d.account_id
                  AND b.native_session_id=NEW.native_session_id AND b.bound_at<=NEW.updated_at
                  AND s.created_at<=NEW.updated_at AND s.revoked_at IS NULL
                  AND s.expires_at>NEW.updated_at AND s.access_expires_at>NEW.updated_at
                  AND s.credential_version=a.security_version AND a.active
            ))) FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Organization credential device is unavailable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_space_provider_session BEFORE INSERT OR UPDATE OR DELETE ON collaboration_space_provider_sessions
    FOR EACH ROW EXECUTE FUNCTION protect_collaboration_space_provider_session();

-- The table starts empty. A later admitted/fenced worker must acquire the exact
-- provider identity outside SQL locks and atomically publish a short-lived token.
-- This migration does not create a provider login, transfer a legacy runtime or
-- authorize decryption, a room exchange or participant-key replacement.
