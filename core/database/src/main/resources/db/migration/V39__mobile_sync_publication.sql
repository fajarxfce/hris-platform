-- A minimal client change queue. Business facts remain in their owning aggregates.
CREATE TABLE mobile_sync_heads (
    company_id uuid PRIMARY KEY REFERENCES companies(id),
    epoch uuid NOT NULL DEFAULT gen_random_uuid(),
    head_position bigint NOT NULL DEFAULT 0 CHECK(head_position>=0),
    pruned_through bigint NOT NULL DEFAULT 0 CHECK(pruned_through BETWEEN 0 AND head_position),
    published_at timestamptz,
    CHECK((head_position=0)=(published_at IS NULL))
);
CREATE TABLE mobile_sync_changes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies(id),
    collection varchar(32) NOT NULL CHECK(collection IN ('EXPENSE_CLAIMS','LEAVE_REQUESTS')),
    resource_id uuid NOT NULL,
    employment_id uuid NOT NULL,
    owner_account_id uuid REFERENCES accounts(id),
    resource_version bigint NOT NULL CHECK(resource_version>=0),
    operation varchar(8) NOT NULL CHECK(operation IN ('UPSERT','DELETE')),
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    sequence bigint CHECK(sequence>0),
    published_at timestamptz,
    UNIQUE(company_id,sequence),
    CHECK((sequence IS NULL)=(published_at IS NULL)),
    CHECK(collection='LEAVE_REQUESTS' OR owner_account_id IS NULL)
);
CREATE INDEX mobile_sync_pending ON mobile_sync_changes(recorded_at,id) WHERE sequence IS NULL;
CREATE INDEX mobile_sync_scope ON mobile_sync_changes(company_id,collection,employment_id,sequence) WHERE sequence IS NOT NULL;
CREATE INDEX mobile_sync_owner ON mobile_sync_changes(company_id,owner_account_id,sequence) WHERE sequence IS NOT NULL AND owner_account_id IS NOT NULL;
CREATE INDEX mobile_sync_pending_audience ON mobile_sync_changes(company_id,collection,employment_id) WHERE sequence IS NULL;
CREATE INDEX mobile_sync_pending_owner ON mobile_sync_changes(company_id,owner_account_id) WHERE sequence IS NULL AND owner_account_id IS NOT NULL;
CREATE INDEX mobile_sync_retention ON mobile_sync_changes(published_at,company_id,sequence) WHERE sequence IS NOT NULL;
ALTER TABLE mobile_sync_heads ENABLE ROW LEVEL SECURITY; ALTER TABLE mobile_sync_heads FORCE ROW LEVEL SECURITY;
ALTER TABLE mobile_sync_changes ENABLE ROW LEVEL SECURITY; ALTER TABLE mobile_sync_changes FORCE ROW LEVEL SECURITY;
CREATE POLICY sync_head_company ON mobile_sync_heads USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
CREATE POLICY sync_change_company ON mobile_sync_changes USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
CREATE POLICY sync_head_worker ON mobile_sync_heads TO hris_worker_capability USING(true) WITH CHECK(true);
CREATE POLICY sync_change_worker ON mobile_sync_changes TO hris_worker_capability USING(true) WITH CHECK(true);
GRANT SELECT,INSERT,UPDATE ON mobile_sync_heads TO hris_worker_capability;
GRANT SELECT,UPDATE,DELETE ON mobile_sync_changes TO hris_worker_capability;
INSERT INTO mobile_sync_heads(company_id) SELECT id FROM companies;

CREATE FUNCTION initialize_mobile_sync_head() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    INSERT INTO mobile_sync_heads(company_id) VALUES(NEW.id);
    RETURN NULL;
END $$;
CREATE TRIGGER company_sync_head AFTER INSERT ON companies FOR EACH ROW EXECUTE FUNCTION initialize_mobile_sync_head();

-- These triggers copy storage identities and versions; scope and client policy belong to use cases.
CREATE FUNCTION record_expense_sync_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE item expense_claims%ROWTYPE;
BEGIN
    IF TG_OP='DELETE' THEN item:=OLD; ELSE item:=NEW; END IF;
    INSERT INTO mobile_sync_changes(company_id,collection,resource_id,employment_id,resource_version,operation)
        VALUES(item.company_id,'EXPENSE_CLAIMS',item.id,item.employment_id,item.version,CASE WHEN TG_OP='DELETE' THEN 'DELETE' ELSE 'UPSERT' END);
    RETURN NULL;
END $$;
CREATE TRIGGER expense_sync_change AFTER INSERT OR UPDATE OR DELETE ON expense_claims FOR EACH ROW EXECUTE FUNCTION record_expense_sync_change();
CREATE FUNCTION record_leave_sync_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE item leave_requests%ROWTYPE;
BEGIN
    IF TG_OP='DELETE' THEN item:=OLD; ELSE item:=NEW; END IF;
    INSERT INTO mobile_sync_changes(company_id,collection,resource_id,employment_id,owner_account_id,resource_version,operation)
        VALUES(item.company_id,'LEAVE_REQUESTS',item.id,item.employment_id,item.owner_account_id,item.version,CASE WHEN TG_OP='DELETE' THEN 'DELETE' ELSE 'UPSERT' END);
    RETURN NULL;
END $$;
CREATE TRIGGER leave_sync_change AFTER INSERT OR UPDATE OR DELETE ON leave_requests FOR EACH ROW EXECUTE FUNCTION record_leave_sync_change();

