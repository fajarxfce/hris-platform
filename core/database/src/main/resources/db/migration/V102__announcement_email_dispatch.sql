ALTER TABLE announcement_revisions ADD COLUMN email_notification boolean NOT NULL DEFAULT false;

-- Omitted API selection preserves existing drafts. Queued and published content
-- retains its explicit selection through publication, recovery and archival.
CREATE FUNCTION protect_announcement_email_selection() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE previous announcement_revisions%ROWTYPE;
BEGIN
    IF NEW.version>0 THEN
        SELECT * INTO previous FROM announcement_revisions
            WHERE company_id=NEW.company_id AND id=NEW.id AND version=NEW.version-1;
        IF previous.id IS NOT NULL AND (previous.status<>'DRAFT' OR NEW.status<>'DRAFT')
            AND NEW.email_notification IS DISTINCT FROM previous.email_notification THEN
            RAISE EXCEPTION 'Queued email selection must retain its reviewed revision' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER announcement_email_selection BEFORE INSERT ON announcement_revisions
    FOR EACH ROW EXECUTE FUNCTION protect_announcement_email_selection();

-- No recipient address, message content or credential is persisted in the queue.
CREATE TABLE inbox_email_dispatches (
    company_id uuid NOT NULL, inbox_id uuid NOT NULL, account_id uuid NOT NULL,
    published_at timestamptz NOT NULL,
    enqueued_at timestamptz NOT NULL DEFAULT now() CHECK(isfinite(enqueued_at)),
    state varchar(16) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','LEASED','ACCEPTED','FAILED','SUPERSEDED')),
    available_at timestamptz NOT NULL DEFAULT now() CHECK(isfinite(available_at)),
    attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 8),
    lease_owner uuid, lease_token uuid,
    lease_until timestamptz CHECK(lease_until IS NULL OR isfinite(lease_until)),
    finished_at timestamptz CHECK(finished_at IS NULL OR isfinite(finished_at)),
    failure_code varchar(80) CHECK(failure_code IS NULL OR failure_code ~ '^[a-z][a-z0-9_]{0,79}$'),
    PRIMARY KEY(company_id,inbox_id),
    FOREIGN KEY(company_id,inbox_id,account_id,published_at) REFERENCES inbox_items(company_id,id,owner_account_id,delivered_at),
    CHECK((state='LEASED' AND lease_owner IS NOT NULL AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (state<>'LEASED' AND lease_owner IS NULL AND lease_token IS NULL AND lease_until IS NULL)),
    CHECK((state IN ('PENDING','LEASED') AND finished_at IS NULL)
        OR (state IN ('ACCEPTED','FAILED','SUPERSEDED') AND finished_at IS NOT NULL))
);
CREATE INDEX inbox_email_due ON inbox_email_dispatches(available_at,company_id,inbox_id) WHERE state IN ('PENDING','LEASED');
CREATE INDEX inbox_email_age ON inbox_email_dispatches(enqueued_at,company_id,inbox_id) WHERE state IN ('PENDING','LEASED');
CREATE INDEX inbox_email_leased ON inbox_email_dispatches(lease_until) WHERE state='LEASED';
CREATE INDEX inbox_email_exhausted ON inbox_email_dispatches(attempts,enqueued_at,company_id,inbox_id) WHERE state IN ('PENDING','LEASED');
CREATE INDEX inbox_email_retention ON inbox_email_dispatches(finished_at,company_id,inbox_id) WHERE state IN ('ACCEPTED','FAILED','SUPERSEDED');
ALTER TABLE inbox_email_dispatches ENABLE ROW LEVEL SECURITY;
ALTER TABLE inbox_email_dispatches FORCE ROW LEVEL SECURITY;
CREATE POLICY inbox_email_owner ON inbox_email_dispatches FOR SELECT
    USING(company_id=current_company_id() AND account_id=current_actor_id());
CREATE POLICY inbox_email_insert ON inbox_email_dispatches FOR INSERT WITH CHECK(company_id=current_company_id());
CREATE POLICY inbox_email_worker ON inbox_email_dispatches TO hris_worker_capability USING(true) WITH CHECK(true);

