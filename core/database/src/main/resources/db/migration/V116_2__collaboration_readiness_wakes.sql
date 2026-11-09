-- One retained fanout checkpoint for a device's immutable first key publication.
-- Capture shares the existing outbox transaction; provider delivery remains separate.
CREATE TABLE collaboration_readiness_wakes (
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
CREATE INDEX collaboration_readiness_due ON collaboration_readiness_wakes(expires_at,available_at,event_id)
    WHERE completed_at IS NULL AND attempts<8;
CREATE INDEX collaboration_readiness_leased ON collaboration_readiness_wakes(lease_until)
    WHERE lease_token IS NOT NULL;
ALTER TABLE collaboration_readiness_wakes ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_readiness_wakes FORCE ROW LEVEL SECURITY;
-- ON CONFLICT also needs a SELECT policy. Keep that capability inside the capture
-- trigger: ordinary publishers must not read worker lease tokens/checkpoints.
DO $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='hris_collaboration_readiness_capture') THEN
        CREATE ROLE hris_collaboration_readiness_capture NOLOGIN NOINHERIT NOSUPERUSER
            NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;
END $$;
CREATE POLICY collaboration_readiness_capture ON collaboration_readiness_wakes FOR INSERT
    TO hris_collaboration_readiness_capture
    WITH CHECK(company_id=current_company_id() AND publisher_account_id=current_actor_id());
CREATE POLICY collaboration_readiness_capture_read ON collaboration_readiness_wakes FOR SELECT
    TO hris_collaboration_readiness_capture
    USING(company_id=current_company_id() AND publisher_account_id=current_actor_id());
GRANT USAGE ON SCHEMA public TO hris_collaboration_readiness_capture;
GRANT INSERT(event_id,company_id,publisher_account_id,publisher_device_id,
    publication_cutoff,expires_at,available_at)
    ON collaboration_readiness_wakes TO hris_collaboration_readiness_capture;
GRANT SELECT(company_id,publisher_device_id)
    ON collaboration_readiness_wakes TO hris_collaboration_readiness_capture;
GRANT SELECT(id,company_id,actor_id,event_type,resource_id)
    ON outbox_events TO hris_collaboration_readiness_capture;
GRANT SELECT(company_id,device_id,account_id,version,published_at)
    ON collaboration_device_key_publications TO hris_collaboration_readiness_capture;
CREATE POLICY collaboration_readiness_worker_read ON collaboration_readiness_wakes FOR SELECT
    TO hris_worker_capability USING(true);
CREATE POLICY collaboration_readiness_worker_write ON collaboration_readiness_wakes FOR UPDATE
    TO hris_worker_capability USING(true) WITH CHECK(true);

