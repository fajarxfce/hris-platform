-- Immutable proof of one admitted retained-device handover. Legacy runtime stays
-- DRAINING forever; this mapping supplies replay provenance, never a content grant.
CREATE TABLE collaboration_legacy_runtime_completions (
    legacy_company_id uuid NOT NULL,
    legacy_device_id uuid NOT NULL,
    account_id uuid NOT NULL,
    native_session_id uuid NOT NULL,
    space_id uuid NOT NULL,
    device_id uuid NOT NULL,
    matrix_user_id varchar(255) NOT NULL,
    matrix_device_id varchar(80) NOT NULL,
    fingerprint varchar(64) NOT NULL CHECK(fingerprint ~ '^[a-f0-9]{64}$'),
    native_session_version bigint NOT NULL CHECK(native_session_version>=0),
    provisioning_revision bigint NOT NULL CHECK(provisioning_revision BETWEEN 0 AND 999999),
    source_device_version bigint NOT NULL CHECK(source_device_version BETWEEN 1 AND 1000000),
    source_provider_generation bigint NOT NULL CHECK(source_provider_generation BETWEEN 1 AND 1000000),
    source_gateway_id uuid,
    source_gateway_version bigint CHECK(source_gateway_version BETWEEN 1 AND 1000000),
    target_device_version bigint NOT NULL CHECK(target_device_version BETWEEN 1 AND 999999),
    target_provider_generation bigint NOT NULL CHECK(target_provider_generation BETWEEN 0 AND 999999),
    frozen_at timestamptz NOT NULL CHECK(isfinite(frozen_at)),
    keys_observed_at timestamptz NOT NULL CHECK(isfinite(keys_observed_at) AND keys_observed_at>=frozen_at),
    completed_at timestamptz NOT NULL CHECK(isfinite(completed_at) AND completed_at>=keys_observed_at),
    PRIMARY KEY(legacy_company_id,legacy_device_id),
    UNIQUE(space_id,device_id),
    FOREIGN KEY(legacy_company_id,legacy_device_id,account_id,native_session_id)
        REFERENCES collaboration_legacy_runtime(company_id,device_id,account_id,native_session_id),
    FOREIGN KEY(space_id,device_id,account_id,native_session_id,matrix_user_id,matrix_device_id)
        REFERENCES collaboration_space_devices(space_id,id,account_id,origin_native_session_id,matrix_user_id,matrix_device_id),
    CHECK((source_gateway_id IS NULL)=(source_gateway_version IS NULL))
);
ALTER TABLE collaboration_legacy_runtime_completions ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_legacy_runtime_completions FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_legacy_completion_read ON collaboration_legacy_runtime_completions FOR SELECT
    USING(account_id=current_actor_id());
CREATE TRIGGER immutable_collaboration_legacy_completion BEFORE UPDATE OR DELETE ON collaboration_legacy_runtime_completions
    FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

-- Only a fixed worker-callable function can use these rights. Applications are
-- never members of the non-login owner role. Ciphertext/hash SELECT is not granted.
DO $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='hris_collaboration_transfer_owner') THEN
        CREATE ROLE hris_collaboration_transfer_owner NOLOGIN NOINHERIT NOSUPERUSER
            NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;
END $$;
GRANT USAGE ON SCHEMA public TO hris_collaboration_transfer_owner;
GRANT SELECT(id,active,security_version),UPDATE(id) ON accounts TO hris_collaboration_transfer_owner;
GRANT SELECT(id,account_id,version,credential_version,authenticated_at,mfa_verified_at,created_at,rotated_at,expires_at,access_expires_at,revoked_at),UPDATE(id)
    ON native_sessions TO hris_collaboration_transfer_owner;
GRANT SELECT(space_id,id,account_id,origin,legacy_company_id,legacy_device_id,legacy_key_fingerprint,origin_native_session_id,matrix_user_id,matrix_device_id,platform,state,version,created_at,updated_at,revoked_at),UPDATE(id)
    ON collaboration_space_devices TO hris_collaboration_transfer_owner;
