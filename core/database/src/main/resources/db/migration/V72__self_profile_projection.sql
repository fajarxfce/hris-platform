SET LOCAL lock_timeout = '5s';
LOCK TABLE employments,employment_revisions,employment_revision_cancellations,
    persons,organization_units,companies IN SHARE ROW EXCLUSIVE MODE;

CREATE TABLE self_profile_refresh_queue (
    company_id uuid NOT NULL,
    employment_id uuid NOT NULL,
    refresh_at timestamptz DEFAULT '-infinity',
    PRIMARY KEY(company_id,employment_id),
    FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id)
);
CREATE INDEX self_profile_refresh_due ON self_profile_refresh_queue(refresh_at,company_id,employment_id)
    WHERE refresh_at IS NOT NULL;
ALTER TABLE self_profile_refresh_queue ENABLE ROW LEVEL SECURITY;
ALTER TABLE self_profile_refresh_queue FORCE ROW LEVEL SECURITY;
CREATE POLICY self_profile_refresh_scope ON self_profile_refresh_queue
    USING(company_id IN(current_company_id(),current_secondary_company_id()))
    WITH CHECK(company_id IN(current_company_id(),current_secondary_company_id()));
CREATE POLICY self_profile_refresh_worker ON self_profile_refresh_queue FOR SELECT
    TO hris_worker_capability USING(true);
GRANT SELECT ON self_profile_refresh_queue TO hris_worker_capability;

CREATE TABLE self_profile_records (
    company_id uuid NOT NULL,
    id uuid NOT NULL,
    version bigint NOT NULL CHECK(version>=0),
    account_id uuid,
    payload jsonb,
    PRIMARY KEY(company_id,id),
    FOREIGN KEY(company_id,id) REFERENCES employments(company_id,id),
    CHECK((payload IS NULL)=(account_id IS NULL)),
    CHECK(payload IS NULL OR (jsonb_typeof(payload)='object' AND octet_length(payload::text)<=32768))
);
ALTER TABLE self_profile_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE self_profile_records FORCE ROW LEVEL SECURITY;
CREATE POLICY self_profile_record_read ON self_profile_records FOR SELECT
    USING(company_id=current_company_id());
CREATE POLICY self_profile_record_insert ON self_profile_records FOR INSERT
    TO hris_worker_capability WITH CHECK(company_id=current_company_id());
CREATE POLICY self_profile_record_update ON self_profile_records FOR UPDATE
    TO hris_worker_capability USING(company_id=current_company_id())
    WITH CHECK(company_id=current_company_id());

INSERT INTO self_profile_refresh_queue(company_id,employment_id)
    SELECT company_id,id FROM employments;

-- These triggers capture dependencies only. The worker owns effective-date policy.
CREATE FUNCTION capture_self_profile_employment_refresh() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO self_profile_refresh_queue(company_id,employment_id)
    SELECT NEW.company_id,NEW.id
    UNION SELECT company_id,employment_id FROM employment_revisions
        WHERE company_id=NEW.company_id AND manager_id=NEW.id
    ORDER BY 1,2
    ON CONFLICT(company_id,employment_id) DO UPDATE SET refresh_at='-infinity';
    RETURN NULL;
END $$;
CREATE TRIGGER self_profile_employment_refresh AFTER INSERT OR UPDATE ON employments
    FOR EACH ROW EXECUTE FUNCTION capture_self_profile_employment_refresh();

CREATE FUNCTION capture_self_profile_revision_refresh() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO self_profile_refresh_queue(company_id,employment_id)
    SELECT NEW.company_id,NEW.employment_id
    UNION SELECT company_id,employment_id FROM employment_revisions
        WHERE company_id=NEW.company_id AND manager_id=NEW.employment_id
    ORDER BY 1,2
    ON CONFLICT(company_id,employment_id) DO UPDATE SET refresh_at='-infinity';
    RETURN NULL;
END $$;
CREATE TRIGGER self_profile_revision_refresh AFTER INSERT ON employment_revisions
    FOR EACH ROW EXECUTE FUNCTION capture_self_profile_revision_refresh();
CREATE TRIGGER self_profile_cancellation_refresh AFTER INSERT ON employment_revision_cancellations
    FOR EACH ROW EXECUTE FUNCTION capture_self_profile_revision_refresh();

-- Shared persons can affect several companies. This non-login role can only
-- identify dependencies and enqueue them; it cannot read private profile fields.
DO $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='hris_profile_capture') THEN
        CREATE ROLE hris_profile_capture NOLOGIN NOINHERIT NOSUPERUSER
            NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;
END $$;
GRANT USAGE ON SCHEMA public TO hris_profile_capture;
GRANT SELECT(company_id,id,person_id) ON employments TO hris_profile_capture;
GRANT SELECT(company_id,employment_id,manager_id) ON employment_revisions TO hris_profile_capture;
GRANT SELECT,INSERT,UPDATE ON self_profile_refresh_queue TO hris_profile_capture;
CREATE POLICY profile_capture_employments ON employments FOR SELECT
    TO hris_profile_capture USING(true);
CREATE POLICY profile_capture_revisions ON employment_revisions FOR SELECT
    TO hris_profile_capture USING(true);
