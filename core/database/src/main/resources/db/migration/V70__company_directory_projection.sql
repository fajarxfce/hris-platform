-- Capture source invalidations before allowing another writer past the backfill.
SET LOCAL lock_timeout = '5s';
LOCK TABLE employments,employment_revisions,employment_revision_cancellations,
    persons,organization_units,companies IN SHARE ROW EXCLUSIVE MODE;

CREATE TABLE directory_refresh_queue (
    company_id uuid NOT NULL,
    employment_id uuid NOT NULL,
    refresh_at timestamptz DEFAULT '-infinity',
    PRIMARY KEY(company_id,employment_id),
    FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id)
);
CREATE INDEX directory_refresh_due ON directory_refresh_queue(refresh_at,company_id,employment_id)
    WHERE refresh_at IS NOT NULL;
ALTER TABLE directory_refresh_queue ENABLE ROW LEVEL SECURITY;
ALTER TABLE directory_refresh_queue FORCE ROW LEVEL SECURITY;
CREATE POLICY directory_refresh_scope ON directory_refresh_queue
    USING(company_id IN(current_company_id(),current_secondary_company_id()))
    WITH CHECK(company_id IN(current_company_id(),current_secondary_company_id()));
-- Only source identities and due instants are visible across companies to discovery.
CREATE POLICY directory_refresh_worker ON directory_refresh_queue FOR SELECT
    TO hris_worker_capability USING(true);
GRANT SELECT ON directory_refresh_queue TO hris_worker_capability;

CREATE TABLE directory_employee_records (
    company_id uuid NOT NULL,
    id uuid NOT NULL,
    version bigint NOT NULL CHECK(version>=0),
    employee_number varchar(32),
    name varchar(200),
    position_id uuid,
    position varchar(200),
    department_id uuid,
    department varchar(200),
    PRIMARY KEY(company_id,id),
    FOREIGN KEY(company_id,id) REFERENCES employments(company_id,id),
    CHECK((employee_number IS NULL AND name IS NULL AND position_id IS NULL
        AND position IS NULL AND department_id IS NULL AND department IS NULL) OR
        (employee_number IS NOT NULL AND name IS NOT NULL
        AND (position_id IS NULL)=(position IS NULL)
        AND (department_id IS NULL)=(department IS NULL)))
);
ALTER TABLE directory_employee_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE directory_employee_records FORCE ROW LEVEL SECURITY;
CREATE POLICY directory_record_read ON directory_employee_records FOR SELECT
    USING(company_id=current_company_id());
CREATE POLICY directory_record_insert ON directory_employee_records FOR INSERT
    TO hris_worker_capability WITH CHECK(company_id=current_company_id());
CREATE POLICY directory_record_update ON directory_employee_records FOR UPDATE
    TO hris_worker_capability USING(company_id=current_company_id())
    WITH CHECK(company_id=current_company_id());

INSERT INTO directory_refresh_queue(company_id,employment_id)
    SELECT company_id,id FROM employments;

CREATE FUNCTION capture_directory_employment_refresh() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO directory_refresh_queue(company_id,employment_id)
    VALUES(NEW.company_id,NEW.id)
    ON CONFLICT(company_id,employment_id) DO UPDATE SET refresh_at='-infinity';
    RETURN NULL;
END $$;
CREATE TRIGGER directory_employment_refresh AFTER INSERT OR UPDATE ON employments
    FOR EACH ROW EXECUTE FUNCTION capture_directory_employment_refresh();

CREATE FUNCTION capture_directory_revision_refresh() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO directory_refresh_queue(company_id,employment_id)
    VALUES(NEW.company_id,NEW.employment_id)
    ON CONFLICT(company_id,employment_id) DO UPDATE SET refresh_at='-infinity';
    RETURN NULL;
END $$;
CREATE TRIGGER directory_revision_refresh AFTER INSERT ON employment_revisions
    FOR EACH ROW EXECUTE FUNCTION capture_directory_revision_refresh();
CREATE TRIGGER directory_cancellation_refresh AFTER INSERT ON employment_revision_cancellations
    FOR EACH ROW EXECUTE FUNCTION capture_directory_revision_refresh();

-- A person can have employments in several companies after a transfer. This
-- trigger copies their identities only; it neither returns nor acquires profiles.
-- Its arguments come exclusively from the changed row, never an HTTP parameter.
DO $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='hris_directory_capture') THEN
        CREATE ROLE hris_directory_capture NOLOGIN NOINHERIT NOSUPERUSER
            NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;
END $$;
GRANT USAGE ON SCHEMA public TO hris_directory_capture;
GRANT SELECT(company_id,id,person_id) ON employments TO hris_directory_capture;
GRANT SELECT,INSERT,UPDATE ON directory_refresh_queue TO hris_directory_capture;
CREATE POLICY directory_capture_employments ON employments FOR SELECT
    TO hris_directory_capture USING(true);
CREATE POLICY directory_capture_read ON directory_refresh_queue FOR SELECT
    TO hris_directory_capture USING(true);
CREATE POLICY directory_capture_insert ON directory_refresh_queue FOR INSERT
    TO hris_directory_capture WITH CHECK(true);
