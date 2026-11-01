-- One bounded wake head per retained device. Matrix remains the durable message log.
ALTER TABLE collaboration_push_bindings ADD CONSTRAINT collaboration_push_binding_identity
    UNIQUE(company_id,device_id,account_id,native_session_id);

CREATE TABLE collaboration_push_hints (
    company_id uuid NOT NULL,
    device_id uuid NOT NULL,
    account_id uuid NOT NULL,
    native_session_id uuid NOT NULL,
    revision bigint NOT NULL CHECK(revision BETWEEN 1 AND 999999999999),
    delivered_revision bigint NOT NULL DEFAULT 0 CHECK(delivered_revision BETWEEN 0 AND revision),
    created_at timestamptz NOT NULL CHECK(isfinite(created_at)),
    last_hint_at timestamptz NOT NULL CHECK(isfinite(last_hint_at) AND last_hint_at>=created_at),
    expires_at timestamptz NOT NULL CHECK(isfinite(expires_at) AND expires_at>last_hint_at
        AND expires_at<=last_hint_at+interval '1 day'),
    visible_revision bigint NOT NULL DEFAULT 0 CHECK(visible_revision BETWEEN 0 AND revision),
    event_room_id varchar(255) CHECK(event_room_id ~ '^![!-~]{1,254}$'),
    event_id varchar(255) CHECK(event_id ~ '^[$][!-~]{1,254}$'),
    visible_at timestamptz CHECK(visible_at IS NULL OR (isfinite(visible_at)
        AND visible_at>=created_at AND visible_at<=last_hint_at)),
    recent_event_hashes varchar(64)[] NOT NULL DEFAULT '{}',
    available_at timestamptz NOT NULL CHECK(isfinite(available_at)),
    attempt_revision bigint NOT NULL DEFAULT 0 CHECK(attempt_revision BETWEEN 0 AND revision),
    attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 8),
    lease_owner uuid,
    lease_token uuid,
    lease_until timestamptz CHECK(lease_until IS NULL OR isfinite(lease_until)),
    lease_revision bigint CHECK(lease_revision BETWEEN 1 AND revision),
    PRIMARY KEY(company_id,device_id),
    FOREIGN KEY(company_id,device_id,account_id,native_session_id)
        REFERENCES collaboration_push_bindings(company_id,device_id,account_id,native_session_id),
    CHECK((visible_revision=0 AND event_room_id IS NULL AND event_id IS NULL AND visible_at IS NULL)
        OR (visible_revision>0 AND event_room_id IS NOT NULL AND event_id IS NOT NULL AND visible_at IS NOT NULL)),
    CHECK(cardinality(recent_event_hashes) BETWEEN 0 AND 64
        AND coalesce(array_ndims(recent_event_hashes),1)=1
        AND array_position(recent_event_hashes,NULL) IS NULL
        AND length(array_to_string(recent_event_hashes,''))=64*cardinality(recent_event_hashes)
        AND array_to_string(recent_event_hashes,'') ~ '^[a-f0-9]*$'),
    CHECK((lease_token IS NULL AND lease_owner IS NULL AND lease_until IS NULL AND lease_revision IS NULL)
        OR (lease_token IS NOT NULL AND lease_owner IS NOT NULL AND lease_until IS NOT NULL
            AND lease_revision IS NOT NULL AND lease_revision>delivered_revision
            AND attempt_revision=lease_revision AND attempts>0))
);
CREATE INDEX collaboration_push_hint_due ON collaboration_push_hints(expires_at,available_at,company_id,device_id)
    WHERE delivered_revision<revision;
CREATE INDEX collaboration_push_hint_leased ON collaboration_push_hints(lease_until)
    WHERE lease_token IS NOT NULL;
ALTER TABLE collaboration_push_hints ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_push_hints FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_push_hint_owner ON collaboration_push_hints
    USING(company_id=current_company_id() AND account_id=current_actor_id())
    WITH CHECK(company_id=current_company_id() AND account_id=current_actor_id());
CREATE POLICY collaboration_push_hint_worker ON collaboration_push_hints TO hris_worker_capability
    USING(true) WITH CHECK(true);