CREATE POLICY profile_capture_read ON self_profile_refresh_queue FOR SELECT
    TO hris_profile_capture USING(true);
CREATE POLICY profile_capture_insert ON self_profile_refresh_queue FOR INSERT
    TO hris_profile_capture WITH CHECK(true);
CREATE POLICY profile_capture_update ON self_profile_refresh_queue FOR UPDATE
    TO hris_profile_capture USING(true) WITH CHECK(true);
CREATE FUNCTION capture_self_profile_person_refresh() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
BEGIN
    INSERT INTO public.self_profile_refresh_queue(company_id,employment_id)
    SELECT company_id,id FROM public.employments WHERE person_id=NEW.id
    UNION SELECT r.company_id,r.employment_id FROM public.employment_revisions r
        JOIN public.employments m ON m.company_id=r.company_id AND m.id=r.manager_id
        WHERE m.person_id=NEW.id AND OLD.legal_name IS DISTINCT FROM NEW.legal_name
    ORDER BY 1,2
    ON CONFLICT(company_id,employment_id) DO UPDATE SET refresh_at='-infinity';
    RETURN NULL;
END $$;
ALTER FUNCTION capture_self_profile_person_refresh() OWNER TO hris_profile_capture;
REVOKE ALL ON FUNCTION capture_self_profile_person_refresh() FROM PUBLIC;
CREATE TRIGGER self_profile_person_refresh AFTER UPDATE ON persons
    FOR EACH ROW WHEN(OLD IS DISTINCT FROM NEW)
    EXECUTE FUNCTION capture_self_profile_person_refresh();

CREATE FUNCTION capture_self_profile_unit_refresh() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO self_profile_refresh_queue(company_id,employment_id)
    SELECT DISTINCT company_id,employment_id FROM employment_revisions
    WHERE company_id=NEW.company_id AND
        (branch_id=NEW.id OR department_id=NEW.id OR position_id=NEW.id OR cost_center_id=NEW.id)
    ORDER BY company_id,employment_id
    ON CONFLICT(company_id,employment_id) DO UPDATE SET refresh_at='-infinity';
    RETURN NULL;
END $$;
CREATE TRIGGER self_profile_unit_refresh AFTER UPDATE OF code,name,active ON organization_units
    FOR EACH ROW WHEN(OLD IS DISTINCT FROM NEW)
    EXECUTE FUNCTION capture_self_profile_unit_refresh();

CREATE FUNCTION capture_self_profile_timezone_refresh() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO self_profile_refresh_queue(company_id,employment_id)
    SELECT company_id,id FROM employments WHERE company_id=NEW.id ORDER BY id
    ON CONFLICT(company_id,employment_id) DO UPDATE SET refresh_at='-infinity';
    RETURN NULL;
END $$;
CREATE TRIGGER self_profile_timezone_refresh AFTER UPDATE OF timezone ON companies
    FOR EACH ROW WHEN(OLD.timezone IS DISTINCT FROM NEW.timezone)
    EXECUTE FUNCTION capture_self_profile_timezone_refresh();

CREATE FUNCTION protect_self_profile_record() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' OR NOT pg_has_role(current_user,'hris_worker_capability','MEMBER') THEN
        RAISE EXCEPTION 'Self profile projection requires its worker' USING ERRCODE='42501';
    END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.version<>0 OR NEW.payload IS NULL THEN
            RAISE EXCEPTION 'Invalid initial self profile record' USING ERRCODE='23514';
        END IF;
    ELSIF NEW.company_id<>OLD.company_id OR NEW.id<>OLD.id OR NEW.version<>OLD.version+1 THEN
        RAISE EXCEPTION 'Invalid self profile revision' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER self_profile_record_guard BEFORE INSERT OR UPDATE OR DELETE ON self_profile_records
    FOR EACH ROW EXECUTE FUNCTION protect_self_profile_record();

ALTER TABLE mobile_sync_changes DROP CONSTRAINT mobile_sync_changes_collection_check;
ALTER TABLE mobile_sync_changes ADD CONSTRAINT mobile_sync_changes_collection_check
    CHECK(collection IN('EXPENSE_CLAIMS','LEAVE_REQUESTS','OVERTIME_REQUESTS','LEAVE_BALANCES',
        'PAYSLIPS','PAYROLL_PAYMENTS','INBOX','ATTENDANCE_DAYS','WORK_SCHEDULES','ROSTER_DAYS',
        'WORK_HOLIDAYS','EXPENSE_PAYMENTS','DIRECTORY_EMPLOYEES','DOCUMENT_EVIDENCE','SELF_PROFILES'));
CREATE FUNCTION capture_self_profile_record_sync() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO mobile_sync_changes(company_id,collection,resource_id,resource_version,employment_id,operation)
    VALUES(NEW.company_id,'SELF_PROFILES',NEW.id,NEW.version,NEW.id,
        CASE WHEN NEW.payload IS NULL THEN 'DELETE' ELSE 'UPSERT' END);
    RETURN NULL;
END $$;
CREATE TRIGGER self_profile_record_sync AFTER INSERT OR UPDATE ON self_profile_records
    FOR EACH ROW EXECUTE FUNCTION capture_self_profile_record_sync();