CREATE FUNCTION protect_mobile_sync_head() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.head_position<>0 OR NEW.pruned_through<>0 OR NEW.published_at IS NOT NULL THEN
            RAISE EXCEPTION 'A synchronization partition must start empty' USING ERRCODE='23514'; END IF;
        RETURN NEW;
    END IF;
    IF TG_OP='DELETE' OR NOT pg_has_role(current_user,'hris_worker_capability','MEMBER') THEN
        RAISE EXCEPTION 'Synchronization positions are worker owned' USING ERRCODE='42501'; END IF;
    IF (NEW.company_id,NEW.epoch) IS DISTINCT FROM (OLD.company_id,OLD.epoch) OR
        NOT ((NEW.head_position=OLD.head_position+1 AND NEW.pruned_through=OLD.pruned_through AND NEW.published_at IS NOT NULL AND (OLD.published_at IS NULL OR NEW.published_at>=OLD.published_at)) OR
             (NEW.head_position=OLD.head_position AND NEW.pruned_through>OLD.pruned_through AND NEW.published_at IS NOT DISTINCT FROM OLD.published_at)) THEN
        RAISE EXCEPTION 'Synchronization positions must advance without changing their partition' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER mobile_sync_head_immutable BEFORE INSERT OR UPDATE OR DELETE ON mobile_sync_heads FOR EACH ROW EXECUTE FUNCTION protect_mobile_sync_head();
CREATE FUNCTION protect_mobile_sync_change() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.sequence IS NOT NULL OR NEW.published_at IS NOT NULL THEN
            RAISE EXCEPTION 'Changes must enter the pending queue before publication' USING ERRCODE='23514'; END IF;
        RETURN NEW;
    END IF;
    IF NOT pg_has_role(current_user,'hris_worker_capability','MEMBER') THEN
        RAISE EXCEPTION 'Synchronization changes are immutable' USING ERRCODE='42501'; END IF;
    IF TG_OP='DELETE' THEN
        IF OLD.published_at IS NULL OR OLD.published_at>clock_timestamp()-interval '8 days' THEN
            RAISE EXCEPTION 'Pending and recent synchronization changes must be retained' USING ERRCODE='42501'; END IF;
        RETURN OLD;
    END IF;
    IF (to_jsonb(NEW)-'sequence'-'published_at') IS DISTINCT FROM (to_jsonb(OLD)-'sequence'-'published_at') OR
        OLD.sequence IS NOT NULL OR NEW.sequence IS NULL OR NEW.published_at IS NULL OR
        NOT EXISTS(SELECT 1 FROM mobile_sync_heads h WHERE h.company_id=NEW.company_id AND h.head_position=NEW.sequence AND h.published_at=NEW.published_at) THEN
        RAISE EXCEPTION 'A change can only receive its committed publication position once' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER mobile_sync_change_immutable BEFORE INSERT OR UPDATE OR DELETE ON mobile_sync_changes FOR EACH ROW EXECUTE FUNCTION protect_mobile_sync_change();

-- Invoker rights, queue-only RLS, finite work, and a transaction-owned publication guard.
-- A reader observes the head and assigned changes together after commit.
CREATE FUNCTION publish_mobile_sync_changes(p_limit integer) RETURNS integer LANGUAGE plpgsql SET search_path=pg_catalog,public AS $$
DECLARE item public.mobile_sync_changes%ROWTYPE; position bigint; published timestamptz; amount integer:=0;
BEGIN
    IF p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 200 THEN RAISE EXCEPTION 'Invalid publication batch' USING ERRCODE='22023'; END IF;
    IF NOT pg_try_advisory_xact_lock(hashtextextended('hris:mobile-sync-maintenance',0)) THEN RETURN 0; END IF;
    FOR item IN SELECT * FROM public.mobile_sync_changes WHERE sequence IS NULL ORDER BY recorded_at,id FOR UPDATE SKIP LOCKED LIMIT p_limit LOOP
        UPDATE public.mobile_sync_heads SET head_position=head_position+1,published_at=greatest(published_at,clock_timestamp())
            WHERE company_id=item.company_id RETURNING head_position,published_at INTO position,published;
        IF position IS NULL THEN RAISE EXCEPTION 'Missing synchronization partition' USING ERRCODE='23514'; END IF;
        UPDATE public.mobile_sync_changes SET sequence=position,published_at=published WHERE id=item.id;
        amount:=amount+1;
    END LOOP;
    RETURN amount;
END $$;
REVOKE ALL ON FUNCTION publish_mobile_sync_changes(integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION publish_mobile_sync_changes(integer) TO hris_worker_capability;
CREATE FUNCTION prune_mobile_sync_changes(p_limit integer) RETURNS integer LANGUAGE plpgsql SET search_path=pg_catalog,public AS $$
DECLARE item public.mobile_sync_changes%ROWTYPE; amount integer:=0;
BEGIN
    IF p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 500 THEN RAISE EXCEPTION 'Invalid retention batch' USING ERRCODE='22023'; END IF;
    IF NOT pg_try_advisory_xact_lock(hashtextextended('hris:mobile-sync-maintenance',0)) THEN RETURN 0; END IF;
    FOR item IN SELECT * FROM public.mobile_sync_changes WHERE sequence IS NOT NULL AND published_at<clock_timestamp()-interval '8 days'
        ORDER BY published_at,company_id,sequence FOR UPDATE SKIP LOCKED LIMIT p_limit LOOP
        DELETE FROM public.mobile_sync_changes WHERE id=item.id;
        UPDATE public.mobile_sync_heads SET pruned_through=item.sequence WHERE company_id=item.company_id AND pruned_through<item.sequence;
        amount:=amount+1;
    END LOOP;
    RETURN amount;
END $$;
REVOKE ALL ON FUNCTION prune_mobile_sync_changes(integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION prune_mobile_sync_changes(integer) TO hris_worker_capability;
