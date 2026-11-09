-- Organization first-key publication and both delivery channels commit together.
-- No historical backfill, payload access, private-key generation or provider I/O.
DO $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='hris_collaboration_space_readiness_capture') THEN
        CREATE ROLE hris_collaboration_space_readiness_capture NOLOGIN NOINHERIT NOSUPERUSER
            NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;
    IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='hris_collaboration_space_readiness_reader') THEN
        CREATE ROLE hris_collaboration_space_readiness_reader NOLOGIN NOINHERIT NOSUPERUSER
            NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;
END $$;
GRANT USAGE ON SCHEMA public TO hris_collaboration_space_readiness_capture,hris_collaboration_space_readiness_reader;

CREATE TABLE collaboration_space_readiness_revisions (
    space_id uuid PRIMARY KEY REFERENCES collaboration_spaces(id),
    revision bigint NOT NULL CHECK(revision>0)
);
ALTER TABLE collaboration_space_readiness_revisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_space_readiness_revisions FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_space_readiness_revision_capture ON collaboration_space_readiness_revisions
    TO hris_collaboration_space_readiness_capture
    USING(current_company_id() IS NULL) WITH CHECK(current_company_id() IS NULL);
GRANT SELECT(space_id,revision),INSERT(space_id,revision),UPDATE(revision)
    ON collaboration_space_readiness_revisions TO hris_collaboration_space_readiness_capture;

CREATE FUNCTION protect_collaboration_space_readiness_revision() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog,public AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Organization publication revision is retained' USING ERRCODE='23514';
    ELSIF TG_OP='INSERT' THEN
        IF public.current_company_id() IS NOT NULL OR NEW.revision<>1 THEN
            RAISE EXCEPTION 'Invalid organization publication revision' USING ERRCODE='23514';
        END IF;
    ELSIF NEW.space_id IS DISTINCT FROM OLD.space_id OR NEW.revision<>OLD.revision+1 THEN
        RAISE EXCEPTION 'Organization publication revision must advance once' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_space_readiness_revision BEFORE INSERT OR UPDATE OR DELETE
    ON collaboration_space_readiness_revisions FOR EACH ROW EXECUTE FUNCTION protect_collaboration_space_readiness_revision();

CREATE TABLE collaboration_space_readiness_deliveries (
    event_id uuid NOT NULL REFERENCES outbox_events(id),
    channel varchar(8) NOT NULL CHECK(channel IN ('MATRIX','PUSH')),
    space_id uuid NOT NULL,
    publisher_account_id uuid NOT NULL,
    publisher_device_id uuid NOT NULL,
    publication_revision bigint NOT NULL CHECK(publication_revision>0),
    publication_cutoff timestamptz NOT NULL CHECK(isfinite(publication_cutoff)),
    captured_at timestamptz NOT NULL CHECK(isfinite(captured_at) AND captured_at>=publication_cutoff),
    expires_at timestamptz NOT NULL CHECK(isfinite(expires_at) AND expires_at=publication_cutoff+interval '1 day'),
    after_account_id uuid,
    after_device_id uuid,
    available_at timestamptz NOT NULL CHECK(isfinite(available_at) AND available_at>=publication_cutoff),
    attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 8),
    lease_owner uuid CHECK(lease_owner IS NULL OR lease_owner<>'00000000-0000-0000-0000-000000000000'),
    lease_token uuid CHECK(lease_token IS NULL OR lease_token<>'00000000-0000-0000-0000-000000000000'),
    lease_until timestamptz CHECK(lease_until IS NULL OR (isfinite(lease_until) AND lease_until<=expires_at)),
    completed_at timestamptz CHECK(completed_at IS NULL OR (isfinite(completed_at) AND completed_at>=captured_at)),
    PRIMARY KEY(event_id,channel),
    UNIQUE(space_id,publisher_device_id,channel),
    UNIQUE(space_id,publication_revision,channel),
    FOREIGN KEY(space_id,publisher_device_id) REFERENCES collaboration_space_key_publications(space_id,device_id),
    CHECK((after_account_id IS NULL)=(after_device_id IS NULL)),
    CHECK(after_account_id IS NULL OR after_account_id<>publisher_account_id),
    CHECK((lease_owner IS NULL AND lease_token IS NULL AND lease_until IS NULL)
        OR (lease_owner IS NOT NULL AND lease_token IS NOT NULL AND lease_until IS NOT NULL)),
    CHECK(completed_at IS NULL OR lease_token IS NULL)
);
CREATE INDEX collaboration_space_readiness_due
    ON collaboration_space_readiness_deliveries(channel,expires_at,available_at,event_id)
    WHERE completed_at IS NULL AND attempts<8;
