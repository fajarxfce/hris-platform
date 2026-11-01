-- Stable opaque pusher identity per immutable provider device. Token rotation is
-- resolved through the current native push registration, never copied to Synapse.
CREATE TABLE collaboration_push_bindings (
    company_id uuid NOT NULL,
    device_id uuid NOT NULL,
    account_id uuid NOT NULL,
    native_session_id uuid NOT NULL,
    key_hash varchar(64) NOT NULL UNIQUE CHECK(key_hash ~ '^[a-f0-9]{64}$'),
    key_encrypted varchar(2048) NOT NULL CHECK(length(key_encrypted) BETWEEN 1 AND 2048),
    version bigint NOT NULL CHECK(version IN (0,1)),
    reserved_at timestamptz NOT NULL CHECK(isfinite(reserved_at)),
    published_at timestamptz CHECK(published_at IS NULL OR (isfinite(published_at) AND published_at>=reserved_at)),
    PRIMARY KEY(company_id,device_id),
    FOREIGN KEY(company_id,device_id,account_id) REFERENCES collaboration_devices(company_id,id,account_id),
    CHECK((version=1)=(published_at IS NOT NULL))
);
ALTER TABLE collaboration_push_bindings ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_push_bindings FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_push_binding_owner ON collaboration_push_bindings
    USING(company_id=current_company_id() AND account_id=current_actor_id())
    WITH CHECK(company_id=current_company_id() AND account_id=current_actor_id());
-- Lookup only identifies the next scope/locks. It grants no current chat access.
CREATE POLICY collaboration_push_binding_lookup ON collaboration_push_bindings FOR SELECT
    USING(current_setting('app.collaboration_push_lookup',true) ~ '^[a-f0-9]{64}$'
        AND key_hash=current_setting('app.collaboration_push_lookup',true));
CREATE FUNCTION protect_collaboration_push_binding() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Collaboration push identity is retained' USING ERRCODE='23514';
    ELSIF TG_OP='INSERT' THEN
        IF NEW.version<>0 OR NEW.published_at IS NOT NULL THEN
            RAISE EXCEPTION 'Invalid initial push reservation' USING ERRCODE='23514';
        END IF;
    ELSE
        IF (NEW.company_id,NEW.device_id,NEW.account_id,NEW.native_session_id,NEW.key_hash,NEW.key_encrypted,NEW.reserved_at)
            IS DISTINCT FROM (OLD.company_id,OLD.device_id,OLD.account_id,OLD.native_session_id,OLD.key_hash,OLD.key_encrypted,OLD.reserved_at)
            OR OLD.version<>0 OR NEW.version<>1 OR NEW.published_at IS NULL THEN
            RAISE EXCEPTION 'Invalid push publication transition' USING ERRCODE='23514';
        END IF;
    END IF;
    PERFORM 1 FROM collaboration_devices d
        WHERE d.company_id=NEW.company_id AND d.id=NEW.device_id AND d.account_id=NEW.account_id
          AND d.platform IN ('ANDROID','IOS') AND d.state='ACTIVE' AND d.revoked_at IS NULL
          AND d.created_at<=NEW.reserved_at FOR SHARE;
    IF NOT FOUND OR NOT EXISTS(
        SELECT 1 FROM collaboration_device_sessions b
        WHERE b.company_id=NEW.company_id AND b.device_id=NEW.device_id AND b.account_id=NEW.account_id
          AND b.native_session_id=NEW.native_session_id AND b.bound_at<=NEW.reserved_at
    ) THEN RAISE EXCEPTION 'Invalid push device binding' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_push_binding BEFORE INSERT OR UPDATE OR DELETE ON collaboration_push_bindings
    FOR EACH ROW EXECUTE FUNCTION protect_collaboration_push_binding();
