-- This additive version follows the shipped V116 prefix and precedes the
-- unshipped V117+ group branch. Existing canonical direct identity stays immutable.
SET LOCAL lock_timeout = '5s';
LOCK TABLE accounts,collaboration_direct_conversations IN SHARE ROW EXCLUSIVE MODE;

-- A global name change captures ONE identity. Bounded worker fanout owns the
-- continuation; account writes never enumerate every dependent conversation.
CREATE TABLE collaboration_summary_account_queue (
    account_id uuid PRIMARY KEY REFERENCES accounts(id),
    due_at timestamptz DEFAULT '-infinity',
    after_company_id uuid,
    after_conversation_id uuid,
    CHECK((after_company_id IS NULL)=(after_conversation_id IS NULL)),
    CHECK(due_at IS NOT NULL OR after_company_id IS NULL)
);
CREATE INDEX collaboration_summary_account_due ON collaboration_summary_account_queue(due_at,account_id)
    WHERE due_at IS NOT NULL;
ALTER TABLE collaboration_summary_account_queue ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_summary_account_queue FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_summary_account_worker ON collaboration_summary_account_queue
    TO hris_worker_capability USING(true) WITH CHECK(true);
CREATE POLICY collaboration_summary_account_owned ON collaboration_summary_account_queue FOR SELECT
    USING(EXISTS(SELECT 1 FROM collaboration_direct_conversations c
        WHERE c.company_id=current_company_id()
        AND current_actor_id() IN(c.first_account_id,c.second_account_id)
        AND account_id IN(c.first_account_id,c.second_account_id)));

CREATE TABLE collaboration_summary_refresh_queue (
    company_id uuid NOT NULL,
    conversation_id uuid NOT NULL,
    due_at timestamptz DEFAULT '-infinity',
    PRIMARY KEY(company_id,conversation_id),
    FOREIGN KEY(company_id,conversation_id) REFERENCES collaboration_direct_conversations(company_id,id)
);
CREATE INDEX collaboration_summary_refresh_due ON collaboration_summary_refresh_queue(due_at,company_id,conversation_id)
    WHERE due_at IS NOT NULL;
ALTER TABLE collaboration_summary_refresh_queue ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_summary_refresh_queue FORCE ROW LEVEL SECURITY;
-- Global worker access is limited to raw queue identities, not summary contents.
CREATE POLICY collaboration_summary_refresh_worker ON collaboration_summary_refresh_queue
    TO hris_worker_capability USING(true) WITH CHECK(true);
CREATE POLICY collaboration_summary_refresh_owned ON collaboration_summary_refresh_queue FOR SELECT
    USING(company_id=current_company_id() AND EXISTS(
        SELECT 1 FROM collaboration_direct_conversations c
        WHERE c.company_id=collaboration_summary_refresh_queue.company_id
        AND c.id=conversation_id AND current_actor_id() IN(c.first_account_id,c.second_account_id)));
CREATE POLICY collaboration_summary_refresh_capture ON collaboration_summary_refresh_queue FOR INSERT
    WITH CHECK(company_id=current_company_id() AND EXISTS(
        SELECT 1 FROM collaboration_direct_conversations c
        WHERE c.company_id=collaboration_summary_refresh_queue.company_id
        AND c.id=conversation_id AND current_actor_id() IN(c.first_account_id,c.second_account_id)));

CREATE TABLE collaboration_direct_summary_records (
    company_id uuid NOT NULL,
    conversation_id uuid NOT NULL,
    owner_account_id uuid NOT NULL REFERENCES accounts(id),
    peer_account_id uuid NOT NULL REFERENCES accounts(id),
    version bigint NOT NULL CHECK(version>=0),
    peer_display_name varchar(200),
    PRIMARY KEY(company_id,owner_account_id,conversation_id),
    FOREIGN KEY(company_id,conversation_id) REFERENCES collaboration_direct_conversations(company_id,id),
    CHECK(owner_account_id<>peer_account_id),
    CHECK(peer_display_name IS NULL OR length(btrim(peer_display_name))>0)
);
ALTER TABLE collaboration_direct_summary_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_direct_summary_records FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_summary_owned ON collaboration_direct_summary_records FOR SELECT
    USING(company_id=current_company_id() AND owner_account_id=current_actor_id());
