-- Opaque push identity is retained separately from company push ownership.
-- The current native registration resolves the FCM/APNs token at delivery time.
CREATE TABLE collaboration_space_push_bindings (
    space_id uuid NOT NULL,
    account_id uuid NOT NULL,
    device_id uuid NOT NULL,
    native_session_id uuid NOT NULL,
    matrix_user_id varchar(255) NOT NULL,
    matrix_device_id varchar(80) NOT NULL,
    key_hash varchar(64) NOT NULL UNIQUE CHECK(key_hash ~ '^[a-f0-9]{64}$'),
    key_encrypted varchar(2048) NOT NULL CHECK(length(key_encrypted) BETWEEN 1 AND 2048),
    version bigint NOT NULL CHECK(version IN (0,1)),
    reserved_at timestamptz NOT NULL CHECK(isfinite(reserved_at)),
    published_at timestamptz CHECK(published_at IS NULL OR (isfinite(published_at) AND published_at>=reserved_at)),
    PRIMARY KEY(space_id,device_id),
    FOREIGN KEY(space_id,device_id,account_id,native_session_id,matrix_user_id,matrix_device_id)
        REFERENCES collaboration_space_devices(space_id,id,account_id,origin_native_session_id,matrix_user_id,matrix_device_id),
    CHECK((version=1)=(published_at IS NOT NULL))
);
ALTER TABLE collaboration_space_push_bindings ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_space_push_bindings FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_space_push_read ON collaboration_space_push_bindings FOR SELECT
    USING(current_company_id() IS NULL AND account_id=current_actor_id());
CREATE POLICY collaboration_space_push_insert ON collaboration_space_push_bindings FOR INSERT
    WITH CHECK(current_company_id() IS NULL AND account_id=current_actor_id()
        AND EXISTS(SELECT 1 FROM collaboration_space_directory s WHERE s.space_id=collaboration_space_push_bindings.space_id));
CREATE POLICY collaboration_space_push_update ON collaboration_space_push_bindings FOR UPDATE
    USING(current_company_id() IS NULL AND account_id=current_actor_id())
    WITH CHECK(current_company_id() IS NULL AND account_id=current_actor_id()
        AND EXISTS(SELECT 1 FROM collaboration_space_directory s WHERE s.space_id=collaboration_space_push_bindings.space_id));
-- This exact lookup identifies the next account/locks, never current device authority.
CREATE POLICY collaboration_space_push_lookup ON collaboration_space_push_bindings FOR SELECT
    USING(current_company_id() IS NULL
        AND current_actor_id()='00000000-0000-0000-0000-000000000000'::uuid
        AND current_setting('app.collaboration_space_push_lookup',true) ~ '^[a-f0-9]{64}$'
        AND key_hash=current_setting('app.collaboration_space_push_lookup',true));

CREATE FUNCTION protect_collaboration_space_push_binding() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE at_time timestamptz := clock_timestamp(); device collaboration_space_devices;
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Organization push identity is retained' USING ERRCODE='23514';
    ELSIF TG_OP='INSERT' THEN
        IF NEW.version<>0 OR NEW.published_at IS NOT NULL THEN
            RAISE EXCEPTION 'Invalid initial organization push reservation' USING ERRCODE='23514';
        END IF;
    ELSE
        IF (NEW.space_id,NEW.account_id,NEW.device_id,NEW.native_session_id,NEW.matrix_user_id,
            NEW.matrix_device_id,NEW.key_hash,NEW.key_encrypted,NEW.reserved_at)
            IS DISTINCT FROM (OLD.space_id,OLD.account_id,OLD.device_id,OLD.native_session_id,OLD.matrix_user_id,
            OLD.matrix_device_id,OLD.key_hash,OLD.key_encrypted,OLD.reserved_at)
            OR OLD.version<>0 OR NEW.version<>1 OR NEW.published_at IS NULL THEN
            RAISE EXCEPTION 'Invalid organization push publication transition' USING ERRCODE='23514';
        END IF;
    END IF;
    IF current_company_id() IS NOT NULL OR NEW.account_id IS DISTINCT FROM current_actor_id()
        OR NEW.reserved_at>at_time OR NEW.published_at>at_time THEN
        RAISE EXCEPTION 'Invalid organization push scope or time' USING ERRCODE='23514';
    END IF;
    -- The use case owns ordered account/native/device/gateway/request guards and
    -- rechecks native push registration around the provider operation.
    SELECT d.* INTO device FROM collaboration_space_devices d
        WHERE d.space_id=NEW.space_id AND d.id=NEW.device_id AND d.account_id=NEW.account_id
            AND d.origin_native_session_id=NEW.native_session_id AND d.matrix_user_id=NEW.matrix_user_id
            AND d.matrix_device_id=NEW.matrix_device_id AND d.state='ACTIVE' AND d.revoked_at IS NULL
            AND d.platform IN ('ANDROID','IOS') AND d.created_at<=NEW.reserved_at
            AND d.updated_at<=at_time FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Organization push device is unavailable' USING ERRCODE='23514'; END IF;
    at_time := clock_timestamp();
    IF NOT EXISTS(
        SELECT 1 FROM collaboration_space_device_sessions b
        JOIN native_sessions n ON n.id=b.native_session_id AND n.account_id=b.account_id
        JOIN accounts a ON a.id=n.account_id
        WHERE b.space_id=NEW.space_id AND b.device_id=NEW.device_id AND b.account_id=NEW.account_id
            AND b.native_session_id=NEW.native_session_id AND b.bound_at<=at_time
            AND n.created_at<=at_time AND n.rotated_at<=at_time AND n.revoked_at IS NULL
            AND n.expires_at>at_time AND n.access_expires_at>at_time
            AND n.credential_version=a.security_version AND a.active
    ) THEN RAISE EXCEPTION 'Organization push parent is unavailable' USING ERRCODE='23514'; END IF;
    IF device.origin='LEGACY' AND NOT EXISTS(
        SELECT 1 FROM collaboration_legacy_runtime_completions c
        WHERE c.space_id=NEW.space_id AND c.device_id=NEW.device_id AND c.account_id=NEW.account_id
            AND c.native_session_id=NEW.native_session_id AND c.matrix_user_id=NEW.matrix_user_id
            AND c.matrix_device_id=NEW.matrix_device_id AND c.fingerprint=device.legacy_key_fingerprint
            AND c.completed_at<=NEW.reserved_at
    ) THEN RAISE EXCEPTION 'Retained organization push handover is incomplete' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_space_push_binding BEFORE INSERT OR UPDATE OR DELETE ON collaboration_space_push_bindings
    FOR EACH ROW EXECUTE FUNCTION protect_collaboration_space_push_binding();

-- No delete/reset policy or native-session FK: original provenance and the stable
-- capability survive parent cleanup. Callback/delivery still require fresh admission.
