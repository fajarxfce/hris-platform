-- Group room identity is separate from a conversation's creator and from the
-- current native device performing preparation. No provider secret lives here.
CREATE TABLE collaboration_space_room_intents (
    space_id uuid NOT NULL,
    conversation_id uuid NOT NULL,
    first_account_id uuid NOT NULL REFERENCES accounts(id),
    second_account_id uuid NOT NULL REFERENCES accounts(id),
    requested_by uuid NOT NULL REFERENCES accounts(id),
    matrix_creator_account_id uuid NOT NULL REFERENCES accounts(id),
    matrix_server_name varchar(180) NOT NULL CHECK(length(matrix_server_name)>0 AND matrix_server_name !~ '[[:space:][:cntrl:]/?#@]'),
    first_matrix_user_id varchar(255) NOT NULL,
    second_matrix_user_id varchar(255) NOT NULL,
    origin varchar(16) NOT NULL CHECK(origin IN ('SPACE','LEGACY')),
    legacy_company_id uuid,
    legacy_conversation_id uuid,
    legacy_room_id varchar(255),
    legacy_bound_at timestamptz CHECK(legacy_bound_at IS NULL OR isfinite(legacy_bound_at)),
    created_at timestamptz NOT NULL CHECK(isfinite(created_at)),
    PRIMARY KEY(space_id,conversation_id),
    UNIQUE(space_id,conversation_id,requested_by),
    UNIQUE(legacy_company_id,legacy_conversation_id),
    FOREIGN KEY(space_id,conversation_id) REFERENCES collaboration_space_direct_conversations(space_id,id),
    FOREIGN KEY(legacy_company_id,legacy_conversation_id) REFERENCES collaboration_direct_room_bindings(company_id,conversation_id),
    CHECK(first_account_id<second_account_id AND requested_by IN(first_account_id,second_account_id)
        AND matrix_creator_account_id IN(first_account_id,second_account_id)),
    CHECK(first_matrix_user_id<>second_matrix_user_id),
    CHECK((origin='SPACE' AND matrix_creator_account_id=requested_by AND legacy_company_id IS NULL
        AND legacy_conversation_id IS NULL AND legacy_room_id IS NULL AND legacy_bound_at IS NULL)
        OR (origin='LEGACY' AND legacy_company_id IS NOT NULL AND legacy_conversation_id IS NOT NULL
            AND legacy_room_id IS NOT NULL AND legacy_bound_at IS NOT NULL AND legacy_bound_at<=created_at))
);
ALTER TABLE collaboration_space_room_intents ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_space_room_intents FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_space_room_intent_read ON collaboration_space_room_intents FOR SELECT
    USING(current_company_id() IS NULL AND current_actor_id() IN(first_account_id,second_account_id));
CREATE POLICY collaboration_space_room_intent_insert ON collaboration_space_room_intents FOR INSERT
    WITH CHECK(current_company_id() IS NULL AND current_actor_id()=requested_by);