GRANT SELECT(space_id,device_id,account_id,native_session_id,bound_at)
    ON collaboration_space_device_sessions TO hris_collaboration_transfer_owner;
GRANT SELECT(company_id,device_id,account_id,native_session_id,bound_at)
    ON collaboration_device_sessions TO hris_collaboration_transfer_owner;
GRANT SELECT(company_id,device_id,account_id,native_session_id,phase,version,created_at,frozen_at,target_space_id,target_device_id),UPDATE(device_id)
    ON collaboration_legacy_runtime TO hris_collaboration_transfer_owner;
GRANT SELECT(company_id,device_id,account_id,native_session_id,expires_at,released_at)
    ON collaboration_legacy_runtime_leases TO hris_collaboration_transfer_owner;
GRANT SELECT(space_id,device_id,account_id,native_session_id,revision,native_session_version,credential_version,authenticated_at,mfa_verified_at,client_platform,state,requested_at,expires_at,completed_at),UPDATE(device_id)
    ON collaboration_space_provisioning TO hris_collaboration_transfer_owner;
GRANT SELECT(space_id,device_id,account_id,native_session_id,matrix_user_id,matrix_device_id,generation,created_at,updated_at,token_expires_at,cleared_at)
    ON collaboration_space_provider_sessions TO hris_collaboration_transfer_owner;
GRANT SELECT ON collaboration_space_directory TO hris_collaboration_transfer_owner;
GRANT SELECT(company_id,id,account_id,platform,state,version,created_at,updated_at,revoked_at),UPDATE(state,version,updated_at,revoked_at)
    ON collaboration_devices TO hris_collaboration_transfer_owner;
GRANT SELECT(company_id,device_id,account_id,matrix_user_id,matrix_device_id,generation,created_at,updated_at,cleared_at),UPDATE(generation,token_encrypted,cleared_at,updated_at)
    ON collaboration_provider_sessions TO hris_collaboration_transfer_owner;
GRANT SELECT(company_id,id,account_id,device_id,native_session_id,version,rotated_at,revoked_at),UPDATE(version,access_hash,refresh_hash,revoked_at,replay_kind,bootstrap_operation_id,previous_refresh_hash,replay_until,replay_encrypted)
    ON collaboration_gateway_sessions TO hris_collaboration_transfer_owner;
GRANT SELECT(company_id,device_id,account_id,fingerprint,reserved_at)
    ON collaboration_device_key_publications TO hris_collaboration_transfer_owner;
GRANT SELECT,INSERT ON collaboration_legacy_runtime_completions TO hris_collaboration_transfer_owner;

-- UPDATE(id) above exists solely for PostgreSQL row locking; the fixed function
-- never changes those columns. Existing ownership/immutability triggers stay active.
CREATE POLICY collaboration_transfer_device_retire ON collaboration_devices FOR UPDATE TO hris_collaboration_transfer_owner
    USING(current_company_id() IS NULL AND account_id=current_actor_id() AND EXISTS(
        SELECT 1 FROM collaboration_legacy_runtime r WHERE r.company_id=collaboration_devices.company_id
            AND r.device_id=collaboration_devices.id AND r.account_id=current_actor_id() AND r.phase='DRAINING'))
    WITH CHECK(current_company_id() IS NULL AND account_id=current_actor_id() AND state='REVOKED');
CREATE POLICY collaboration_transfer_provider_read ON collaboration_provider_sessions FOR SELECT TO hris_collaboration_transfer_owner
    USING(current_company_id() IS NULL AND account_id=current_actor_id());
