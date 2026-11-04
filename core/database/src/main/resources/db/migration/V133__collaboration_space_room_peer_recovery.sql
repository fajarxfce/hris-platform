-- Each exact participant retains only its own bounded operational request.
-- The immutable conversation/initial requester and single published room remain shared.
-- No peer receives access to another participant's native/device/provider proof.
DO $$
DECLARE relation_name text; constraint_name text; matches integer;
BEGIN
    FOREACH relation_name IN ARRAY ARRAY['collaboration_space_room_provisioning','collaboration_space_room_bindings'] LOOP
        SELECT count(*),min(c.conname) INTO matches,constraint_name FROM pg_constraint c
        WHERE c.conrelid=relation_name::regclass AND c.confrelid='collaboration_space_room_intents'::regclass
            AND c.contype='f' AND cardinality(c.conkey)=3;
        IF matches<>1 THEN RAISE EXCEPTION 'Unexpected organization room owner reference'; END IF;
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I',relation_name,constraint_name);
    END LOOP;
END $$;
ALTER TABLE collaboration_space_room_provisioning DROP CONSTRAINT collaboration_space_room_provisioning_pkey;
ALTER TABLE collaboration_space_room_provisioning ADD PRIMARY KEY(space_id,conversation_id,account_id);
ALTER TABLE collaboration_space_room_provisioning ADD CONSTRAINT collaboration_space_room_request_intent
    FOREIGN KEY(space_id,conversation_id) REFERENCES collaboration_space_room_intents(space_id,conversation_id);
-- At most one live preparation per conversation. A peer can resume only after the
-- prior request has a terminal outcome. Each participant keeps its own revision.
CREATE UNIQUE INDEX collaboration_space_room_one_active_request
    ON collaboration_space_room_provisioning(space_id,conversation_id) WHERE state IN ('QUEUED','RUNNING');
ALTER TABLE collaboration_space_room_bindings ADD CONSTRAINT collaboration_space_room_binding_request
    FOREIGN KEY(space_id,conversation_id,published_by)
    REFERENCES collaboration_space_room_provisioning(space_id,conversation_id,account_id);

-- First requester is provenance, not necessarily the creator seen after a lost
-- provider response. Existing bindings retain exactly their originally pinned creator.
ALTER TABLE collaboration_space_room_bindings ADD COLUMN matrix_creator_account_id uuid REFERENCES accounts(id);
ALTER TABLE collaboration_space_room_bindings DISABLE TRIGGER immutable_collaboration_space_room_binding;
UPDATE collaboration_space_room_bindings b SET matrix_creator_account_id=i.matrix_creator_account_id
    FROM collaboration_space_room_intents i WHERE i.space_id=b.space_id AND i.conversation_id=b.conversation_id;
ALTER TABLE collaboration_space_room_bindings ENABLE TRIGGER immutable_collaboration_space_room_binding;
ALTER TABLE collaboration_space_room_bindings ALTER COLUMN matrix_creator_account_id SET NOT NULL;

CREATE OR REPLACE FUNCTION protect_collaboration_space_room_provisioning() RETURNS trigger LANGUAGE plpgsql AS $$
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
                WHERE i.space_id=NEW.space_id AND i.conversation_id=NEW.conversation_id AND NEW.account_id IN(i.first_account_id,i.second_account_id)
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

CREATE OR REPLACE FUNCTION claim_collaboration_space_room_provisioning(p_owner uuid,p_limit integer,p_lease_seconds integer)
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
        SELECT space_id,conversation_id,account_id FROM collaboration_space_room_provisioning
        WHERE state IN ('QUEUED','RUNNING') AND expires_at<=at_time
        ORDER BY expires_at,space_id,conversation_id,account_id FOR UPDATE SKIP LOCKED LIMIT 8
    ), exhausted AS (
        SELECT space_id,conversation_id,account_id FROM collaboration_space_room_provisioning
        WHERE state='RUNNING' AND attempts=8 AND lease_until<=at_time
        ORDER BY lease_until,space_id,conversation_id,account_id FOR UPDATE SKIP LOCKED LIMIT 8
    ), ended AS (
        SELECT * FROM expired UNION SELECT * FROM exhausted
    ) UPDATE collaboration_space_room_provisioning h SET state='FAILED',completed_at=at_time,
        failure_code=CASE WHEN h.expires_at<=at_time THEN 'REQUEST_EXPIRED' ELSE 'ATTEMPTS_EXHAUSTED' END,
        last_outcome_revision=h.revision,last_outcome_state='FAILED',last_outcome_at=at_time,
        last_failure_code=CASE WHEN h.expires_at<=at_time THEN 'REQUEST_EXPIRED' ELSE 'ATTEMPTS_EXHAUSTED' END,
        lease_owner=NULL,lease_token=NULL,lease_until=NULL
        FROM ended e WHERE h.space_id=e.space_id AND h.conversation_id=e.conversation_id AND h.account_id=e.account_id;
    SELECT greatest(0,16-count(*)) INTO capacity FROM (
        SELECT 1 FROM collaboration_space_room_provisioning WHERE state='RUNNING' AND lease_until>at_time LIMIT 16
    ) active;
    IF capacity=0 THEN RETURN; END IF;
    RETURN QUERY WITH candidates AS (
        SELECT space_id,conversation_id,account_id FROM collaboration_space_room_provisioning
        WHERE state IN ('QUEUED','RUNNING') AND expires_at>at_time AND available_at<=at_time
            AND (lease_until IS NULL OR lease_until<=at_time) AND attempts<8
        ORDER BY available_at,space_id,conversation_id,account_id FOR UPDATE SKIP LOCKED LIMIT least(p_limit,capacity)
    ) UPDATE collaboration_space_room_provisioning h SET state='RUNNING',attempts=h.attempts+1,
        lease_owner=p_owner,lease_token=gen_random_uuid(),lease_until=least(at_time+make_interval(secs=>p_lease_seconds),h.expires_at)
        FROM candidates c WHERE h.space_id=c.space_id AND h.conversation_id=c.conversation_id AND h.account_id=c.account_id RETURNING h.*;