CREATE POLICY collaboration_summary_worker ON collaboration_direct_summary_records
    TO hris_worker_capability USING(company_id=current_company_id())
    WITH CHECK(company_id=current_company_id());
-- Projection source reads use an explicit worker company transaction. No global
-- policy or mutation authority is added to the immutable canonical pair.
CREATE POLICY collaboration_summary_worker_source ON collaboration_direct_conversations FOR SELECT
    TO hris_worker_capability USING(company_id=current_company_id());
GRANT SELECT,INSERT,UPDATE ON collaboration_summary_account_queue,
    collaboration_summary_refresh_queue,collaboration_direct_summary_records TO hris_worker_capability;
GRANT SELECT ON collaboration_direct_conversations TO hris_worker_capability;

DO $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='hris_collaboration_summary_capture') THEN
        CREATE ROLE hris_collaboration_summary_capture NOLOGIN NOINHERIT NOSUPERUSER
            NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;
END $$;
GRANT USAGE ON SCHEMA public TO hris_collaboration_summary_capture;
GRANT SELECT(company_id,id,first_account_id,second_account_id)
    ON collaboration_direct_conversations TO hris_collaboration_summary_capture;
GRANT SELECT,INSERT,UPDATE ON collaboration_summary_account_queue TO hris_collaboration_summary_capture;
CREATE POLICY collaboration_summary_capture_source ON collaboration_direct_conversations FOR SELECT
    TO hris_collaboration_summary_capture USING(true);
CREATE POLICY collaboration_summary_capture_account ON collaboration_summary_account_queue
    TO hris_collaboration_summary_capture USING(true) WITH CHECK(true);

CREATE FUNCTION capture_collaboration_summary_account() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
BEGIN
    INSERT INTO public.collaboration_summary_account_queue(account_id) VALUES(NEW.id)
    ON CONFLICT(account_id) DO UPDATE
        SET due_at='-infinity',after_company_id=NULL,after_conversation_id=NULL;
    RETURN NULL;
END $$;
ALTER FUNCTION capture_collaboration_summary_account() OWNER TO hris_collaboration_summary_capture;
REVOKE ALL ON FUNCTION capture_collaboration_summary_account() FROM PUBLIC;
CREATE TRIGGER collaboration_summary_account_changed AFTER UPDATE OF display_name ON accounts
    FOR EACH ROW WHEN(OLD.display_name IS DISTINCT FROM NEW.display_name)
    EXECUTE FUNCTION capture_collaboration_summary_account();

-- The capture role exposes only a finite identity page, and only to the worker.
-- Neither the role nor this function is inherited/executable by API logins.
CREATE INDEX collaboration_summary_first_account ON collaboration_direct_conversations(first_account_id,company_id,id);
CREATE INDEX collaboration_summary_second_account ON collaboration_direct_conversations(second_account_id,company_id,id);
CREATE FUNCTION collaboration_summary_account_targets(
    p_account uuid,p_after_company uuid,p_after_conversation uuid,p_limit integer
) RETURNS TABLE(company_id uuid,conversation_id uuid)
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
BEGIN
    IF p_account IS NULL OR (p_after_company IS NULL)<>(p_after_conversation IS NULL)
        OR p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 201 THEN
        RAISE EXCEPTION 'Invalid summary target page' USING ERRCODE='22023';
    END IF;
    RETURN QUERY SELECT targets.company_id,targets.id FROM (
        SELECT c.company_id,c.id FROM public.collaboration_direct_conversations c
            WHERE c.first_account_id=p_account
            AND (p_after_company IS NULL OR (c.company_id,c.id)>(p_after_company,p_after_conversation))
        UNION ALL
        SELECT c.company_id,c.id FROM public.collaboration_direct_conversations c
            WHERE c.second_account_id=p_account
            AND (p_after_company IS NULL OR (c.company_id,c.id)>(p_after_company,p_after_conversation))
    ) targets ORDER BY targets.company_id,targets.id LIMIT p_limit;
