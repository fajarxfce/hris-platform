ALTER TABLE inbox_items ADD CONSTRAINT inbox_push_identity UNIQUE(company_id,id,owner_account_id,delivered_at);

CREATE TABLE inbox_push_dispatches (
    company_id uuid NOT NULL,
    inbox_id uuid NOT NULL,
    account_id uuid NOT NULL,
    published_at timestamptz NOT NULL,
    enqueued_at timestamptz NOT NULL DEFAULT now() CHECK(isfinite(enqueued_at)),
    state varchar(16) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','LEASED','COMPLETE','FAILED','SUPERSEDED')),
    available_at timestamptz NOT NULL DEFAULT now() CHECK(isfinite(available_at)),
    cursor_session_id uuid,
    target_session_id uuid,
    processed_count integer NOT NULL DEFAULT 0 CHECK(processed_count BETWEEN 0 AND 500),
    accepted_count integer NOT NULL DEFAULT 0 CHECK(accepted_count BETWEEN 0 AND 500),
    rejected_count integer NOT NULL DEFAULT 0 CHECK(rejected_count BETWEEN 0 AND 500),
    attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 8),
    lease_owner uuid,
    lease_token uuid,
    lease_until timestamptz CHECK(lease_until IS NULL OR isfinite(lease_until)),
    finished_at timestamptz CHECK(finished_at IS NULL OR isfinite(finished_at)),
    failure_code varchar(80) CHECK(failure_code IS NULL OR failure_code ~ '^[a-z][a-z0-9_]{0,79}$'),
    PRIMARY KEY(company_id,inbox_id),
    FOREIGN KEY(company_id,inbox_id,account_id,published_at) REFERENCES inbox_items(company_id,id,owner_account_id,delivered_at),
    CHECK(accepted_count+rejected_count<=processed_count),
    CHECK(target_session_id IS NULL OR cursor_session_id IS NULL OR target_session_id>cursor_session_id),
    CHECK((state='LEASED' AND lease_owner IS NOT NULL AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (state<>'LEASED' AND lease_owner IS NULL AND lease_token IS NULL AND lease_until IS NULL)),
    CHECK((state IN ('PENDING','LEASED') AND finished_at IS NULL)
        OR (state IN ('COMPLETE','FAILED','SUPERSEDED') AND finished_at IS NOT NULL AND target_session_id IS NULL))
);
CREATE INDEX inbox_push_due ON inbox_push_dispatches(available_at,company_id,inbox_id) WHERE state IN ('PENDING','LEASED');
CREATE INDEX inbox_push_age ON inbox_push_dispatches(enqueued_at,company_id,inbox_id) WHERE state IN ('PENDING','LEASED');
CREATE INDEX inbox_push_leased ON inbox_push_dispatches(lease_until) WHERE state='LEASED';
CREATE INDEX inbox_push_exhausted ON inbox_push_dispatches(attempts,enqueued_at,company_id,inbox_id) WHERE state IN ('PENDING','LEASED');
CREATE INDEX inbox_push_retention ON inbox_push_dispatches(finished_at,company_id,inbox_id) WHERE state IN ('COMPLETE','FAILED','SUPERSEDED');
ALTER TABLE inbox_push_dispatches ENABLE ROW LEVEL SECURITY;
ALTER TABLE inbox_push_dispatches FORCE ROW LEVEL SECURITY;
CREATE POLICY inbox_push_owner ON inbox_push_dispatches FOR SELECT
    USING(company_id=current_company_id() AND account_id=current_actor_id());
CREATE POLICY inbox_push_insert ON inbox_push_dispatches FOR INSERT WITH CHECK(company_id=current_company_id());
CREATE POLICY inbox_push_worker ON inbox_push_dispatches TO hris_worker_capability USING(true) WITH CHECK(true);

-- Capture delivery identities in the same transaction as the bounded inbox statement.
-- No recipient selection or provider I/O happens in the trigger.
CREATE FUNCTION capture_inbox_push_dispatch() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    INSERT INTO inbox_push_dispatches(company_id,inbox_id,account_id,published_at)
        SELECT company_id,id,owner_account_id,delivered_at FROM inbox_push_rows;
    RETURN NULL;
END $$;
CREATE TRIGGER record_inbox_push_dispatch AFTER INSERT ON inbox_items REFERENCING NEW TABLE AS inbox_push_rows
    FOR EACH STATEMENT EXECUTE FUNCTION capture_inbox_push_dispatch();