CREATE FUNCTION validate_collaboration_space_room_intent() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE original_company uuid;
BEGIN
    IF NEW.created_at>clock_timestamp() OR NOT EXISTS(
        SELECT 1 FROM collaboration_space_direct_conversations c
        JOIN collaboration_space_members first ON first.space_id=c.space_id AND first.account_id=c.first_account_id
        JOIN collaboration_space_members second ON second.space_id=c.space_id AND second.account_id=c.second_account_id
        WHERE c.space_id=NEW.space_id AND c.id=NEW.conversation_id
            AND c.first_account_id=NEW.first_account_id AND c.second_account_id=NEW.second_account_id
            AND c.created_at<=NEW.created_at
            AND first.matrix_server_name=NEW.matrix_server_name AND first.matrix_user_id=NEW.first_matrix_user_id
            AND second.matrix_server_name=NEW.matrix_server_name AND second.matrix_user_id=NEW.second_matrix_user_id
            AND first.identity_selected_at<=NEW.created_at AND second.identity_selected_at<=NEW.created_at
    ) THEN RAISE EXCEPTION 'Organization room participants are unavailable' USING ERRCODE='23514'; END IF;
    -- A matching original conversation can carry an existing or uncertain room.
    -- Its canonical pair cannot silently start replacement group history.
    IF NEW.origin='SPACE' AND NEW.first_matrix_user_id ~ '^@hris_[a-f0-9]{32}_' THEN
        original_company := substring(NEW.first_matrix_user_id from 7 for 32)::uuid;
        IF NEW.second_matrix_user_id='@hris_'||replace(original_company::text,'-','')||'_'||replace(NEW.second_account_id::text,'-','')||':'||NEW.matrix_server_name
            AND EXISTS(SELECT 1 FROM collaboration_space_legacy_conversations original
                WHERE original.space_id=NEW.space_id AND original.legacy_company_id=original_company
                    AND original.first_account_id=NEW.first_account_id AND original.second_account_id=NEW.second_account_id) THEN
            RAISE EXCEPTION 'Original organization conversation requires explicit room recovery' USING ERRCODE='23514';
        END IF;
    END IF;
    IF NEW.origin='LEGACY' AND (
        NEW.first_matrix_user_id<>'@hris_'||replace(NEW.legacy_company_id::text,'-','')||'_'||replace(NEW.first_account_id::text,'-','')||':'||NEW.matrix_server_name
        OR NEW.second_matrix_user_id<>'@hris_'||replace(NEW.legacy_company_id::text,'-','')||'_'||replace(NEW.second_account_id::text,'-','')||':'||NEW.matrix_server_name
        OR NOT EXISTS(SELECT 1 FROM collaboration_space_legacy_conversations original
            WHERE original.space_id=NEW.space_id AND original.legacy_company_id=NEW.legacy_company_id
                AND original.legacy_conversation_id=NEW.legacy_conversation_id
                AND original.first_account_id=NEW.first_account_id AND original.second_account_id=NEW.second_account_id
                AND original.matrix_server_name=NEW.matrix_server_name AND original.matrix_room_id=NEW.legacy_room_id
                AND original.room_created_by=NEW.matrix_creator_account_id AND original.room_bound_at=NEW.legacy_bound_at)
    ) THEN RAISE EXCEPTION 'Original organization room identity changed' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER validate_collaboration_space_room_intent BEFORE INSERT ON collaboration_space_room_intents
    FOR EACH ROW EXECUTE FUNCTION validate_collaboration_space_room_intent();
CREATE TRIGGER immutable_collaboration_space_room_intent BEFORE UPDATE OR DELETE ON collaboration_space_room_intents
    FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();


