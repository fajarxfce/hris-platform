-- Parent and public-key metadata are separate from the retained inventory: a
-- purged parent must disappear here without erasing the original identity there.
GRANT SELECT(company_id,device_id,account_id,native_session_id,bound_at)
    ON collaboration_device_sessions TO hris_collaboration_legacy_reader;
GRANT SELECT(company_id,device_id,account_id,fingerprint,version,reserved_at,published_at)
    ON collaboration_device_key_publications TO hris_collaboration_legacy_reader;
CREATE POLICY collaboration_key_owned_metadata ON collaboration_device_key_publications FOR SELECT
    TO hris_collaboration_legacy_reader USING(account_id=current_actor_id());

CREATE VIEW collaboration_space_legacy_parents WITH(security_barrier=true) AS
SELECT device.*,binding.native_session_id,binding.bound_at,
    publication.fingerprint AS key_fingerprint,publication.version AS key_version,
    publication.reserved_at AS key_reserved_at,publication.published_at AS key_published_at
FROM public.collaboration_space_legacy_devices device
JOIN public.collaboration_device_sessions binding
    ON binding.company_id=device.legacy_company_id AND binding.device_id=device.legacy_device_id
    AND binding.account_id=device.account_id
LEFT JOIN public.collaboration_device_key_publications publication
    ON publication.company_id=device.legacy_company_id AND publication.device_id=device.legacy_device_id
    AND publication.account_id=device.account_id
WHERE device.account_id=public.current_actor_id();
ALTER VIEW collaboration_space_legacy_parents OWNER TO hris_collaboration_legacy_reader;

ALTER TABLE collaboration_space_identities ADD CONSTRAINT collaboration_space_user_reference
    UNIQUE(space_id,account_id,matrix_user_id);
ALTER TABLE collaboration_provider_sessions ADD CONSTRAINT collaboration_legacy_device_reference
    UNIQUE(company_id,device_id,account_id,matrix_user_id,matrix_device_id);
ALTER TABLE collaboration_device_key_publications ADD CONSTRAINT collaboration_legacy_key_reference
    UNIQUE(company_id,device_id,account_id,fingerprint);

-- Organization ownership has its own columns and namespace. Legacy IDs and
-- fingerprints are immutable references, never copied credentials or fresh keys.
CREATE TABLE collaboration_space_devices (
    space_id uuid NOT NULL,
    id uuid NOT NULL,
    account_id uuid NOT NULL,
    matrix_user_id varchar(255) NOT NULL,
    matrix_device_id varchar(80) NOT NULL,
    origin varchar(16) NOT NULL CHECK(origin IN ('LEGACY','SPACE')),
    legacy_company_id uuid,
    legacy_device_id uuid,
    legacy_key_fingerprint varchar(64),
    -- Retained provenance, intentionally without a parent FK. After parent purge
    -- the device cannot silently bind to a different parent with the same ID.
    origin_native_session_id uuid NOT NULL,
    platform varchar(16) NOT NULL CHECK(platform IN ('ANDROID','IOS')),
    label varchar(64) NOT NULL CHECK(length(label) BETWEEN 1 AND 64 AND label=btrim(label) AND label !~ '[[:cntrl:]]'),
    state varchar(16) NOT NULL CHECK(state IN ('PROVISIONING','ACTIVE','REVOKED')),
    version bigint NOT NULL CHECK(version BETWEEN 0 AND 999999),
    created_at timestamptz NOT NULL CHECK(isfinite(created_at)),
    updated_at timestamptz NOT NULL CHECK(isfinite(updated_at) AND updated_at>=created_at),
    revoked_at timestamptz CHECK(revoked_at IS NULL OR isfinite(revoked_at)),
    PRIMARY KEY(space_id,id),
    UNIQUE(space_id,id,account_id,origin_native_session_id),
    UNIQUE(matrix_user_id,matrix_device_id),
    FOREIGN KEY(space_id,account_id,matrix_user_id)
        REFERENCES collaboration_space_identities(space_id,account_id,matrix_user_id),
    FOREIGN KEY(legacy_company_id,legacy_device_id,account_id,matrix_user_id,matrix_device_id)
        REFERENCES collaboration_provider_sessions(company_id,device_id,account_id,matrix_user_id,matrix_device_id),
    FOREIGN KEY(legacy_company_id,legacy_device_id,account_id,legacy_key_fingerprint)
        REFERENCES collaboration_device_key_publications(company_id,device_id,account_id,fingerprint),
    CHECK((origin='SPACE' AND legacy_company_id IS NULL AND legacy_device_id IS NULL
        AND legacy_key_fingerprint IS NULL AND matrix_device_id='HRISG_'||replace(id::text,'-',''))
        OR (origin='LEGACY' AND legacy_company_id IS NOT NULL AND legacy_device_id IS NOT NULL
        AND legacy_key_fingerprint IS NOT NULL AND legacy_key_fingerprint ~ '^[a-f0-9]{64}$'
        AND matrix_device_id='HRIS_'||replace(legacy_device_id::text,'-',''))),
    CHECK((state='REVOKED')=(revoked_at IS NOT NULL)),
    CHECK(revoked_at IS NULL OR (revoked_at>=created_at AND revoked_at<=updated_at))
);
CREATE INDEX collaboration_space_device_owner ON collaboration_space_devices(account_id,space_id,created_at,id);