END $$;

CREATE OR REPLACE FUNCTION validate_collaboration_space_room_binding() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE q collaboration_space_room_provisioning; at_time timestamptz;
BEGIN
    IF current_company_id() IS NOT NULL OR NEW.published_by IS DISTINCT FROM current_actor_id()
        OR NOT pg_has_role(current_user,'hris_worker_capability','member') THEN
        RAISE EXCEPTION 'Organization room publication requires its admitted worker' USING ERRCODE='23514';
    END IF;
    -- Compatibility for the original first-requester-only writer. A recovering
    -- peer must provide the creator it actually verified; never infer its identity.
    IF NEW.matrix_creator_account_id IS NULL THEN
        SELECT i.matrix_creator_account_id INTO NEW.matrix_creator_account_id
        FROM collaboration_space_room_intents i WHERE i.space_id=NEW.space_id
            AND i.conversation_id=NEW.conversation_id AND i.requested_by=NEW.published_by;
    END IF;
    -- Application guard order is account/native/device before this queue row.
    -- Queue claims commit before any of those guards are acquired.
    SELECT * INTO q FROM collaboration_space_room_provisioning
        WHERE space_id=NEW.space_id AND conversation_id=NEW.conversation_id AND account_id=NEW.published_by FOR UPDATE;
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
        WHERE i.space_id=NEW.space_id AND i.conversation_id=NEW.conversation_id AND NEW.published_by IN(i.first_account_id,i.second_account_id)
            AND first.matrix_user_id=i.first_matrix_user_id AND first.matrix_server_name=i.matrix_server_name
            AND second.matrix_user_id=i.second_matrix_user_id AND second.matrix_server_name=i.matrix_server_name
            AND NEW.matrix_creator_account_id IN(i.first_account_id,i.second_account_id)
            AND (i.origin='SPACE' OR (i.legacy_room_id=NEW.matrix_room_id
                AND i.matrix_creator_account_id=NEW.matrix_creator_account_id))
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

-- One peer-safe public head: successful publisher, else active work, else latest
-- terminal request. Owner plus revision identifies the observed attempt exactly.
-- Full private heads remain protected by the unchanged account-only RLS policy.
GRANT SELECT(account_id) ON collaboration_space_room_provisioning TO hris_collaboration_directory_reader;
CREATE OR REPLACE VIEW collaboration_space_room_status WITH(security_barrier=true) AS
SELECT DISTINCT ON(q.space_id,q.conversation_id)
    q.space_id,q.conversation_id,q.revision,q.state,q.attempts,q.requested_at,q.available_at,q.expires_at,
    q.completed_at,q.failure_code,q.last_outcome_revision,q.last_outcome_state,q.last_outcome_at,q.last_failure_code,
    q.account_id
FROM public.collaboration_space_room_provisioning q
JOIN public.collaboration_space_room_intents i ON i.space_id=q.space_id AND i.conversation_id=q.conversation_id
WHERE public.current_actor_id() IN(i.first_account_id,i.second_account_id)
ORDER BY q.space_id,q.conversation_id,
    CASE WHEN q.state='SUCCEEDED' THEN 0 WHEN q.state IN ('QUEUED','RUNNING') THEN 1 ELSE 2 END,
    q.requested_at DESC,q.account_id;
ALTER VIEW collaboration_space_room_status OWNER TO hris_collaboration_directory_reader;
