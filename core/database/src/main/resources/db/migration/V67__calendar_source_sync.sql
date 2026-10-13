-- Keep preceding writers ahead of the backfill and install capture before later
-- writes can proceed. A busy schema fails atomically rather than omitting a change.
SET LOCAL lock_timeout = '5s';
LOCK TABLE schedule_assignments,roster_days,work_holidays IN SHARE ROW EXCLUSIVE MODE;

CREATE TABLE work_schedule_sync_heads (
    company_id uuid NOT NULL,
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    employment_id uuid NOT NULL,
    effective_from date NOT NULL,
    revision bigint NOT NULL CHECK(revision >= 0),
    PRIMARY KEY(company_id,employment_id,effective_from),
    UNIQUE(company_id,id),
    FOREIGN KEY(company_id,employment_id,revision)
        REFERENCES schedule_assignments(company_id,employment_id,revision)
);
ALTER TABLE work_schedule_sync_heads ENABLE ROW LEVEL SECURITY;
ALTER TABLE work_schedule_sync_heads FORCE ROW LEVEL SECURITY;
CREATE POLICY company_scope ON work_schedule_sync_heads
    USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());

-- Retain one current rule for each effective date, without rewriting assignment
-- history or substituting a subsequently edited shift template for its snapshot.
INSERT INTO work_schedule_sync_heads(company_id,employment_id,effective_from,revision)
SELECT DISTINCT ON(company_id,employment_id,effective_from)
    company_id,employment_id,effective_from,revision
FROM schedule_assignments
ORDER BY company_id,employment_id,effective_from,revision DESC;

ALTER TABLE roster_days ADD COLUMN id uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE roster_days ADD CONSTRAINT roster_day_identity UNIQUE(company_id,id);

ALTER TABLE mobile_sync_changes DROP CONSTRAINT mobile_sync_changes_collection_check;
ALTER TABLE mobile_sync_changes ADD CONSTRAINT mobile_sync_changes_collection_check
    CHECK(collection IN ('EXPENSE_CLAIMS','LEAVE_REQUESTS','OVERTIME_REQUESTS','LEAVE_BALANCES','PAYSLIPS','PAYROLL_PAYMENTS','INBOX','ATTENDANCE_DAYS','WORK_SCHEDULES','ROSTER_DAYS','WORK_HOLIDAYS'));
ALTER TABLE mobile_sync_changes DROP CONSTRAINT mobile_sync_owner_scope;
ALTER TABLE mobile_sync_changes ADD CONSTRAINT mobile_sync_owner_scope CHECK(
    (collection='INBOX' AND employment_id IS NULL AND owner_account_id IS NOT NULL) OR
    (collection='WORK_HOLIDAYS' AND employment_id IS NULL AND owner_account_id IS NULL) OR
    (collection NOT IN ('INBOX','WORK_HOLIDAYS') AND employment_id IS NOT NULL
        AND (collection='LEAVE_REQUESTS' OR owner_account_id IS NULL))
);

CREATE FUNCTION capture_work_schedule_sync() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE head work_schedule_sync_heads;
BEGIN
    INSERT INTO work_schedule_sync_heads(company_id,employment_id,effective_from,revision)
    VALUES(NEW.company_id,NEW.employment_id,NEW.effective_from,NEW.revision)
    ON CONFLICT(company_id,employment_id,effective_from)
    DO UPDATE SET revision=EXCLUDED.revision
        WHERE EXCLUDED.revision > work_schedule_sync_heads.revision
    RETURNING * INTO head;
    IF FOUND THEN
        INSERT INTO mobile_sync_changes(company_id,collection,resource_id,employment_id,resource_version,operation)
        VALUES(head.company_id,'WORK_SCHEDULES',head.id,head.employment_id,head.revision,'UPSERT');
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER work_schedule_sync AFTER INSERT ON schedule_assignments
    FOR EACH ROW EXECUTE FUNCTION capture_work_schedule_sync();

CREATE FUNCTION capture_roster_day_sync() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO mobile_sync_changes(company_id,collection,resource_id,employment_id,resource_version,operation)
    VALUES(NEW.company_id,'ROSTER_DAYS',NEW.id,NEW.employment_id,NEW.version,'UPSERT');
    RETURN NEW;
END $$;
CREATE TRIGGER roster_day_sync AFTER INSERT OR UPDATE ON roster_days
    FOR EACH ROW EXECUTE FUNCTION capture_roster_day_sync();

CREATE FUNCTION capture_work_holiday_sync() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO mobile_sync_changes(company_id,collection,resource_id,resource_version,operation)
    VALUES(NEW.company_id,'WORK_HOLIDAYS',NEW.id,NEW.version,'UPSERT');
    RETURN NEW;
END $$;
CREATE TRIGGER work_holiday_sync AFTER INSERT OR UPDATE ON work_holidays
    FOR EACH ROW EXECUTE FUNCTION capture_work_holiday_sync();
