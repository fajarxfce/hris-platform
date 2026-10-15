-- A recovery records a confirmed incoming bank amount. It is never a negative payment.
-- Conflicting schema work aborts atomically; operators drain API/worker writes for upgrades.
SET LOCAL lock_timeout = '5s';

CREATE TABLE payroll_payment_recoveries (
 company_id uuid NOT NULL,id uuid NOT NULL,assessment_id uuid NOT NULL,
 assessment_revision bigint NOT NULL CHECK(assessment_revision>=0),payment_version bigint NOT NULL CHECK(payment_version>=0),
 amount numeric(14,2) NOT NULL CHECK(amount>0),transaction_reference varchar(100) NOT NULL CHECK(transaction_reference ~ '^[A-Za-z0-9][A-Za-z0-9._:/ -]{0,99}$'),
 occurred_at timestamptz NOT NULL,recorded_at timestamptz NOT NULL CHECK(occurred_at<=recorded_at),
 actor_id uuid NOT NULL REFERENCES accounts(id),reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
 PRIMARY KEY(company_id,id),UNIQUE(company_id,transaction_reference),
 FOREIGN KEY(company_id,assessment_id,assessment_revision) REFERENCES payroll_assessment_revisions(company_id,assessment_id,revision)
);
CREATE INDEX payroll_payment_recovery_assessment ON payroll_payment_recoveries(company_id,assessment_id);
ALTER TABLE payroll_payment_recoveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE payroll_payment_recoveries FORCE ROW LEVEL SECURITY;
CREATE POLICY company_scope ON payroll_payment_recoveries USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
CREATE POLICY payroll_recovery_author ON payroll_payment_recoveries AS RESTRICTIVE FOR INSERT WITH CHECK(actor_id=current_actor_id());
CREATE TRIGGER payroll_payment_recovery_immutable BEFORE UPDATE OR DELETE ON payroll_payment_recoveries FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

-- The companion revision includes every change that affects employee payment progress.
ALTER TABLE payroll_payment_heads DROP CONSTRAINT payroll_payment_heads_version_check;
ALTER TABLE payroll_payment_heads ADD CHECK(version>=1 AND version<9223372036854775807);
CREATE FUNCTION payroll_payment_progress_version(p_company uuid,p_assessment uuid) RETURNS bigint LANGUAGE sql STABLE AS $$
 SELECT h.revision + coalesce((SELECT sum(i.version+1) FROM payroll_payment_items i WHERE i.company_id=p_company AND i.assessment_id=p_assessment),0)::bigint
  + (SELECT count(*) FROM payroll_payment_recoveries r WHERE r.company_id=p_company AND r.assessment_id=p_assessment)
 FROM payroll_assessment_heads h WHERE h.company_id=p_company AND h.assessment_id=p_assessment
$$;
CREATE OR REPLACE FUNCTION validate_payroll_payment_head() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='UPDATE' AND ((NEW.company_id,NEW.assessment_id,NEW.employment_id) IS DISTINCT FROM (OLD.company_id,OLD.assessment_id,OLD.employment_id) OR NEW.version<>OLD.version+1)
  THEN RAISE EXCEPTION 'Payment progress must advance without changing its owner' USING ERRCODE='23514'; END IF;
 IF NEW.version IS DISTINCT FROM payroll_payment_progress_version(NEW.company_id,NEW.assessment_id) OR
  NOT EXISTS(SELECT 1 FROM payroll_assessments WHERE company_id=NEW.company_id AND id=NEW.assessment_id AND employment_id=NEW.employment_id)
  THEN RAISE EXCEPTION 'Payment progress must match its retained assessment and financial evidence' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE OR REPLACE FUNCTION advance_payroll_payment_head() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 INSERT INTO payroll_payment_heads(company_id,assessment_id,employment_id,version)
  SELECT NEW.company_id,NEW.assessment_id,a.employment_id,payroll_payment_progress_version(NEW.company_id,NEW.assessment_id)
  FROM payroll_assessments a WHERE a.company_id=NEW.company_id AND a.id=NEW.assessment_id
  ON CONFLICT(company_id,assessment_id) DO UPDATE SET version=EXCLUDED.version;
 RETURN NULL;