CREATE FUNCTION protect_inbox_email_dispatch() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='INSERT' THEN
        IF pg_trigger_depth()<2 OR NEW.state<>'PENDING' OR NEW.attempts<>0 OR NEW.failure_code IS NOT NULL THEN
            RAISE EXCEPTION 'Email dispatch requires initial inbox capture' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF (NEW.company_id,NEW.inbox_id,NEW.account_id,NEW.published_at,NEW.enqueued_at)
        IS DISTINCT FROM (OLD.company_id,OLD.inbox_id,OLD.account_id,OLD.published_at,OLD.enqueued_at)
        OR (OLD.state IN ('ACCEPTED','FAILED','SUPERSEDED') AND NEW IS DISTINCT FROM OLD) THEN
        RAISE EXCEPTION 'Email identity and terminal state are immutable' USING ERRCODE='23514';
    END IF;
    IF NEW.lease_token IS DISTINCT FROM OLD.lease_token AND NEW.lease_token IS NOT NULL THEN
        IF NEW.state<>'LEASED' OR NEW.attempts<>OLD.attempts+1
            OR (OLD.state='LEASED' AND OLD.lease_until>clock_timestamp()) THEN
            RAISE EXCEPTION 'Email claim requires an unowned step' USING ERRCODE='23514';
        END IF;
    ELSIF NEW.attempts<>OLD.attempts THEN
        RAISE EXCEPTION 'Email attempts change only on claim' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_inbox_email_dispatch BEFORE INSERT OR UPDATE ON inbox_email_dispatches
    FOR EACH ROW EXECUTE FUNCTION protect_inbox_email_dispatch();

-- Capture only the immutable publication's selected channel, in the same bounded
-- statement transaction as Inbox. There is deliberately no historical backfill.
CREATE FUNCTION capture_inbox_email_dispatch() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    INSERT INTO inbox_email_dispatches(company_id,inbox_id,account_id,published_at)
        SELECT i.company_id,i.id,i.owner_account_id,i.delivered_at FROM inbox_email_rows i
        JOIN announcement_publications p ON p.company_id=i.company_id AND p.id=i.publication_id
        JOIN announcement_revisions r ON r.company_id=p.company_id AND r.id=p.announcement_id AND r.version=p.content_version
        WHERE r.email_notification;
    RETURN NULL;
END $$;
CREATE TRIGGER record_inbox_email_dispatch AFTER INSERT ON inbox_items REFERENCING NEW TABLE AS inbox_email_rows
    FOR EACH STATEMENT EXECUTE FUNCTION capture_inbox_email_dispatch();

CREATE FUNCTION claim_inbox_email(p_owner uuid,p_limit integer,p_lease_seconds integer,p_max_attempts integer)
RETURNS SETOF inbox_email_dispatches LANGUAGE plpgsql SECURITY INVOKER AS $$
DECLARE capacity integer; claim_time timestamptz := clock_timestamp();
BEGIN
    IF p_limit NOT BETWEEN 1 AND 4 OR p_lease_seconds NOT BETWEEN 60 AND 300 OR p_max_attempts NOT BETWEEN 1 AND 8 THEN
        RAISE EXCEPTION 'Invalid email claim bounds' USING ERRCODE='22023';
    END IF;
    IF NOT pg_try_advisory_xact_lock(hashtextextended('hris:inbox-email-claims',0)) THEN RETURN; END IF;
    SELECT greatest(0,16-count(*)) INTO capacity FROM (
        SELECT 1 FROM inbox_email_dispatches WHERE state='LEASED' AND lease_until>claim_time LIMIT 16
    ) active;
    IF capacity=0 THEN RETURN; END IF;
    RETURN QUERY WITH candidates AS (
        SELECT d.company_id,d.inbox_id FROM inbox_email_dispatches d
        WHERE d.state IN ('PENDING','LEASED') AND d.available_at<=claim_time
            AND d.enqueued_at>claim_time-interval '24 hours'
            AND (d.state='PENDING' OR d.lease_until<=claim_time) AND d.attempts<p_max_attempts
        ORDER BY d.available_at,d.company_id,d.inbox_id FOR UPDATE SKIP LOCKED LIMIT least(p_limit,capacity)
    ) UPDATE inbox_email_dispatches d SET state='LEASED',attempts=d.attempts+1,lease_owner=p_owner,
        lease_token=gen_random_uuid(),lease_until=claim_time+make_interval(secs=>p_lease_seconds)
        FROM candidates c WHERE d.company_id=c.company_id AND d.inbox_id=c.inbox_id RETURNING d.*;