END $$;
ALTER FUNCTION collaboration_summary_account_targets(uuid,uuid,uuid,integer) OWNER TO hris_collaboration_summary_capture;
REVOKE ALL ON FUNCTION collaboration_summary_account_targets(uuid,uuid,uuid,integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION collaboration_summary_account_targets(uuid,uuid,uuid,integer) TO hris_worker_capability;

CREATE FUNCTION capture_collaboration_summary_pair() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO collaboration_summary_refresh_queue(company_id,conversation_id)
        VALUES(NEW.company_id,NEW.id);
    RETURN NULL;
END $$;
CREATE TRIGGER collaboration_summary_pair_created AFTER INSERT ON collaboration_direct_conversations
    FOR EACH ROW EXECUTE FUNCTION capture_collaboration_summary_pair();
INSERT INTO collaboration_summary_refresh_queue(company_id,conversation_id)
    SELECT company_id,id FROM collaboration_direct_conversations;

CREATE FUNCTION protect_collaboration_summary_record() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' OR NOT pg_has_role(current_user,'hris_worker_capability','MEMBER') THEN
        RAISE EXCEPTION 'Summary projection requires its worker' USING ERRCODE='42501';
    END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.version<>0 OR NEW.peer_display_name IS NULL OR NOT EXISTS(
            SELECT 1 FROM collaboration_direct_conversations c
            WHERE c.company_id=NEW.company_id AND c.id=NEW.conversation_id
            AND NEW.owner_account_id IN(c.first_account_id,c.second_account_id)
            AND NEW.peer_account_id IN(c.first_account_id,c.second_account_id)) THEN
            RAISE EXCEPTION 'Invalid initial summary record' USING ERRCODE='23514';
        END IF;
    ELSIF (NEW.company_id,NEW.conversation_id,NEW.owner_account_id,NEW.peer_account_id)
        IS DISTINCT FROM (OLD.company_id,OLD.conversation_id,OLD.owner_account_id,OLD.peer_account_id)
        OR NEW.version<>OLD.version+1 OR NEW.peer_display_name IS NOT DISTINCT FROM OLD.peer_display_name THEN
        RAISE EXCEPTION 'Invalid summary revision' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER collaboration_summary_record_guard BEFORE INSERT OR UPDATE OR DELETE ON collaboration_direct_summary_records
    FOR EACH ROW EXECUTE FUNCTION protect_collaboration_summary_record();

-- Reuse the existing after-commit publisher and retention floor. This collection
-- is deliberately absent from the generic sync API's selection enum: native chat
-- admission must authorize the dedicated metadata reader independently.
ALTER TABLE mobile_sync_changes DROP CONSTRAINT mobile_sync_changes_collection_check;
ALTER TABLE mobile_sync_changes ADD CONSTRAINT mobile_sync_changes_collection_check
    CHECK(collection IN('EXPENSE_CLAIMS','LEAVE_REQUESTS','OVERTIME_REQUESTS','LEAVE_BALANCES',
        'PAYSLIPS','PAYROLL_PAYMENTS','INBOX','ATTENDANCE_DAYS','WORK_SCHEDULES','ROSTER_DAYS',
        'WORK_HOLIDAYS','EXPENSE_PAYMENTS','DIRECTORY_EMPLOYEES','DOCUMENT_EVIDENCE','SELF_PROFILES',
        'COLLABORATION_DIRECT'));
ALTER TABLE mobile_sync_changes DROP CONSTRAINT mobile_sync_owner_scope;
ALTER TABLE mobile_sync_changes ADD CONSTRAINT mobile_sync_owner_scope CHECK(
    (collection IN('INBOX','COLLABORATION_DIRECT') AND employment_id IS NULL AND owner_account_id IS NOT NULL) OR
    (collection IN('WORK_HOLIDAYS','DIRECTORY_EMPLOYEES') AND employment_id IS NULL AND owner_account_id IS NULL) OR
    (collection NOT IN('INBOX','COLLABORATION_DIRECT','WORK_HOLIDAYS','DIRECTORY_EMPLOYEES') AND employment_id IS NOT NULL
        AND (collection='LEAVE_REQUESTS' OR owner_account_id IS NULL))
);
CREATE FUNCTION capture_collaboration_summary_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO mobile_sync_changes(company_id,collection,resource_id,owner_account_id,resource_version,operation)
    VALUES(NEW.company_id,'COLLABORATION_DIRECT',NEW.conversation_id,NEW.owner_account_id,NEW.version,
        CASE WHEN NEW.peer_display_name IS NULL THEN 'DELETE' ELSE 'UPSERT' END);
    RETURN NULL;
END $$;
CREATE TRIGGER collaboration_summary_changed AFTER INSERT OR UPDATE ON collaboration_direct_summary_records
    FOR EACH ROW EXECUTE FUNCTION capture_collaboration_summary_change();
