ALTER TABLE mobile_sync_changes DROP CONSTRAINT mobile_sync_changes_collection_check;
ALTER TABLE mobile_sync_changes ADD CONSTRAINT mobile_sync_changes_collection_check
    CHECK(collection IN ('EXPENSE_CLAIMS','LEAVE_REQUESTS','OVERTIME_REQUESTS','LEAVE_BALANCES','PAYSLIPS','PAYROLL_PAYMENTS','INBOX'));

-- Replace the original unnamed owner constraint without relying on PostgreSQL's
-- generated suffix. Existing collection ownership remains unchanged.
DO $$ DECLARE owner_checks text[]; BEGIN
    SELECT array_agg(conname) INTO owner_checks FROM pg_constraint
        WHERE conrelid='mobile_sync_changes'::regclass AND contype='c'
            AND pg_get_constraintdef(oid) LIKE '%owner_account_id%';
    IF cardinality(owner_checks) IS DISTINCT FROM 1 THEN
        RAISE EXCEPTION 'Expected the original sync owner constraint';
    END IF;
    EXECUTE format('ALTER TABLE mobile_sync_changes DROP CONSTRAINT %I',owner_checks[1]);
END $$;
ALTER TABLE mobile_sync_changes ALTER COLUMN employment_id DROP NOT NULL;
ALTER TABLE mobile_sync_changes ADD CONSTRAINT mobile_sync_owner_scope CHECK(
    (collection='INBOX' AND employment_id IS NULL AND owner_account_id IS NOT NULL) OR
    (collection<>'INBOX' AND employment_id IS NOT NULL AND (collection='LEAVE_REQUESTS' OR owner_account_id IS NULL))
);

-- Copy the committed storage identity/state for the entire bounded statement.
-- Mailbox ownership is the original account; employment never grants this feed.
CREATE FUNCTION record_inbox_sync_changes() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    INSERT INTO mobile_sync_changes(company_id,collection,resource_id,owner_account_id,resource_version,operation)
        SELECT company_id,'INBOX',id,owner_account_id,version,CASE WHEN withdrawn THEN 'DELETE' ELSE 'UPSERT' END
        FROM inbox_sync_rows;
    RETURN NULL;
END $$;
CREATE TRIGGER record_inbox_insert_changes AFTER INSERT ON inbox_items REFERENCING NEW TABLE AS inbox_sync_rows
    FOR EACH STATEMENT EXECUTE FUNCTION record_inbox_sync_changes();
CREATE TRIGGER record_inbox_update_changes AFTER UPDATE ON inbox_items REFERENCING NEW TABLE AS inbox_sync_rows
    FOR EACH STATEMENT EXECUTE FUNCTION record_inbox_sync_changes();
