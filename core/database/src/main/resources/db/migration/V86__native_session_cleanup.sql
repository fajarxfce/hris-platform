-- Replays have short independent lifetimes. Retain consumed hashes until their
-- absolute session expiry plus the existing one-day session-retention window.
CREATE INDEX native_exchange_replay_expiry ON native_sessions(exchange_replay_until NULLS FIRST,id)
    WHERE exchange_encrypted IS NOT NULL;
CREATE INDEX consumed_refresh_replay_expiry ON consumed_refresh_tokens(replay_until NULLS FIRST,token_hash)
    WHERE replay_encrypted IS NOT NULL;

CREATE FUNCTION prune_native_session_material(
    p_expired_before timestamptz,
    p_replay_before timestamptz,
    p_session_limit integer,
    p_replay_limit integer
) RETURNS TABLE(deleted_sessions integer,cleared_exchange_replays integer,cleared_refresh_replays integer)
LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,public AS $$
BEGIN
    IF p_expired_before IS NULL OR NOT isfinite(p_expired_before)
        OR p_replay_before IS NULL OR NOT isfinite(p_replay_before)
        OR p_expired_before>statement_timestamp()-interval '24 hours'
        OR p_replay_before>statement_timestamp()
        OR p_expired_before>p_replay_before-interval '24 hours'
        OR p_session_limit IS NULL OR p_session_limit NOT BETWEEN 1 AND 16
        OR p_replay_limit IS NULL OR p_replay_limit NOT BETWEEN 1 AND 512 THEN
        RAISE EXCEPTION 'Invalid native session cleanup bounds' USING ERRCODE='22023';
    END IF;

    -- Lock parents before their children, matching native credential operations.
    -- Busy sessions remain eligible for a subsequent bounded pass.
    WITH selected AS MATERIALIZED (
        SELECT s.id FROM public.native_sessions s
        WHERE s.expires_at<=p_expired_before
        ORDER BY s.expires_at,s.id LIMIT p_session_limit FOR UPDATE SKIP LOCKED
    )
    DELETE FROM public.native_sessions s USING selected d WHERE s.id=d.id;
    GET DIAGNOSTICS deleted_sessions=ROW_COUNT;

    WITH selected AS MATERIALIZED (
        SELECT s.id FROM public.native_sessions s
        WHERE s.expires_at>p_expired_before AND s.exchange_encrypted IS NOT NULL
            AND (s.exchange_replay_until IS NULL OR s.exchange_replay_until<=p_replay_before)
        ORDER BY s.exchange_replay_until NULLS FIRST,s.id
        LIMIT p_replay_limit FOR UPDATE SKIP LOCKED
    )
    UPDATE public.native_sessions s SET exchange_encrypted=NULL,exchange_replay_until=NULL
        FROM selected d WHERE s.id=d.id;
    GET DIAGNOSTICS cleared_exchange_replays=ROW_COUNT;

    WITH selected AS MATERIALIZED (
        SELECT r.token_hash FROM public.consumed_refresh_tokens r
        WHERE r.expires_at>p_expired_before AND r.replay_encrypted IS NOT NULL
            AND (r.replay_until IS NULL OR r.replay_until<=p_replay_before)
        ORDER BY r.replay_until NULLS FIRST,r.token_hash
        LIMIT p_replay_limit FOR UPDATE SKIP LOCKED
    )
    UPDATE public.consumed_refresh_tokens r SET replay_encrypted=NULL
        FROM selected d WHERE r.token_hash=d.token_hash;
    GET DIAGNOSTICS cleared_refresh_replays=ROW_COUNT;
    RETURN NEXT;
END $$;
REVOKE ALL ON FUNCTION prune_native_session_material(timestamptz,timestamptz,integer,integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION prune_native_session_material(timestamptz,timestamptz,integer,integer)
    TO hris_worker_capability;
