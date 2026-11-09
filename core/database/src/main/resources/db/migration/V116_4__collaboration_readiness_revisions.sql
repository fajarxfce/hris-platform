-- A per-company row lock orders first-key publication commits, not wall-clock timestamps.
-- No historical revision assignment: legacy V1 journals remain retained but unclaimable by V2.
CREATE TABLE collaboration_readiness_revisions (
    company_id uuid PRIMARY KEY REFERENCES companies(id),
    revision bigint NOT NULL CHECK(revision>0)
);
ALTER TABLE collaboration_readiness_revisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_readiness_revisions FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_readiness_revision_capture ON collaboration_readiness_revisions
    TO hris_collaboration_readiness_capture
    USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
GRANT SELECT(company_id,revision),INSERT(company_id,revision),UPDATE(revision)
    ON collaboration_readiness_revisions TO hris_collaboration_readiness_capture;
CREATE FUNCTION protect_collaboration_readiness_revision() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog,public AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Readiness revision is retained' USING ERRCODE='23514';
    ELSIF TG_OP='INSERT' THEN
        IF NEW.company_id IS DISTINCT FROM public.current_company_id() OR NEW.revision<>1 THEN
            RAISE EXCEPTION 'Invalid initial readiness revision' USING ERRCODE='23514';
        END IF;
    ELSIF NEW.company_id IS DISTINCT FROM OLD.company_id OR NEW.revision<>OLD.revision+1 THEN
        RAISE EXCEPTION 'Readiness revision must advance once' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_readiness_revision BEFORE INSERT OR UPDATE OR DELETE
    ON collaboration_readiness_revisions FOR EACH ROW EXECUTE FUNCTION protect_collaboration_readiness_revision();

ALTER TABLE collaboration_readiness_controls ADD COLUMN publication_revision bigint CHECK(publication_revision>0);
ALTER TABLE collaboration_readiness_controls ADD CONSTRAINT collaboration_readiness_control_revision_unique
    UNIQUE(company_id,publication_revision);
CREATE INDEX collaboration_readiness_control_revision_due
    ON collaboration_readiness_controls(expires_at,available_at,event_id)
    WHERE publication_revision IS NOT NULL AND completed_at IS NULL AND attempts<8;
DROP INDEX collaboration_readiness_control_due;
GRANT INSERT(publication_revision) ON collaboration_readiness_controls TO hris_collaboration_readiness_capture;
CREATE FUNCTION protect_collaboration_readiness_control_revision() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog,public AS $$
BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.publication_revision IS NULL OR NOT EXISTS(
            SELECT 1 FROM public.collaboration_readiness_revisions r
                WHERE r.company_id=NEW.company_id AND r.revision=NEW.publication_revision) THEN
            RAISE EXCEPTION 'Readiness revision is not captured' USING ERRCODE='23514';
        END IF;
    ELSIF NEW.publication_revision IS DISTINCT FROM OLD.publication_revision THEN
        RAISE EXCEPTION 'Readiness revision cannot change' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_readiness_control_revision BEFORE INSERT OR UPDATE
    ON collaboration_readiness_controls FOR EACH ROW EXECUTE FUNCTION protect_collaboration_readiness_control_revision();

CREATE OR REPLACE FUNCTION capture_collaboration_readiness_control() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE published timestamptz; captured bigint;
BEGIN
    IF NEW.event_type<>'collaboration.device_keys_published' THEN RETURN NEW; END IF;
    SELECT k.published_at INTO published FROM public.collaboration_device_key_publications k
        WHERE k.company_id=NEW.company_id AND k.account_id=NEW.actor_id
            AND k.device_id=NEW.resource_id AND k.version=1;
    IF NOT FOUND OR published IS NULL THEN
        RAISE EXCEPTION 'Readiness publication is not confirmed' USING ERRCODE='23514';
    END IF;
    IF EXISTS(SELECT 1 FROM public.collaboration_readiness_controls w
        WHERE w.company_id=NEW.company_id AND w.publisher_device_id=NEW.resource_id) THEN RETURN NEW; END IF;
    -- This is the final shared guard after the publisher's own key guard and audit insert.
    -- Its row lock lasts until the entire publication transaction commits or rolls back.
    INSERT INTO public.collaboration_readiness_revisions(company_id,revision) VALUES(NEW.company_id,1)
        ON CONFLICT(company_id) DO UPDATE SET revision=collaboration_readiness_revisions.revision+1
        RETURNING revision INTO captured;
    INSERT INTO public.collaboration_readiness_controls(event_id,company_id,publisher_account_id,
        publisher_device_id,publication_cutoff,expires_at,available_at,publication_revision)
        VALUES(NEW.id,NEW.company_id,NEW.actor_id,NEW.resource_id,published,
            published+interval '1 day',published,captured)
        ON CONFLICT(company_id,publisher_device_id) DO NOTHING;
    RETURN NEW;
END $$;
ALTER FUNCTION capture_collaboration_readiness_control() OWNER TO hris_collaboration_readiness_capture;
REVOKE ALL ON FUNCTION capture_collaboration_readiness_control() FROM PUBLIC;

CREATE OR REPLACE FUNCTION claim_collaboration_readiness_controls(p_owner uuid,p_limit integer)
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
        WHERE publication_revision IS NOT NULL AND completed_at IS NULL AND expires_at>at_time AND available_at<=at_time AND attempts<8
            AND (lease_token IS NULL OR lease_until<=at_time)
        ORDER BY expires_at,available_at,event_id FOR UPDATE SKIP LOCKED LIMIT least(p_limit,capacity)
    ) UPDATE public.collaboration_readiness_controls w SET lease_owner=p_owner,lease_token=gen_random_uuid(),
        lease_until=least(w.expires_at,at_time+interval '60 seconds'),attempts=w.attempts+1
        FROM candidates c WHERE w.event_id=c.event_id RETURNING w.*;
END $$;
REVOKE ALL ON FUNCTION claim_collaboration_readiness_controls(uuid,integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION claim_collaboration_readiness_controls(uuid,integer) TO hris_worker_capability;

CREATE OR REPLACE VIEW collaboration_readiness_control_targets WITH(security_barrier=true) AS
SELECT w.event_id,w.company_id,w.publisher_account_id,w.publisher_device_id,
    w.publication_cutoff,w.after_account_id,w.after_device_id,w.lease_owner,w.lease_token,w.lease_until,
    k.account_id AS target_account_id,k.device_id AS target_device_id,s.native_session_id,w.publication_revision
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
