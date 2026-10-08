CREATE TABLE expense_claims (
    company_id uuid NOT NULL REFERENCES companies(id), id uuid NOT NULL, employment_id uuid NOT NULL,
    created_by uuid NOT NULL REFERENCES accounts(id), created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK(version>=0), draft_revision integer NOT NULL DEFAULT 0 CHECK(draft_revision BETWEEN 0 AND 99),
    status varchar(24) NOT NULL CHECK(status IN ('DRAFT','CANCELLED')),
    PRIMARY KEY(company_id,id), FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id)
);
CREATE INDEX expense_claim_employee ON expense_claims(company_id,employment_id,created_at DESC,id DESC);
CREATE INDEX expense_claim_created ON expense_claims(company_id,created_at DESC,id DESC);
CREATE INDEX expense_claim_active_author ON expense_claims(company_id,created_by) WHERE status='DRAFT';
CREATE TABLE expense_drafts (
    company_id uuid NOT NULL, claim_id uuid NOT NULL, revision integer NOT NULL CHECK(revision BETWEEN 0 AND 99),
    employee_number varchar(32) NOT NULL, employee_name varchar(200) NOT NULL,
    title varchar(160) NOT NULL CHECK(length(trim(title))>0), description varchar(2000) NOT NULL,
    total_amount numeric(14,2) NOT NULL CHECK(total_amount>=0), line_count integer NOT NULL CHECK(line_count BETWEEN 0 AND 20),
    actor_id uuid NOT NULL REFERENCES accounts(id), reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0), recorded_at timestamptz NOT NULL,
    write_xid xid8 NOT NULL DEFAULT pg_current_xact_id(),
    PRIMARY KEY(company_id,claim_id,revision), FOREIGN KEY(company_id,claim_id) REFERENCES expense_claims(company_id,id)
);
ALTER TABLE expense_claims ADD FOREIGN KEY(company_id,id,draft_revision) REFERENCES expense_drafts(company_id,claim_id,revision) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE expense_draft_lines (
    company_id uuid NOT NULL, claim_id uuid NOT NULL, draft_revision integer NOT NULL, id uuid NOT NULL, ordinal integer NOT NULL CHECK(ordinal BETWEEN 1 AND 20),
    category_id uuid NOT NULL, occurred_on date NOT NULL CHECK(occurred_on BETWEEN DATE '1900-01-01' AND DATE '2200-12-31'),
    amount numeric(14,2) NOT NULL CHECK(amount>0), description varchar(500) NOT NULL CHECK(length(trim(description))>0), cost_center_id uuid,
    PRIMARY KEY(company_id,claim_id,draft_revision,id), UNIQUE(company_id,claim_id,draft_revision,ordinal),
    FOREIGN KEY(company_id,claim_id,draft_revision) REFERENCES expense_drafts(company_id,claim_id,revision),
    FOREIGN KEY(company_id,category_id) REFERENCES expense_categories(company_id,id),
    FOREIGN KEY(company_id,cost_center_id) REFERENCES organization_units(company_id,id)
);
CREATE TABLE expense_draft_receipts (
    company_id uuid NOT NULL, claim_id uuid NOT NULL, draft_revision integer NOT NULL, line_id uuid NOT NULL,
    ordinal integer NOT NULL CHECK(ordinal BETWEEN 1 AND 3), document_revision_id uuid NOT NULL,
    PRIMARY KEY(company_id,claim_id,draft_revision,line_id,ordinal), UNIQUE(company_id,claim_id,draft_revision,line_id,document_revision_id),
    FOREIGN KEY(company_id,claim_id,draft_revision,line_id) REFERENCES expense_draft_lines(company_id,claim_id,draft_revision,id),
    FOREIGN KEY(company_id,document_revision_id) REFERENCES document_revisions(company_id,id)
);
CREATE TABLE expense_claim_changes (
    company_id uuid NOT NULL, claim_id uuid NOT NULL, version bigint NOT NULL CHECK(version>=0), draft_revision integer NOT NULL,
    status varchar(24) NOT NULL CHECK(status IN ('DRAFT','CANCELLED')), kind varchar(32) NOT NULL CHECK(kind IN ('DRAFT_SAVED','CANCELLED')),
    actor_id uuid NOT NULL REFERENCES accounts(id), reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0), recorded_at timestamptz NOT NULL,
    PRIMARY KEY(company_id,claim_id,version), FOREIGN KEY(company_id,claim_id,draft_revision) REFERENCES expense_drafts(company_id,claim_id,revision)
);
ALTER TABLE expense_claims ADD FOREIGN KEY(company_id,id,version) REFERENCES expense_claim_changes(company_id,claim_id,version) DEFERRABLE INITIALLY DEFERRED;
DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['expense_claims','expense_drafts','expense_draft_lines','expense_draft_receipts','expense_claim_changes'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
    END LOOP;
    FOREACH t IN ARRAY ARRAY['expense_drafts','expense_draft_lines','expense_draft_receipts','expense_claim_changes'] LOOP
        EXECUTE format('CREATE TRIGGER immutable_expense_evidence BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION deny_history_mutation()',t);
    END LOOP;
END $$;
CREATE POLICY expense_claim_actor ON expense_claims AS RESTRICTIVE FOR INSERT TO PUBLIC WITH CHECK(created_by=current_actor_id());
CREATE POLICY expense_draft_actor ON expense_drafts AS RESTRICTIVE FOR INSERT TO PUBLIC WITH CHECK(actor_id=current_actor_id());
CREATE POLICY expense_change_actor ON expense_claim_changes AS RESTRICTIVE FOR INSERT TO PUBLIC WITH CHECK(actor_id=current_actor_id());
CREATE TRIGGER preserve_expense_claim BEFORE DELETE ON expense_claims FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE FUNCTION protect_expense_claim() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.status<>'DRAFT' OR NEW.version<>0 OR NEW.draft_revision<>0 THEN RAISE EXCEPTION 'Claims must start as a new draft' USING ERRCODE='23514';END IF;
    ELSE
        IF (NEW.company_id,NEW.id,NEW.employment_id,NEW.created_by,NEW.created_at) IS DISTINCT FROM (OLD.company_id,OLD.id,OLD.employment_id,OLD.created_by,OLD.created_at)
            OR OLD.status<>'DRAFT' OR NEW.version<>OLD.version+1 THEN RAISE EXCEPTION 'Claim identity and cancelled claims are immutable' USING ERRCODE='42501';END IF;
        IF (NEW.status='DRAFT' AND NEW.draft_revision<>OLD.draft_revision+1) OR (NEW.status='CANCELLED' AND NEW.draft_revision<>OLD.draft_revision) THEN
            RAISE EXCEPTION 'Claim draft revision sequence is invalid' USING ERRCODE='23514';END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_expense_claim BEFORE INSERT OR UPDATE ON expense_claims FOR EACH ROW EXECUTE FUNCTION protect_expense_claim();
CREATE FUNCTION initialize_expense_draft() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM expense_claims c WHERE c.company_id=NEW.company_id AND c.id=NEW.claim_id AND c.draft_revision=NEW.revision AND c.status='DRAFT') THEN
        RAISE EXCEPTION 'Draft revision must match its claim' USING ERRCODE='23514';END IF;
    NEW.write_xid:=pg_current_xact_id();RETURN NEW;