CREATE FUNCTION protect_collaboration_readiness_wake() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog,public AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Readiness publication identity is retained' USING ERRCODE='23514';
    ELSIF TG_OP='INSERT' THEN
        IF NEW.company_id IS DISTINCT FROM public.current_company_id()
            OR NEW.publisher_account_id IS DISTINCT FROM public.current_actor_id()
            OR NEW.after_account_id IS NOT NULL OR NEW.lease_token IS NOT NULL
            OR NEW.attempts<>0 OR NEW.completed_at IS NOT NULL
            OR NEW.available_at<>NEW.publication_cutoff
            OR NOT EXISTS(SELECT 1 FROM public.outbox_events e
                JOIN public.collaboration_device_key_publications k
                    ON k.company_id=e.company_id AND k.device_id=e.resource_id AND k.account_id=e.actor_id
                WHERE e.id=NEW.event_id AND e.event_type='collaboration.device_keys_published'
                    AND e.company_id=NEW.company_id AND e.actor_id=NEW.publisher_account_id
                    AND e.resource_id=NEW.publisher_device_id AND k.version=1
                    AND k.published_at=NEW.publication_cutoff) THEN
            RAISE EXCEPTION 'Invalid readiness publication source' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF (NEW.event_id,NEW.company_id,NEW.publisher_account_id,NEW.publisher_device_id,
            NEW.publication_cutoff,NEW.expires_at)
        IS DISTINCT FROM (OLD.event_id,OLD.company_id,OLD.publisher_account_id,OLD.publisher_device_id,
            OLD.publication_cutoff,OLD.expires_at) OR OLD.completed_at IS NOT NULL THEN
        RAISE EXCEPTION 'Readiness publication identity changed' USING ERRCODE='23514';
    END IF;
    IF (NEW.after_account_id,NEW.after_device_id)
        IS DISTINCT FROM (OLD.after_account_id,OLD.after_device_id) THEN
        IF OLD.lease_token IS NULL OR OLD.lease_until<=clock_timestamp()
            OR (NEW.lease_owner,NEW.lease_token,NEW.lease_until)
                IS DISTINCT FROM (OLD.lease_owner,OLD.lease_token,OLD.lease_until)
            OR NEW.after_account_id IS NULL
            OR (OLD.after_account_id IS NOT NULL AND
                (NEW.after_account_id,NEW.after_device_id)<=(OLD.after_account_id,OLD.after_device_id))
            OR NEW.attempts<>0 OR NEW.available_at<>OLD.available_at
            OR NEW.completed_at IS NOT NULL THEN
            RAISE EXCEPTION 'Invalid readiness checkpoint advance' USING ERRCODE='23514';
        END IF;
    ELSIF NEW.lease_token IS NOT NULL AND NEW.lease_token IS DISTINCT FROM OLD.lease_token THEN
        IF OLD.lease_until>clock_timestamp() OR OLD.expires_at<=clock_timestamp()
            OR OLD.available_at>clock_timestamp() OR OLD.attempts>=8
            OR NEW.attempts<>OLD.attempts+1 OR NEW.available_at<>OLD.available_at
            OR NEW.completed_at IS NOT NULL OR NEW.lease_until<=clock_timestamp()
            OR NEW.lease_until>clock_timestamp()+interval '60 seconds' THEN
            RAISE EXCEPTION 'Invalid readiness lease claim' USING ERRCODE='23514';
        END IF;
    ELSIF NEW.lease_token IS NULL AND OLD.lease_token IS NOT NULL THEN
        IF OLD.lease_until<=clock_timestamp() OR NEW.attempts<>OLD.attempts
            OR NEW.available_at>clock_timestamp()+interval '300 seconds'
            OR (NEW.completed_at IS NOT NULL AND NEW.completed_at>clock_timestamp()) THEN
            RAISE EXCEPTION 'Invalid readiness lease release' USING ERRCODE='23514';
        END IF;
    ELSE
        RAISE EXCEPTION 'Readiness update requires a fenced transition' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_readiness_wake BEFORE INSERT OR UPDATE OR DELETE
    ON collaboration_readiness_wakes FOR EACH ROW EXECUTE FUNCTION protect_collaboration_readiness_wake();

CREATE FUNCTION capture_collaboration_readiness_wake() RETURNS trigger
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
    INSERT INTO public.collaboration_readiness_wakes(event_id,company_id,publisher_account_id,
        publisher_device_id,publication_cutoff,expires_at,available_at)
        VALUES(NEW.id,NEW.company_id,NEW.actor_id,NEW.resource_id,published,
            published+interval '1 day',published)
        ON CONFLICT(company_id,publisher_device_id) DO NOTHING;
    RETURN NEW;
END $$;
ALTER FUNCTION capture_collaboration_readiness_wake() OWNER TO hris_collaboration_readiness_capture;
REVOKE ALL ON FUNCTION capture_collaboration_readiness_wake() FROM PUBLIC;
CREATE TRIGGER capture_collaboration_readiness_wake AFTER INSERT ON outbox_events
    FOR EACH ROW WHEN (NEW.event_type='collaboration.device_keys_published')
    EXECUTE FUNCTION capture_collaboration_readiness_wake();