CREATE TABLE collaboration_space_device_sessions (
    space_id uuid NOT NULL,
    device_id uuid NOT NULL,
    account_id uuid NOT NULL,
    native_session_id uuid NOT NULL,
    bound_at timestamptz NOT NULL CHECK(isfinite(bound_at)),
    PRIMARY KEY(space_id,device_id),
    UNIQUE(space_id,native_session_id),
    FOREIGN KEY(space_id,device_id,account_id,native_session_id)
        REFERENCES collaboration_space_devices(space_id,id,account_id,origin_native_session_id),
    FOREIGN KEY(native_session_id,account_id) REFERENCES native_sessions(id,account_id) ON DELETE CASCADE
);
CREATE INDEX collaboration_space_binding_owner ON collaboration_space_device_sessions(account_id,native_session_id);

ALTER TABLE collaboration_space_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_space_devices FORCE ROW LEVEL SECURITY;
ALTER TABLE collaboration_space_device_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_space_device_sessions FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_space_device_read ON collaboration_space_devices FOR SELECT
    USING(account_id=current_actor_id() AND EXISTS(SELECT 1 FROM collaboration_space_directory s WHERE s.space_id=collaboration_space_devices.space_id));
CREATE POLICY collaboration_space_device_insert ON collaboration_space_devices FOR INSERT
    WITH CHECK(current_company_id() IS NULL AND account_id=current_actor_id()
        AND EXISTS(SELECT 1 FROM collaboration_space_directory s WHERE s.space_id=collaboration_space_devices.space_id));
CREATE POLICY collaboration_space_device_update ON collaboration_space_devices FOR UPDATE
    USING(current_company_id() IS NULL AND account_id=current_actor_id()
        AND EXISTS(SELECT 1 FROM collaboration_space_directory s WHERE s.space_id=collaboration_space_devices.space_id))
    WITH CHECK(current_company_id() IS NULL AND account_id=current_actor_id()
        AND EXISTS(SELECT 1 FROM collaboration_space_directory s WHERE s.space_id=collaboration_space_devices.space_id));
CREATE POLICY collaboration_space_binding_read ON collaboration_space_device_sessions FOR SELECT
    USING(account_id=current_actor_id() AND EXISTS(SELECT 1 FROM collaboration_space_directory s WHERE s.space_id=collaboration_space_device_sessions.space_id));
CREATE POLICY collaboration_space_binding_insert ON collaboration_space_device_sessions FOR INSERT
    WITH CHECK(current_company_id() IS NULL AND account_id=current_actor_id()
        AND EXISTS(SELECT 1 FROM collaboration_space_directory s WHERE s.space_id=collaboration_space_device_sessions.space_id));

