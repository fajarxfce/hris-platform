-- Organization gateway ownership is distinct from every legacy HR-company scope.
-- One credential head and bounded encrypted lost-response replay per native device.
-- These are HRIS gateway credentials, never Synapse bearers or participant E2EE keys.
CREATE TABLE collaboration_space_gateway_sessions (
    space_id uuid NOT NULL,
    id uuid NOT NULL,
    account_id uuid NOT NULL,
    device_id uuid NOT NULL,
    native_session_id uuid NOT NULL,
    platform varchar(16) NOT NULL CHECK(platform IN ('ANDROID','IOS')),
    client_build integer NOT NULL CHECK(client_build BETWEEN 1 AND 999999999),
    version bigint NOT NULL CHECK(version BETWEEN 0 AND 1000000),
    access_hash varchar(64) UNIQUE CHECK(access_hash IS NULL OR access_hash ~ '^[a-f0-9]{64}$'),
    refresh_hash varchar(64) UNIQUE CHECK(refresh_hash IS NULL OR refresh_hash ~ '^[a-f0-9]{64}$'),
    created_at timestamptz NOT NULL CHECK(isfinite(created_at)),
    rotated_at timestamptz NOT NULL CHECK(isfinite(rotated_at) AND rotated_at>=created_at),
    expires_at timestamptz NOT NULL CHECK(isfinite(expires_at) AND expires_at>created_at AND expires_at<=created_at+interval '744 hours'),
    access_expires_at timestamptz NOT NULL CHECK(isfinite(access_expires_at) AND access_expires_at<=expires_at AND access_expires_at>rotated_at AND access_expires_at<=rotated_at+interval '15 minutes'),
    revoked_at timestamptz CHECK(revoked_at IS NULL OR (isfinite(revoked_at) AND revoked_at>=rotated_at)),
    replay_kind varchar(16) CHECK(replay_kind IN ('BOOTSTRAP','REFRESH')),
    bootstrap_operation_id uuid,
    previous_refresh_hash varchar(64) UNIQUE CHECK(previous_refresh_hash IS NULL OR previous_refresh_hash ~ '^[a-f0-9]{64}$'),
    replay_until timestamptz CHECK(replay_until IS NULL OR (isfinite(replay_until) AND replay_until>rotated_at AND replay_until<=rotated_at+interval '60 seconds' AND replay_until<=access_expires_at)),
    replay_encrypted varchar(4096) CHECK(replay_encrypted IS NULL OR length(replay_encrypted) BETWEEN 1 AND 4096),
    PRIMARY KEY(space_id,id),
    UNIQUE(space_id,device_id),
    FOREIGN KEY(space_id,device_id,account_id,native_session_id)
        REFERENCES collaboration_space_devices(space_id,id,account_id,origin_native_session_id),
    CHECK((revoked_at IS NULL AND access_hash IS NOT NULL AND refresh_hash IS NOT NULL AND access_hash<>refresh_hash AND version<1000000)
        OR (revoked_at IS NOT NULL AND access_hash IS NULL AND refresh_hash IS NULL AND replay_encrypted IS NULL)),
    CHECK((replay_encrypted IS NULL AND replay_kind IS NULL AND bootstrap_operation_id IS NULL AND previous_refresh_hash IS NULL)
        OR (replay_encrypted IS NOT NULL AND replay_until IS NOT NULL AND replay_kind IS NOT NULL AND
            ((replay_kind='BOOTSTRAP' AND bootstrap_operation_id IS NOT NULL AND previous_refresh_hash IS NULL)
             OR (replay_kind='REFRESH' AND bootstrap_operation_id IS NULL AND previous_refresh_hash IS NOT NULL AND previous_refresh_hash<>refresh_hash))))
);
CREATE INDEX collaboration_space_gateway_replay_expiry ON collaboration_space_gateway_sessions(replay_until,space_id,id) WHERE replay_encrypted IS NOT NULL;

ALTER TABLE collaboration_space_gateway_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_space_gateway_sessions FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_space_gateway_read ON collaboration_space_gateway_sessions FOR SELECT
    USING(current_company_id() IS NULL AND account_id=current_actor_id());
CREATE POLICY collaboration_space_gateway_insert ON collaboration_space_gateway_sessions FOR INSERT
    WITH CHECK(current_company_id() IS NULL AND account_id=current_actor_id()
        AND EXISTS(SELECT 1 FROM collaboration_space_directory s WHERE s.space_id=collaboration_space_gateway_sessions.space_id));
CREATE POLICY collaboration_space_gateway_update ON collaboration_space_gateway_sessions FOR UPDATE
    USING(current_company_id() IS NULL AND account_id=current_actor_id())
    WITH CHECK(current_company_id() IS NULL AND account_id=current_actor_id()
        AND (revoked_at IS NOT NULL OR EXISTS(SELECT 1 FROM collaboration_space_directory s WHERE s.space_id=collaboration_space_gateway_sessions.space_id)));
-- The auth lookup transaction sets only the hash of a validated opaque token.
-- A lookup identifies a lock; it is not live HRIS/company admission. All subsequent
-- guarded reads and mutations use the resolved account/organization transaction. HR access is not added.
CREATE POLICY collaboration_space_gateway_lookup ON collaboration_space_gateway_sessions FOR SELECT
    USING(current_company_id() IS NULL AND current_actor_id()='00000000-0000-0000-0000-000000000000'::uuid
        AND current_setting('app.collaboration_space_gateway_lookup',true) ~ '^[a-f0-9]{64}$'
        AND current_setting('app.collaboration_space_gateway_lookup',true) IN (access_hash,refresh_hash,previous_refresh_hash));
CREATE POLICY collaboration_space_gateway_expired_read ON collaboration_space_gateway_sessions FOR SELECT TO hris_worker_capability
    USING(replay_until<=clock_timestamp());
