CREATE TABLE payroll_payment_batches (
    company_id uuid NOT NULL REFERENCES companies(id), id uuid NOT NULL,
    status varchar(12) NOT NULL CHECK(status IN ('PREPARED','RELEASED','CLOSED','CANCELLED')),
    version bigint NOT NULL CHECK(version BETWEEN 0 AND 101), title varchar(160) NOT NULL CHECK(length(trim(title))>0),
    total_amount numeric(16,2) NOT NULL CHECK(total_amount>0), item_count integer NOT NULL CHECK(item_count BETWEEN 1 AND 100),
    created_by uuid NOT NULL REFERENCES accounts(id), created_at timestamptz NOT NULL,
    released_by uuid REFERENCES accounts(id), released_at timestamptz,
    write_xid xid8 NOT NULL DEFAULT pg_current_xact_id(),
    PRIMARY KEY(company_id,id),
    CHECK((status IN ('PREPARED','CANCELLED') AND released_by IS NULL AND released_at IS NULL) OR
          (status IN ('RELEASED','CLOSED') AND released_by IS NOT NULL AND released_at IS NOT NULL AND released_at>=created_at AND released_by<>created_by))
);
CREATE TABLE payroll_payment_items (
    company_id uuid NOT NULL, id uuid NOT NULL, batch_id uuid NOT NULL, assessment_id uuid NOT NULL,
    employment_id uuid NOT NULL, employee_number varchar(32) NOT NULL, employee_name varchar(200) NOT NULL,
    tax_month date NOT NULL CHECK(extract(day FROM tax_month)=1), planned_payment_date date NOT NULL,
    amount numeric(14,2) NOT NULL CHECK(amount>0), bank_code varchar(12) NOT NULL CHECK(bank_code ~ '^[A-Z0-9]{2,12}$'),
    account_number varchar(34) NOT NULL CHECK(account_number ~ '^[0-9]{6,34}$'), account_name varchar(120) NOT NULL CHECK(length(trim(account_name))>0 AND account_name !~ '[[:cntrl:]]'),
    status varchar(12) NOT NULL CHECK(status IN ('PREPARED','PENDING','SUCCEEDED','FAILED','CANCELLED')),
    version integer NOT NULL CHECK(version BETWEEN 0 AND 2), batch_version bigint NOT NULL CHECK(batch_version BETWEEN 0 AND 101),
    transaction_reference varchar(100), occurred_at timestamptz,
    PRIMARY KEY(company_id,id), UNIQUE(company_id,batch_id,assessment_id), UNIQUE(company_id,batch_id,id),
    FOREIGN KEY(company_id,batch_id) REFERENCES payroll_payment_batches(company_id,id),
    FOREIGN KEY(company_id,assessment_id) REFERENCES payroll_assessments(company_id,id),
    FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id),
    CHECK((status='SUCCEEDED' AND transaction_reference IS NOT NULL AND transaction_reference ~ '^[A-Za-z0-9][A-Za-z0-9._:/ -]{0,99}$' AND occurred_at IS NOT NULL) OR
          (status='FAILED' AND transaction_reference IS NULL AND occurred_at IS NOT NULL) OR
          (status IN ('PREPARED','PENDING','CANCELLED') AND transaction_reference IS NULL AND occurred_at IS NULL))
);
CREATE UNIQUE INDEX payroll_payment_assessment_reservation ON payroll_payment_items(company_id,assessment_id) WHERE status IN ('PREPARED','PENDING','SUCCEEDED');
CREATE UNIQUE INDEX payroll_payment_transaction_reference ON payroll_payment_items(company_id,transaction_reference) WHERE status='SUCCEEDED';
CREATE INDEX payroll_payment_assessment_history ON payroll_payment_items(company_id,assessment_id);
CREATE INDEX payroll_payment_batch_cursor ON payroll_payment_batches(company_id,created_at DESC,id DESC);
CREATE INDEX payroll_payment_batch_open ON payroll_payment_batches(company_id) WHERE status IN ('PREPARED','RELEASED');
CREATE TABLE payroll_payment_actions (
    company_id uuid NOT NULL, batch_id uuid NOT NULL, version bigint NOT NULL CHECK(version BETWEEN 0 AND 101),
    kind varchar(12) NOT NULL CHECK(kind IN ('PREPARED','RELEASED','CANCELLED','RECONCILED')),
    status varchar(12) NOT NULL CHECK(status IN ('PREPARED','RELEASED','CLOSED','CANCELLED')),
    actor_id uuid NOT NULL REFERENCES accounts(id), reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0), recorded_at timestamptz NOT NULL,
    PRIMARY KEY(company_id,batch_id,version), FOREIGN KEY(company_id,batch_id) REFERENCES payroll_payment_batches(company_id,id)
);
ALTER TABLE payroll_payment_items ADD FOREIGN KEY(company_id,batch_id,batch_version)
    REFERENCES payroll_payment_actions(company_id,batch_id,version) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE payroll_payment_results (
    company_id uuid NOT NULL, batch_id uuid NOT NULL, item_id uuid NOT NULL, batch_version bigint NOT NULL,
    status varchar(12) NOT NULL CHECK(status IN ('SUCCEEDED','FAILED')), transaction_reference varchar(100), occurred_at timestamptz NOT NULL,
    actor_id uuid NOT NULL REFERENCES accounts(id), reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0), recorded_at timestamptz NOT NULL,
    PRIMARY KEY(company_id,item_id),
    FOREIGN KEY(company_id,batch_id,item_id) REFERENCES payroll_payment_items(company_id,batch_id,id),
    FOREIGN KEY(company_id,batch_id,batch_version) REFERENCES payroll_payment_actions(company_id,batch_id,version) DEFERRABLE INITIALLY DEFERRED,
    CHECK((status='SUCCEEDED' AND transaction_reference IS NOT NULL AND transaction_reference ~ '^[A-Za-z0-9][A-Za-z0-9._:/ -]{0,99}$') OR (status='FAILED' AND transaction_reference IS NULL))
);
DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['payroll_payment_batches','payroll_payment_items','payroll_payment_actions','payroll_payment_results'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
        EXECUTE format('CREATE TRIGGER deny_payroll_payment_delete BEFORE DELETE ON %I FOR EACH ROW EXECUTE FUNCTION deny_history_mutation()',t);
    END LOOP;
END $$;
CREATE POLICY payroll_payment_author ON payroll_payment_batches AS RESTRICTIVE FOR INSERT TO PUBLIC WITH CHECK(created_by=current_actor_id());
CREATE POLICY payroll_payment_action_actor ON payroll_payment_actions AS RESTRICTIVE FOR INSERT TO PUBLIC WITH CHECK(actor_id=current_actor_id());
CREATE POLICY payroll_payment_result_actor ON payroll_payment_results AS RESTRICTIVE FOR INSERT TO PUBLIC WITH CHECK(actor_id=current_actor_id());
CREATE TRIGGER immutable_payroll_payment_action BEFORE UPDATE ON payroll_payment_actions FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE TRIGGER immutable_payroll_payment_result BEFORE UPDATE ON payroll_payment_results FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE FUNCTION protect_payroll_payment_batch() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.status<>'PREPARED' OR NEW.version<>0 THEN RAISE EXCEPTION 'Payment batches must start prepared' USING ERRCODE='23514';END IF;
        NEW.write_xid:=pg_current_xact_id();
    ELSE
        IF (NEW.company_id,NEW.id,NEW.title,NEW.total_amount,NEW.item_count,NEW.created_by,NEW.created_at,NEW.write_xid) IS DISTINCT FROM
           (OLD.company_id,OLD.id,OLD.title,OLD.total_amount,OLD.item_count,OLD.created_by,OLD.created_at,OLD.write_xid) OR NEW.version<>OLD.version+1 THEN
            RAISE EXCEPTION 'Payment instruction snapshot is immutable' USING ERRCODE='42501';END IF;
        IF OLD.status='PREPARED' AND NEW.status IN ('RELEASED','CANCELLED') THEN
            IF NEW.status='RELEASED' AND (NEW.released_by IS DISTINCT FROM current_actor_id() OR NEW.released_by=OLD.created_by) THEN
                RAISE EXCEPTION 'Payment release requires an independent actor' USING ERRCODE='23514';END IF;
        ELSIF OLD.status='RELEASED' AND NEW.status IN ('RELEASED','CLOSED') THEN
            IF (NEW.released_by,NEW.released_at) IS DISTINCT FROM (OLD.released_by,OLD.released_at) THEN RAISE EXCEPTION 'Payment release is immutable' USING ERRCODE='42501';END IF;
        ELSE RAISE EXCEPTION 'Unsupported payment batch transition' USING ERRCODE='23514';END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_payroll_payment_batch BEFORE INSERT OR UPDATE ON payroll_payment_batches FOR EACH ROW EXECUTE FUNCTION protect_payroll_payment_batch();
CREATE FUNCTION protect_payroll_payment_item() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.status<>'PREPARED' OR NEW.version<>0 OR NEW.batch_version<>0 OR (SELECT count(*) FROM payroll_payment_items WHERE company_id=NEW.company_id AND assessment_id=NEW.assessment_id)>=20 OR NOT EXISTS(
            SELECT 1 FROM payroll_payment_batches b
            JOIN payroll_assessments a ON a.company_id=b.company_id AND a.id=NEW.assessment_id
            JOIN payroll_run_targets t ON t.company_id=a.company_id AND t.run_id=a.run_id AND t.ordinal=a.ordinal
            JOIN payroll_run_results o ON o.company_id=a.company_id AND o.run_id=a.run_id AND o.ordinal=a.ordinal
            JOIN payroll_runs r ON r.company_id=a.company_id AND r.id=a.run_id
            WHERE b.company_id=NEW.company_id AND b.id=NEW.batch_id AND b.status='PREPARED' AND b.write_xid=pg_current_xact_id()
            AND a.employment_id=NEW.employment_id AND a.tax_month=NEW.tax_month AND r.planned_payment_date=NEW.planned_payment_date
            AND t.employee_number=NEW.employee_number AND t.employee_name=NEW.employee_name AND o.take_home=NEW.amount
        ) THEN RAISE EXCEPTION 'Payment item must snapshot its finalized assessment during preparation' USING ERRCODE='23514';END IF;
    ELSE
        IF (NEW.company_id,NEW.id,NEW.batch_id,NEW.assessment_id,NEW.employment_id,NEW.employee_number,NEW.employee_name,NEW.tax_month,NEW.planned_payment_date,NEW.amount,NEW.bank_code,NEW.account_number,NEW.account_name) IS DISTINCT FROM
           (OLD.company_id,OLD.id,OLD.batch_id,OLD.assessment_id,OLD.employment_id,OLD.employee_number,OLD.employee_name,OLD.tax_month,OLD.planned_payment_date,OLD.amount,OLD.bank_code,OLD.account_number,OLD.account_name)
           OR NEW.version<>OLD.version+1 OR NEW.batch_version<=OLD.batch_version THEN
            RAISE EXCEPTION 'Payment item instructions are immutable' USING ERRCODE='42501';END IF;
        IF NOT ((OLD.status='PREPARED' AND NEW.status IN ('PENDING','CANCELLED')) OR (OLD.status='PENDING' AND NEW.status IN ('SUCCEEDED','FAILED'))) THEN
            RAISE EXCEPTION 'Payment result cannot be changed or retried in place' USING ERRCODE='23514';END IF;
        IF NOT EXISTS(SELECT 1 FROM payroll_payment_batches b WHERE b.company_id=NEW.company_id AND b.id=NEW.batch_id AND b.version=NEW.batch_version
            AND ((NEW.status='CANCELLED' AND b.status='CANCELLED') OR (NEW.status='PENDING' AND b.status='RELEASED') OR
                 (NEW.status IN ('SUCCEEDED','FAILED') AND b.status IN ('RELEASED','CLOSED') AND NEW.occurred_at>=b.released_at))) THEN
            RAISE EXCEPTION 'Payment item transition must match its batch' USING ERRCODE='23514';END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_payroll_payment_item BEFORE INSERT OR UPDATE ON payroll_payment_items FOR EACH ROW EXECUTE FUNCTION protect_payroll_payment_item();
CREATE FUNCTION validate_payroll_payment_action() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM payroll_payment_batches b WHERE b.company_id=NEW.company_id AND b.id=NEW.batch_id AND b.version=NEW.version AND b.status=NEW.status
        AND ((NEW.kind='PREPARED' AND NEW.version=0 AND b.created_by=NEW.actor_id AND b.created_at=NEW.recorded_at)
          OR (NEW.kind='RELEASED' AND NEW.version=1 AND b.released_by=NEW.actor_id AND b.released_at=NEW.recorded_at)
          OR (NEW.kind='CANCELLED' AND NEW.version=1 AND b.status='CANCELLED')
          OR (NEW.kind='RECONCILED' AND NEW.version>=2 AND b.status IN ('RELEASED','CLOSED')))) THEN
        RAISE EXCEPTION 'Payment action must match its batch transition' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER validate_payroll_payment_action BEFORE INSERT ON payroll_payment_actions FOR EACH ROW EXECUTE FUNCTION validate_payroll_payment_action();