CREATE FUNCTION protect_collaboration_push_hint() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Push recovery identity is retained' USING ERRCODE='23514';
    ELSIF TG_OP='INSERT' THEN
        IF NEW.revision<>1 OR NEW.delivered_revision<>0 OR NEW.attempt_revision<>0 OR NEW.attempts<>0
            OR NEW.lease_token IS NOT NULL OR NEW.last_hint_at<>NEW.created_at
            OR NEW.visible_revision NOT IN (0,1) THEN
            RAISE EXCEPTION 'Invalid initial push hint' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF (NEW.company_id,NEW.device_id,NEW.account_id,NEW.native_session_id,NEW.created_at)
        IS DISTINCT FROM (OLD.company_id,OLD.device_id,OLD.account_id,OLD.native_session_id,OLD.created_at) THEN
        RAISE EXCEPTION 'Push recovery identity is immutable' USING ERRCODE='23514';
    END IF;
    IF NEW.revision<>OLD.revision THEN
        IF NEW.revision<>OLD.revision+1 OR NEW.delivered_revision<>OLD.delivered_revision
            OR NEW.last_hint_at<OLD.last_hint_at
            OR (NEW.attempt_revision,NEW.attempts,NEW.lease_owner,NEW.lease_token,NEW.lease_until,NEW.lease_revision)
                IS DISTINCT FROM (OLD.attempt_revision,OLD.attempts,OLD.lease_owner,OLD.lease_token,OLD.lease_until,OLD.lease_revision)
            OR (NEW.visible_revision<>OLD.visible_revision AND NEW.visible_revision<>NEW.revision)
            OR (NEW.visible_revision=OLD.visible_revision AND
                (NEW.event_room_id,NEW.event_id,NEW.visible_at,NEW.recent_event_hashes)
                IS DISTINCT FROM (OLD.event_room_id,OLD.event_id,OLD.visible_at,OLD.recent_event_hashes)) THEN
            RAISE EXCEPTION 'Invalid push hint advancement' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF NOT pg_has_role(current_user,'hris_worker_capability','member')
        OR (NEW.last_hint_at,NEW.expires_at,NEW.visible_revision,NEW.event_room_id,NEW.event_id,NEW.visible_at,NEW.recent_event_hashes)
            IS DISTINCT FROM (OLD.last_hint_at,OLD.expires_at,OLD.visible_revision,OLD.event_room_id,OLD.event_id,OLD.visible_at,OLD.recent_event_hashes) THEN
        RAISE EXCEPTION 'Only a worker may advance push delivery' USING ERRCODE='23514';
    END IF;
    IF NEW.lease_token IS NOT NULL AND NEW.lease_token IS DISTINCT FROM OLD.lease_token THEN
        IF (OLD.lease_token IS NOT NULL AND OLD.lease_until>clock_timestamp())
            OR NEW.lease_revision<>OLD.revision OR NEW.attempt_revision<>OLD.revision
            OR NEW.attempts<>(CASE WHEN OLD.attempt_revision=OLD.revision THEN OLD.attempts+1 ELSE 1 END)
            OR NEW.delivered_revision<>OLD.delivered_revision OR NEW.available_at<>OLD.available_at
            OR NEW.lease_until<=clock_timestamp() OR NEW.lease_until>clock_timestamp()+interval '60 seconds' THEN
            RAISE EXCEPTION 'Invalid push lease claim' USING ERRCODE='23514';
        END IF;
    ELSIF NEW.lease_token IS NULL AND OLD.lease_token IS NOT NULL THEN
        IF OLD.lease_until<=clock_timestamp() OR NEW.available_at>clock_timestamp()+interval '300 seconds'
            OR NEW.delivered_revision NOT IN (OLD.delivered_revision,OLD.lease_revision)
            OR NEW.attempt_revision<>OLD.attempt_revision OR NEW.attempts<>OLD.attempts THEN
            RAISE EXCEPTION 'Invalid push lease completion' USING ERRCODE='23514';
        END IF;
    ELSE
        RAISE EXCEPTION 'A push delivery update requires a lease transition' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_push_hint BEFORE INSERT OR UPDATE OR DELETE ON collaboration_push_hints
    FOR EACH ROW EXECUTE FUNCTION protect_collaboration_push_hint();

-- The caller already owns current background admission. This only atomically stores
-- bounded wake identity and preserves in-flight delivery metadata.
CREATE FUNCTION append_collaboration_push_hint(p_company uuid,p_device uuid,p_account uuid,p_parent uuid,
    p_room varchar,p_event varchar,p_event_hash varchar,p_lifetime integer)
RETURNS boolean LANGUAGE plpgsql SECURITY INVOKER AS $$
DECLARE at_time timestamptz := clock_timestamp(); inserted boolean;
    head collaboration_push_hints%ROWTYPE; hashes varchar(64)[];
