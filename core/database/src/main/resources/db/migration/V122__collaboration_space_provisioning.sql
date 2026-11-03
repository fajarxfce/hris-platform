-- One bounded operational head per organization device. No provider secret or
-- participant key is stored here; a claim supplies coordinates, not content access.
CREATE TABLE collaboration_space_provisioning (
    space_id uuid NOT NULL,
    device_id uuid NOT NULL,
    account_id uuid NOT NULL,
    native_session_id uuid NOT NULL,
    revision bigint NOT NULL CHECK(revision BETWEEN 0 AND 999999),
    native_session_version bigint NOT NULL CHECK(native_session_version>=0),
    credential_version bigint NOT NULL CHECK(credential_version>=0),
    authenticated_at timestamptz NOT NULL CHECK(isfinite(authenticated_at)),
    mfa_verified_at timestamptz CHECK(mfa_verified_at IS NULL OR isfinite(mfa_verified_at)),
    correlation_id uuid NOT NULL,
    client_platform varchar(16) NOT NULL CHECK(client_platform IN ('ANDROID','IOS')),
    client_build bigint NOT NULL CHECK(client_build BETWEEN 1 AND 999999999),
    created_at timestamptz NOT NULL CHECK(isfinite(created_at)),
    requested_at timestamptz NOT NULL CHECK(isfinite(requested_at) AND requested_at>=created_at),
    expires_at timestamptz NOT NULL CHECK(isfinite(expires_at) AND expires_at>requested_at
        AND expires_at<=requested_at+interval '5 minutes'),
    available_at timestamptz NOT NULL CHECK(isfinite(available_at) AND available_at>=requested_at AND available_at<=expires_at),
    state varchar(16) NOT NULL CHECK(state IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
    attempts integer NOT NULL CHECK(attempts BETWEEN 0 AND 8),
    lease_owner uuid,
    lease_token uuid,
    lease_until timestamptz CHECK(lease_until IS NULL OR (isfinite(lease_until) AND lease_until>requested_at AND lease_until<=expires_at)),
    completed_at timestamptz CHECK(completed_at IS NULL OR (isfinite(completed_at) AND completed_at>=requested_at)),
    failure_code varchar(40) CHECK(failure_code IN ('REQUEST_EXPIRED','ATTEMPTS_EXHAUSTED','ACCESS_DENIED','PROVIDER_UNAVAILABLE','PROVIDER_IDENTITY_CHANGED','PUBLICATION_CHANGED')),
    last_outcome_revision bigint CHECK(last_outcome_revision BETWEEN 0 AND revision),
    last_outcome_state varchar(16) CHECK(last_outcome_state IN ('SUCCEEDED','FAILED','CANCELLED')),
    last_outcome_at timestamptz CHECK(last_outcome_at IS NULL OR (isfinite(last_outcome_at) AND last_outcome_at>=created_at)),
    last_failure_code varchar(40) CHECK(last_failure_code IN ('REQUEST_EXPIRED','ATTEMPTS_EXHAUSTED','ACCESS_DENIED','PROVIDER_UNAVAILABLE','PROVIDER_IDENTITY_CHANGED','PUBLICATION_CHANGED')),
    PRIMARY KEY(space_id,device_id),
    FOREIGN KEY(space_id,device_id,account_id,native_session_id)
        REFERENCES collaboration_space_devices(space_id,id,account_id,origin_native_session_id),
    CHECK(authenticated_at<=requested_at AND (mfa_verified_at IS NULL OR mfa_verified_at<=requested_at)),
    CHECK((state='RUNNING' AND attempts>0 AND lease_owner IS NOT NULL AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (state<>'RUNNING' AND lease_owner IS NULL AND lease_token IS NULL AND lease_until IS NULL)),
    CHECK((state IN ('SUCCEEDED','FAILED','CANCELLED'))=(completed_at IS NOT NULL)),
    CHECK((state='FAILED')=(failure_code IS NOT NULL)),
    CHECK((last_outcome_revision IS NULL AND last_outcome_state IS NULL AND last_outcome_at IS NULL AND last_failure_code IS NULL)
        OR (last_outcome_revision IS NOT NULL AND last_outcome_state IS NOT NULL AND last_outcome_at IS NOT NULL
            AND (last_outcome_state='FAILED')=(last_failure_code IS NOT NULL))),
    CHECK(completed_at IS NULL OR (last_outcome_revision IS NOT NULL AND last_outcome_state IS NOT NULL AND last_outcome_at IS NOT NULL
        AND last_outcome_revision=revision AND last_outcome_state=state
        AND last_outcome_at=completed_at AND last_failure_code IS NOT DISTINCT FROM failure_code))
);
CREATE INDEX collaboration_space_provisioning_due ON collaboration_space_provisioning(available_at,space_id,device_id)
    WHERE state IN ('QUEUED','RUNNING') AND attempts<8;
CREATE INDEX collaboration_space_provisioning_expiry ON collaboration_space_provisioning(expires_at,space_id,device_id)
    WHERE state IN ('QUEUED','RUNNING');
CREATE INDEX collaboration_space_provisioning_leased ON collaboration_space_provisioning(lease_until)
    WHERE state='RUNNING';
CREATE INDEX collaboration_space_provisioning_exhausted ON collaboration_space_provisioning(lease_until,space_id,device_id)
    WHERE state='RUNNING' AND attempts=8;
ALTER TABLE collaboration_space_provisioning ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_space_provisioning FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_space_provisioning_read ON collaboration_space_provisioning FOR SELECT
    USING(current_company_id() IS NULL AND account_id=current_actor_id());
CREATE POLICY collaboration_space_provisioning_insert ON collaboration_space_provisioning FOR INSERT
    WITH CHECK(current_company_id() IS NULL AND account_id=current_actor_id()
        AND EXISTS(SELECT 1 FROM collaboration_space_directory s WHERE s.space_id=collaboration_space_provisioning.space_id));
CREATE POLICY collaboration_space_provisioning_update ON collaboration_space_provisioning FOR UPDATE
    USING(current_company_id() IS NULL AND account_id=current_actor_id())
    WITH CHECK(current_company_id() IS NULL AND account_id=current_actor_id()
        AND (state='CANCELLED' OR EXISTS(SELECT 1 FROM collaboration_space_directory s WHERE s.space_id=collaboration_space_provisioning.space_id)));
CREATE POLICY collaboration_space_provisioning_worker ON collaboration_space_provisioning TO hris_worker_capability
    USING(true) WITH CHECK(true);

CREATE FUNCTION protect_collaboration_space_provisioning() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE at_time timestamptz := clock_timestamp(); new_request boolean;
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Organization provisioning identity is retained' USING ERRCODE='23514';
    END IF;
    new_request := TG_OP='INSERT';
    IF TG_OP='INSERT' THEN
        IF NEW.revision<>0 OR NEW.created_at<>NEW.requested_at OR NEW.last_outcome_revision IS NOT NULL THEN
            RAISE EXCEPTION 'Invalid initial organization provisioning request' USING ERRCODE='23514';
        END IF;
    ELSE
        IF (NEW.space_id,NEW.device_id,NEW.account_id,NEW.native_session_id,NEW.created_at)
            IS DISTINCT FROM (OLD.space_id,OLD.device_id,OLD.account_id,OLD.native_session_id,OLD.created_at) THEN
            RAISE EXCEPTION 'Organization provisioning owner is immutable' USING ERRCODE='23514';
        END IF;
        IF NEW.revision<>OLD.revision THEN
            IF NEW.revision<>OLD.revision+1 OR OLD.state NOT IN ('SUCCEEDED','FAILED','CANCELLED')
                OR NEW.requested_at<OLD.completed_at
                OR (NEW.last_outcome_revision,NEW.last_outcome_state,NEW.last_outcome_at,NEW.last_failure_code)
                    IS DISTINCT FROM (OLD.last_outcome_revision,OLD.last_outcome_state,OLD.last_outcome_at,OLD.last_failure_code) THEN
                RAISE EXCEPTION 'Invalid organization provisioning retry' USING ERRCODE='23514';
            END IF;
            new_request := true;
        END IF;
    END IF;
    IF new_request THEN
        IF NEW.state<>'QUEUED' OR NEW.attempts<>0 OR NEW.available_at<>NEW.requested_at
            OR NEW.lease_token IS NOT NULL OR NEW.completed_at IS NOT NULL OR NEW.failure_code IS NOT NULL
            OR NEW.requested_at>at_time+interval '1 second' OR NEW.expires_at<=at_time THEN
            RAISE EXCEPTION 'Invalid organization provisioning request' USING ERRCODE='23514';
        END IF;
        PERFORM 1 FROM collaboration_space_devices d
            JOIN native_sessions s ON s.id=d.origin_native_session_id AND s.account_id=d.account_id
            JOIN accounts a ON a.id=s.account_id
            WHERE d.space_id=NEW.space_id AND d.id=NEW.device_id AND d.account_id=NEW.account_id
                AND d.origin_native_session_id=NEW.native_session_id AND d.platform=NEW.client_platform
                AND d.state IN ('PROVISIONING','ACTIVE') AND d.version<999999
                AND d.updated_at<=NEW.requested_at
                AND s.version=NEW.native_session_version AND s.credential_version=NEW.credential_version
                AND s.credential_version=a.security_version AND a.active AND s.revoked_at IS NULL
                AND s.authenticated_at=NEW.authenticated_at AND s.mfa_verified_at IS NOT DISTINCT FROM NEW.mfa_verified_at
                AND s.created_at<=NEW.requested_at AND s.rotated_at<=NEW.requested_at
                AND s.expires_at>=NEW.expires_at AND s.access_expires_at>=NEW.expires_at
                AND EXISTS(SELECT 1 FROM collaboration_space_device_sessions b
                    WHERE b.space_id=d.space_id AND b.device_id=d.id AND b.account_id=d.account_id
                        AND b.native_session_id=s.id AND b.bound_at<=NEW.requested_at)
            FOR SHARE OF d;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Organization provisioning parent is unavailable' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF (NEW.native_session_version,NEW.credential_version,NEW.authenticated_at,NEW.mfa_verified_at,
        NEW.correlation_id,NEW.client_platform,NEW.client_build,NEW.requested_at,NEW.expires_at)
        IS DISTINCT FROM (OLD.native_session_version,OLD.credential_version,OLD.authenticated_at,OLD.mfa_verified_at,
        OLD.correlation_id,OLD.client_platform,OLD.client_build,OLD.requested_at,OLD.expires_at)
        OR OLD.state NOT IN ('QUEUED','RUNNING') THEN
        RAISE EXCEPTION 'Organization provisioning intent is immutable' USING ERRCODE='23514';
    END IF;
    IF NEW.state='CANCELLED' AND current_company_id() IS NULL AND NEW.account_id=current_actor_id() THEN
        IF NEW.attempts<>OLD.attempts OR NEW.available_at<>OLD.available_at OR NEW.completed_at>at_time+interval '1 second' THEN
            RAISE EXCEPTION 'Invalid organization provisioning cancellation' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF NOT pg_has_role(current_user,'hris_worker_capability','member') THEN
        RAISE EXCEPTION 'Only a worker advances organization provisioning' USING ERRCODE='23514';
    END IF;
    IF NEW.state='RUNNING' AND NEW.lease_token IS DISTINCT FROM OLD.lease_token THEN
        IF NEW.attempts<>OLD.attempts+1 OR NEW.available_at<>OLD.available_at
            OR OLD.available_at>at_time OR OLD.expires_at<=at_time
            OR (OLD.lease_until IS NOT NULL AND OLD.lease_until>at_time)
            OR NEW.lease_until<=at_time OR NEW.lease_until>at_time+interval '60 seconds' THEN
            RAISE EXCEPTION 'Invalid organization provisioning claim' USING ERRCODE='23514';
        END IF;
    ELSIF OLD.state='RUNNING' AND OLD.lease_until>at_time AND NEW.state IN ('QUEUED','SUCCEEDED','FAILED') THEN
        IF NEW.attempts<>OLD.attempts OR (NEW.state='QUEUED' AND
            (NEW.attempts>=8 OR NEW.available_at<at_time OR NEW.available_at>at_time+interval '300 seconds'))
            OR (NEW.state<>'QUEUED' AND (NEW.available_at<>OLD.available_at OR NEW.completed_at>at_time+interval '1 second')) THEN
            RAISE EXCEPTION 'Invalid organization provisioning completion' USING ERRCODE='23514';
        END IF;
    ELSIF NEW.state='FAILED' AND (OLD.lease_until IS NULL OR OLD.lease_until<=at_time)
        AND (OLD.expires_at<=at_time OR OLD.attempts>=8) THEN
        IF NEW.attempts<>OLD.attempts OR NEW.available_at<>OLD.available_at OR NEW.completed_at>at_time+interval '1 second'
            OR NOT ((NEW.failure_code='REQUEST_EXPIRED' AND OLD.expires_at<=at_time)
                OR (NEW.failure_code='ATTEMPTS_EXHAUSTED' AND OLD.attempts>=8)) THEN
            RAISE EXCEPTION 'Invalid organization provisioning expiration' USING ERRCODE='23514';
        END IF;
    ELSE
        RAISE EXCEPTION 'Organization provisioning transition requires a current lease' USING ERRCODE='23514';
    END IF;
    IF NEW.state IN ('QUEUED','RUNNING') AND
        (NEW.last_outcome_revision,NEW.last_outcome_state,NEW.last_outcome_at,NEW.last_failure_code)
            IS DISTINCT FROM (OLD.last_outcome_revision,OLD.last_outcome_state,OLD.last_outcome_at,OLD.last_failure_code) THEN
        RAISE EXCEPTION 'Previous organization provisioning outcome is retained' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_space_provisioning BEFORE INSERT OR UPDATE OR DELETE ON collaboration_space_provisioning
    FOR EACH ROW EXECUTE FUNCTION protect_collaboration_space_provisioning();

-- Queue-only transaction. Commit before acquiring any account/native/device guard.
-- Expiration retains a terminal operational outcome; it never touches device state,
-- credentials or provider keys. Business publication is a separate admitted command.
CREATE FUNCTION claim_collaboration_space_provisioning(p_owner uuid,p_limit integer,p_lease_seconds integer)
RETURNS SETOF collaboration_space_provisioning LANGUAGE plpgsql SECURITY INVOKER AS $$
DECLARE at_time timestamptz := clock_timestamp(); capacity integer;
BEGIN
    IF p_owner IS NULL OR p_limit IS NULL OR p_lease_seconds IS NULL
        OR p_limit NOT BETWEEN 1 AND 8 OR p_lease_seconds NOT BETWEEN 1 AND 60 THEN
        RAISE EXCEPTION 'Invalid organization provisioning claim bounds' USING ERRCODE='22023';
    END IF;
    IF NOT pg_try_advisory_xact_lock(hashtextextended('hris:collaboration-space-provisioning-claims',0)) THEN RETURN; END IF;
    -- Separate indexed time ranges avoid an OR across expiry and exhausted leases.
    -- Each side returns at most eight; overlap is deduplicated before terminalization.
    WITH expired AS (
        SELECT space_id,device_id FROM collaboration_space_provisioning
        WHERE state IN ('QUEUED','RUNNING') AND expires_at<=at_time
        ORDER BY expires_at,space_id,device_id FOR UPDATE SKIP LOCKED LIMIT 8
    ), exhausted AS (
        SELECT space_id,device_id FROM collaboration_space_provisioning
        WHERE state='RUNNING' AND attempts=8 AND lease_until<=at_time
        ORDER BY lease_until,space_id,device_id FOR UPDATE SKIP LOCKED LIMIT 8
    ), ended AS (
        SELECT * FROM expired UNION SELECT * FROM exhausted
    ) UPDATE collaboration_space_provisioning h SET state='FAILED',completed_at=at_time,
        failure_code=CASE WHEN h.expires_at<=at_time THEN 'REQUEST_EXPIRED' ELSE 'ATTEMPTS_EXHAUSTED' END,
        last_outcome_revision=h.revision,last_outcome_state='FAILED',last_outcome_at=at_time,
        last_failure_code=CASE WHEN h.expires_at<=at_time THEN 'REQUEST_EXPIRED' ELSE 'ATTEMPTS_EXHAUSTED' END,
        lease_owner=NULL,lease_token=NULL,lease_until=NULL
        FROM ended e WHERE h.space_id=e.space_id AND h.device_id=e.device_id;
    SELECT greatest(0,16-count(*)) INTO capacity FROM (
        SELECT 1 FROM collaboration_space_provisioning WHERE state='RUNNING' AND lease_until>at_time LIMIT 16
    ) active;
    IF capacity=0 THEN RETURN; END IF;
    RETURN QUERY WITH candidates AS (
        SELECT space_id,device_id FROM collaboration_space_provisioning
        WHERE state IN ('QUEUED','RUNNING') AND expires_at>at_time AND available_at<=at_time
            AND (lease_until IS NULL OR lease_until<=at_time) AND attempts<8
        ORDER BY available_at,space_id,device_id FOR UPDATE SKIP LOCKED LIMIT least(p_limit,capacity)
    ) UPDATE collaboration_space_provisioning h SET state='RUNNING',attempts=h.attempts+1,
        lease_owner=p_owner,lease_token=gen_random_uuid(),lease_until=least(at_time+make_interval(secs=>p_lease_seconds),h.expires_at)
        FROM candidates c WHERE h.space_id=c.space_id AND h.device_id=c.device_id RETURNING h.*;
END $$;
REVOKE ALL ON FUNCTION claim_collaboration_space_provisioning(uuid,integer,integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION claim_collaboration_space_provisioning(uuid,integer,integer) TO hris_worker_capability;

-- Initially empty. Native registration/retry and admitted worker publication must
-- integrate these records with operation receipts and business audit/outbox before
-- enabling group endpoints. A global worker claim cannot authorize a provider call.
