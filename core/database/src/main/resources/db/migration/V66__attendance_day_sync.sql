CREATE TABLE attendance_sync_heads (
    company_id uuid NOT NULL,
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    employment_id uuid NOT NULL,
    work_date date NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK(version >= 0),
    PRIMARY KEY(company_id,employment_id,work_date),
    UNIQUE(company_id,id),
    FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id)
);
ALTER TABLE attendance_sync_heads ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance_sync_heads FORCE ROW LEVEL SECURITY;
CREATE POLICY company_scope ON attendance_sync_heads
    USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());

-- Wait for pre-upgrade writers before the backfill, then keep later writers out
-- until capture is installed. Otherwise a commit between these steps can be lost.
SET LOCAL lock_timeout = '5s';
LOCK TABLE attendance_events,attendance_reviews,attendance_corrections
    IN SHARE ROW EXCLUSIVE MODE;

-- Existing evidence receives one stable projection identity. Correction revisions
-- remain independent: a capture or review must not change their command version.
INSERT INTO attendance_sync_heads(company_id,employment_id,work_date)
SELECT company_id,employment_id,work_date FROM attendance_events
UNION
SELECT company_id,employment_id,work_date FROM attendance_corrections;

ALTER TABLE mobile_sync_changes DROP CONSTRAINT mobile_sync_changes_collection_check;
ALTER TABLE mobile_sync_changes ADD CONSTRAINT mobile_sync_changes_collection_check
    CHECK(collection IN ('EXPENSE_CLAIMS','LEAVE_REQUESTS','OVERTIME_REQUESTS','LEAVE_BALANCES','PAYSLIPS','PAYROLL_PAYMENTS','INBOX','ATTENDANCE_DAYS'));

-- Capture only identifiers and versions. Business policy remains in the use case;
-- the publication worker assigns public cursor positions after this transaction.
CREATE FUNCTION capture_attendance_day_sync() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    employee uuid;
    work_day date;
    head attendance_sync_heads;
BEGIN
    IF TG_TABLE_NAME = 'attendance_reviews' THEN
        SELECT employment_id,work_date INTO STRICT employee,work_day
        FROM attendance_events WHERE company_id=NEW.company_id AND id=NEW.event_id;
    ELSE
        employee := NEW.employment_id;
        work_day := NEW.work_date;
    END IF;
    INSERT INTO attendance_sync_heads(company_id,employment_id,work_date)
    VALUES(NEW.company_id,employee,work_day)
    ON CONFLICT(company_id,employment_id,work_date)
    DO UPDATE SET version=attendance_sync_heads.version+1
    RETURNING * INTO head;
    INSERT INTO mobile_sync_changes(company_id,collection,resource_id,employment_id,resource_version,operation)
    VALUES(head.company_id,'ATTENDANCE_DAYS',head.id,head.employment_id,head.version,'UPSERT');
    RETURN NEW;
END $$;

CREATE TRIGGER attendance_day_sync AFTER INSERT ON attendance_events
    FOR EACH ROW EXECUTE FUNCTION capture_attendance_day_sync();
CREATE TRIGGER attendance_day_sync AFTER INSERT ON attendance_reviews
    FOR EACH ROW EXECUTE FUNCTION capture_attendance_day_sync();
CREATE TRIGGER attendance_day_sync AFTER INSERT ON attendance_corrections
    FOR EACH ROW EXECUTE FUNCTION capture_attendance_day_sync();