CREATE FUNCTION validate_payroll_payment_result() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM payroll_payment_items i JOIN payroll_payment_batches b ON b.company_id=i.company_id AND b.id=i.batch_id
        WHERE i.company_id=NEW.company_id AND i.id=NEW.item_id AND i.batch_id=NEW.batch_id AND i.batch_version=NEW.batch_version AND b.version=NEW.batch_version
        AND i.status=NEW.status AND i.transaction_reference IS NOT DISTINCT FROM NEW.transaction_reference AND i.occurred_at=NEW.occurred_at AND NEW.occurred_at<=NEW.recorded_at) THEN
        RAISE EXCEPTION 'Reconciliation evidence must match payment result' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER validate_payroll_payment_result BEFORE INSERT ON payroll_payment_results FOR EACH ROW EXECUTE FUNCTION validate_payroll_payment_result();
CREATE FUNCTION complete_payroll_payment_batch() RETURNS trigger LANGUAGE plpgsql AS $$ DECLARE n integer; total numeric; bad integer; BEGIN
    SELECT count(*),coalesce(sum(amount),0),count(*) FILTER(WHERE
      (NEW.status='PREPARED' AND status<>'PREPARED') OR (NEW.status='CANCELLED' AND status<>'CANCELLED') OR
      (NEW.status='RELEASED' AND status NOT IN ('PENDING','SUCCEEDED','FAILED')) OR (NEW.status='CLOSED' AND status NOT IN ('SUCCEEDED','FAILED')))
      INTO n,total,bad FROM payroll_payment_items WHERE company_id=NEW.company_id AND batch_id=NEW.id;
    IF n<>NEW.item_count OR total<>NEW.total_amount OR bad<>0 OR
      (NEW.status='RELEASED' AND NOT EXISTS(SELECT 1 FROM payroll_payment_items WHERE company_id=NEW.company_id AND batch_id=NEW.id AND status='PENDING')) OR
      NOT EXISTS(SELECT 1 FROM payroll_payment_actions WHERE company_id=NEW.company_id AND batch_id=NEW.id AND version=NEW.version AND status=NEW.status) THEN
        RAISE EXCEPTION 'Payment batch must have complete items and transition evidence' USING ERRCODE='23514';END IF;
    IF NEW.version>=2 AND NOT EXISTS(SELECT 1 FROM payroll_payment_results WHERE company_id=NEW.company_id AND batch_id=NEW.id AND batch_version=NEW.version) THEN
        RAISE EXCEPTION 'A reconciliation must contain a new result' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER complete_payroll_payment_batch AFTER INSERT OR UPDATE ON payroll_payment_batches DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION complete_payroll_payment_batch();