END $$;
REVOKE ALL ON FUNCTION claim_inbox_email(uuid,integer,integer,integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION claim_inbox_email(uuid,integer,integer,integer) TO hris_worker_capability;

-- Minimal administrative observations; raw recipient and lease data stay private.
CREATE TABLE announcement_email_delivery (
    company_id uuid NOT NULL, announcement_id uuid NOT NULL, inbox_id uuid NOT NULL,
    state varchar(16) NOT NULL CHECK(state IN ('PENDING','LEASED','ACCEPTED','FAILED','SUPERSEDED')),
    PRIMARY KEY(company_id,inbox_id),
    FOREIGN KEY(company_id,inbox_id) REFERENCES inbox_items(company_id,id),
    FOREIGN KEY(company_id,announcement_id) REFERENCES announcement_heads(company_id,id)
);
CREATE INDEX announcement_email_delivery_summary ON announcement_email_delivery(company_id,announcement_id,inbox_id);
ALTER TABLE announcement_email_delivery ENABLE ROW LEVEL SECURITY;
ALTER TABLE announcement_email_delivery FORCE ROW LEVEL SECURITY;
CREATE POLICY announcement_email_delivery_read ON announcement_email_delivery FOR SELECT USING (
    company_id=current_company_id() AND EXISTS (
        SELECT 1 FROM accounts a JOIN company_memberships m ON m.account_id=a.id
        JOIN companies c ON c.id=m.company_id
        JOIN membership_permissions p ON p.company_id=m.company_id AND p.account_id=m.account_id
        WHERE a.id=current_actor_id() AND a.active AND m.active AND c.active
            AND m.company_id=current_company_id() AND p.permission='announcements.manage'
    )
);
CREATE POLICY announcement_email_delivery_capture ON announcement_email_delivery FOR ALL USING (
    current_user=pg_catalog.pg_get_userbyid((SELECT relowner FROM pg_catalog.pg_class WHERE oid='public.announcement_email_delivery'::regclass))
) WITH CHECK (
    current_user=pg_catalog.pg_get_userbyid((SELECT relowner FROM pg_catalog.pg_class WHERE oid='public.announcement_email_delivery'::regclass))
);
CREATE FUNCTION capture_announcement_email_delivery() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE announcement uuid;
BEGIN
    IF TG_TABLE_SCHEMA<>'public' OR TG_TABLE_NAME<>'inbox_email_dispatches' THEN
        RAISE EXCEPTION 'Unexpected email projection source' USING ERRCODE='42501';
    END IF;
    IF TG_OP='DELETE' THEN
        DELETE FROM public.announcement_email_delivery WHERE company_id=OLD.company_id AND inbox_id=OLD.inbox_id;
        RETURN OLD;
    ELSIF TG_OP='INSERT' THEN
        SELECT announcement_id INTO STRICT announcement FROM public.inbox_items WHERE company_id=NEW.company_id AND id=NEW.inbox_id;
        INSERT INTO public.announcement_email_delivery(company_id,announcement_id,inbox_id,state)
            VALUES(NEW.company_id,announcement,NEW.inbox_id,NEW.state);
    ELSIF TG_OP='UPDATE' THEN
        UPDATE public.announcement_email_delivery SET state=NEW.state WHERE company_id=NEW.company_id AND inbox_id=NEW.inbox_id;
        IF NOT FOUND THEN RAISE EXCEPTION 'Email projection is missing' USING ERRCODE='23514'; END IF;
    ELSE
        RAISE EXCEPTION 'Unexpected email projection operation' USING ERRCODE='42501';
    END IF;
    RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION capture_announcement_email_delivery() FROM PUBLIC;
CREATE TRIGGER capture_announcement_email_delivery AFTER INSERT OR UPDATE OR DELETE ON inbox_email_dispatches
    FOR EACH ROW EXECUTE FUNCTION capture_announcement_email_delivery();