CREATE POLICY directory_capture_update ON directory_refresh_queue FOR UPDATE
    TO hris_directory_capture USING(true) WITH CHECK(true);
CREATE FUNCTION capture_directory_person_refresh() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
BEGIN
    INSERT INTO public.directory_refresh_queue(company_id,employment_id)
    SELECT company_id,id FROM public.employments WHERE person_id=NEW.id
    ORDER BY company_id,id
    ON CONFLICT(company_id,employment_id) DO UPDATE SET refresh_at='-infinity';
    RETURN NULL;
END $$;
ALTER FUNCTION capture_directory_person_refresh() OWNER TO hris_directory_capture;
REVOKE ALL ON FUNCTION capture_directory_person_refresh() FROM PUBLIC;
CREATE TRIGGER directory_person_refresh AFTER UPDATE OF legal_name ON persons
    FOR EACH ROW WHEN(OLD.legal_name IS DISTINCT FROM NEW.legal_name)
    EXECUTE FUNCTION capture_directory_person_refresh();

CREATE FUNCTION capture_directory_unit_refresh() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO directory_refresh_queue(company_id,employment_id)
    SELECT DISTINCT company_id,employment_id FROM employment_revisions
    WHERE company_id=NEW.company_id AND (department_id=NEW.id OR position_id=NEW.id)
    ORDER BY company_id,employment_id
    ON CONFLICT(company_id,employment_id) DO UPDATE SET refresh_at='-infinity';
    RETURN NULL;
END $$;
CREATE TRIGGER directory_unit_refresh AFTER UPDATE OF name ON organization_units
    FOR EACH ROW WHEN(OLD.name IS DISTINCT FROM NEW.name)
    EXECUTE FUNCTION capture_directory_unit_refresh();

CREATE FUNCTION capture_directory_timezone_refresh() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO directory_refresh_queue(company_id,employment_id)
    SELECT company_id,id FROM employments WHERE company_id=NEW.id ORDER BY id
    ON CONFLICT(company_id,employment_id) DO UPDATE SET refresh_at='-infinity';
    RETURN NULL;
END $$;
CREATE TRIGGER directory_timezone_refresh AFTER UPDATE OF timezone ON companies
    FOR EACH ROW WHEN(OLD.timezone IS DISTINCT FROM NEW.timezone)
    EXECUTE FUNCTION capture_directory_timezone_refresh();

CREATE FUNCTION protect_directory_record() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' OR NOT pg_has_role(current_user,'hris_worker_capability','MEMBER') THEN
        RAISE EXCEPTION 'Directory projection requires its worker' USING ERRCODE='42501';
    END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.version<>0 OR NEW.name IS NULL THEN
            RAISE EXCEPTION 'Invalid initial directory record' USING ERRCODE='23514';
        END IF;
    ELSIF NEW.company_id<>OLD.company_id OR NEW.id<>OLD.id OR NEW.version<>OLD.version+1 THEN
        RAISE EXCEPTION 'Invalid directory revision' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER directory_record_guard BEFORE INSERT OR UPDATE OR DELETE ON directory_employee_records
    FOR EACH ROW EXECUTE FUNCTION protect_directory_record();

ALTER TABLE mobile_sync_changes DROP CONSTRAINT mobile_sync_changes_collection_check;
ALTER TABLE mobile_sync_changes ADD CONSTRAINT mobile_sync_changes_collection_check
    CHECK(collection IN('EXPENSE_CLAIMS','LEAVE_REQUESTS','OVERTIME_REQUESTS','LEAVE_BALANCES',
        'PAYSLIPS','PAYROLL_PAYMENTS','INBOX','ATTENDANCE_DAYS','WORK_SCHEDULES','ROSTER_DAYS',
        'WORK_HOLIDAYS','EXPENSE_PAYMENTS','DIRECTORY_EMPLOYEES'));
ALTER TABLE mobile_sync_changes DROP CONSTRAINT mobile_sync_owner_scope;
ALTER TABLE mobile_sync_changes ADD CONSTRAINT mobile_sync_owner_scope CHECK(
    (collection='INBOX' AND employment_id IS NULL AND owner_account_id IS NOT NULL) OR
    (collection IN('WORK_HOLIDAYS','DIRECTORY_EMPLOYEES') AND employment_id IS NULL AND owner_account_id IS NULL) OR
    (collection NOT IN('INBOX','WORK_HOLIDAYS','DIRECTORY_EMPLOYEES') AND employment_id IS NOT NULL
        AND (collection='LEAVE_REQUESTS' OR owner_account_id IS NULL))
);
CREATE FUNCTION capture_directory_record_sync() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO mobile_sync_changes(company_id,collection,resource_id,resource_version,operation)
    VALUES(NEW.company_id,'DIRECTORY_EMPLOYEES',NEW.id,NEW.version,
        CASE WHEN NEW.name IS NULL THEN 'DELETE' ELSE 'UPSERT' END);
    RETURN NULL;
END $$;
CREATE TRIGGER directory_record_sync AFTER INSERT OR UPDATE ON directory_employee_records
    FOR EACH ROW EXECUTE FUNCTION capture_directory_record_sync();