CREATE FUNCTION complete_payroll_payment_item() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM payroll_payment_actions a WHERE a.company_id=NEW.company_id AND a.batch_id=NEW.batch_id AND a.version=NEW.batch_version
        AND a.kind=CASE NEW.status WHEN 'PREPARED' THEN 'PREPARED' WHEN 'PENDING' THEN 'RELEASED' WHEN 'CANCELLED' THEN 'CANCELLED' ELSE 'RECONCILED' END) THEN
        RAISE EXCEPTION 'Payment item must have matching action history' USING ERRCODE='23514';END IF;
    IF NEW.status IN ('SUCCEEDED','FAILED') AND NOT EXISTS(SELECT 1 FROM payroll_payment_results r JOIN payroll_payment_actions a ON a.company_id=r.company_id AND a.batch_id=r.batch_id AND a.version=r.batch_version
        WHERE r.company_id=NEW.company_id AND r.item_id=NEW.id AND r.batch_version=NEW.batch_version AND r.status=NEW.status
        AND r.transaction_reference IS NOT DISTINCT FROM NEW.transaction_reference AND r.occurred_at=NEW.occurred_at AND r.actor_id=a.actor_id AND r.recorded_at=a.recorded_at) THEN
        RAISE EXCEPTION 'Payment result must have immutable reconciliation evidence' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER complete_payroll_payment_item AFTER INSERT OR UPDATE ON payroll_payment_items DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION complete_payroll_payment_item();

