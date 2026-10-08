CREATE TABLE expense_payment_batches (
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
CREATE TABLE expense_payment_items (
    company_id uuid NOT NULL, id uuid NOT NULL, batch_id uuid NOT NULL, claim_id uuid NOT NULL, submission_id uuid NOT NULL,
    employment_id uuid NOT NULL, employee_number varchar(32) NOT NULL, employee_name varchar(200) NOT NULL,
    amount numeric(14,2) NOT NULL CHECK(amount>0), bank_code varchar(12) NOT NULL CHECK(bank_code ~ '^[A-Z0-9]{2,12}$'),
    account_number varchar(34) NOT NULL CHECK(account_number ~ '^[0-9]{6,34}$'), account_name varchar(120) NOT NULL CHECK(length(trim(account_name))>0 AND account_name !~ '[[:cntrl:]]'),
    status varchar(12) NOT NULL CHECK(status IN ('PREPARED','PENDING','SUCCEEDED','FAILED','CANCELLED')),
    version integer NOT NULL CHECK(version BETWEEN 0 AND 2), batch_version bigint NOT NULL CHECK(batch_version BETWEEN 0 AND 101),
    transaction_reference varchar(100), occurred_at timestamptz,
    PRIMARY KEY(company_id,id), UNIQUE(company_id,batch_id,claim_id), UNIQUE(company_id,batch_id,id),
    FOREIGN KEY(company_id,batch_id) REFERENCES expense_payment_batches(company_id,id),
    FOREIGN KEY(company_id,claim_id,submission_id) REFERENCES expense_submissions(company_id,claim_id,id),
    FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id),
    CHECK((status='SUCCEEDED' AND transaction_reference IS NOT NULL AND transaction_reference ~ '^[A-Za-z0-9][A-Za-z0-9._:/ -]{0,99}$' AND occurred_at IS NOT NULL) OR
          (status='FAILED' AND transaction_reference IS NULL AND occurred_at IS NOT NULL) OR
          (status IN ('PREPARED','PENDING','CANCELLED') AND transaction_reference IS NULL AND occurred_at IS NULL))
);
CREATE UNIQUE INDEX expense_payment_claim_reservation ON expense_payment_items(company_id,claim_id) WHERE status IN ('PREPARED','PENDING','SUCCEEDED');
CREATE UNIQUE INDEX expense_payment_transaction_reference ON expense_payment_items(company_id,transaction_reference) WHERE status='SUCCEEDED';
CREATE INDEX expense_payment_claim_history ON expense_payment_items(company_id,claim_id);
CREATE INDEX expense_payment_batch_cursor ON expense_payment_batches(company_id,created_at DESC,id DESC);
CREATE INDEX expense_payment_batch_open ON expense_payment_batches(company_id) WHERE status IN ('PREPARED','RELEASED');
CREATE TABLE expense_payment_actions (
    company_id uuid NOT NULL, batch_id uuid NOT NULL, version bigint NOT NULL CHECK(version BETWEEN 0 AND 101),
    kind varchar(12) NOT NULL CHECK(kind IN ('PREPARED','RELEASED','CANCELLED','RECONCILED')),
    status varchar(12) NOT NULL CHECK(status IN ('PREPARED','RELEASED','CLOSED','CANCELLED')),
    actor_id uuid NOT NULL REFERENCES accounts(id), reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0), recorded_at timestamptz NOT NULL,
    PRIMARY KEY(company_id,batch_id,version), FOREIGN KEY(company_id,batch_id) REFERENCES expense_payment_batches(company_id,id)
);
ALTER TABLE expense_payment_items ADD FOREIGN KEY(company_id,batch_id,batch_version)
    REFERENCES expense_payment_actions(company_id,batch_id,version) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE expense_payment_results (
    company_id uuid NOT NULL, batch_id uuid NOT NULL, item_id uuid NOT NULL, batch_version bigint NOT NULL,
    status varchar(12) NOT NULL CHECK(status IN ('SUCCEEDED','FAILED')), transaction_reference varchar(100), occurred_at timestamptz NOT NULL,
    actor_id uuid NOT NULL REFERENCES accounts(id), reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0), recorded_at timestamptz NOT NULL,
    PRIMARY KEY(company_id,item_id),
    FOREIGN KEY(company_id,batch_id,item_id) REFERENCES expense_payment_items(company_id,batch_id,id),
    FOREIGN KEY(company_id,batch_id,batch_version) REFERENCES expense_payment_actions(company_id,batch_id,version) DEFERRABLE INITIALLY DEFERRED,
    CHECK((status='SUCCEEDED' AND transaction_reference IS NOT NULL AND transaction_reference ~ '^[A-Za-z0-9][A-Za-z0-9._:/ -]{0,99}$') OR (status='FAILED' AND transaction_reference IS NULL))
);
DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['expense_payment_batches','expense_payment_items','expense_payment_actions','expense_payment_results'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
        EXECUTE format('CREATE TRIGGER deny_expense_payment_delete BEFORE DELETE ON %I FOR EACH ROW EXECUTE FUNCTION deny_history_mutation()',t);
    END LOOP;
END $$;
CREATE POLICY expense_payment_author ON expense_payment_batches AS RESTRICTIVE FOR INSERT TO PUBLIC WITH CHECK(created_by=current_actor_id());
CREATE POLICY expense_payment_action_actor ON expense_payment_actions AS RESTRICTIVE FOR INSERT TO PUBLIC WITH CHECK(actor_id=current_actor_id());
CREATE POLICY expense_payment_result_actor ON expense_payment_results AS RESTRICTIVE FOR INSERT TO PUBLIC WITH CHECK(actor_id=current_actor_id());
CREATE TRIGGER immutable_expense_payment_action BEFORE UPDATE ON expense_payment_actions FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE TRIGGER immutable_expense_payment_result BEFORE UPDATE ON expense_payment_results FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE FUNCTION protect_expense_payment_batch() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
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
CREATE TRIGGER protect_expense_payment_batch BEFORE INSERT OR UPDATE ON expense_payment_batches FOR EACH ROW EXECUTE FUNCTION protect_expense_payment_batch();
CREATE FUNCTION protect_expense_payment_item() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.status<>'PREPARED' OR NEW.version<>0 OR NEW.batch_version<>0 OR NOT EXISTS(
            SELECT 1 FROM expense_payment_batches b JOIN expense_claims c ON c.company_id=b.company_id AND c.id=NEW.claim_id
            JOIN expense_submissions s ON s.company_id=c.company_id AND s.id=c.latest_submission_id
            JOIN expense_drafts d ON d.company_id=s.company_id AND d.claim_id=s.claim_id AND d.revision=s.draft_revision
            WHERE b.company_id=NEW.company_id AND b.id=NEW.batch_id AND b.status='PREPARED' AND b.write_xid=pg_current_xact_id()
            AND c.status='APPROVED' AND s.id=NEW.submission_id AND c.employment_id=NEW.employment_id
            AND s.employee_number=NEW.employee_number AND s.employee_name=NEW.employee_name AND d.total_amount=NEW.amount
        ) THEN RAISE EXCEPTION 'Payment item must snapshot its approved claim during preparation' USING ERRCODE='23514';END IF;
    ELSE
        IF (NEW.company_id,NEW.id,NEW.batch_id,NEW.claim_id,NEW.submission_id,NEW.employment_id,NEW.employee_number,NEW.employee_name,NEW.amount,NEW.bank_code,NEW.account_number,NEW.account_name) IS DISTINCT FROM
           (OLD.company_id,OLD.id,OLD.batch_id,OLD.claim_id,OLD.submission_id,OLD.employment_id,OLD.employee_number,OLD.employee_name,OLD.amount,OLD.bank_code,OLD.account_number,OLD.account_name)
           OR NEW.version<>OLD.version+1 OR NEW.batch_version<=OLD.batch_version THEN
            RAISE EXCEPTION 'Payment item instructions are immutable' USING ERRCODE='42501';END IF;
        IF NOT ((OLD.status='PREPARED' AND NEW.status IN ('PENDING','CANCELLED')) OR (OLD.status='PENDING' AND NEW.status IN ('SUCCEEDED','FAILED'))) THEN
            RAISE EXCEPTION 'Payment result cannot be changed or retried in place' USING ERRCODE='23514';END IF;
        IF NOT EXISTS(SELECT 1 FROM expense_payment_batches b WHERE b.company_id=NEW.company_id AND b.id=NEW.batch_id AND b.version=NEW.batch_version
            AND ((NEW.status='CANCELLED' AND b.status='CANCELLED') OR (NEW.status='PENDING' AND b.status='RELEASED') OR
                 (NEW.status IN ('SUCCEEDED','FAILED') AND b.status IN ('RELEASED','CLOSED') AND NEW.occurred_at>=b.released_at))) THEN
            RAISE EXCEPTION 'Payment item transition must match its batch' USING ERRCODE='23514';END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_expense_payment_item BEFORE INSERT OR UPDATE ON expense_payment_items FOR EACH ROW EXECUTE FUNCTION protect_expense_payment_item();
CREATE FUNCTION validate_expense_payment_action() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM expense_payment_batches b WHERE b.company_id=NEW.company_id AND b.id=NEW.batch_id AND b.version=NEW.version AND b.status=NEW.status
        AND ((NEW.kind='PREPARED' AND NEW.version=0 AND b.created_by=NEW.actor_id AND b.created_at=NEW.recorded_at)
          OR (NEW.kind='RELEASED' AND NEW.version=1 AND b.released_by=NEW.actor_id AND b.released_at=NEW.recorded_at)
          OR (NEW.kind='CANCELLED' AND NEW.version=1 AND b.status='CANCELLED')
          OR (NEW.kind='RECONCILED' AND NEW.version>=2 AND b.status IN ('RELEASED','CLOSED')))) THEN
        RAISE EXCEPTION 'Payment action must match its batch transition' USING ERRCODE='23514';END IF;
    IF NEW.kind<>'CANCELLED' AND EXISTS(
        SELECT 1 FROM expense_payment_items i JOIN expense_submissions s ON s.company_id=i.company_id AND s.id=i.submission_id
        JOIN employments e ON e.company_id=i.company_id AND e.id=i.employment_id JOIN persons p ON p.id=e.person_id
        WHERE i.company_id=NEW.company_id AND i.batch_id=NEW.batch_id AND
          (NEW.actor_id=ANY(s.maker_ids) OR NEW.actor_id=s.requester_id OR NEW.actor_id=p.account_id)) THEN
        RAISE EXCEPTION 'Payment actor must be independent from claimant and makers' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER validate_expense_payment_action BEFORE INSERT ON expense_payment_actions FOR EACH ROW EXECUTE FUNCTION validate_expense_payment_action();