END $$;
CREATE OR REPLACE FUNCTION require_payroll_payment_head() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM payroll_payment_heads h WHERE h.company_id=NEW.company_id AND h.assessment_id=NEW.assessment_id
  AND h.version=payroll_payment_progress_version(NEW.company_id,NEW.assessment_id))
  THEN RAISE EXCEPTION 'Payment progress is missing or stale' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE TRIGGER payroll_recovery_head_advance AFTER INSERT ON payroll_payment_recoveries FOR EACH ROW EXECUTE FUNCTION advance_payroll_payment_head();
CREATE CONSTRAINT TRIGGER payroll_recovery_head_complete AFTER INSERT ON payroll_payment_recoveries
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_payroll_payment_head();
CREATE TRIGGER payroll_assessment_payment_head_advance AFTER UPDATE ON payroll_assessment_heads FOR EACH ROW EXECUTE FUNCTION advance_payroll_payment_head();
CREATE CONSTRAINT TRIGGER payroll_assessment_payment_head_complete AFTER UPDATE ON payroll_assessment_heads
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_payroll_payment_head();
CREATE FUNCTION record_revised_payslip_sync_change() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 INSERT INTO mobile_sync_changes(company_id,collection,resource_id,employment_id,resource_version,operation)
  SELECT NEW.company_id,'PAYSLIPS',NEW.assessment_id,a.employment_id,NEW.revision,'UPSERT'
  FROM payroll_assessments a WHERE a.company_id=NEW.company_id AND a.id=NEW.assessment_id;
 RETURN NULL;
END $$;
CREATE TRIGGER payroll_assessment_revision_sync AFTER UPDATE ON payroll_assessment_heads FOR EACH ROW EXECUTE FUNCTION record_revised_payslip_sync_change();

CREATE VIEW payroll_settlement_balances WITH(security_invoker=true) AS
 SELECT a.company_id,a.id AS assessment_id,a.employment_id,h.revision AS assessment_revision,coalesce(progress.version,0) AS payment_version,
  (e.calculation->'tax'->>'takeHome')::numeric(16,2) AS entitlement,paid.amount AS paid_amount,recovered.amount AS recovered_amount,
  ((e.calculation->'tax'->>'takeHome')::numeric(16,2)-paid.amount+recovered.amount)::numeric(16,2) AS balance,e.published_at
 FROM payroll_assessments a JOIN payroll_assessment_heads h ON h.company_id=a.company_id AND h.assessment_id=a.id
 JOIN payroll_assessment_evidence e ON e.company_id=h.company_id AND e.assessment_id=h.assessment_id AND e.revision=h.revision
 LEFT JOIN payroll_payment_heads progress ON progress.company_id=a.company_id AND progress.assessment_id=a.id
 CROSS JOIN LATERAL(SELECT coalesce(sum(amount),0)::numeric(16,2) AS amount FROM payroll_payment_items i
  WHERE i.company_id=a.company_id AND i.assessment_id=a.id AND i.status='SUCCEEDED') paid
 CROSS JOIN LATERAL(SELECT coalesce(sum(amount),0)::numeric(16,2) AS amount FROM payroll_payment_recoveries r
  WHERE r.company_id=a.company_id AND r.assessment_id=a.id) recovered;
CREATE OR REPLACE VIEW payroll_payable_candidates WITH(security_invoker=true) AS
 SELECT a.company_id,a.id AS assessment_id,a.employment_id,t.employee_number,t.employee_name,a.tax_month,
  r.planned_payment_date,s.balance AS amount,s.published_at,s.assessment_revision,s.payment_version,s.entitlement,s.paid_amount,s.recovered_amount
 FROM payroll_assessments a
 JOIN payroll_settlement_balances s ON s.company_id=a.company_id AND s.assessment_id=a.id
 CROSS JOIN LATERAL(SELECT employee_number,employee_name FROM payroll_run_targets t WHERE t.company_id=a.company_id AND t.run_id=a.run_id AND t.ordinal=a.ordinal LIMIT 1) t
 CROSS JOIN LATERAL(SELECT planned_payment_date FROM payroll_runs r WHERE r.company_id=a.company_id AND r.id=a.run_id LIMIT 1) r;