CREATE POLICY collaboration_space_gateway_expired_clear ON collaboration_space_gateway_sessions FOR UPDATE TO hris_worker_capability
    USING(replay_until<=clock_timestamp()) WITH CHECK(replay_encrypted IS NULL);

CREATE FUNCTION protect_collaboration_space_gateway_session() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Gateway credential generation is retained' USING ERRCODE='23514';
    END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.version<>0 OR NEW.created_at<>NEW.rotated_at OR NEW.revoked_at IS NOT NULL OR NEW.replay_kind IS DISTINCT FROM 'BOOTSTRAP' THEN
            RAISE EXCEPTION 'Invalid initial gateway session' USING ERRCODE='23514';
        END IF;
    ELSE
        -- Retain the expired deadline so the bounded maintenance role can see its updated row.
        -- Cache retirement changes no logical session fact and acquires no reverse device lock.
        IF OLD.replay_until<=clock_timestamp() AND NEW.replay_encrypted IS NULL
            AND NEW.replay_until IS NOT DISTINCT FROM OLD.replay_until
            AND (NEW.space_id,NEW.id,NEW.account_id,NEW.device_id,NEW.native_session_id,NEW.platform,NEW.client_build,NEW.version,
                 NEW.access_hash,NEW.refresh_hash,NEW.created_at,NEW.rotated_at,NEW.expires_at,NEW.access_expires_at,NEW.revoked_at)
            IS NOT DISTINCT FROM (OLD.space_id,OLD.id,OLD.account_id,OLD.device_id,OLD.native_session_id,OLD.platform,OLD.client_build,OLD.version,
                 OLD.access_hash,OLD.refresh_hash,OLD.created_at,OLD.rotated_at,OLD.expires_at,OLD.access_expires_at,OLD.revoked_at) THEN
            RETURN NEW;
        END IF;
        IF (NEW.space_id,NEW.id,NEW.account_id,NEW.device_id,NEW.native_session_id,NEW.platform,NEW.created_at,NEW.expires_at)
            IS DISTINCT FROM (OLD.space_id,OLD.id,OLD.account_id,OLD.device_id,OLD.native_session_id,OLD.platform,OLD.created_at,OLD.expires_at)
            OR NEW.version<>OLD.version+1 OR OLD.revoked_at IS NOT NULL OR NEW.rotated_at<OLD.rotated_at THEN
            RAISE EXCEPTION 'Invalid gateway session transition' USING ERRCODE='23514';
        END IF;
        IF NEW.revoked_at IS NULL AND (NEW.replay_encrypted IS NULL OR NEW.access_hash=OLD.access_hash OR NEW.refresh_hash=OLD.refresh_hash
            OR (NEW.replay_kind='REFRESH' AND (NEW.previous_refresh_hash IS DISTINCT FROM OLD.refresh_hash OR NEW.client_build<>OLD.client_build))) THEN
            RAISE EXCEPTION 'Invalid gateway credential rotation' USING ERRCODE='23514';
        END IF;
        IF NEW.revoked_at IS NOT NULL AND (NEW.rotated_at<>OLD.rotated_at OR NEW.access_expires_at<>OLD.access_expires_at OR NEW.client_build<>OLD.client_build) THEN
            RAISE EXCEPTION 'Gateway revocation preserves its last issued facts' USING ERRCODE='23514';
        END IF;
    END IF;
    -- The caller locks account/native/device before this gateway. Only the expired
    -- cache-only branch above can return without acquiring the device guard.
    PERFORM 1 FROM collaboration_space_devices d
        WHERE d.space_id=NEW.space_id AND d.id=NEW.device_id AND d.account_id=NEW.account_id
          AND d.origin_native_session_id=NEW.native_session_id AND d.platform=NEW.platform
          AND d.created_at<=NEW.created_at
          AND ((NEW.revoked_at IS NULL AND d.state='ACTIVE' AND d.updated_at<=NEW.rotated_at)
            OR (NEW.revoked_at IS NOT NULL AND d.state='REVOKED' AND d.revoked_at<=NEW.revoked_at)) FOR SHARE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Organization gateway device is unavailable' USING ERRCODE='23514'; END IF;
    -- Terminal cleanup survives eligibility loss and native-parent purge. Live
    -- publication/rotation still requires the exact current bound native parent.
    IF NEW.revoked_at IS NULL AND (NEW.rotated_at>clock_timestamp() OR NOT EXISTS(
        SELECT 1 FROM collaboration_space_device_sessions b
        JOIN native_sessions s ON s.id=b.native_session_id AND s.account_id=b.account_id
        JOIN accounts a ON a.id=s.account_id
        WHERE b.space_id=NEW.space_id AND b.device_id=NEW.device_id AND b.account_id=NEW.account_id
          AND b.native_session_id=NEW.native_session_id AND b.bound_at<=NEW.rotated_at
          AND s.created_at<=NEW.created_at AND s.rotated_at<=NEW.rotated_at
          AND s.revoked_at IS NULL AND s.expires_at>clock_timestamp() AND s.access_expires_at>clock_timestamp()
          AND NEW.expires_at<=s.expires_at AND s.credential_version=a.security_version AND a.active
    )) THEN RAISE EXCEPTION 'Organization gateway parent is unavailable' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_space_gateway_session BEFORE INSERT OR UPDATE OR DELETE ON collaboration_space_gateway_sessions
    FOR EACH ROW EXECUTE FUNCTION protect_collaboration_space_gateway_session();

-- No gateway credentials are created by this migration. Group HTTP/provider/room
-- admission and Mobile group-store adoption require their separate implementations.
-- Token prefixes and authenticated replay purposes must be distinct from V109.