CREATE INDEX payroll_payable_published ON payroll_assessments(company_id,published_at DESC,id DESC);
CREATE VIEW payroll_payable_candidates WITH (security_invoker=true) AS
    SELECT a.company_id,a.id AS assessment_id,a.employment_id,t.employee_number,t.employee_name,a.tax_month,
           r.planned_payment_date,o.take_home AS amount,a.published_at
    FROM payroll_assessments a
    CROSS JOIN LATERAL(SELECT employee_number,employee_name FROM payroll_run_targets t
        WHERE t.company_id=a.company_id AND t.run_id=a.run_id AND t.ordinal=a.ordinal LIMIT 1) t
    CROSS JOIN LATERAL(SELECT take_home FROM payroll_run_results o
        WHERE o.company_id=a.company_id AND o.run_id=a.run_id AND o.ordinal=a.ordinal LIMIT 1) o
    CROSS JOIN LATERAL(SELECT planned_payment_date FROM payroll_runs r
        WHERE r.company_id=a.company_id AND r.id=a.run_id LIMIT 1) r
    WHERE o.take_home>0;

-- A single external transfer reference cannot settle both a reimbursement and a salary.
-- This immutable reference index contains no bank destination or employee salary data.
CREATE TABLE bank_payment_references (
    company_id uuid NOT NULL REFERENCES companies(id), transaction_reference varchar(100) NOT NULL,
    origin varchar(8) NOT NULL CHECK(origin IN ('EXPENSE','PAYROLL')), item_id uuid NOT NULL,
    expense_item_id uuid GENERATED ALWAYS AS (CASE WHEN origin='EXPENSE' THEN item_id END) STORED,
    payroll_item_id uuid GENERATED ALWAYS AS (CASE WHEN origin='PAYROLL' THEN item_id END) STORED,
    PRIMARY KEY(company_id,transaction_reference), UNIQUE(company_id,origin,item_id),
    FOREIGN KEY(company_id,expense_item_id) REFERENCES expense_payment_items(company_id,id),
    FOREIGN KEY(company_id,payroll_item_id) REFERENCES payroll_payment_items(company_id,id)
);
ALTER TABLE bank_payment_references ENABLE ROW LEVEL SECURITY;
ALTER TABLE bank_payment_references FORCE ROW LEVEL SECURITY;
CREATE POLICY company_scope ON bank_payment_references USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
CREATE TRIGGER bank_payment_reference_immutable BEFORE UPDATE OR DELETE ON bank_payment_references FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE FUNCTION validate_bank_payment_reference() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended('bank-payment-reference:'||NEW.company_id,0));
    IF NOT ((NEW.origin='EXPENSE' AND EXISTS(SELECT 1 FROM expense_payment_items WHERE company_id=NEW.company_id AND id=NEW.item_id AND status='SUCCEEDED' AND transaction_reference=NEW.transaction_reference)) OR
            (NEW.origin='PAYROLL' AND EXISTS(SELECT 1 FROM payroll_payment_items WHERE company_id=NEW.company_id AND id=NEW.item_id AND status='SUCCEEDED' AND transaction_reference=NEW.transaction_reference))) THEN
        RAISE EXCEPTION 'A bank reference must identify a confirmed payment' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER bank_payment_reference_valid BEFORE INSERT ON bank_payment_references FOR EACH ROW EXECUTE FUNCTION validate_bank_payment_reference();
