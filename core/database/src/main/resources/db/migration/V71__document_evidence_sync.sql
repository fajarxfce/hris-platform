-- Bootstrap reads the existing published headers. Hold preceding writers until
-- capture is installed so their successors cannot fall between the two paths.
SET LOCAL lock_timeout = '5s';
LOCK TABLE documents IN SHARE ROW EXCLUSIVE MODE;

ALTER TABLE mobile_sync_changes DROP CONSTRAINT mobile_sync_changes_collection_check;
ALTER TABLE mobile_sync_changes ADD CONSTRAINT mobile_sync_changes_collection_check
    CHECK(collection IN('EXPENSE_CLAIMS','LEAVE_REQUESTS','LEAVE_BALANCES','OVERTIME_REQUESTS',
        'PAYSLIPS','PAYROLL_PAYMENTS','INBOX','ATTENDANCE_DAYS','WORK_SCHEDULES','ROSTER_DAYS',
        'WORK_HOLIDAYS','EXPENSE_PAYMENTS','DIRECTORY_EMPLOYEES','DOCUMENT_EVIDENCE'));

CREATE INDEX document_evidence_sync_owner ON documents(company_id,employment_id,id)
    WHERE current_revision_id IS NOT NULL AND classification <> 'HR_ONLY';

CREATE FUNCTION record_document_evidence_sync_change() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NEW.classification = 'HR_ONLY' THEN RETURN NULL; END IF;
    IF NEW.current_revision_id IS NOT NULL THEN
        INSERT INTO mobile_sync_changes(company_id,collection,resource_id,employment_id,resource_version,operation)
        VALUES(NEW.company_id,'DOCUMENT_EVIDENCE',NEW.id,NEW.employment_id,NEW.version,'UPSERT');
    ELSIF TG_OP = 'UPDATE' AND OLD.current_revision_id IS NOT NULL THEN
        INSERT INTO mobile_sync_changes(company_id,collection,resource_id,employment_id,resource_version,operation)
        VALUES(NEW.company_id,'DOCUMENT_EVIDENCE',NEW.id,NEW.employment_id,NEW.version,'DELETE');
    END IF;
    RETURN NULL;
END $$;
CREATE TRIGGER document_evidence_sync_change AFTER INSERT OR UPDATE ON documents
    FOR EACH ROW EXECUTE FUNCTION record_document_evidence_sync_change();