CREATE FUNCTION validate_expense_payment_result() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM expense_payment_items i JOIN expense_payment_batches b ON b.company_id=i.company_id AND b.id=i.batch_id
        WHERE i.company_id=NEW.company_id AND i.id=NEW.item_id AND i.batch_id=NEW.batch_id AND i.batch_version=NEW.batch_version AND b.version=NEW.batch_version
        AND i.status=NEW.status AND i.transaction_reference IS NOT DISTINCT FROM NEW.transaction_reference AND i.occurred_at=NEW.occurred_at AND NEW.occurred_at<=NEW.recorded_at) THEN
        RAISE EXCEPTION 'Reconciliation evidence must match payment result' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER validate_expense_payment_result BEFORE INSERT ON expense_payment_results FOR EACH ROW EXECUTE FUNCTION validate_expense_payment_result();
CREATE FUNCTION complete_expense_payment_batch() RETURNS trigger LANGUAGE plpgsql AS $$ DECLARE n integer; total numeric; bad integer; BEGIN
    SELECT count(*),coalesce(sum(amount),0),count(*) FILTER(WHERE
      (NEW.status='PREPARED' AND status<>'PREPARED') OR (NEW.status='CANCELLED' AND status<>'CANCELLED') OR
      (NEW.status='RELEASED' AND status NOT IN ('PENDING','SUCCEEDED','FAILED')) OR (NEW.status='CLOSED' AND status NOT IN ('SUCCEEDED','FAILED')))
      INTO n,total,bad FROM expense_payment_items WHERE company_id=NEW.company_id AND batch_id=NEW.id;
    IF n<>NEW.item_count OR total<>NEW.total_amount OR bad<>0 OR
      (NEW.status='RELEASED' AND NOT EXISTS(SELECT 1 FROM expense_payment_items WHERE company_id=NEW.company_id AND batch_id=NEW.id AND status='PENDING')) OR
      NOT EXISTS(SELECT 1 FROM expense_payment_actions WHERE company_id=NEW.company_id AND batch_id=NEW.id AND version=NEW.version AND status=NEW.status) THEN
        RAISE EXCEPTION 'Payment batch must have complete items and transition evidence' USING ERRCODE='23514';END IF;
    IF NEW.status<>'CANCELLED' AND EXISTS(
        SELECT 1 FROM expense_payment_actions a JOIN expense_payment_items i ON i.company_id=a.company_id AND i.batch_id=a.batch_id
        JOIN expense_submissions s ON s.company_id=i.company_id AND s.id=i.submission_id
        JOIN employments e ON e.company_id=i.company_id AND e.id=i.employment_id JOIN persons p ON p.id=e.person_id
        WHERE a.company_id=NEW.company_id AND a.batch_id=NEW.id AND a.version=NEW.version AND
          (a.actor_id=ANY(s.maker_ids) OR a.actor_id=s.requester_id OR a.actor_id=p.account_id)) THEN
        RAISE EXCEPTION 'Payment action must remain independent after all items are inserted' USING ERRCODE='23514';END IF;
    IF NEW.version>=2 AND NOT EXISTS(SELECT 1 FROM expense_payment_results WHERE company_id=NEW.company_id AND batch_id=NEW.id AND batch_version=NEW.version) THEN
        RAISE EXCEPTION 'A reconciliation must contain a new result' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER complete_expense_payment_batch AFTER INSERT OR UPDATE ON expense_payment_batches DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION complete_expense_payment_batch();
