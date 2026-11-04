-- Operational group requests have their own bounded ownership. Original company
-- runtime slots remain permanently fenced after handover. No provider token is stored.
CREATE TABLE collaboration_space_request_leases (
    space_id uuid NOT NULL,
    account_id uuid NOT NULL,
    device_id uuid NOT NULL,
    native_session_id uuid NOT NULL,
    slot integer NOT NULL CHECK(slot BETWEEN 0 AND 7),
    kind varchar(24) NOT NULL CHECK(kind IN ('GATEWAY','PROVIDER_RENEWAL')),
    gateway_id uuid,
    gateway_version bigint CHECK(gateway_version BETWEEN 0 AND 999999),
    token uuid NOT NULL UNIQUE CHECK(token<>'00000000-0000-0000-0000-000000000000'::uuid),
    started_at timestamptz NOT NULL CHECK(isfinite(started_at)),
    expires_at timestamptz NOT NULL CHECK(isfinite(expires_at) AND expires_at>started_at
        AND expires_at<=started_at+interval '60 seconds'),
    PRIMARY KEY(space_id,device_id,slot),
    FOREIGN KEY(space_id,device_id,account_id,native_session_id)
        REFERENCES collaboration_space_devices(space_id,id,account_id,origin_native_session_id),
    FOREIGN KEY(space_id,gateway_id) REFERENCES collaboration_space_gateway_sessions(space_id,id),
    CHECK((slot=0 AND kind='PROVIDER_RENEWAL' AND gateway_id IS NULL AND gateway_version IS NULL)
        OR (slot BETWEEN 1 AND 7 AND kind='GATEWAY' AND gateway_id IS NOT NULL AND gateway_version IS NOT NULL))
);
CREATE INDEX collaboration_space_request_expiry ON collaboration_space_request_leases(expires_at,space_id,device_id,slot);

ALTER TABLE collaboration_space_request_leases ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_space_request_leases FORCE ROW LEVEL SECURITY;
-- Retained request observations and exact release survive offboarding/parent purge.
-- Neither is authority to acquire or publish a provider response.
CREATE POLICY collaboration_space_request_read ON collaboration_space_request_leases FOR SELECT
    USING(current_company_id() IS NULL AND account_id=current_actor_id());
CREATE POLICY collaboration_space_request_insert ON collaboration_space_request_leases FOR INSERT
    WITH CHECK(current_company_id() IS NULL AND account_id=current_actor_id()
        AND EXISTS(SELECT 1 FROM collaboration_space_directory s WHERE s.space_id=collaboration_space_request_leases.space_id));
CREATE POLICY collaboration_space_request_update ON collaboration_space_request_leases FOR UPDATE
    USING(current_company_id() IS NULL AND account_id=current_actor_id())
    WITH CHECK(current_company_id() IS NULL AND account_id=current_actor_id()
        AND EXISTS(SELECT 1 FROM collaboration_space_directory s WHERE s.space_id=collaboration_space_request_leases.space_id));
CREATE POLICY collaboration_space_request_release ON collaboration_space_request_leases FOR DELETE
    USING(current_company_id() IS NULL AND account_id=current_actor_id());
CREATE POLICY collaboration_space_request_expired_read ON collaboration_space_request_leases FOR SELECT TO hris_worker_capability
    USING(expires_at<=clock_timestamp());
-- SELECT FOR UPDATE also needs an UPDATE visibility policy. It permits locking
-- expired rows for the delete batch, but does not permit publishing an updated row.
CREATE POLICY collaboration_space_request_expired_lock ON collaboration_space_request_leases FOR UPDATE TO hris_worker_capability
    USING(expires_at<=clock_timestamp()) WITH CHECK(false);
CREATE POLICY collaboration_space_request_expired_delete ON collaboration_space_request_leases FOR DELETE TO hris_worker_capability
    USING(expires_at<=clock_timestamp());