CREATE INDEX collaboration_space_readiness_leased ON collaboration_space_readiness_deliveries(channel,lease_until)
    WHERE lease_token IS NOT NULL;
ALTER TABLE collaboration_space_readiness_deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_space_readiness_deliveries FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_space_readiness_capture ON collaboration_space_readiness_deliveries FOR INSERT
    TO hris_collaboration_space_readiness_capture
    WITH CHECK(current_company_id() IS NULL AND publisher_account_id=current_actor_id());
CREATE POLICY collaboration_space_readiness_capture_read ON collaboration_space_readiness_deliveries FOR SELECT
    TO hris_collaboration_space_readiness_capture
    USING(current_company_id() IS NULL AND publisher_account_id=current_actor_id());
CREATE POLICY collaboration_space_readiness_worker_read ON collaboration_space_readiness_deliveries FOR SELECT
    TO hris_worker_capability USING(true);
CREATE POLICY collaboration_space_readiness_worker_write ON collaboration_space_readiness_deliveries FOR UPDATE
    TO hris_worker_capability USING(true) WITH CHECK(true);
GRANT SELECT,UPDATE ON collaboration_space_readiness_deliveries TO hris_worker_capability;
GRANT INSERT(event_id,channel,space_id,publisher_account_id,publisher_device_id,publication_revision,
    publication_cutoff,captured_at,expires_at,available_at)
    ON collaboration_space_readiness_deliveries TO hris_collaboration_space_readiness_capture;
GRANT SELECT(space_id,publisher_device_id,channel)
    ON collaboration_space_readiness_deliveries TO hris_collaboration_space_readiness_capture;
GRANT SELECT(id,company_id,actor_id,event_type,resource_id)
    ON outbox_events TO hris_collaboration_space_readiness_capture;
GRANT SELECT(space_id,device_id,account_id,version,published_at)
    ON collaboration_space_key_publications TO hris_collaboration_space_readiness_capture;

CREATE FUNCTION capture_collaboration_space_readiness() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE published timestamptz; captured_space uuid; captured_revision bigint; captured timestamptz; retained integer;
BEGIN
    IF NEW.event_type<>'collaboration.space_device_keys_published' THEN RETURN NEW; END IF;
    IF NEW.company_id IS NOT NULL OR public.current_company_id() IS NOT NULL
        OR NEW.actor_id IS DISTINCT FROM public.current_actor_id() THEN
        RAISE EXCEPTION 'Invalid organization readiness scope' USING ERRCODE='23514';
    END IF;
    -- There is exactly one immutable organization per installation. Its key record,
    -- not outbox JSON or a selected HR company, supplies the actual organization ID.
    SELECT k.space_id,k.published_at INTO captured_space,published FROM public.collaboration_space_key_publications k
        WHERE k.account_id=NEW.actor_id AND k.device_id=NEW.resource_id AND k.version=1;
    IF NOT FOUND OR published IS NULL THEN
        RAISE EXCEPTION 'Organization readiness publication is not confirmed' USING ERRCODE='23514';
    END IF;
    SELECT count(*) INTO retained FROM public.collaboration_space_readiness_deliveries d
        WHERE d.space_id=captured_space AND d.publisher_device_id=NEW.resource_id;
    IF retained=2 THEN RETURN NEW; END IF;
    IF retained<>0 THEN
        RAISE EXCEPTION 'Organization readiness channels are incomplete' USING ERRCODE='23514';
    END IF;
    -- Final shared guard: callers already hold their account/native/device/key guards.
    -- Keep the counter lock through the original publication transaction's commit.
    INSERT INTO public.collaboration_space_readiness_revisions(space_id,revision) VALUES(captured_space,1)
        ON CONFLICT(space_id) DO UPDATE SET revision=collaboration_space_readiness_revisions.revision+1
        RETURNING collaboration_space_readiness_revisions.revision INTO captured_revision;
    captured := clock_timestamp();
    INSERT INTO public.collaboration_space_readiness_deliveries(event_id,channel,space_id,
        publisher_account_id,publisher_device_id,publication_revision,publication_cutoff,captured_at,expires_at,available_at)
        SELECT NEW.id,channel,captured_space,NEW.actor_id,NEW.resource_id,captured_revision,published,captured,
            published+interval '1 day',captured FROM (VALUES('MATRIX'),('PUSH')) channels(channel);
    RETURN NEW;