-- Refuse a partial backfill if the migration account cannot read every company.
-- row_security=off does not bypass policies; it raises instead of silently filtering rows.
SET LOCAL row_security=off;
INSERT INTO bank_payment_references(company_id,transaction_reference,origin,item_id)
    SELECT company_id,transaction_reference,'EXPENSE',id FROM expense_payment_items WHERE status='SUCCEEDED';
SET LOCAL row_security=on;
CREATE FUNCTION capture_bank_payment_reference() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NEW.status='SUCCEEDED' THEN
        INSERT INTO bank_payment_references(company_id,transaction_reference,origin,item_id)
        VALUES(NEW.company_id,NEW.transaction_reference,CASE TG_TABLE_NAME WHEN 'expense_payment_items' THEN 'EXPENSE' ELSE 'PAYROLL' END,NEW.id);
    END IF;
    RETURN NULL;
END $$;
CREATE TRIGGER expense_bank_reference AFTER UPDATE ON expense_payment_items FOR EACH ROW EXECUTE FUNCTION capture_bank_payment_reference();
CREATE TRIGGER payroll_bank_reference AFTER UPDATE ON payroll_payment_items FOR EACH ROW EXECUTE FUNCTION capture_bank_payment_reference();
CREATE FUNCTION require_bank_payment_reference() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NEW.status='SUCCEEDED' AND NOT EXISTS(SELECT 1 FROM bank_payment_references WHERE company_id=NEW.company_id AND transaction_reference=NEW.transaction_reference AND item_id=NEW.id AND origin=CASE TG_TABLE_NAME WHEN 'expense_payment_items' THEN 'EXPENSE' ELSE 'PAYROLL' END) THEN
        RAISE EXCEPTION 'Confirmed payment is missing its bank reference' USING ERRCODE='23514';END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER expense_bank_reference_complete AFTER UPDATE ON expense_payment_items DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_bank_payment_reference();
