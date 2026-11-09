-- Independent Matrix delivery checkpoint. No backfill and no change to FCM progress.
-- Both AFTER INSERT triggers share the original first-publication transaction.
CREATE TABLE collaboration_readiness_controls (
    event_id uuid PRIMARY KEY REFERENCES outbox_events(id),
    company_id uuid NOT NULL,
    publisher_account_id uuid NOT NULL,
    publisher_device_id uuid NOT NULL,
    publication_cutoff timestamptz NOT NULL CHECK(isfinite(publication_cutoff)),
    expires_at timestamptz NOT NULL CHECK(isfinite(expires_at)
        AND expires_at=publication_cutoff+interval '1 day'),
    after_account_id uuid,
    after_device_id uuid,
    available_at timestamptz NOT NULL CHECK(isfinite(available_at)),
    attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 8),
    lease_owner uuid,
    lease_token uuid,
    lease_until timestamptz CHECK(lease_until IS NULL OR (isfinite(lease_until) AND lease_until<=expires_at)),
    completed_at timestamptz CHECK(completed_at IS NULL OR (isfinite(completed_at)
        AND completed_at>=publication_cutoff)),
    UNIQUE(company_id,publisher_device_id),
    FOREIGN KEY(company_id,publisher_device_id,publisher_account_id)
        REFERENCES collaboration_devices(company_id,id,account_id),
    CHECK((after_account_id IS NULL)=(after_device_id IS NULL)),
    CHECK(after_account_id IS NULL OR after_account_id<>publisher_account_id),
    CHECK((lease_owner IS NULL AND lease_token IS NULL AND lease_until IS NULL)
        OR (lease_owner IS NOT NULL AND lease_token IS NOT NULL AND lease_until IS NOT NULL)),
    CHECK(completed_at IS NULL OR lease_token IS NULL)
);
CREATE INDEX collaboration_readiness_control_due ON collaboration_readiness_controls(expires_at,available_at,event_id)
    WHERE completed_at IS NULL AND attempts<8;
CREATE INDEX collaboration_readiness_control_leased ON collaboration_readiness_controls(lease_until)
    WHERE lease_token IS NOT NULL;
ALTER TABLE collaboration_readiness_controls ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_readiness_controls FORCE ROW LEVEL SECURITY;
-- ON CONFLICT also needs a SELECT policy. Keep that capability inside the capture
-- trigger: ordinary publishers must not read worker lease tokens/checkpoints.
CREATE POLICY collaboration_readiness_capture ON collaboration_readiness_controls FOR INSERT
    TO hris_collaboration_readiness_capture
    WITH CHECK(company_id=current_company_id() AND publisher_account_id=current_actor_id());
CREATE POLICY collaboration_readiness_capture_read ON collaboration_readiness_controls FOR SELECT
    TO hris_collaboration_readiness_capture
    USING(company_id=current_company_id() AND publisher_account_id=current_actor_id());
GRANT USAGE ON SCHEMA public TO hris_collaboration_readiness_capture;
GRANT INSERT(event_id,company_id,publisher_account_id,publisher_device_id,
    publication_cutoff,expires_at,available_at)
    ON collaboration_readiness_controls TO hris_collaboration_readiness_capture;
GRANT SELECT(company_id,publisher_device_id)
    ON collaboration_readiness_controls TO hris_collaboration_readiness_capture;
GRANT SELECT(id,company_id,actor_id,event_type,resource_id)
    ON outbox_events TO hris_collaboration_readiness_capture;
GRANT SELECT(company_id,device_id,account_id,version,published_at)
    ON collaboration_device_key_publications TO hris_collaboration_readiness_capture;
CREATE POLICY collaboration_readiness_worker_read ON collaboration_readiness_controls FOR SELECT
    TO hris_worker_capability USING(true);
CREATE POLICY collaboration_readiness_worker_write ON collaboration_readiness_controls FOR UPDATE
    TO hris_worker_capability USING(true) WITH CHECK(true);

CREATE TRIGGER protect_collaboration_readiness_wake BEFORE INSERT OR UPDATE OR DELETE
    ON collaboration_readiness_controls FOR EACH ROW EXECUTE FUNCTION protect_collaboration_readiness_wake();

CREATE FUNCTION capture_collaboration_readiness_control() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE published timestamptz;
BEGIN
    IF NEW.event_type<>'collaboration.device_keys_published' THEN RETURN NEW; END IF;
    SELECT k.published_at INTO published FROM public.collaboration_device_key_publications k
        WHERE k.company_id=NEW.company_id AND k.account_id=NEW.actor_id
            AND k.device_id=NEW.resource_id AND k.version=1;
    IF NOT FOUND OR published IS NULL THEN
        RAISE EXCEPTION 'Readiness publication is not confirmed' USING ERRCODE='23514';
    END IF;
    INSERT INTO public.collaboration_readiness_controls(event_id,company_id,publisher_account_id,
        publisher_device_id,publication_cutoff,expires_at,available_at)
        VALUES(NEW.id,NEW.company_id,NEW.actor_id,NEW.resource_id,published,
            published+interval '1 day',published)
        ON CONFLICT(company_id,publisher_device_id) DO NOTHING;
    RETURN NEW;
END $$;
ALTER FUNCTION capture_collaboration_readiness_control() OWNER TO hris_collaboration_readiness_capture;
REVOKE ALL ON FUNCTION capture_collaboration_readiness_control() FROM PUBLIC;
CREATE TRIGGER capture_collaboration_readiness_control AFTER INSERT ON outbox_events
    FOR EACH ROW WHEN (NEW.event_type='collaboration.device_keys_published')
    EXECUTE FUNCTION capture_collaboration_readiness_control();