END $$;
ALTER FUNCTION capture_collaboration_space_readiness() OWNER TO hris_collaboration_space_readiness_capture;
REVOKE ALL ON FUNCTION capture_collaboration_space_readiness() FROM PUBLIC;
CREATE TRIGGER capture_collaboration_space_readiness AFTER INSERT ON outbox_events
    FOR EACH ROW WHEN (NEW.event_type='collaboration.space_device_keys_published')
    EXECUTE FUNCTION capture_collaboration_space_readiness();

CREATE FUNCTION protect_collaboration_space_readiness_delivery() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog,public AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Organization readiness identity is retained' USING ERRCODE='23514';
    ELSIF TG_OP='INSERT' THEN
        IF public.current_company_id() IS NOT NULL OR NEW.publisher_account_id IS DISTINCT FROM public.current_actor_id()
            OR NEW.after_account_id IS NOT NULL OR NEW.lease_token IS NOT NULL OR NEW.attempts<>0
            OR NEW.completed_at IS NOT NULL OR NEW.available_at<>NEW.captured_at
            OR NEW.captured_at>clock_timestamp()
            OR NOT EXISTS(SELECT 1 FROM public.collaboration_space_readiness_revisions r
                WHERE r.space_id=NEW.space_id AND r.revision=NEW.publication_revision)
            OR NOT EXISTS(SELECT 1 FROM public.outbox_events e
                JOIN public.collaboration_space_key_publications k
                    ON k.device_id=e.resource_id AND k.account_id=e.actor_id
                WHERE e.id=NEW.event_id AND e.company_id IS NULL
                    AND e.event_type='collaboration.space_device_keys_published'
                    AND e.actor_id=NEW.publisher_account_id AND e.resource_id=NEW.publisher_device_id
                    AND k.space_id=NEW.space_id AND k.version=1 AND k.published_at=NEW.publication_cutoff) THEN
            RAISE EXCEPTION 'Invalid organization readiness source' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF public.current_company_id() IS NOT NULL
        OR NOT pg_has_role(current_user,'hris_worker_capability','member') THEN
        RAISE EXCEPTION 'Organization readiness worker required' USING ERRCODE='42501';
    END IF;
    IF (NEW.event_id,NEW.channel,NEW.space_id,NEW.publisher_account_id,NEW.publisher_device_id,
        NEW.publication_revision,NEW.publication_cutoff,NEW.captured_at,NEW.expires_at)
        IS DISTINCT FROM (OLD.event_id,OLD.channel,OLD.space_id,OLD.publisher_account_id,OLD.publisher_device_id,
        OLD.publication_revision,OLD.publication_cutoff,OLD.captured_at,OLD.expires_at)
        OR OLD.completed_at IS NOT NULL THEN
        RAISE EXCEPTION 'Organization readiness identity changed' USING ERRCODE='23514';
    END IF;
    IF (NEW.after_account_id,NEW.after_device_id) IS DISTINCT FROM (OLD.after_account_id,OLD.after_device_id) THEN
        IF OLD.publisher_account_id IS DISTINCT FROM public.current_actor_id()
            OR OLD.lease_token IS NULL OR OLD.lease_until<=clock_timestamp()
            OR (NEW.lease_owner,NEW.lease_token,NEW.lease_until)
                IS DISTINCT FROM (OLD.lease_owner,OLD.lease_token,OLD.lease_until)
            OR NEW.after_account_id IS NULL
            OR (OLD.after_account_id IS NOT NULL AND
                (NEW.after_account_id,NEW.after_device_id)<=(OLD.after_account_id,OLD.after_device_id))
            OR NEW.attempts<>0 OR NEW.available_at<>OLD.available_at OR NEW.completed_at IS NOT NULL THEN
            RAISE EXCEPTION 'Invalid organization readiness cursor advance' USING ERRCODE='23514';
        END IF;
    ELSIF NEW.lease_token IS NOT NULL AND NEW.lease_token IS DISTINCT FROM OLD.lease_token THEN
        IF OLD.lease_until>clock_timestamp() OR OLD.expires_at<=clock_timestamp()
            OR OLD.available_at>clock_timestamp() OR OLD.attempts>=8
            OR NEW.attempts<>OLD.attempts+1 OR NEW.available_at<>OLD.available_at
            OR NEW.completed_at IS NOT NULL OR NEW.lease_until<=clock_timestamp()
            OR NEW.lease_until>clock_timestamp()+interval '60 seconds' THEN
            RAISE EXCEPTION 'Invalid organization readiness claim' USING ERRCODE='23514';
        END IF;
    ELSIF NEW.lease_token IS NULL AND OLD.lease_token IS NOT NULL THEN
        IF OLD.publisher_account_id IS DISTINCT FROM public.current_actor_id()
            OR OLD.lease_until<=clock_timestamp() OR NEW.attempts<>OLD.attempts
            OR NEW.available_at>clock_timestamp()+interval '300 seconds'
            OR (NEW.completed_at IS NOT NULL AND NEW.completed_at>clock_timestamp()) THEN
            RAISE EXCEPTION 'Invalid organization readiness release' USING ERRCODE='23514';
        END IF;
    ELSE
        RAISE EXCEPTION 'Organization readiness update requires a lease transition' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_space_readiness_delivery BEFORE INSERT OR UPDATE OR DELETE
    ON collaboration_space_readiness_deliveries FOR EACH ROW EXECUTE FUNCTION protect_collaboration_space_readiness_delivery();

