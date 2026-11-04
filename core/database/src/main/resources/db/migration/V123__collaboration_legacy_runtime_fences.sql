-- A retained legacy runtime has one durable owner and at most eight reusable
-- in-flight slots. No provider credential, SDK store or E2EE key is moved here.
-- All old callers must claim before network I/O before transfer can be exposed.
CREATE TABLE collaboration_legacy_runtime (
    company_id uuid NOT NULL,
    device_id uuid NOT NULL,
    account_id uuid NOT NULL,
    native_session_id uuid NOT NULL,
    phase varchar(16) NOT NULL CHECK(phase IN ('LEGACY','DRAINING')),
    version bigint NOT NULL CHECK(version IN (0,1)),
    created_at timestamptz NOT NULL CHECK(isfinite(created_at)),
    frozen_at timestamptz CHECK(frozen_at IS NULL OR (isfinite(frozen_at) AND frozen_at>=created_at)),
    target_space_id uuid,
    target_device_id uuid,
    PRIMARY KEY(company_id,device_id),
    UNIQUE(company_id,device_id,account_id,native_session_id),
    FOREIGN KEY(company_id,device_id,account_id) REFERENCES collaboration_devices(company_id,id,account_id),
    FOREIGN KEY(target_space_id,target_device_id,account_id,native_session_id)
        REFERENCES collaboration_space_devices(space_id,id,account_id,origin_native_session_id),
    CHECK((phase='LEGACY' AND version=0 AND frozen_at IS NULL AND target_space_id IS NULL AND target_device_id IS NULL)
        OR (phase='DRAINING' AND version=1 AND frozen_at IS NOT NULL AND target_space_id IS NOT NULL AND target_device_id IS NOT NULL))
);
CREATE INDEX collaboration_legacy_runtime_owner ON collaboration_legacy_runtime(account_id,company_id,device_id);
ALTER TABLE collaboration_legacy_runtime ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_legacy_runtime FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_legacy_runtime_read ON collaboration_legacy_runtime FOR SELECT
    USING(account_id=current_actor_id());
CREATE POLICY collaboration_legacy_runtime_insert ON collaboration_legacy_runtime FOR INSERT
    WITH CHECK(account_id=current_actor_id() AND (current_company_id() IS NULL OR company_id=current_company_id()));
CREATE POLICY collaboration_legacy_runtime_update ON collaboration_legacy_runtime FOR UPDATE
    USING(account_id=current_actor_id() AND (current_company_id() IS NULL OR company_id=current_company_id()))
    WITH CHECK(account_id=current_actor_id() AND (current_company_id() IS NULL OR company_id=current_company_id()));

CREATE FUNCTION protect_collaboration_legacy_runtime() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE at_time timestamptz := clock_timestamp();
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Legacy runtime ownership is retained' USING ERRCODE='23514';
    ELSIF TG_OP='INSERT' THEN
        IF NEW.phase<>'LEGACY' OR NEW.created_at>at_time+interval '1 second' OR NOT EXISTS(
            SELECT 1 FROM collaboration_device_sessions b
            WHERE b.company_id=NEW.company_id AND b.device_id=NEW.device_id
                AND b.account_id=NEW.account_id AND b.native_session_id=NEW.native_session_id
                AND b.bound_at<=NEW.created_at
        ) THEN
            RAISE EXCEPTION 'Invalid initial legacy runtime owner' USING ERRCODE='23514';
        END IF;
    ELSE
        IF (NEW.company_id,NEW.device_id,NEW.account_id,NEW.native_session_id,NEW.created_at)
            IS DISTINCT FROM (OLD.company_id,OLD.device_id,OLD.account_id,OLD.native_session_id,OLD.created_at)
            OR OLD.phase<>'LEGACY' OR NEW.phase<>'DRAINING' OR NEW.version<>OLD.version+1
            OR NEW.frozen_at>at_time+interval '1 second' OR current_company_id() IS NOT NULL
            OR NOT EXISTS(
                SELECT 1 FROM collaboration_space_devices d
                WHERE d.space_id=NEW.target_space_id AND d.id=NEW.target_device_id
                    AND d.account_id=NEW.account_id AND d.origin_native_session_id=NEW.native_session_id
                    AND d.origin='LEGACY' AND d.legacy_company_id=NEW.company_id AND d.legacy_device_id=NEW.device_id
                    AND d.state='PROVISIONING' AND d.created_at<=NEW.frozen_at
            ) THEN
            RAISE EXCEPTION 'Invalid legacy runtime freeze' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_legacy_runtime BEFORE INSERT OR UPDATE OR DELETE ON collaboration_legacy_runtime
    FOR EACH ROW EXECUTE FUNCTION protect_collaboration_legacy_runtime();