BEGIN
    IF p_lifetime IS NULL OR p_lifetime NOT BETWEEN 1 AND 86400
        OR ((p_room IS NULL)::integer+(p_event IS NULL)::integer+(p_event_hash IS NULL)::integer) NOT IN (0,3)
        OR (p_event_hash IS NOT NULL AND p_event_hash !~ '^[a-f0-9]{64}$') THEN
        RAISE EXCEPTION 'Invalid push hint bounds' USING ERRCODE='22023';
    END IF;
    hashes := CASE WHEN p_event_hash IS NULL THEN ARRAY[]::varchar(64)[] ELSE ARRAY[p_event_hash]::varchar(64)[] END;
    INSERT INTO collaboration_push_hints(company_id,device_id,account_id,native_session_id,revision,
        created_at,last_hint_at,expires_at,visible_revision,event_room_id,event_id,visible_at,recent_event_hashes,available_at)
        VALUES(p_company,p_device,p_account,p_parent,1,at_time,at_time,at_time+make_interval(secs=>p_lifetime),
            CASE WHEN p_event IS NULL THEN 0 ELSE 1 END,p_room,p_event,
            CASE WHEN p_event IS NULL THEN NULL ELSE at_time END,hashes,at_time)
        ON CONFLICT(company_id,device_id) DO NOTHING RETURNING true INTO inserted;
    IF inserted THEN RETURN true; END IF;
    SELECT * INTO head FROM collaboration_push_hints
        WHERE company_id=p_company AND device_id=p_device AND account_id=p_account AND native_session_id=p_parent FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Push hint owner unavailable' USING ERRCODE='23514'; END IF;
    IF p_event_hash=ANY(head.recent_event_hashes) THEN RETURN false; END IF;
    -- A not-yet-claimed quiet hint already guarantees a later wake. A hint arriving
    -- during an active/expired lease advances the revision so an old ACK cannot eat it.
    IF p_event IS NULL AND head.delivered_revision<head.revision AND head.lease_token IS NULL
        AND head.expires_at>at_time AND (head.attempt_revision<head.revision OR head.attempts=0) THEN RETURN false; END IF;
    IF head.revision>=999999999999 THEN RAISE EXCEPTION 'Push hint revision exhausted' USING ERRCODE='22003'; END IF;
    at_time := greatest(at_time,head.last_hint_at);
    hashes := head.recent_event_hashes;
    IF p_event_hash IS NOT NULL THEN
        hashes := array_append(hashes,p_event_hash);
        IF cardinality(hashes)>64 THEN hashes := hashes[2:65]; END IF;
    END IF;
    UPDATE collaboration_push_hints SET revision=head.revision+1,last_hint_at=at_time,
        expires_at=at_time+make_interval(secs=>p_lifetime),available_at=least(head.available_at,at_time),
        visible_revision=CASE WHEN p_event IS NULL THEN head.visible_revision ELSE head.revision+1 END,
        event_room_id=CASE WHEN p_event IS NULL THEN head.event_room_id ELSE p_room END,
        event_id=CASE WHEN p_event IS NULL THEN head.event_id ELSE p_event END,
        visible_at=CASE WHEN p_event IS NULL THEN head.visible_at ELSE at_time END,recent_event_hashes=hashes
        WHERE company_id=p_company AND device_id=p_device;
    RETURN true;
END $$;

CREATE FUNCTION claim_collaboration_push_hints(p_owner uuid,p_limit integer,p_lease_seconds integer,p_max_attempts integer)
RETURNS SETOF collaboration_push_hints LANGUAGE plpgsql SECURITY INVOKER AS $$
DECLARE at_time timestamptz := clock_timestamp(); capacity integer;
BEGIN
    IF p_owner IS NULL OR p_limit IS NULL OR p_lease_seconds IS NULL OR p_max_attempts IS NULL
        OR p_limit NOT BETWEEN 1 AND 8 OR p_lease_seconds NOT BETWEEN 1 AND 60
        OR p_max_attempts NOT BETWEEN 1 AND 8 THEN
        RAISE EXCEPTION 'Invalid push claim bounds' USING ERRCODE='22023';
    END IF;
    IF NOT pg_try_advisory_xact_lock(hashtextextended('hris:collaboration-push-claims',0)) THEN RETURN; END IF;
    SELECT greatest(0,16-count(*)) INTO capacity FROM (
        SELECT 1 FROM collaboration_push_hints WHERE lease_until>at_time LIMIT 16
    ) active;
    IF capacity=0 THEN RETURN; END IF;
    RETURN QUERY WITH candidates AS (
        SELECT company_id,device_id FROM collaboration_push_hints
        WHERE delivered_revision<revision AND expires_at>at_time AND available_at<=at_time
            AND (lease_token IS NULL OR lease_until<=at_time)
            AND (attempt_revision<revision OR attempts<p_max_attempts)
        ORDER BY expires_at,available_at,company_id,device_id FOR UPDATE SKIP LOCKED LIMIT least(p_limit,capacity)
    ) UPDATE collaboration_push_hints h SET lease_owner=p_owner,lease_token=gen_random_uuid(),
        lease_until=least(at_time+make_interval(secs=>p_lease_seconds),h.expires_at),lease_revision=h.revision,
        attempts=CASE WHEN h.attempt_revision=h.revision THEN h.attempts+1 ELSE 1 END,attempt_revision=h.revision
        FROM candidates c WHERE h.company_id=c.company_id AND h.device_id=c.device_id RETURNING h.*;
END $$;
REVOKE ALL ON FUNCTION claim_collaboration_push_hints(uuid,integer,integer,integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION claim_collaboration_push_hints(uuid,integer,integer,integer) TO hris_worker_capability;