CREATE POLICY collaboration_transfer_provider_retire ON collaboration_provider_sessions FOR UPDATE TO hris_collaboration_transfer_owner
    USING(current_company_id() IS NULL AND account_id=current_actor_id() AND EXISTS(
        SELECT 1 FROM collaboration_legacy_runtime r WHERE r.company_id=collaboration_provider_sessions.company_id
            AND r.device_id=collaboration_provider_sessions.device_id AND r.account_id=current_actor_id() AND r.phase='DRAINING'))
    WITH CHECK(current_company_id() IS NULL AND account_id=current_actor_id() AND cleared_at IS NOT NULL);
CREATE POLICY collaboration_transfer_gateway_read ON collaboration_gateway_sessions FOR SELECT TO hris_collaboration_transfer_owner
    USING(current_company_id() IS NULL AND account_id=current_actor_id());
CREATE POLICY collaboration_transfer_gateway_retire ON collaboration_gateway_sessions FOR UPDATE TO hris_collaboration_transfer_owner
    USING(current_company_id() IS NULL AND account_id=current_actor_id() AND EXISTS(
        SELECT 1 FROM collaboration_legacy_runtime r WHERE r.company_id=collaboration_gateway_sessions.company_id
            AND r.device_id=collaboration_gateway_sessions.device_id AND r.account_id=current_actor_id() AND r.phase='DRAINING'))
    WITH CHECK(current_company_id() IS NULL AND account_id=current_actor_id() AND revoked_at IS NOT NULL);
CREATE POLICY collaboration_transfer_key_read ON collaboration_device_key_publications FOR SELECT TO hris_collaboration_transfer_owner
    USING(current_company_id() IS NULL AND account_id=current_actor_id());
CREATE POLICY collaboration_transfer_completion_insert ON collaboration_legacy_runtime_completions FOR INSERT TO hris_collaboration_transfer_owner
    WITH CHECK(current_company_id() IS NULL AND account_id=current_actor_id());

-- Caller publishes the exact group credential/device/SUCCEEDED queue in this SAME
-- transaction and has verified provider keys outside SQL. This function does not
-- own/commit a transaction and never acquires old job or direct-room guards.
CREATE FUNCTION complete_collaboration_legacy_runtime(
    requested_space uuid, requested_device uuid, requested_native uuid,
    expected_native_version bigint, activated_device_version bigint,
    target_provider_generation bigint, expected_provisioning_revision bigint,
    expected_legacy_device_version bigint, expected_legacy_provider_generation bigint,
    expected_legacy_gateway_version bigint, verified_fingerprint text, verified_at timestamptz
) RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, public AS $$
DECLARE caller uuid := public.current_actor_id(); account_row record; native_row record;
    target_row record; runtime_row record; queue_row record; provider_row record;
    old_device record; old_provider record; old_gateway record; has_gateway boolean;
    at_time timestamptz; affected integer;