-- Existing instructions retain revision zero and an unknown observation. Every new
-- instruction must explicitly snapshot its current assessment and payment version.
ALTER TABLE payroll_payment_items ADD COLUMN assessment_revision bigint NOT NULL DEFAULT 0 CHECK(assessment_revision>=0);
ALTER TABLE payroll_payment_items ADD COLUMN observed_payment_version bigint CHECK(observed_payment_version>=0);
ALTER TABLE payroll_payment_items ADD FOREIGN KEY(company_id,assessment_id,assessment_revision)
 REFERENCES payroll_assessment_revisions(company_id,assessment_id,revision);
DROP INDEX payroll_payment_assessment_reservation;
CREATE UNIQUE INDEX payroll_payment_assessment_reservation ON payroll_payment_items(company_id,assessment_id) WHERE status IN('PREPARED','PENDING');

CREATE OR REPLACE FUNCTION protect_payroll_payment_item() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='INSERT' THEN
  PERFORM pg_advisory_xact_lock(hashtextextended('payroll-payments:'||NEW.company_id::text,0));
  IF NEW.status<>'PREPARED' OR NEW.version<>0 OR NEW.batch_version<>0 OR NEW.observed_payment_version IS NULL OR
   (SELECT count(*) FROM payroll_payment_items WHERE company_id=NEW.company_id AND assessment_id=NEW.assessment_id)>=20 OR NOT EXISTS(
    SELECT 1 FROM payroll_payment_batches b JOIN payroll_payable_candidates a ON a.company_id=b.company_id AND a.assessment_id=NEW.assessment_id
    WHERE b.company_id=NEW.company_id AND b.id=NEW.batch_id AND b.status='PREPARED' AND b.write_xid=pg_current_xact_id() AND
     (a.employment_id,a.employee_number,a.employee_name,a.tax_month,a.planned_payment_date,a.amount,a.assessment_revision,a.payment_version)
      IS NOT DISTINCT FROM (NEW.employment_id,NEW.employee_number,NEW.employee_name,NEW.tax_month,NEW.planned_payment_date,NEW.amount,NEW.assessment_revision,NEW.observed_payment_version))
   THEN RAISE EXCEPTION 'Payment item must snapshot its current positive balance and observed revisions' USING ERRCODE='23514'; END IF;
 ELSE
  IF (to_jsonb(NEW)-ARRAY['status','version','batch_version','transaction_reference','occurred_at']) IS DISTINCT FROM
     (to_jsonb(OLD)-ARRAY['status','version','batch_version','transaction_reference','occurred_at']) OR NEW.version<>OLD.version+1 OR NEW.batch_version<=OLD.batch_version
   THEN RAISE EXCEPTION 'Payment item instructions are immutable' USING ERRCODE='42501'; END IF;
  IF NOT ((OLD.status='PREPARED' AND NEW.status IN('PENDING','CANCELLED')) OR (OLD.status='PENDING' AND NEW.status IN('SUCCEEDED','FAILED')))
   THEN RAISE EXCEPTION 'Payment result cannot be changed or retried in place' USING ERRCODE='23514'; END IF;
  IF NOT EXISTS(SELECT 1 FROM payroll_payment_batches b WHERE b.company_id=NEW.company_id AND b.id=NEW.batch_id AND b.version=NEW.batch_version
   AND ((NEW.status='CANCELLED' AND b.status='CANCELLED') OR (NEW.status='PENDING' AND b.status='RELEASED') OR
        (NEW.status IN('SUCCEEDED','FAILED') AND b.status IN('RELEASED','CLOSED') AND NEW.occurred_at>=b.released_at)))
   THEN RAISE EXCEPTION 'Payment item transition must match its batch' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;