CREATE FUNCTION protect_collaboration_space_device() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Organization device identity is retained' USING ERRCODE='23514';
    ELSIF TG_OP='INSERT' THEN
        IF NEW.state<>'PROVISIONING' OR NEW.version<>0 OR NEW.created_at<>NEW.updated_at THEN
            RAISE EXCEPTION 'Invalid initial organization device' USING ERRCODE='23514';
        END IF;
        IF NEW.origin='LEGACY' AND NOT EXISTS(
            SELECT 1 FROM collaboration_space_legacy_parents p
            WHERE p.space_id=NEW.space_id AND p.account_id=NEW.account_id
              AND p.legacy_company_id=NEW.legacy_company_id AND p.legacy_device_id=NEW.legacy_device_id
              AND p.matrix_user_id=NEW.matrix_user_id AND p.matrix_device_id=NEW.matrix_device_id
              AND p.native_session_id=NEW.origin_native_session_id AND p.platform=NEW.platform
              AND p.state='ACTIVE' AND p.device_revoked_at IS NULL AND p.credential_retired_at IS NULL
              AND p.bound_at<=NEW.created_at AND p.key_reserved_at<=NEW.created_at
              AND p.key_fingerprint=NEW.legacy_key_fingerprint
        ) THEN
            RAISE EXCEPTION 'Invalid retained organization device source' USING ERRCODE='23514';
        END IF;
    ELSE
        IF (NEW.space_id,NEW.id,NEW.account_id,NEW.matrix_user_id,NEW.matrix_device_id,NEW.origin,
            NEW.legacy_company_id,NEW.legacy_device_id,NEW.legacy_key_fingerprint,
            NEW.origin_native_session_id,NEW.platform,NEW.created_at)
            IS DISTINCT FROM (OLD.space_id,OLD.id,OLD.account_id,OLD.matrix_user_id,OLD.matrix_device_id,OLD.origin,
            OLD.legacy_company_id,OLD.legacy_device_id,OLD.legacy_key_fingerprint,
            OLD.origin_native_session_id,OLD.platform,OLD.created_at)
            OR NEW.version<>OLD.version+1 OR NEW.updated_at<OLD.updated_at
            OR (OLD.state='REVOKED' AND (NEW.state<>'REVOKED' OR NEW.revoked_at<>OLD.revoked_at))
            OR (OLD.state='ACTIVE' AND NEW.state='PROVISIONING') THEN
            RAISE EXCEPTION 'Invalid organization device transition' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_space_device BEFORE INSERT OR UPDATE OR DELETE ON collaboration_space_devices
    FOR EACH ROW EXECUTE FUNCTION protect_collaboration_space_device();

CREATE FUNCTION validate_collaboration_space_binding() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS(SELECT 1 FROM collaboration_space_devices d JOIN native_sessions s
        ON s.id=d.origin_native_session_id AND s.account_id=d.account_id
        JOIN accounts a ON a.id=d.account_id
        WHERE d.space_id=NEW.space_id AND d.id=NEW.device_id AND d.account_id=NEW.account_id
          AND s.id=NEW.native_session_id AND s.revoked_at IS NULL AND s.expires_at>NEW.bound_at
          AND s.access_expires_at>NEW.bound_at AND s.created_at<=NEW.bound_at
          AND s.credential_version=a.security_version AND a.active
          AND d.created_at<=NEW.bound_at AND d.state<>'REVOKED' AND d.revoked_at IS NULL) THEN
        RAISE EXCEPTION 'Invalid organization native binding' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER validate_collaboration_space_binding BEFORE INSERT ON collaboration_space_device_sessions
    FOR EACH ROW EXECUTE FUNCTION validate_collaboration_space_binding();
CREATE TRIGGER immutable_collaboration_space_binding BEFORE UPDATE ON collaboration_space_device_sessions
    FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

-- No automatic adoption/activation/provisioning or token transfer. A future
-- registration command owns admission, account quota, keys/store matching and
-- atomic receipt/journal/job writes. Metadata and state alone grant no gateway.