BEGIN
    IF caller IS NULL OR public.current_company_id() IS NOT NULL
        OR requested_space IS NULL OR requested_device IS NULL OR requested_native IS NULL
        OR expected_native_version IS NULL OR expected_native_version<0
        OR activated_device_version IS NULL OR activated_device_version NOT BETWEEN 1 AND 999999
        OR target_provider_generation IS NULL OR target_provider_generation NOT BETWEEN 0 AND 999999
        OR expected_provisioning_revision IS NULL OR expected_provisioning_revision NOT BETWEEN 0 AND 999999
        OR expected_legacy_device_version IS NULL OR expected_legacy_device_version NOT BETWEEN 0 AND 999999
        OR expected_legacy_provider_generation IS NULL OR expected_legacy_provider_generation NOT BETWEEN 0 AND 999999
        OR (expected_legacy_gateway_version IS NOT NULL AND expected_legacy_gateway_version NOT BETWEEN 0 AND 999999)
        OR verified_fingerprint IS NULL OR verified_fingerprint !~ '^[a-f0-9]{64}$'
        OR verified_at IS NULL OR NOT isfinite(verified_at) THEN
        RAISE EXCEPTION 'Retained transfer request is invalid' USING ERRCODE='23514';
    END IF;
    SELECT a.id,a.active,a.security_version INTO account_row FROM public.accounts a
        WHERE a.id=caller FOR NO KEY UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Retained transfer owner is unavailable' USING ERRCODE='23514'; END IF;
    SELECT s.id,s.account_id,s.version,s.credential_version,s.authenticated_at,s.mfa_verified_at,
        s.created_at,s.rotated_at,s.expires_at,s.access_expires_at,s.revoked_at INTO native_row
        FROM public.native_sessions s WHERE s.id=requested_native AND s.account_id=caller FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Retained transfer parent is unavailable' USING ERRCODE='23514'; END IF;
    SELECT d.space_id,d.id,d.account_id,d.origin,d.legacy_company_id,d.legacy_device_id,d.legacy_key_fingerprint,
        d.origin_native_session_id,d.matrix_user_id,d.matrix_device_id,d.platform,d.state,d.version,d.updated_at,d.revoked_at
        INTO target_row FROM public.collaboration_space_devices d
        WHERE d.space_id=requested_space AND d.id=requested_device AND d.account_id=caller FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Retained transfer target is unavailable' USING ERRCODE='23514'; END IF;
    SELECT q.space_id,q.device_id,q.account_id,q.native_session_id,q.revision,q.native_session_version,
        q.credential_version,q.authenticated_at,q.mfa_verified_at,q.client_platform,q.state,q.requested_at,q.expires_at,q.completed_at
        INTO queue_row FROM public.collaboration_space_provisioning q
        WHERE q.space_id=requested_space AND q.device_id=requested_device AND q.account_id=caller FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Retained transfer work is unavailable' USING ERRCODE='23514'; END IF;
    SELECT r.company_id,r.device_id,r.account_id,r.native_session_id,r.phase,r.version,r.created_at,r.frozen_at,r.target_space_id,r.target_device_id
        INTO runtime_row FROM public.collaboration_legacy_runtime r
        WHERE r.company_id=target_row.legacy_company_id AND r.device_id=target_row.legacy_device_id AND r.account_id=caller FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Retained transfer runtime is unavailable' USING ERRCODE='23514'; END IF;
    SELECT p.space_id,p.device_id,p.account_id,p.native_session_id,p.matrix_user_id,p.matrix_device_id,p.generation,p.updated_at,p.token_expires_at,p.cleared_at
        INTO provider_row FROM public.collaboration_space_provider_sessions p
        WHERE p.space_id=requested_space AND p.device_id=requested_device AND p.account_id=caller;
    IF NOT FOUND THEN RAISE EXCEPTION 'Retained transfer credential is unavailable' USING ERRCODE='23514'; END IF;
    SELECT d.company_id,d.id,d.account_id,d.platform,d.state,d.version,d.updated_at,d.revoked_at
        INTO old_device FROM public.collaboration_devices d
        WHERE d.company_id=runtime_row.company_id AND d.id=runtime_row.device_id AND d.account_id=caller FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Original transfer device is unavailable' USING ERRCODE='23514'; END IF;
    SELECT p.company_id,p.device_id,p.account_id,p.matrix_user_id,p.matrix_device_id,p.generation,p.updated_at,p.cleared_at
        INTO old_provider FROM public.collaboration_provider_sessions p
        WHERE p.company_id=runtime_row.company_id AND p.device_id=runtime_row.device_id AND p.account_id=caller FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Original transfer credential is unavailable' USING ERRCODE='23514'; END IF;
    SELECT g.id,g.native_session_id,g.version,g.rotated_at,g.revoked_at INTO old_gateway
        FROM public.collaboration_gateway_sessions g
        WHERE g.company_id=runtime_row.company_id AND g.device_id=runtime_row.device_id AND g.account_id=caller FOR UPDATE;
    has_gateway := FOUND;
    -- Read database time only after every potentially blocking guard. A prior
    -- zero-count client observation or elapsed application lease is insufficient.
    at_time := clock_timestamp();
    IF NOT account_row.active OR native_row.version<>expected_native_version OR native_row.revoked_at IS NOT NULL
        OR native_row.credential_version<>account_row.security_version OR native_row.created_at>at_time
        OR native_row.rotated_at>at_time OR native_row.expires_at<=at_time OR native_row.access_expires_at<=at_time
        OR NOT EXISTS(SELECT 1 FROM public.collaboration_space_directory s WHERE s.space_id=requested_space)
        OR target_row.origin<>'LEGACY' OR target_row.origin_native_session_id<>requested_native
        OR target_row.state<>'ACTIVE' OR target_row.version<>activated_device_version OR target_row.revoked_at IS NOT NULL
        OR target_row.legacy_key_fingerprint<>verified_fingerprint OR target_row.updated_at>at_time
        OR runtime_row.native_session_id<>requested_native OR runtime_row.phase<>'DRAINING' OR runtime_row.version<>1
        OR runtime_row.target_space_id<>requested_space OR runtime_row.target_device_id<>requested_device
        OR verified_at<runtime_row.frozen_at OR verified_at>at_time OR verified_at<at_time-interval '30 seconds'
        OR queue_row.native_session_id<>requested_native OR queue_row.revision<>expected_provisioning_revision
        OR queue_row.native_session_version<>expected_native_version OR queue_row.credential_version<>account_row.security_version
        OR queue_row.authenticated_at<>native_row.authenticated_at OR queue_row.mfa_verified_at IS DISTINCT FROM native_row.mfa_verified_at
        OR queue_row.client_platform<>target_row.platform OR queue_row.state<>'SUCCEEDED'
        OR queue_row.requested_at>verified_at OR queue_row.expires_at<=at_time OR queue_row.completed_at>at_time
        OR provider_row.native_session_id<>requested_native OR provider_row.generation<>target_provider_generation
        OR provider_row.matrix_user_id<>target_row.matrix_user_id OR provider_row.matrix_device_id<>target_row.matrix_device_id
        OR provider_row.cleared_at IS NOT NULL OR provider_row.updated_at>at_time OR provider_row.token_expires_at<=at_time
        OR old_device.state<>'ACTIVE' OR old_device.version<>expected_legacy_device_version OR old_device.revoked_at IS NOT NULL
        OR old_device.platform<>target_row.platform OR old_device.updated_at>at_time
        OR old_provider.generation<>expected_legacy_provider_generation OR old_provider.cleared_at IS NOT NULL
        OR old_provider.updated_at>at_time OR old_provider.matrix_user_id<>target_row.matrix_user_id
        OR old_provider.matrix_device_id<>target_row.matrix_device_id
        OR (has_gateway AND (old_gateway.version IS DISTINCT FROM expected_legacy_gateway_version
            OR old_gateway.native_session_id<>requested_native OR old_gateway.revoked_at IS NOT NULL OR old_gateway.rotated_at>at_time))
        OR (NOT has_gateway AND expected_legacy_gateway_version IS NOT NULL)
        OR NOT EXISTS(SELECT 1 FROM public.collaboration_space_device_sessions b WHERE b.space_id=requested_space
            AND b.device_id=requested_device AND b.account_id=caller AND b.native_session_id=requested_native AND b.bound_at<=at_time)
        OR NOT EXISTS(SELECT 1 FROM public.collaboration_device_sessions b WHERE b.company_id=runtime_row.company_id
            AND b.device_id=runtime_row.device_id AND b.account_id=caller AND b.native_session_id=requested_native AND b.bound_at<=at_time)
        OR NOT EXISTS(SELECT 1 FROM public.collaboration_device_key_publications k WHERE k.company_id=runtime_row.company_id
            AND k.device_id=runtime_row.device_id AND k.account_id=caller AND k.fingerprint=verified_fingerprint AND k.reserved_at<=verified_at)
        OR EXISTS(SELECT 1 FROM public.collaboration_legacy_runtime_leases l WHERE l.company_id=runtime_row.company_id
            AND l.device_id=runtime_row.device_id AND l.account_id=caller AND l.native_session_id=requested_native
            AND l.released_at IS NULL AND l.expires_at>at_time) THEN
        RAISE EXCEPTION 'Retained transfer state changed' USING ERRCODE='23514';
    END IF;
    UPDATE public.collaboration_devices SET state='REVOKED',version=version+1,updated_at=at_time,revoked_at=at_time
        WHERE company_id=runtime_row.company_id AND id=runtime_row.device_id AND account_id=caller;
    GET DIAGNOSTICS affected = ROW_COUNT;
    IF affected<>1 THEN RAISE EXCEPTION 'Original transfer device changed' USING ERRCODE='23514'; END IF;
    UPDATE public.collaboration_provider_sessions SET generation=generation+1,token_encrypted=NULL,cleared_at=at_time,updated_at=at_time
        WHERE company_id=runtime_row.company_id AND device_id=runtime_row.device_id AND account_id=caller;
    GET DIAGNOSTICS affected = ROW_COUNT;
    IF affected<>1 THEN RAISE EXCEPTION 'Original transfer credential changed' USING ERRCODE='23514'; END IF;
    IF has_gateway THEN
        UPDATE public.collaboration_gateway_sessions SET version=version+1,access_hash=NULL,refresh_hash=NULL,revoked_at=at_time,
            replay_kind=NULL,bootstrap_operation_id=NULL,previous_refresh_hash=NULL,replay_until=NULL,replay_encrypted=NULL
            WHERE company_id=runtime_row.company_id AND id=old_gateway.id AND account_id=caller;
        GET DIAGNOSTICS affected = ROW_COUNT;
        IF affected<>1 THEN RAISE EXCEPTION 'Original transfer gateway changed' USING ERRCODE='23514'; END IF;
    END IF;
    IF clock_timestamp()>=least(native_row.expires_at,native_row.access_expires_at,queue_row.expires_at,
        provider_row.token_expires_at,verified_at+interval '30 seconds')
        OR NOT EXISTS(SELECT 1 FROM public.collaboration_space_directory s WHERE s.space_id=requested_space) THEN
        RAISE EXCEPTION 'Retained transfer admission expired' USING ERRCODE='23514';
    END IF;
    INSERT INTO public.collaboration_legacy_runtime_completions(
        legacy_company_id,legacy_device_id,account_id,native_session_id,space_id,device_id,matrix_user_id,matrix_device_id,
        fingerprint,native_session_version,provisioning_revision,source_device_version,source_provider_generation,
        source_gateway_id,source_gateway_version,target_device_version,target_provider_generation,frozen_at,keys_observed_at,completed_at
    ) VALUES(runtime_row.company_id,runtime_row.device_id,caller,requested_native,requested_space,requested_device,
        target_row.matrix_user_id,target_row.matrix_device_id,verified_fingerprint,expected_native_version,expected_provisioning_revision,
        expected_legacy_device_version+1,expected_legacy_provider_generation+1,
        CASE WHEN has_gateway THEN old_gateway.id END,CASE WHEN has_gateway THEN old_gateway.version+1 END,
        activated_device_version,target_provider_generation,runtime_row.frozen_at,verified_at,at_time);
    RETURN true;
END $$;
ALTER FUNCTION complete_collaboration_legacy_runtime(uuid,uuid,uuid,bigint,bigint,bigint,bigint,bigint,bigint,bigint,text,timestamptz)
    OWNER TO hris_collaboration_transfer_owner;
REVOKE ALL ON FUNCTION complete_collaboration_legacy_runtime(uuid,uuid,uuid,bigint,bigint,bigint,bigint,bigint,bigint,bigint,text,timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION complete_collaboration_legacy_runtime(uuid,uuid,uuid,bigint,bigint,bigint,bigint,bigint,bigint,bigint,text,timestamptz)
    TO hris_worker_capability;