-- One bounded operational head per organization conversation. No provider secret or
-- participant key is stored here; a claim supplies coordinates, not content access.
CREATE TABLE collaboration_space_room_provisioning (
    space_id uuid NOT NULL,
    conversation_id uuid NOT NULL,
    device_id uuid NOT NULL,
    device_version bigint NOT NULL CHECK(device_version BETWEEN 0 AND 999999),
    provider_generation bigint NOT NULL CHECK(provider_generation BETWEEN 0 AND 999999),
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
    failure_code varchar(40) CHECK(failure_code IN ('REQUEST_EXPIRED','ATTEMPTS_EXHAUSTED','ACCESS_DENIED','PROVIDER_UNAVAILABLE','PROVIDER_IDENTITY_CHANGED','PUBLICATION_CHANGED','ROOM_MISMATCH')),
    last_outcome_revision bigint CHECK(last_outcome_revision BETWEEN 0 AND revision),
    last_outcome_state varchar(16) CHECK(last_outcome_state IN ('SUCCEEDED','FAILED','CANCELLED')),
    last_outcome_at timestamptz CHECK(last_outcome_at IS NULL OR (isfinite(last_outcome_at) AND last_outcome_at>=created_at)),
    last_failure_code varchar(40) CHECK(last_failure_code IN ('REQUEST_EXPIRED','ATTEMPTS_EXHAUSTED','ACCESS_DENIED','PROVIDER_UNAVAILABLE','PROVIDER_IDENTITY_CHANGED','PUBLICATION_CHANGED','ROOM_MISMATCH')),
    PRIMARY KEY(space_id,conversation_id),
    FOREIGN KEY(space_id,conversation_id,account_id)
        REFERENCES collaboration_space_room_intents(space_id,conversation_id,requested_by),
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
CREATE INDEX collaboration_space_room_provisioning_due ON collaboration_space_room_provisioning(available_at,space_id,conversation_id)
    WHERE state IN ('QUEUED','RUNNING') AND attempts<8;
CREATE INDEX collaboration_space_room_provisioning_expiry ON collaboration_space_room_provisioning(expires_at,space_id,conversation_id)
    WHERE state IN ('QUEUED','RUNNING');
CREATE INDEX collaboration_space_room_provisioning_leased ON collaboration_space_room_provisioning(lease_until)
    WHERE state='RUNNING';
CREATE INDEX collaboration_space_room_provisioning_exhausted ON collaboration_space_room_provisioning(lease_until,space_id,conversation_id)
    WHERE state='RUNNING' AND attempts=8;
ALTER TABLE collaboration_space_room_provisioning ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_space_room_provisioning FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_space_room_provisioning_read ON collaboration_space_room_provisioning FOR SELECT
    USING(current_company_id() IS NULL AND account_id=current_actor_id());
CREATE POLICY collaboration_space_room_provisioning_insert ON collaboration_space_room_provisioning FOR INSERT
    WITH CHECK(current_company_id() IS NULL AND account_id=current_actor_id()
        AND EXISTS(SELECT 1 FROM collaboration_space_directory s WHERE s.space_id=collaboration_space_room_provisioning.space_id));
CREATE POLICY collaboration_space_room_provisioning_update ON collaboration_space_room_provisioning FOR UPDATE
    USING(current_company_id() IS NULL AND account_id=current_actor_id())
    WITH CHECK(current_company_id() IS NULL AND account_id=current_actor_id()
        AND (state='CANCELLED' OR EXISTS(SELECT 1 FROM collaboration_space_directory s WHERE s.space_id=collaboration_space_room_provisioning.space_id)));
CREATE POLICY collaboration_space_room_provisioning_worker ON collaboration_space_room_provisioning TO hris_worker_capability
    USING(true) WITH CHECK(true);

CREATE FUNCTION protect_collaboration_space_room_provisioning() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE at_time timestamptz := clock_timestamp(); new_request boolean;
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Organization room provisioning identity is retained' USING ERRCODE='23514';
    END IF;
    new_request := TG_OP='INSERT';
    IF TG_OP='INSERT' THEN
        IF NEW.revision<>0 OR NEW.created_at<>NEW.requested_at OR NEW.last_outcome_revision IS NOT NULL THEN
            RAISE EXCEPTION 'Invalid initial organization room provisioning request' USING ERRCODE='23514';
        END IF;
    ELSE
        IF (NEW.space_id,NEW.conversation_id,NEW.account_id,NEW.created_at)
            IS DISTINCT FROM (OLD.space_id,OLD.conversation_id,OLD.account_id,OLD.created_at) THEN
            RAISE EXCEPTION 'Organization room provisioning owner is immutable' USING ERRCODE='23514';
        END IF;
        IF NEW.revision<>OLD.revision THEN
            IF NEW.revision<>OLD.revision+1 OR OLD.state NOT IN ('SUCCEEDED','FAILED','CANCELLED')
                OR NEW.requested_at<OLD.completed_at
                OR (NEW.last_outcome_revision,NEW.last_outcome_state,NEW.last_outcome_at,NEW.last_failure_code)
                    IS DISTINCT FROM (OLD.last_outcome_revision,OLD.last_outcome_state,OLD.last_outcome_at,OLD.last_failure_code) THEN
                RAISE EXCEPTION 'Invalid organization room provisioning retry' USING ERRCODE='23514';
            END IF;
            new_request := true;
        END IF;
    END IF;
    IF new_request THEN
        IF NEW.state<>'QUEUED' OR NEW.attempts<>0 OR NEW.available_at<>NEW.requested_at
            OR NEW.lease_token IS NOT NULL OR NEW.completed_at IS NOT NULL OR NEW.failure_code IS NOT NULL
            OR NEW.requested_at>at_time+interval '1 second' OR NEW.expires_at<=at_time THEN
            RAISE EXCEPTION 'Invalid organization room provisioning request' USING ERRCODE='23514';
        END IF;
        PERFORM 1 FROM collaboration_space_devices d
            JOIN native_sessions s ON s.id=d.origin_native_session_id AND s.account_id=d.account_id
            JOIN accounts a ON a.id=s.account_id
            JOIN collaboration_space_provider_sessions p ON p.space_id=d.space_id AND p.device_id=d.id
                AND p.account_id=d.account_id AND p.native_session_id=d.origin_native_session_id
            WHERE d.space_id=NEW.space_id AND d.id=NEW.device_id AND d.account_id=NEW.account_id
                AND d.origin_native_session_id=NEW.native_session_id AND d.platform=NEW.client_platform
                AND d.state='ACTIVE' AND d.revoked_at IS NULL AND d.version=NEW.device_version
                AND d.updated_at<=NEW.requested_at
                AND p.generation=NEW.provider_generation AND p.cleared_at IS NULL AND p.token_encrypted IS NOT NULL
                AND p.matrix_user_id=d.matrix_user_id AND p.matrix_device_id=d.matrix_device_id
                AND p.updated_at<=NEW.requested_at AND p.token_expires_at>=NEW.expires_at
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
            RAISE EXCEPTION 'Organization room provisioning parent is unavailable' USING ERRCODE='23514';
        END IF;
        -- Recheck real time after the device row wait. The request deadline never slides.
        at_time := clock_timestamp();
        IF NEW.expires_at<=at_time OR EXISTS(SELECT 1 FROM collaboration_space_room_bindings b
                WHERE b.space_id=NEW.space_id AND b.conversation_id=NEW.conversation_id)
            OR NOT EXISTS(SELECT 1 FROM collaboration_space_room_intents i
                JOIN collaboration_space_members first ON first.space_id=i.space_id AND first.account_id=i.first_account_id
                JOIN collaboration_space_members second ON second.space_id=i.space_id AND second.account_id=i.second_account_id
                WHERE i.space_id=NEW.space_id AND i.conversation_id=NEW.conversation_id AND i.requested_by=NEW.account_id
                    AND i.created_at<=NEW.requested_at
                    AND first.matrix_user_id=i.first_matrix_user_id AND first.matrix_server_name=i.matrix_server_name
                    AND second.matrix_user_id=i.second_matrix_user_id AND second.matrix_server_name=i.matrix_server_name)
        THEN RAISE EXCEPTION 'Organization room request is unavailable' USING ERRCODE='23514'; END IF;
        RETURN NEW;
    END IF;
    IF (NEW.device_id,NEW.device_version,NEW.provider_generation,NEW.native_session_id,NEW.native_session_version,NEW.credential_version,NEW.authenticated_at,NEW.mfa_verified_at,
        NEW.correlation_id,NEW.client_platform,NEW.client_build,NEW.requested_at,NEW.expires_at)
        IS DISTINCT FROM (OLD.device_id,OLD.device_version,OLD.provider_generation,OLD.native_session_id,OLD.native_session_version,OLD.credential_version,OLD.authenticated_at,OLD.mfa_verified_at,
        OLD.correlation_id,OLD.client_platform,OLD.client_build,OLD.requested_at,OLD.expires_at)
        OR OLD.state NOT IN ('QUEUED','RUNNING') THEN
        RAISE EXCEPTION 'Organization room provisioning intent is immutable' USING ERRCODE='23514';
    END IF;
    IF NEW.state='CANCELLED' AND current_company_id() IS NULL AND NEW.account_id=current_actor_id() THEN
        IF NEW.attempts<>OLD.attempts OR NEW.available_at<>OLD.available_at OR NEW.completed_at>at_time+interval '1 second' THEN
            RAISE EXCEPTION 'Invalid organization room provisioning cancellation' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF NOT pg_has_role(current_user,'hris_worker_capability','member') THEN
        RAISE EXCEPTION 'Only a worker advances organization room provisioning' USING ERRCODE='23514';
    END IF;
    IF NEW.state='SUCCEEDED' AND NOT EXISTS(SELECT 1 FROM collaboration_space_room_bindings b
        WHERE b.space_id=NEW.space_id AND b.conversation_id=NEW.conversation_id
            AND b.request_revision=NEW.revision AND b.published_by=NEW.account_id
            AND b.lease_token=OLD.lease_token AND b.created_at<=NEW.completed_at) THEN
        RAISE EXCEPTION 'Organization room success requires its verified binding' USING ERRCODE='23514';
    END IF;
    IF NEW.state='RUNNING' AND NEW.lease_token IS DISTINCT FROM OLD.lease_token THEN
        IF NEW.attempts<>OLD.attempts+1 OR NEW.available_at<>OLD.available_at
            OR OLD.available_at>at_time OR OLD.expires_at<=at_time
            OR (OLD.lease_until IS NOT NULL AND OLD.lease_until>at_time)
            OR NEW.lease_until<=at_time OR NEW.lease_until>at_time+interval '60 seconds' THEN
            RAISE EXCEPTION 'Invalid organization room provisioning claim' USING ERRCODE='23514';
        END IF;
    ELSIF OLD.state='RUNNING' AND OLD.lease_until>at_time AND NEW.state IN ('QUEUED','SUCCEEDED','FAILED') THEN
        IF NEW.attempts<>OLD.attempts OR (NEW.state='QUEUED' AND
            (NEW.attempts>=8 OR NEW.available_at<at_time OR NEW.available_at>at_time+interval '300 seconds'))
            OR (NEW.state<>'QUEUED' AND (NEW.available_at<>OLD.available_at OR NEW.completed_at>at_time+interval '1 second')) THEN
            RAISE EXCEPTION 'Invalid organization room provisioning completion' USING ERRCODE='23514';
        END IF;
    ELSIF NEW.state='FAILED' AND (OLD.lease_until IS NULL OR OLD.lease_until<=at_time)
        AND (OLD.expires_at<=at_time OR OLD.attempts>=8) THEN
        IF NEW.attempts<>OLD.attempts OR NEW.available_at<>OLD.available_at OR NEW.completed_at>at_time+interval '1 second'
            OR NOT ((NEW.failure_code='REQUEST_EXPIRED' AND OLD.expires_at<=at_time)
                OR (NEW.failure_code='ATTEMPTS_EXHAUSTED' AND OLD.attempts>=8)) THEN
            RAISE EXCEPTION 'Invalid organization room provisioning expiration' USING ERRCODE='23514';
        END IF;
    ELSE
        RAISE EXCEPTION 'Organization room provisioning transition requires a current lease' USING ERRCODE='23514';
    END IF;
    IF NEW.state IN ('QUEUED','RUNNING') AND
        (NEW.last_outcome_revision,NEW.last_outcome_state,NEW.last_outcome_at,NEW.last_failure_code)
            IS DISTINCT FROM (OLD.last_outcome_revision,OLD.last_outcome_state,OLD.last_outcome_at,OLD.last_failure_code) THEN
        RAISE EXCEPTION 'Previous organization room provisioning outcome is retained' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_space_room_provisioning BEFORE INSERT OR UPDATE OR DELETE ON collaboration_space_room_provisioning
    FOR EACH ROW EXECUTE FUNCTION protect_collaboration_space_room_provisioning();

-- Queue-only transaction. Commit before acquiring any account/native/device guard.
-- Expiration retains a terminal operational outcome; it never touches device state,
-- credentials or provider keys. Business publication is a separate admitted command.
CREATE FUNCTION claim_collaboration_space_room_provisioning(p_owner uuid,p_limit integer,p_lease_seconds integer)
RETURNS SETOF collaboration_space_room_provisioning LANGUAGE plpgsql SECURITY INVOKER AS $$
DECLARE at_time timestamptz := clock_timestamp(); capacity integer;
BEGIN
    IF p_owner IS NULL OR p_owner='00000000-0000-0000-0000-000000000000'::uuid OR p_limit IS NULL OR p_lease_seconds IS NULL
        OR p_limit NOT BETWEEN 1 AND 8 OR p_lease_seconds NOT BETWEEN 1 AND 60 THEN
        RAISE EXCEPTION 'Invalid organization room provisioning claim bounds' USING ERRCODE='22023';
    END IF;
    IF NOT pg_try_advisory_xact_lock(hashtextextended('hris:collaboration-space-room-provisioning-claims',0)) THEN RETURN; END IF;
    -- Separate indexed time ranges avoid an OR across expiry and exhausted leases.
    -- Each side returns at most eight; overlap is deduplicated before terminalization.
    WITH expired AS (
        SELECT space_id,conversation_id FROM collaboration_space_room_provisioning
        WHERE state IN ('QUEUED','RUNNING') AND expires_at<=at_time
        ORDER BY expires_at,space_id,conversation_id FOR UPDATE SKIP LOCKED LIMIT 8
    ), exhausted AS (
        SELECT space_id,conversation_id FROM collaboration_space_room_provisioning
        WHERE state='RUNNING' AND attempts=8 AND lease_until<=at_time
        ORDER BY lease_until,space_id,conversation_id FOR UPDATE SKIP LOCKED LIMIT 8
    ), ended AS (
        SELECT * FROM expired UNION SELECT * FROM exhausted
    ) UPDATE collaboration_space_room_provisioning h SET state='FAILED',completed_at=at_time,
        failure_code=CASE WHEN h.expires_at<=at_time THEN 'REQUEST_EXPIRED' ELSE 'ATTEMPTS_EXHAUSTED' END,
        last_outcome_revision=h.revision,last_outcome_state='FAILED',last_outcome_at=at_time,
        last_failure_code=CASE WHEN h.expires_at<=at_time THEN 'REQUEST_EXPIRED' ELSE 'ATTEMPTS_EXHAUSTED' END,
        lease_owner=NULL,lease_token=NULL,lease_until=NULL
        FROM ended e WHERE h.space_id=e.space_id AND h.conversation_id=e.conversation_id;
    SELECT greatest(0,16-count(*)) INTO capacity FROM (
        SELECT 1 FROM collaboration_space_room_provisioning WHERE state='RUNNING' AND lease_until>at_time LIMIT 16
    ) active;
    IF capacity=0 THEN RETURN; END IF;
    RETURN QUERY WITH candidates AS (
        SELECT space_id,conversation_id FROM collaboration_space_room_provisioning
        WHERE state IN ('QUEUED','RUNNING') AND expires_at>at_time AND available_at<=at_time
            AND (lease_until IS NULL OR lease_until<=at_time) AND attempts<8
        ORDER BY available_at,space_id,conversation_id FOR UPDATE SKIP LOCKED LIMIT least(p_limit,capacity)
    ) UPDATE collaboration_space_room_provisioning h SET state='RUNNING',attempts=h.attempts+1,
        lease_owner=p_owner,lease_token=gen_random_uuid(),lease_until=least(at_time+make_interval(secs=>p_lease_seconds),h.expires_at)
        FROM candidates c WHERE h.space_id=c.space_id AND h.conversation_id=c.conversation_id RETURNING h.*;
END $$;
REVOKE ALL ON FUNCTION claim_collaboration_space_room_provisioning(uuid,integer,integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION claim_collaboration_space_room_provisioning(uuid,integer,integer) TO hris_worker_capability;


-- An immutable verified binding is published only from the exact current request
-- lease. Requester, original Matrix creator and provider room identity stay distinct.
CREATE TABLE collaboration_space_room_bindings (
    space_id uuid NOT NULL,
    conversation_id uuid NOT NULL,
    published_by uuid NOT NULL,
    request_revision bigint NOT NULL CHECK(request_revision BETWEEN 0 AND 999999),
    lease_token uuid NOT NULL CHECK(lease_token<>'00000000-0000-0000-0000-000000000000'::uuid),
    matrix_room_id varchar(255) NOT NULL CHECK(matrix_room_id ~ '^![!-~]{1,254}$'),
    created_at timestamptz NOT NULL CHECK(isfinite(created_at)),
    PRIMARY KEY(space_id,conversation_id),
    UNIQUE(space_id,matrix_room_id),
    FOREIGN KEY(space_id,conversation_id,published_by)
        REFERENCES collaboration_space_room_intents(space_id,conversation_id,requested_by)
);
ALTER TABLE collaboration_space_room_bindings ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_space_room_bindings FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_space_room_binding_read ON collaboration_space_room_bindings FOR SELECT
    USING(current_company_id() IS NULL AND EXISTS(SELECT 1 FROM collaboration_space_room_intents i
        WHERE i.space_id=collaboration_space_room_bindings.space_id
            AND i.conversation_id=collaboration_space_room_bindings.conversation_id
            AND current_actor_id() IN(i.first_account_id,i.second_account_id)));
CREATE POLICY collaboration_space_room_binding_insert ON collaboration_space_room_bindings FOR INSERT
    TO hris_worker_capability WITH CHECK(current_company_id() IS NULL AND published_by=current_actor_id());
CREATE FUNCTION validate_collaboration_space_room_binding() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE q collaboration_space_room_provisioning; at_time timestamptz;
BEGIN
    IF current_company_id() IS NOT NULL OR NEW.published_by IS DISTINCT FROM current_actor_id()
        OR NOT pg_has_role(current_user,'hris_worker_capability','member') THEN
        RAISE EXCEPTION 'Organization room publication requires its admitted worker' USING ERRCODE='23514';
    END IF;
    -- Application guard order is account/native/device before this queue row.
    -- Queue claims commit before any of those guards are acquired.
    SELECT * INTO q FROM collaboration_space_room_provisioning
        WHERE space_id=NEW.space_id AND conversation_id=NEW.conversation_id FOR UPDATE;
    at_time := clock_timestamp();
    IF NOT FOUND OR q.account_id<>NEW.published_by OR q.revision<>NEW.request_revision
        OR q.state<>'RUNNING' OR q.lease_token IS DISTINCT FROM NEW.lease_token
        OR q.lease_until<=at_time OR q.expires_at<=at_time
        OR NEW.created_at<q.requested_at OR NEW.created_at>at_time THEN
        RAISE EXCEPTION 'Organization room publication lease changed' USING ERRCODE='23514';
    END IF;
    IF NOT EXISTS(
        SELECT 1 FROM collaboration_space_room_intents i
        JOIN collaboration_space_members first ON first.space_id=i.space_id AND first.account_id=i.first_account_id
        JOIN collaboration_space_members second ON second.space_id=i.space_id AND second.account_id=i.second_account_id
        JOIN collaboration_space_devices d ON d.space_id=i.space_id AND d.id=q.device_id AND d.account_id=q.account_id
        JOIN collaboration_space_device_sessions b ON b.space_id=d.space_id AND b.device_id=d.id AND b.account_id=d.account_id
        JOIN native_sessions n ON n.id=b.native_session_id AND n.account_id=b.account_id
        JOIN accounts a ON a.id=n.account_id
        JOIN collaboration_space_provider_sessions p ON p.space_id=d.space_id AND p.device_id=d.id AND p.account_id=d.account_id
        WHERE i.space_id=NEW.space_id AND i.conversation_id=NEW.conversation_id AND i.requested_by=NEW.published_by
            AND first.matrix_user_id=i.first_matrix_user_id AND first.matrix_server_name=i.matrix_server_name
            AND second.matrix_user_id=i.second_matrix_user_id AND second.matrix_server_name=i.matrix_server_name
            AND (i.origin='SPACE' OR i.legacy_room_id=NEW.matrix_room_id)
            AND d.origin_native_session_id=q.native_session_id AND d.state='ACTIVE' AND d.revoked_at IS NULL
            AND d.version=q.device_version AND d.updated_at<=q.requested_at
            AND d.matrix_user_id=CASE WHEN d.account_id=i.first_account_id THEN i.first_matrix_user_id ELSE i.second_matrix_user_id END
            AND b.native_session_id=q.native_session_id AND b.bound_at<=q.requested_at
            AND n.version=q.native_session_version AND n.credential_version=q.credential_version
            AND n.authenticated_at=q.authenticated_at AND n.mfa_verified_at IS NOT DISTINCT FROM q.mfa_verified_at
            AND n.revoked_at IS NULL AND n.expires_at>at_time AND n.access_expires_at>at_time
            AND a.active AND a.security_version=q.credential_version
            AND p.native_session_id=q.native_session_id AND p.generation=q.provider_generation
            AND p.matrix_user_id=d.matrix_user_id AND p.matrix_device_id=d.matrix_device_id
            AND p.updated_at<=q.requested_at AND p.cleared_at IS NULL AND p.token_encrypted IS NOT NULL
            AND p.token_expires_at>at_time
    ) THEN RAISE EXCEPTION 'Organization room publication authority changed' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER validate_collaboration_space_room_binding BEFORE INSERT ON collaboration_space_room_bindings
    FOR EACH ROW EXECUTE FUNCTION validate_collaboration_space_room_binding();
CREATE TRIGGER immutable_collaboration_space_room_binding BEFORE UPDATE OR DELETE ON collaboration_space_room_bindings
    FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

-- Both participants can observe an operational outcome without reading the
-- requester's native authentication proof, provider generation or device details.
GRANT SELECT(space_id,conversation_id,first_account_id,second_account_id)
    ON collaboration_space_room_intents TO hris_collaboration_directory_reader;
CREATE POLICY collaboration_space_room_intent_status ON collaboration_space_room_intents FOR SELECT
    TO hris_collaboration_directory_reader
    USING(current_company_id() IS NULL AND current_actor_id() IN(first_account_id,second_account_id));
GRANT SELECT(space_id,conversation_id,revision,state,attempts,requested_at,available_at,expires_at,completed_at,failure_code,
    last_outcome_revision,last_outcome_state,last_outcome_at,last_failure_code)
    ON collaboration_space_room_provisioning TO hris_collaboration_directory_reader;
CREATE POLICY collaboration_space_room_queue_status ON collaboration_space_room_provisioning FOR SELECT
    TO hris_collaboration_directory_reader USING(EXISTS(SELECT 1 FROM collaboration_space_room_intents i
        WHERE i.space_id=collaboration_space_room_provisioning.space_id
            AND i.conversation_id=collaboration_space_room_provisioning.conversation_id
            AND current_actor_id() IN(i.first_account_id,i.second_account_id)));
CREATE VIEW collaboration_space_room_status WITH(security_barrier=true) AS
SELECT q.space_id,q.conversation_id,q.revision,q.state,q.attempts,q.requested_at,q.available_at,q.expires_at,
    q.completed_at,q.failure_code,q.last_outcome_revision,q.last_outcome_state,q.last_outcome_at,q.last_failure_code
FROM public.collaboration_space_room_provisioning q
JOIN public.collaboration_space_room_intents i ON i.space_id=q.space_id AND i.conversation_id=q.conversation_id
WHERE public.current_actor_id() IN(i.first_account_id,i.second_account_id);
ALTER VIEW collaboration_space_room_status OWNER TO hris_collaboration_directory_reader;


-- Publishing a binding without finishing the matching request cannot commit.
-- A failed journal/receipt Result rolls back both before this deferred check runs.
CREATE FUNCTION validate_collaboration_space_room_publication_commit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS(SELECT 1 FROM collaboration_space_room_provisioning q
        WHERE q.space_id=NEW.space_id AND q.conversation_id=NEW.conversation_id
            AND q.account_id=NEW.published_by AND q.revision=NEW.request_revision
            AND q.state='SUCCEEDED' AND q.completed_at>=NEW.created_at) THEN
        RAISE EXCEPTION 'Organization room publication outcome is incomplete' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER validate_collaboration_space_room_publication_commit
    AFTER INSERT ON collaboration_space_room_bindings DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_collaboration_space_room_publication_commit();