CREATE FUNCTION protect_collaboration_space_request_lease() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE at_time timestamptz := clock_timestamp();
BEGIN
    IF TG_OP='UPDATE' THEN
        IF (NEW.space_id,NEW.account_id,NEW.device_id,NEW.native_session_id,NEW.slot,NEW.kind)
            IS DISTINCT FROM (OLD.space_id,OLD.account_id,OLD.device_id,OLD.native_session_id,OLD.slot,OLD.kind)
            OR NEW.token=OLD.token OR OLD.expires_at>at_time OR NEW.started_at<OLD.started_at THEN
            RAISE EXCEPTION 'Organization request slot is occupied or its owner changed' USING ERRCODE='23514';
        END IF;
    END IF;
    IF current_company_id() IS NOT NULL OR NEW.account_id IS DISTINCT FROM current_actor_id()
        OR NEW.started_at>at_time OR NEW.expires_at<=at_time OR NEW.expires_at>at_time+interval '60 seconds' THEN
        RAISE EXCEPTION 'Invalid organization request lifetime' USING ERRCODE='23514';
    END IF;
    -- Callers acquire account/native/device and optional gateway before the slot.
    -- Expired worker deletion deliberately acquires none of these reverse guards.
    PERFORM 1 FROM collaboration_space_devices d
        WHERE d.space_id=NEW.space_id AND d.id=NEW.device_id AND d.account_id=NEW.account_id
            AND d.origin_native_session_id=NEW.native_session_id AND d.state='ACTIVE'
            AND d.revoked_at IS NULL AND d.updated_at<=NEW.started_at FOR UPDATE;
    IF NOT FOUND OR NOT EXISTS(
        SELECT 1 FROM collaboration_space_device_sessions b
        JOIN native_sessions n ON n.id=b.native_session_id AND n.account_id=b.account_id
        JOIN accounts a ON a.id=n.account_id
        WHERE b.space_id=NEW.space_id AND b.device_id=NEW.device_id AND b.account_id=NEW.account_id
            AND b.native_session_id=NEW.native_session_id AND b.bound_at<=NEW.started_at
            AND n.created_at<=NEW.started_at AND n.rotated_at<=NEW.started_at AND n.revoked_at IS NULL
            AND n.expires_at>=NEW.expires_at AND n.access_expires_at>=NEW.expires_at
            AND n.credential_version=a.security_version AND a.active
    ) THEN RAISE EXCEPTION 'Organization request parent is unavailable' USING ERRCODE='23514'; END IF;
    IF NEW.kind='GATEWAY' AND NOT EXISTS(
        SELECT 1 FROM collaboration_space_gateway_sessions g
        WHERE g.space_id=NEW.space_id AND g.id=NEW.gateway_id AND g.account_id=NEW.account_id
            AND g.device_id=NEW.device_id AND g.native_session_id=NEW.native_session_id
            AND g.version=NEW.gateway_version AND g.revoked_at IS NULL AND g.rotated_at<=NEW.started_at
            AND g.access_expires_at>=NEW.expires_at AND g.expires_at>=NEW.expires_at
    ) THEN RAISE EXCEPTION 'Organization gateway request is unavailable' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_space_request_lease BEFORE INSERT OR UPDATE ON collaboration_space_request_leases
    FOR EACH ROW EXECUTE FUNCTION protect_collaboration_space_request_lease();

CREATE FUNCTION claim_collaboration_space_request(
    p_space uuid,p_account uuid,p_device uuid,p_native uuid,p_kind varchar,
    p_gateway uuid,p_gateway_version bigint,p_until timestamptz
) RETURNS SETOF collaboration_space_request_leases LANGUAGE plpgsql AS $$
DECLARE at_time timestamptz := clock_timestamp(); chosen integer;
BEGIN
    IF p_space IS NULL OR p_device IS NULL OR p_native IS NULL OR p_account IS NULL
        OR p_kind IS NULL OR p_kind NOT IN ('GATEWAY','PROVIDER_RENEWAL')
        OR p_until IS NULL OR NOT isfinite(p_until) OR p_until<=at_time OR p_until>at_time+interval '60 seconds'
        OR current_company_id() IS NOT NULL OR p_account IS DISTINCT FROM current_actor_id()
        OR (p_kind='GATEWAY' AND (p_gateway IS NULL OR p_gateway_version IS NULL OR p_gateway_version NOT BETWEEN 0 AND 999999))
        OR (p_kind='PROVIDER_RENEWAL' AND (p_gateway IS NOT NULL OR p_gateway_version IS NOT NULL)) THEN
        RAISE EXCEPTION 'Invalid organization request bounds' USING ERRCODE='22023';
    END IF;
    PERFORM 1 FROM collaboration_space_devices d WHERE d.space_id=p_space AND d.id=p_device
        AND d.account_id=p_account AND d.origin_native_session_id=p_native AND d.state='ACTIVE'
        AND d.revoked_at IS NULL FOR UPDATE;
    IF NOT FOUND THEN RETURN; END IF;
    at_time := clock_timestamp();
    IF p_until<=at_time THEN RETURN; END IF;
    SELECT candidate INTO chosen FROM generate_series(
        CASE WHEN p_kind='PROVIDER_RENEWAL' THEN 0 ELSE 1 END,
        CASE WHEN p_kind='PROVIDER_RENEWAL' THEN 0 ELSE 7 END
    ) candidate LEFT JOIN collaboration_space_request_leases l
        ON l.space_id=p_space AND l.device_id=p_device AND l.slot=candidate
        WHERE l.slot IS NULL OR l.expires_at<=at_time ORDER BY candidate LIMIT 1;
    IF chosen IS NULL THEN RETURN; END IF;
    RETURN QUERY INSERT INTO collaboration_space_request_leases(
        space_id,account_id,device_id,native_session_id,slot,kind,gateway_id,gateway_version,token,started_at,expires_at
    ) VALUES(p_space,p_account,p_device,p_native,chosen,p_kind,p_gateway,p_gateway_version,gen_random_uuid(),at_time,p_until)
    ON CONFLICT(space_id,device_id,slot) DO UPDATE SET gateway_id=EXCLUDED.gateway_id,
        gateway_version=EXCLUDED.gateway_version,token=EXCLUDED.token,started_at=EXCLUDED.started_at,expires_at=EXCLUDED.expires_at
    RETURNING *;
END $$;

-- Deleting completed/expired operational leases never erases device/key provenance.
-- Worker source calls must use a finite ordered SKIP LOCKED batch; current application
-- admission and exact lease revalidation remain required before/after provider I/O.
-- This migration enables no provider renewal, gateway exchange or Mobile caller.