CREATE CONSTRAINT TRIGGER payroll_bank_reference_complete AFTER UPDATE ON payroll_payment_items DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_bank_payment_reference();

-- Monotonic companion projection for payment progress; the original payslip remains version zero.
CREATE TABLE payroll_payment_heads (
    company_id uuid NOT NULL, assessment_id uuid NOT NULL, employment_id uuid NOT NULL,
    version bigint NOT NULL CHECK(version BETWEEN 1 AND 60), PRIMARY KEY(company_id,assessment_id),
    FOREIGN KEY(company_id,assessment_id) REFERENCES payroll_assessments(company_id,id)
);
CREATE INDEX payroll_payment_head_owner ON payroll_payment_heads(company_id,employment_id,assessment_id);
ALTER TABLE payroll_payment_heads ENABLE ROW LEVEL SECURITY;
ALTER TABLE payroll_payment_heads FORCE ROW LEVEL SECURITY;
CREATE POLICY company_scope ON payroll_payment_heads USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
CREATE TRIGGER payroll_payment_head_retained BEFORE DELETE ON payroll_payment_heads FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE FUNCTION validate_payroll_payment_head() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='UPDATE' AND ((NEW.company_id,NEW.assessment_id,NEW.employment_id) IS DISTINCT FROM (OLD.company_id,OLD.assessment_id,OLD.employment_id) OR NEW.version<>OLD.version+1) THEN
        RAISE EXCEPTION 'Payment progress must advance without changing its owner' USING ERRCODE='23514';END IF;
    IF NEW.version IS DISTINCT FROM (SELECT coalesce(sum(version+1),0) FROM payroll_payment_items WHERE company_id=NEW.company_id AND assessment_id=NEW.assessment_id) OR
       NOT EXISTS(SELECT 1 FROM payroll_assessments WHERE company_id=NEW.company_id AND id=NEW.assessment_id AND employment_id=NEW.employment_id) THEN
        RAISE EXCEPTION 'Payment progress must match its retained instructions' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER payroll_payment_head_valid BEFORE INSERT OR UPDATE ON payroll_payment_heads FOR EACH ROW EXECUTE FUNCTION validate_payroll_payment_head();