CREATE TABLE collaboration_legacy_runtime_leases (
    company_id uuid NOT NULL,
    device_id uuid NOT NULL,
    account_id uuid NOT NULL,
    native_session_id uuid NOT NULL,
    slot integer NOT NULL CHECK(slot BETWEEN 0 AND 7),
    runtime_version bigint NOT NULL CHECK(runtime_version=0),
    token uuid NOT NULL,
    started_at timestamptz NOT NULL CHECK(isfinite(started_at)),
    expires_at timestamptz NOT NULL CHECK(isfinite(expires_at) AND expires_at>started_at AND expires_at<=started_at+interval '60 seconds'),
    released_at timestamptz CHECK(released_at IS NULL OR (isfinite(released_at) AND released_at>=started_at)),
    PRIMARY KEY(company_id,device_id,slot),
    FOREIGN KEY(company_id,device_id,account_id,native_session_id)
        REFERENCES collaboration_legacy_runtime(company_id,device_id,account_id,native_session_id)
);
ALTER TABLE collaboration_legacy_runtime_leases ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_legacy_runtime_leases FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_legacy_lease_read ON collaboration_legacy_runtime_leases FOR SELECT
    USING(account_id=current_actor_id());
CREATE POLICY collaboration_legacy_lease_insert ON collaboration_legacy_runtime_leases FOR INSERT
    WITH CHECK(company_id=current_company_id() AND account_id=current_actor_id());
CREATE POLICY collaboration_legacy_lease_update ON collaboration_legacy_runtime_leases FOR UPDATE
    USING(account_id=current_actor_id() AND (current_company_id() IS NULL OR company_id=current_company_id()))
    WITH CHECK(account_id=current_actor_id() AND (current_company_id() IS NULL OR company_id=current_company_id()));

CREATE FUNCTION protect_collaboration_legacy_runtime_lease() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE at_time timestamptz := clock_timestamp();
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Legacy runtime lease slots are retained' USING ERRCODE='23514';
    END IF;
    IF TG_OP='UPDATE' THEN
        IF (NEW.company_id,NEW.device_id,NEW.account_id,NEW.native_session_id,NEW.slot)
            IS DISTINCT FROM (OLD.company_id,OLD.device_id,OLD.account_id,OLD.native_session_id,OLD.slot) THEN
            RAISE EXCEPTION 'Legacy runtime lease owner is immutable' USING ERRCODE='23514';
        END IF;
        IF NEW.token=OLD.token THEN
            IF (NEW.runtime_version,NEW.started_at,NEW.expires_at)
                IS DISTINCT FROM (OLD.runtime_version,OLD.started_at,OLD.expires_at)
                OR OLD.released_at IS NOT NULL OR NEW.released_at IS NULL OR NEW.released_at>at_time+interval '1 second' THEN
                RAISE EXCEPTION 'Invalid legacy runtime lease release' USING ERRCODE='23514';
            END IF;
            -- Exact release works after freeze, offboarding and native-parent purge.
            -- It does not reacquire a runtime or extend any authority.
            RETURN NEW;
        END IF;
        IF (OLD.released_at IS NULL AND OLD.expires_at>at_time) OR NEW.started_at<OLD.started_at THEN
            RAISE EXCEPTION 'Legacy runtime slot is still occupied' USING ERRCODE='23514';
        END IF;
    END IF;
    IF current_company_id() IS DISTINCT FROM NEW.company_id OR NEW.account_id IS DISTINCT FROM current_actor_id()
        OR NEW.released_at IS NOT NULL OR NEW.started_at>at_time+interval '1 second'
        OR NEW.expires_at<=at_time OR NEW.expires_at>at_time+interval '60 seconds' THEN
        RAISE EXCEPTION 'Invalid legacy runtime claim' USING ERRCODE='23514';
    END IF;
    -- The parent row serializes a claim against freeze. Use cases acquire account
    -- and native admission guards before this row and commit before network I/O.
    PERFORM 1 FROM collaboration_legacy_runtime r
        WHERE r.company_id=NEW.company_id AND r.device_id=NEW.device_id AND r.account_id=NEW.account_id
            AND r.native_session_id=NEW.native_session_id AND r.phase='LEGACY'
            AND r.version=NEW.runtime_version AND r.created_at<=NEW.started_at FOR UPDATE;
    IF NOT FOUND OR NOT EXISTS(
        SELECT 1 FROM collaboration_device_sessions b
        JOIN collaboration_devices d ON d.company_id=b.company_id AND d.id=b.device_id AND d.account_id=b.account_id
        JOIN native_sessions s ON s.id=b.native_session_id AND s.account_id=b.account_id
        JOIN accounts a ON a.id=s.account_id
        WHERE b.company_id=NEW.company_id AND b.device_id=NEW.device_id AND b.account_id=NEW.account_id
            AND b.native_session_id=NEW.native_session_id AND b.bound_at<=NEW.started_at
            AND d.state IN ('PROVISIONING','ACTIVE') AND d.updated_at<=NEW.started_at
            AND a.active AND s.credential_version=a.security_version AND s.revoked_at IS NULL
            AND s.created_at<=NEW.started_at AND s.rotated_at<=NEW.started_at
            AND s.expires_at>=NEW.expires_at AND s.access_expires_at>=NEW.expires_at
    ) THEN
        RAISE EXCEPTION 'Legacy runtime is unavailable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_legacy_runtime_lease BEFORE INSERT OR UPDATE OR DELETE ON collaboration_legacy_runtime_leases
    FOR EACH ROW EXECUTE FUNCTION protect_collaboration_legacy_runtime_lease();