CREATE FUNCTION claim_collaboration_space_readiness(p_channel varchar,p_owner uuid,p_limit integer)
RETURNS SETOF collaboration_space_readiness_deliveries LANGUAGE plpgsql SECURITY INVOKER
SET search_path=pg_catalog,public AS $$
DECLARE at_time timestamptz := clock_timestamp(); capacity integer;
BEGIN
    IF public.current_company_id() IS NOT NULL
        OR NOT pg_has_role(current_user,'hris_worker_capability','member') THEN
        RAISE EXCEPTION 'Organization readiness worker required' USING ERRCODE='42501';
    END IF;
    IF p_channel IS NULL OR p_channel NOT IN ('MATRIX','PUSH') OR p_owner IS NULL
        OR p_owner='00000000-0000-0000-0000-000000000000' OR p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 4 THEN
        RAISE EXCEPTION 'Invalid organization readiness claim bounds' USING ERRCODE='22023';
    END IF;
    IF NOT pg_try_advisory_xact_lock(hashtextextended('hris:space-readiness-claims:'||p_channel,0)) THEN RETURN; END IF;
    SELECT greatest(0,8-count(*)) INTO capacity FROM (
        SELECT 1 FROM public.collaboration_space_readiness_deliveries
        WHERE channel=p_channel AND lease_until>at_time LIMIT 8
    ) active;
    IF capacity=0 THEN RETURN; END IF;
    RETURN QUERY WITH candidates AS (
        SELECT event_id FROM public.collaboration_space_readiness_deliveries
        WHERE channel=p_channel AND completed_at IS NULL AND expires_at>at_time AND available_at<=at_time
            AND attempts<8 AND (lease_token IS NULL OR lease_until<=at_time)
        ORDER BY expires_at,available_at,event_id FOR UPDATE SKIP LOCKED LIMIT least(p_limit,capacity)
    ) UPDATE public.collaboration_space_readiness_deliveries d
        SET lease_owner=p_owner,lease_token=gen_random_uuid(),
            lease_until=least(d.expires_at,at_time+interval '60 seconds'),attempts=d.attempts+1
        FROM candidates c WHERE d.event_id=c.event_id AND d.channel=p_channel RETURNING d.*;