CREATE FUNCTION advance_payroll_payment_head() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    INSERT INTO payroll_payment_heads(company_id,assessment_id,employment_id,version) VALUES(NEW.company_id,NEW.assessment_id,NEW.employment_id,(SELECT sum(version+1) FROM payroll_payment_items WHERE company_id=NEW.company_id AND assessment_id=NEW.assessment_id))
        ON CONFLICT(company_id,assessment_id) DO UPDATE SET version=EXCLUDED.version;
    RETURN NULL;
END $$;
CREATE TRIGGER payroll_payment_head_advance AFTER INSERT OR UPDATE ON payroll_payment_items FOR EACH ROW EXECUTE FUNCTION advance_payroll_payment_head();
CREATE FUNCTION require_payroll_payment_head() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM payroll_payment_heads h WHERE h.company_id=NEW.company_id AND h.assessment_id=NEW.assessment_id AND h.employment_id=NEW.employment_id
        AND h.version=(SELECT sum(version+1) FROM payroll_payment_items WHERE company_id=NEW.company_id AND assessment_id=NEW.assessment_id)) THEN
        RAISE EXCEPTION 'Payment progress is missing or stale' USING ERRCODE='23514';END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER payroll_payment_head_complete AFTER INSERT OR UPDATE ON payroll_payment_items DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_payroll_payment_head();
ALTER TABLE mobile_sync_changes DROP CONSTRAINT mobile_sync_changes_collection_check;
ALTER TABLE mobile_sync_changes ADD CONSTRAINT mobile_sync_changes_collection_check CHECK(collection IN ('EXPENSE_CLAIMS','LEAVE_REQUESTS','OVERTIME_REQUESTS','LEAVE_BALANCES','PAYSLIPS','PAYROLL_PAYMENTS'));
CREATE FUNCTION record_payroll_payment_sync_change() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    INSERT INTO mobile_sync_changes(company_id,collection,resource_id,employment_id,resource_version,operation)
    VALUES(NEW.company_id,'PAYROLL_PAYMENTS',NEW.assessment_id,NEW.employment_id,NEW.version,'UPSERT');
    RETURN NULL;
END $$;
CREATE TRIGGER payroll_payment_sync_change AFTER INSERT OR UPDATE ON payroll_payment_heads FOR EACH ROW EXECUTE FUNCTION record_payroll_payment_sync_change();