CREATE FUNCTION claim_collaboration_readiness_controls(p_owner uuid,p_limit integer)
RETURNS SETOF collaboration_readiness_controls LANGUAGE plpgsql SECURITY INVOKER
SET search_path=pg_catalog,public AS $$
DECLARE at_time timestamptz := clock_timestamp(); capacity integer;
BEGIN
    IF NOT pg_has_role(current_user,'hris_worker_capability','member') THEN
        RAISE EXCEPTION 'Readiness worker required' USING ERRCODE='42501';
    END IF;
    IF p_owner IS NULL OR p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 4 THEN
        RAISE EXCEPTION 'Invalid readiness claim bounds' USING ERRCODE='22023';
    END IF;
    IF NOT pg_try_advisory_xact_lock(hashtextextended('hris:collaboration-readiness-control-claims',0)) THEN RETURN; END IF;
    SELECT greatest(0,16-count(*)) INTO capacity FROM (
        SELECT 1 FROM public.collaboration_readiness_controls WHERE lease_until>at_time LIMIT 16
    ) active;
    IF capacity=0 THEN RETURN; END IF;
    RETURN QUERY WITH candidates AS (
        SELECT event_id FROM public.collaboration_readiness_controls
        WHERE completed_at IS NULL AND expires_at>at_time AND available_at<=at_time AND attempts<8
            AND (lease_token IS NULL OR lease_until<=at_time)
        ORDER BY expires_at,available_at,event_id FOR UPDATE SKIP LOCKED LIMIT least(p_limit,capacity)
    ) UPDATE public.collaboration_readiness_controls w SET lease_owner=p_owner,lease_token=gen_random_uuid(),
        lease_until=least(w.expires_at,at_time+interval '60 seconds'),attempts=w.attempts+1
        FROM candidates c WHERE w.event_id=c.event_id RETURNING w.*;
END $$;
REVOKE ALL ON FUNCTION claim_collaboration_readiness_controls(uuid,integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION claim_collaboration_readiness_controls(uuid,integer) TO hris_worker_capability;

-- Public device/publication coordinates only, independent of notification permission.
CREATE INDEX collaboration_device_key_readiness_target
    ON collaboration_device_key_publications(company_id,account_id,device_id)
    INCLUDE(published_at) WHERE version=1;
GRANT SELECT ON collaboration_readiness_controls TO hris_collaboration_readiness_reader;
GRANT SELECT(company_id,account_id,device_id,version,published_at)
    ON collaboration_device_key_publications TO hris_collaboration_readiness_reader;
GRANT SELECT(company_id,account_id,id,state,platform)
    ON collaboration_devices TO hris_collaboration_readiness_reader;
GRANT SELECT(company_id,account_id,device_id,native_session_id)
    ON collaboration_device_sessions TO hris_collaboration_readiness_reader;
CREATE POLICY collaboration_readiness_control_projection_sessions ON collaboration_device_sessions FOR SELECT
    TO hris_collaboration_readiness_reader USING(company_id=current_company_id());
CREATE POLICY collaboration_readiness_control_projection_source ON collaboration_readiness_controls FOR SELECT
    TO hris_collaboration_readiness_reader
    USING(company_id=current_company_id() AND publisher_account_id=current_actor_id());
CREATE POLICY collaboration_readiness_control_projection_keys ON collaboration_device_key_publications FOR SELECT
    TO hris_collaboration_readiness_reader USING(company_id=current_company_id());
CREATE POLICY collaboration_readiness_control_projection_devices ON collaboration_devices FOR SELECT
    TO hris_collaboration_readiness_reader USING(company_id=current_company_id());
CREATE VIEW collaboration_readiness_control_targets WITH(security_barrier=true) AS
SELECT w.event_id,w.company_id,w.publisher_account_id,w.publisher_device_id,
    w.publication_cutoff,w.after_account_id,w.after_device_id,w.lease_owner,w.lease_token,w.lease_until,
    k.account_id AS target_account_id,k.device_id AS target_device_id,s.native_session_id
FROM public.collaboration_readiness_controls w
JOIN public.collaboration_device_key_publications k ON k.company_id=w.company_id
JOIN public.collaboration_devices d ON d.company_id=k.company_id AND d.account_id=k.account_id AND d.id=k.device_id
JOIN public.collaboration_device_sessions s ON s.company_id=d.company_id AND s.account_id=d.account_id AND s.device_id=d.id
WHERE pg_has_role(session_user,'hris_worker_capability','member')
    AND w.company_id=public.current_company_id() AND w.publisher_account_id=public.current_actor_id()
    AND w.completed_at IS NULL AND w.lease_until>clock_timestamp() AND w.expires_at>clock_timestamp()
    AND k.account_id<>w.publisher_account_id AND k.version=1 AND k.published_at<=w.publication_cutoff
    AND d.state='ACTIVE' AND d.platform IN ('ANDROID','IOS')
    AND (w.after_account_id IS NULL OR (k.account_id,k.device_id)>(w.after_account_id,w.after_device_id))
    AND EXISTS(SELECT 1 FROM public.collaboration_direct_conversations c
        WHERE c.company_id=w.company_id AND c.created_at<=w.publication_cutoff
            AND c.first_account_id=least(w.publisher_account_id,k.account_id)
            AND c.second_account_id=greatest(w.publisher_account_id,k.account_id));
ALTER VIEW collaboration_readiness_control_targets OWNER TO hris_collaboration_readiness_reader;
GRANT SELECT ON collaboration_readiness_control_targets TO hris_worker_capability;
