-- Serialize preceding payment writers with backfill and install capture before
-- later writes proceed. A busy schema aborts this migration atomically.
SET LOCAL lock_timeout = '5s';
LOCK TABLE expense_payment_items IN SHARE ROW EXCLUSIVE MODE;

CREATE TABLE expense_payment_heads (
    company_id uuid NOT NULL,
    claim_id uuid NOT NULL,
    employment_id uuid NOT NULL,
    version bigint NOT NULL CHECK(version BETWEEN 1 AND 60),
    PRIMARY KEY(company_id,claim_id),
    FOREIGN KEY(company_id,claim_id) REFERENCES expense_claims(company_id,id)
);
CREATE INDEX expense_payment_head_owner ON expense_payment_heads(company_id,employment_id,claim_id);
ALTER TABLE expense_payment_heads ENABLE ROW LEVEL SECURITY;
ALTER TABLE expense_payment_heads FORCE ROW LEVEL SECURITY;
CREATE POLICY company_scope ON expense_payment_heads
    USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
CREATE TRIGGER expense_payment_head_retained BEFORE DELETE ON expense_payment_heads
    FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

CREATE FUNCTION validate_expense_payment_head() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='UPDATE' AND (
        (NEW.company_id,NEW.claim_id,NEW.employment_id)
            IS DISTINCT FROM (OLD.company_id,OLD.claim_id,OLD.employment_id)
        OR NEW.version<>OLD.version+1
    ) THEN
        RAISE EXCEPTION 'Payment progress must advance without changing its owner' USING ERRCODE='23514';
    END IF;
    IF NEW.version IS DISTINCT FROM (
        SELECT coalesce(sum(version+1),0) FROM expense_payment_items
        WHERE company_id=NEW.company_id AND claim_id=NEW.claim_id
    ) OR NOT EXISTS(
        SELECT 1 FROM expense_claims
        WHERE company_id=NEW.company_id AND id=NEW.claim_id AND employment_id=NEW.employment_id
    ) THEN
        RAISE EXCEPTION 'Payment progress must match its retained instructions' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER expense_payment_head_valid BEFORE INSERT OR UPDATE ON expense_payment_heads
    FOR EACH ROW EXECUTE FUNCTION validate_expense_payment_head();

INSERT INTO expense_payment_heads(company_id,claim_id,employment_id,version)
SELECT company_id,claim_id,employment_id,sum(version+1)
FROM expense_payment_items GROUP BY company_id,claim_id,employment_id;

CREATE FUNCTION advance_expense_payment_head() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    INSERT INTO expense_payment_heads(company_id,claim_id,employment_id,version)
    VALUES(NEW.company_id,NEW.claim_id,NEW.employment_id,(
        SELECT sum(version+1) FROM expense_payment_items
        WHERE company_id=NEW.company_id AND claim_id=NEW.claim_id
    ))
    ON CONFLICT(company_id,claim_id) DO UPDATE SET version=EXCLUDED.version;
    RETURN NULL;
END $$;
CREATE TRIGGER expense_payment_head_advance AFTER INSERT OR UPDATE ON expense_payment_items
    FOR EACH ROW EXECUTE FUNCTION advance_expense_payment_head();

CREATE FUNCTION require_expense_payment_head() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(
        SELECT 1 FROM expense_payment_heads h
        WHERE h.company_id=NEW.company_id AND h.claim_id=NEW.claim_id
            AND h.employment_id=NEW.employment_id AND h.version=(
                SELECT sum(version+1) FROM expense_payment_items
                WHERE company_id=NEW.company_id AND claim_id=NEW.claim_id
            )
    ) THEN
        RAISE EXCEPTION 'Payment progress is missing or stale' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER expense_payment_head_complete AFTER INSERT OR UPDATE ON expense_payment_items
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_expense_payment_head();

ALTER TABLE mobile_sync_changes DROP CONSTRAINT mobile_sync_changes_collection_check;
ALTER TABLE mobile_sync_changes ADD CONSTRAINT mobile_sync_changes_collection_check CHECK(collection IN (
    'EXPENSE_CLAIMS','EXPENSE_PAYMENTS','LEAVE_REQUESTS','OVERTIME_REQUESTS','LEAVE_BALANCES',
    'PAYSLIPS','PAYROLL_PAYMENTS','INBOX','ATTENDANCE_DAYS','WORK_SCHEDULES','ROSTER_DAYS','WORK_HOLIDAYS'
));
CREATE FUNCTION record_expense_payment_sync_change() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    INSERT INTO mobile_sync_changes(company_id,collection,resource_id,employment_id,resource_version,operation)
    VALUES(NEW.company_id,'EXPENSE_PAYMENTS',NEW.claim_id,NEW.employment_id,NEW.version,'UPSERT');
    RETURN NULL;
END $$;
CREATE TRIGGER expense_payment_sync_change AFTER INSERT OR UPDATE ON expense_payment_heads
    FOR EACH ROW EXECUTE FUNCTION record_expense_payment_sync_change();