END $$;
CREATE TRIGGER initialize_expense_draft BEFORE INSERT ON expense_drafts FOR EACH ROW EXECUTE FUNCTION initialize_expense_draft();
CREATE FUNCTION own_expense_draft_inserts() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM expense_drafts d WHERE d.company_id=NEW.company_id AND d.claim_id=NEW.claim_id AND d.revision=NEW.draft_revision AND d.write_xid=pg_current_xact_id()) THEN
        RAISE EXCEPTION 'Draft contents can only be inserted with their immutable revision' USING ERRCODE='42501';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER own_expense_line_inserts BEFORE INSERT ON expense_draft_lines FOR EACH ROW EXECUTE FUNCTION own_expense_draft_inserts();
CREATE TRIGGER own_expense_receipt_inserts BEFORE INSERT ON expense_draft_receipts FOR EACH ROW EXECUTE FUNCTION own_expense_draft_inserts();
CREATE FUNCTION validate_expense_receipt_reference() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM expense_claims c JOIN document_revisions r ON r.company_id=c.company_id AND r.id=NEW.document_revision_id
        JOIN documents d ON d.company_id=r.company_id AND d.id=r.document_id AND d.employment_id=c.employment_id AND d.classification='RECEIPT'
        WHERE c.company_id=NEW.company_id AND c.id=NEW.claim_id) THEN
        RAISE EXCEPTION 'Claim receipt must belong to the same employee and company' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER validate_expense_receipt_reference BEFORE INSERT ON expense_draft_receipts FOR EACH ROW EXECUTE FUNCTION validate_expense_receipt_reference();
CREATE FUNCTION validate_expense_draft_totals() RETURNS trigger LANGUAGE plpgsql AS $$ DECLARE count_lines integer; total numeric;BEGIN
    SELECT count(*),coalesce(sum(amount),0) INTO count_lines,total FROM expense_draft_lines WHERE company_id=NEW.company_id AND claim_id=NEW.claim_id AND draft_revision=NEW.revision;
    IF count_lines<>NEW.line_count OR total<>NEW.total_amount THEN RAISE EXCEPTION 'Draft totals must match the immutable lines' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER validate_expense_draft_totals AFTER INSERT ON expense_drafts DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_expense_draft_totals();
CREATE FUNCTION sequence_expense_claim_change() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM expense_claims c WHERE c.company_id=NEW.company_id AND c.id=NEW.claim_id AND c.version=NEW.version AND c.draft_revision=NEW.draft_revision AND c.status=NEW.status) THEN
        RAISE EXCEPTION 'Claim history must match its version' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER sequence_expense_claim_change BEFORE INSERT ON expense_claim_changes FOR EACH ROW EXECUTE FUNCTION sequence_expense_claim_change();