-- One bounded claim. No retry or provider work runs in SQL. A caller must separately
-- validate the current native proof, company/MFA/client policy and fixed operation.
CREATE FUNCTION claim_collaboration_legacy_runtime(
    p_company uuid,p_device uuid,p_account uuid,p_native uuid,p_until timestamptz
) RETURNS SETOF collaboration_legacy_runtime_leases LANGUAGE plpgsql SECURITY INVOKER AS $$
DECLARE at_time timestamptz := clock_timestamp(); chosen integer;
BEGIN
    IF p_company IS NULL OR p_device IS NULL OR p_account IS NULL OR p_native IS NULL OR p_until IS NULL
        OR NOT isfinite(p_until) OR p_until<=at_time OR p_until>at_time+interval '60 seconds'
        OR p_account IS DISTINCT FROM current_actor_id() OR p_company IS DISTINCT FROM current_company_id() THEN
        RAISE EXCEPTION 'Invalid legacy runtime claim bounds' USING ERRCODE='22023';
    END IF;
    PERFORM 1 FROM collaboration_legacy_runtime r
        WHERE r.company_id=p_company AND r.device_id=p_device AND r.account_id=p_account
            AND r.native_session_id=p_native AND r.phase='LEGACY' AND r.version=0 FOR UPDATE;
    IF NOT FOUND THEN RETURN; END IF;
    -- Recompute database time after waiting for ownership. A waited-out deadline
    -- cannot be reclaimed or returned as a fresh lease.
    at_time := clock_timestamp();
    IF p_until<=at_time THEN RETURN; END IF;
    SELECT candidate INTO chosen FROM generate_series(0,7) candidate
        LEFT JOIN collaboration_legacy_runtime_leases l
            ON l.company_id=p_company AND l.device_id=p_device AND l.slot=candidate
        WHERE l.slot IS NULL OR l.released_at IS NOT NULL OR l.expires_at<=at_time
        ORDER BY candidate LIMIT 1;
    IF chosen IS NULL THEN RETURN; END IF;
    RETURN QUERY INSERT INTO collaboration_legacy_runtime_leases(
        company_id,device_id,account_id,native_session_id,slot,runtime_version,token,started_at,expires_at
    ) VALUES(p_company,p_device,p_account,p_native,chosen,0,gen_random_uuid(),at_time,p_until)
    ON CONFLICT(company_id,device_id,slot) DO UPDATE SET runtime_version=EXCLUDED.runtime_version,
        token=EXCLUDED.token,started_at=EXCLUDED.started_at,expires_at=EXCLUDED.expires_at,released_at=NULL
    RETURNING *;
END $$;

-- DRAINING permanently fences old acquisition. Completion requires a later admitted
-- command that atomically publishes the group credential and retires legacy access.
-- Expired slots allow bounded crash recovery; they do not prove provider rollback.
-- This migration neither freezes devices nor claims that SDK clients have drained.