END $$;
REVOKE ALL ON FUNCTION claim_collaboration_space_readiness(varchar,uuid,integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION claim_collaboration_space_readiness(varchar,uuid,integer) TO hris_worker_capability;

-- Only existing peer/device coordinates are exposed through the exact claimed row.
-- Current per-target delivery admission remains the fanout use case's responsibility.
GRANT SELECT ON collaboration_space_readiness_deliveries TO hris_collaboration_space_readiness_reader;
GRANT SELECT(space_id,account_id,device_id,publication_confirmed)
    ON collaboration_space_recipient_devices TO hris_collaboration_space_readiness_reader;
GRANT SELECT(space_id,account_id,device_id,native_session_id,version,published_at)
    ON collaboration_space_key_publications TO hris_collaboration_space_readiness_reader;
GRANT SELECT(space_id,conversation_id,first_account_id,second_account_id,created_at)
    ON collaboration_space_room_intents TO hris_collaboration_space_readiness_reader;
GRANT SELECT(space_id,conversation_id,created_at)
    ON collaboration_space_room_bindings TO hris_collaboration_space_readiness_reader;
CREATE POLICY collaboration_space_readiness_projection_queue ON collaboration_space_readiness_deliveries FOR SELECT
    TO hris_collaboration_space_readiness_reader
    USING(current_company_id() IS NULL);
CREATE POLICY collaboration_space_readiness_projection_keys ON collaboration_space_key_publications FOR SELECT
    TO hris_collaboration_space_readiness_reader USING(current_company_id() IS NULL);
CREATE POLICY collaboration_space_readiness_projection_intents ON collaboration_space_room_intents FOR SELECT
    TO hris_collaboration_space_readiness_reader USING(current_company_id() IS NULL);
CREATE POLICY collaboration_space_readiness_projection_rooms ON collaboration_space_room_bindings FOR SELECT
    TO hris_collaboration_space_readiness_reader USING(current_company_id() IS NULL);
CREATE VIEW collaboration_space_readiness_targets WITH(security_barrier=true) AS
SELECT d.event_id,d.channel,d.space_id,d.publisher_account_id,d.publisher_device_id,d.publication_revision,
    d.publication_cutoff,d.captured_at,d.expires_at,d.available_at,d.attempts,
    d.after_account_id,d.after_device_id,d.lease_owner,d.lease_token,d.lease_until,
    r.account_id AS target_account_id,r.device_id AS target_device_id,k.native_session_id
FROM public.collaboration_space_readiness_deliveries d
JOIN public.collaboration_space_recipient_devices r ON r.space_id=d.space_id
JOIN public.collaboration_space_key_publications k
    ON k.space_id=r.space_id AND k.account_id=r.account_id AND k.device_id=r.device_id
LEFT JOIN public.collaboration_space_readiness_deliveries receiver_publication
    ON receiver_publication.space_id=r.space_id AND receiver_publication.publisher_device_id=r.device_id
    AND receiver_publication.publisher_account_id=r.account_id AND receiver_publication.channel=d.channel
WHERE pg_has_role(session_user,'hris_worker_capability','member') AND public.current_company_id() IS NULL
    AND d.publisher_account_id=public.current_actor_id() AND d.completed_at IS NULL
    AND d.lease_until>clock_timestamp() AND d.expires_at>clock_timestamp()
    AND r.account_id<>d.publisher_account_id AND r.publication_confirmed AND k.version=1
    AND k.published_at<=d.captured_at
    -- Publication and both channels are atomic. No receiver row means retained
    -- pre-V139 keys; a later commit must not sneak in through an older timestamp.
    AND (receiver_publication.event_id IS NULL OR receiver_publication.publication_revision<d.publication_revision)
    AND (d.after_account_id IS NULL OR (r.account_id,r.device_id)>(d.after_account_id,d.after_device_id))
    AND EXISTS(SELECT 1 FROM public.collaboration_space_room_intents i
        JOIN public.collaboration_space_room_bindings b ON b.space_id=i.space_id AND b.conversation_id=i.conversation_id
        WHERE i.space_id=d.space_id AND i.created_at<=d.captured_at AND b.created_at<=d.captured_at
            AND i.first_account_id=least(d.publisher_account_id,r.account_id)
            AND i.second_account_id=greatest(d.publisher_account_id,r.account_id));
ALTER VIEW collaboration_space_readiness_targets OWNER TO hris_collaboration_space_readiness_reader;
GRANT SELECT ON collaboration_space_readiness_targets TO hris_worker_capability;