CREATE FUNCTION protect_inbox_push_dispatch() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.state<>'PENDING' OR NEW.attempts<>0 OR NEW.processed_count<>0 OR NEW.accepted_count<>0
            OR NEW.rejected_count<>0 OR NEW.cursor_session_id IS NOT NULL OR NEW.target_session_id IS NOT NULL THEN
            RAISE EXCEPTION 'Initial push state required' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF (NEW.company_id,NEW.inbox_id,NEW.account_id,NEW.published_at,NEW.enqueued_at)
        IS DISTINCT FROM (OLD.company_id,OLD.inbox_id,OLD.account_id,OLD.published_at,OLD.enqueued_at)
        OR (OLD.state IN ('COMPLETE','FAILED','SUPERSEDED') AND NEW IS DISTINCT FROM OLD) THEN
        RAISE EXCEPTION 'Push identity and terminal state are immutable' USING ERRCODE='23514';
    END IF;
    IF NEW.cursor_session_id IS DISTINCT FROM OLD.cursor_session_id THEN
        IF NEW.cursor_session_id IS NULL OR (OLD.cursor_session_id IS NOT NULL AND NEW.cursor_session_id<=OLD.cursor_session_id)
            OR NEW.cursor_session_id IS DISTINCT FROM OLD.target_session_id OR NEW.target_session_id IS NOT NULL
            OR NEW.processed_count<>OLD.processed_count+1 OR NEW.attempts<>0
            OR NEW.accepted_count-OLD.accepted_count NOT BETWEEN 0 AND 1 OR NEW.rejected_count-OLD.rejected_count NOT BETWEEN 0 AND 1
            OR (NEW.accepted_count-OLD.accepted_count)+(NEW.rejected_count-OLD.rejected_count)>1 THEN
            RAISE EXCEPTION 'Push progress must complete its pinned target' USING ERRCODE='23514';
        END IF;
    ELSIF (NEW.processed_count,NEW.accepted_count,NEW.rejected_count) IS DISTINCT FROM (OLD.processed_count,OLD.accepted_count,OLD.rejected_count) THEN
        RAISE EXCEPTION 'Push counters require cursor progress' USING ERRCODE='23514';
    END IF;
    IF NEW.target_session_id IS DISTINCT FROM OLD.target_session_id AND NEW.target_session_id IS NOT NULL THEN
        IF OLD.target_session_id IS NOT NULL OR NEW.state<>'LEASED' OR NOT EXISTS(
            SELECT 1 FROM native_push_registrations r WHERE r.session_id=NEW.target_session_id
                AND r.account_id=NEW.account_id AND r.registered_at<=NEW.published_at) THEN
            RAISE EXCEPTION 'Push target must belong to the original recipient' USING ERRCODE='23514';
        END IF;
    END IF;
    IF OLD.target_session_id IS NOT NULL AND NEW.target_session_id IS NULL AND NEW.cursor_session_id IS NOT DISTINCT FROM OLD.cursor_session_id
        AND NEW.state NOT IN ('COMPLETE','FAILED','SUPERSEDED') THEN
        RAISE EXCEPTION 'Pinned push target cannot be forgotten' USING ERRCODE='23514';
    END IF;
    IF NEW.lease_token IS DISTINCT FROM OLD.lease_token AND NEW.lease_token IS NOT NULL THEN
        IF NEW.state<>'LEASED' OR NEW.attempts<>OLD.attempts+1 OR (OLD.state='LEASED' AND OLD.lease_until>clock_timestamp()) THEN
            RAISE EXCEPTION 'A push claim requires a due unowned step' USING ERRCODE='23514';
        END IF;
    ELSIF NEW.cursor_session_id IS NOT DISTINCT FROM OLD.cursor_session_id AND NEW.attempts<>OLD.attempts THEN
        RAISE EXCEPTION 'Push attempts change only on claim or progress' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_inbox_push_dispatch BEFORE INSERT OR UPDATE ON inbox_push_dispatches
    FOR EACH ROW EXECUTE FUNCTION protect_inbox_push_dispatch();

CREATE FUNCTION claim_inbox_push(p_owner uuid,p_limit integer,p_lease_seconds integer,p_max_attempts integer)
RETURNS SETOF inbox_push_dispatches LANGUAGE plpgsql SECURITY INVOKER AS $$
DECLARE capacity integer; claim_time timestamptz := clock_timestamp();
BEGIN
    IF p_limit NOT BETWEEN 1 AND 8 OR p_lease_seconds NOT BETWEEN 60 AND 300 OR p_max_attempts NOT BETWEEN 1 AND 8 THEN
        RAISE EXCEPTION 'Invalid push claim bounds' USING ERRCODE='22023';
    END IF;
    IF NOT pg_try_advisory_xact_lock(hashtextextended('hris:inbox-push-claims',0)) THEN RETURN; END IF;
    SELECT greatest(0,16-count(*)) INTO capacity FROM (
        SELECT 1 FROM inbox_push_dispatches WHERE state='LEASED' AND lease_until>claim_time LIMIT 16
    ) active;
    IF capacity=0 THEN RETURN; END IF;
    RETURN QUERY WITH candidates AS (
        SELECT d.company_id,d.inbox_id FROM inbox_push_dispatches d
        WHERE d.state IN ('PENDING','LEASED') AND d.available_at<=claim_time
            AND (d.state='PENDING' OR d.lease_until<=claim_time) AND d.attempts<p_max_attempts
        ORDER BY d.available_at,d.company_id,d.inbox_id FOR UPDATE SKIP LOCKED LIMIT least(p_limit,capacity)
    ) UPDATE inbox_push_dispatches d SET state='LEASED',attempts=d.attempts+1,lease_owner=p_owner,
        lease_token=gen_random_uuid(),lease_until=claim_time+make_interval(secs=>p_lease_seconds)
        FROM candidates c WHERE d.company_id=c.company_id AND d.inbox_id=c.inbox_id RETURNING d.*;
END $$;
REVOKE ALL ON FUNCTION claim_inbox_push(uuid,integer,integer,integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION claim_inbox_push(uuid,integer,integer,integer) TO hris_worker_capability;
