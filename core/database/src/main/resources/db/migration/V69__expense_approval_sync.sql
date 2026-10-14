-- Approval assignment can change without a claim command. Capture another
-- invalidation at the existing claim version; clients consume publication cursors.
SET LOCAL lock_timeout = '5s';
LOCK TABLE approval_requests IN SHARE ROW EXCLUSIVE MODE;

CREATE FUNCTION record_expense_approval_sync_change() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    INSERT INTO mobile_sync_changes(company_id,collection,resource_id,employment_id,resource_version,operation)
    SELECT c.company_id,'EXPENSE_CLAIMS',c.id,c.employment_id,c.version,'UPSERT'
    FROM expense_submissions s JOIN expense_claims c
        ON c.company_id=s.company_id AND c.id=s.claim_id AND c.latest_submission_id=s.id
    WHERE s.company_id=NEW.company_id AND s.approval_id=NEW.id;
    RETURN NULL;
END $$;
CREATE TRIGGER expense_approval_sync_change AFTER UPDATE OF version ON approval_requests
    FOR EACH ROW WHEN (NEW.kind='EXPENSE' AND OLD.version IS DISTINCT FROM NEW.version)
    EXECUTE FUNCTION record_expense_approval_sync_change();