CREATE FUNCTION complete_expense_payment_item() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM expense_payment_actions a WHERE a.company_id=NEW.company_id AND a.batch_id=NEW.batch_id AND a.version=NEW.batch_version
        AND a.kind=CASE NEW.status WHEN 'PREPARED' THEN 'PREPARED' WHEN 'PENDING' THEN 'RELEASED' WHEN 'CANCELLED' THEN 'CANCELLED' ELSE 'RECONCILED' END) THEN
        RAISE EXCEPTION 'Payment item must have matching action history' USING ERRCODE='23514';END IF;
    IF NEW.status IN ('SUCCEEDED','FAILED') AND NOT EXISTS(SELECT 1 FROM expense_payment_results r JOIN expense_payment_actions a ON a.company_id=r.company_id AND a.batch_id=r.batch_id AND a.version=r.batch_version
        WHERE r.company_id=NEW.company_id AND r.item_id=NEW.id AND r.batch_version=NEW.batch_version AND r.status=NEW.status
        AND r.transaction_reference IS NOT DISTINCT FROM NEW.transaction_reference AND r.occurred_at=NEW.occurred_at AND r.actor_id=a.actor_id AND r.recorded_at=a.recorded_at) THEN
        RAISE EXCEPTION 'Payment result must have immutable reconciliation evidence' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER complete_expense_payment_item AFTER INSERT OR UPDATE ON expense_payment_items DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION complete_expense_payment_item();
CREATE INDEX expense_review_approved_cursor ON expense_reviews(company_id,decided_at DESC,claim_id DESC) WHERE resulting_status='APPROVED';
CREATE VIEW expense_payable_candidates WITH (security_invoker=true) AS
    SELECT c.company_id,c.id AS claim_id,s.id AS submission_id,c.employment_id,s.employee_number,s.employee_name,d.title,d.total_amount AS amount,
           r.decided_at AS approved_at,s.maker_ids,s.requester_id,p.account_id AS current_account_id
    FROM expense_claims c JOIN expense_submissions s ON s.company_id=c.company_id AND s.id=c.latest_submission_id
    JOIN expense_drafts d ON d.company_id=s.company_id AND d.claim_id=s.claim_id AND d.revision=s.draft_revision
    JOIN expense_reviews r ON r.company_id=s.company_id AND r.submission_id=s.id AND r.resulting_status='APPROVED'
    JOIN employments e ON e.company_id=c.company_id AND e.id=c.employment_id JOIN persons p ON p.id=e.person_id
    WHERE c.status='APPROVED';