-- Queue-wide coordinates only. Runtime/API roles cannot claim another account's fanout.
CREATE FUNCTION claim_collaboration_readiness_wakes(p_owner uuid,p_limit integer)
RETURNS SETOF collaboration_readiness_wakes LANGUAGE plpgsql SECURITY INVOKER
SET search_path=pg_catalog,public AS $$
DECLARE at_time timestamptz := clock_timestamp(); capacity integer;
BEGIN
    IF NOT pg_has_role(current_user,'hris_worker_capability','member') THEN
        RAISE EXCEPTION 'Readiness worker required' USING ERRCODE='42501';
    END IF;
    IF p_owner IS NULL OR p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 4 THEN
        RAISE EXCEPTION 'Invalid readiness claim bounds' USING ERRCODE='22023';
    END IF;
    IF NOT pg_try_advisory_xact_lock(hashtextextended('hris:collaboration-readiness-claims',0)) THEN RETURN; END IF;
    SELECT greatest(0,16-count(*)) INTO capacity FROM (
        SELECT 1 FROM public.collaboration_readiness_wakes WHERE lease_until>at_time LIMIT 16
    ) active;
    IF capacity=0 THEN RETURN; END IF;
    RETURN QUERY WITH candidates AS (
        SELECT event_id FROM public.collaboration_readiness_wakes
        WHERE completed_at IS NULL AND expires_at>at_time AND available_at<=at_time AND attempts<8
            AND (lease_token IS NULL OR lease_until<=at_time)
        ORDER BY expires_at,available_at,event_id FOR UPDATE SKIP LOCKED LIMIT least(p_limit,capacity)
    ) UPDATE public.collaboration_readiness_wakes w SET lease_owner=p_owner,lease_token=gen_random_uuid(),
        lease_until=least(w.expires_at,at_time+interval '60 seconds'),attempts=w.attempts+1
        FROM candidates c WHERE w.event_id=c.event_id RETURNING w.*;
END $$;
REVOKE ALL ON FUNCTION claim_collaboration_readiness_wakes(uuid,integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION claim_collaboration_readiness_wakes(uuid,integer) TO hris_worker_capability;

-- The stable target cursor seeks published binding metadata without scanning prior pages.
CREATE INDEX collaboration_push_binding_readiness_target
    ON collaboration_push_bindings(company_id,account_id,device_id)
    INCLUDE(native_session_id,published_at) WHERE version=1;

-- A non-login reader gets only immutable public binding coordinates, never pusher secrets.
DO $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='hris_collaboration_readiness_reader') THEN
        CREATE ROLE hris_collaboration_readiness_reader NOLOGIN NOINHERIT NOSUPERUSER
            NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;
END $$;
GRANT USAGE ON SCHEMA public TO hris_collaboration_readiness_reader;
GRANT SELECT ON collaboration_readiness_wakes TO hris_collaboration_readiness_reader;
GRANT SELECT(company_id,account_id,device_id,native_session_id,version,published_at)
    ON collaboration_push_bindings TO hris_collaboration_readiness_reader;
GRANT SELECT(company_id,first_account_id,second_account_id,created_at)
    ON collaboration_direct_conversations TO hris_collaboration_readiness_reader;
CREATE POLICY collaboration_readiness_projection_source ON collaboration_readiness_wakes FOR SELECT
    TO hris_collaboration_readiness_reader
    USING(company_id=current_company_id() AND publisher_account_id=current_actor_id());
CREATE POLICY collaboration_readiness_projection_binding ON collaboration_push_bindings FOR SELECT
    TO hris_collaboration_readiness_reader USING(company_id=current_company_id());
CREATE VIEW collaboration_readiness_targets WITH(security_barrier=true) AS
SELECT w.event_id,w.company_id,w.publisher_account_id,w.publisher_device_id,
    w.publication_cutoff,w.after_account_id,w.after_device_id,w.lease_owner,w.lease_token,w.lease_until,
    b.account_id AS target_account_id,b.device_id AS target_device_id,b.native_session_id
FROM public.collaboration_readiness_wakes w
JOIN public.collaboration_push_bindings b ON b.company_id=w.company_id
WHERE pg_has_role(session_user,'hris_worker_capability','member')
    AND w.company_id=public.current_company_id() AND w.publisher_account_id=public.current_actor_id()
    AND w.completed_at IS NULL AND w.lease_until>clock_timestamp() AND w.expires_at>clock_timestamp()
    AND b.account_id<>w.publisher_account_id AND b.version=1 AND b.published_at<=w.publication_cutoff
    AND (w.after_account_id IS NULL OR (b.account_id,b.device_id)>(w.after_account_id,w.after_device_id))
    AND EXISTS(SELECT 1 FROM public.collaboration_direct_conversations d
        WHERE d.company_id=w.company_id AND d.created_at<=w.publication_cutoff
            AND d.first_account_id=least(w.publisher_account_id,b.account_id)
            AND d.second_account_id=greatest(w.publisher_account_id,b.account_id));
ALTER VIEW collaboration_readiness_targets OWNER TO hris_collaboration_readiness_reader;
-- The session-role predicate stays authoritative even in installations granting SELECT on all views.
GRANT SELECT ON collaboration_readiness_targets TO hris_worker_capability;