CREATE FUNCTION validate_payroll_payment_recovery() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s payroll_settlement_balances; latest_payment timestamptz;
BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended('payroll-payments:'||NEW.company_id::text,0));
 SELECT * INTO s FROM payroll_settlement_balances WHERE company_id=NEW.company_id AND assessment_id=NEW.assessment_id;
 SELECT max(occurred_at) INTO latest_payment FROM payroll_payment_items WHERE company_id=NEW.company_id AND assessment_id=NEW.assessment_id AND status='SUCCEEDED';
 IF s.assessment_id IS NULL OR NEW.assessment_revision<>s.assessment_revision OR NEW.payment_version<>s.payment_version OR
  s.balance>=0 OR NEW.amount>-s.balance OR NEW.occurred_at<latest_payment OR NEW.occurred_at<s.published_at OR
  (SELECT count(*) FROM payroll_payment_recoveries WHERE company_id=NEW.company_id AND assessment_id=NEW.assessment_id)>=20 OR
  EXISTS(SELECT 1 FROM payroll_payment_items WHERE company_id=NEW.company_id AND assessment_id=NEW.assessment_id AND status IN('PREPARED','PENDING'))
  THEN RAISE EXCEPTION 'Recovery requires the observed outstanding overpayment and confirmed bank evidence' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_payment_recovery_valid BEFORE INSERT ON payroll_payment_recoveries FOR EACH ROW EXECUTE FUNCTION validate_payroll_payment_recovery();

ALTER TABLE bank_payment_references DROP CONSTRAINT bank_payment_references_origin_check;
ALTER TABLE bank_payment_references ADD CHECK(origin IN('EXPENSE','PAYROLL','RECOVERY'));
ALTER TABLE bank_payment_references ADD COLUMN payroll_recovery_id uuid GENERATED ALWAYS AS(CASE WHEN origin='RECOVERY' THEN item_id END) STORED;
ALTER TABLE bank_payment_references ADD FOREIGN KEY(company_id,payroll_recovery_id) REFERENCES payroll_payment_recoveries(company_id,id);
CREATE OR REPLACE FUNCTION validate_bank_payment_reference() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended('bank-payment-reference:'||NEW.company_id,0));
 IF NOT ((NEW.origin='EXPENSE' AND EXISTS(SELECT 1 FROM expense_payment_items WHERE company_id=NEW.company_id AND id=NEW.item_id AND status='SUCCEEDED' AND transaction_reference=NEW.transaction_reference)) OR
  (NEW.origin='PAYROLL' AND EXISTS(SELECT 1 FROM payroll_payment_items WHERE company_id=NEW.company_id AND id=NEW.item_id AND status='SUCCEEDED' AND transaction_reference=NEW.transaction_reference)) OR
  (NEW.origin='RECOVERY' AND EXISTS(SELECT 1 FROM payroll_payment_recoveries WHERE company_id=NEW.company_id AND id=NEW.item_id AND transaction_reference=NEW.transaction_reference)))
  THEN RAISE EXCEPTION 'A bank reference must identify confirmed financial evidence' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE FUNCTION capture_payroll_recovery_bank_reference() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 INSERT INTO bank_payment_references(company_id,transaction_reference,origin,item_id)
 VALUES(NEW.company_id,NEW.transaction_reference,'RECOVERY',NEW.id);
 RETURN NULL;
END $$;
CREATE TRIGGER payroll_recovery_bank_reference AFTER INSERT ON payroll_payment_recoveries FOR EACH ROW EXECUTE FUNCTION capture_payroll_recovery_bank_reference();
CREATE FUNCTION require_payroll_recovery_bank_reference() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM bank_payment_references WHERE company_id=NEW.company_id AND item_id=NEW.id AND origin='RECOVERY' AND transaction_reference=NEW.transaction_reference)
  THEN RAISE EXCEPTION 'A recovery requires its unique confirmed bank reference' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER payroll_recovery_bank_reference_complete AFTER INSERT ON payroll_payment_recoveries
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_payroll_recovery_bank_reference();
