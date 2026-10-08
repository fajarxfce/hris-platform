ALTER TABLE expense_claims DROP CONSTRAINT expense_claims_status_check;
ALTER TABLE expense_claims ADD CHECK(status IN ('DRAFT','PENDING','CANCELLED'));
ALTER TABLE expense_claim_changes DROP CONSTRAINT expense_claim_changes_status_check;
ALTER TABLE expense_claim_changes ADD CHECK(status IN ('DRAFT','PENDING','CANCELLED'));
ALTER TABLE expense_claim_changes DROP CONSTRAINT expense_claim_changes_kind_check;
ALTER TABLE expense_claim_changes ADD CHECK(kind IN ('DRAFT_SAVED','SUBMITTED','WITHDRAWN','CANCELLED'));
ALTER TABLE expense_claims ADD COLUMN submission_count integer NOT NULL DEFAULT 0 CHECK(submission_count BETWEEN 0 AND 20);
ALTER TABLE expense_claims ADD COLUMN latest_submission_id uuid;
ALTER TABLE expense_claims ADD CHECK((submission_count=0)=(latest_submission_id IS NULL));
ALTER TABLE expense_claim_changes ADD COLUMN submission_id uuid;
DROP INDEX expense_claim_active_author;
CREATE INDEX expense_claim_open_author ON expense_claims(company_id,created_by) WHERE status IN ('DRAFT','PENDING');
CREATE TABLE expense_submissions (
    company_id uuid NOT NULL, claim_id uuid NOT NULL, id uuid NOT NULL, number integer NOT NULL CHECK(number BETWEEN 1 AND 20),
    draft_revision integer NOT NULL, approval_id uuid NOT NULL, requester_id uuid REFERENCES accounts(id),
    employee_number varchar(32) NOT NULL, employee_name varchar(200) NOT NULL,
    maker_ids uuid[] NOT NULL CHECK(cardinality(maker_ids) BETWEEN 1 AND 102 AND array_position(maker_ids,NULL) IS NULL),
    submitted_by uuid NOT NULL REFERENCES accounts(id), submitted_at timestamptz NOT NULL,
    reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    write_xid xid8 NOT NULL DEFAULT pg_current_xact_id(),
    PRIMARY KEY(company_id,id), UNIQUE(company_id,claim_id,number,id), UNIQUE(company_id,claim_id,number),
    UNIQUE(company_id,claim_id,id,draft_revision), UNIQUE(company_id,claim_id,id), UNIQUE(company_id,approval_id),
    FOREIGN KEY(company_id,claim_id,draft_revision) REFERENCES expense_drafts(company_id,claim_id,revision),
    FOREIGN KEY(company_id,approval_id) REFERENCES approval_requests(company_id,id)
);
ALTER TABLE expense_claims ADD FOREIGN KEY(company_id,id,submission_count,latest_submission_id)
    REFERENCES expense_submissions(company_id,claim_id,number,id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE expense_claim_changes ADD FOREIGN KEY(company_id,claim_id,submission_id)
    REFERENCES expense_submissions(company_id,claim_id,id);
CREATE TABLE expense_submitted_lines (
    company_id uuid NOT NULL, submission_id uuid NOT NULL, claim_id uuid NOT NULL, draft_revision integer NOT NULL, line_id uuid NOT NULL,
    category_id uuid NOT NULL, category_revision bigint NOT NULL, category_version bigint NOT NULL CHECK(category_version>=category_revision),
    cost_center_code varchar(32), cost_center_name varchar(200), cost_center_version bigint CHECK(cost_center_version>=0),
    PRIMARY KEY(company_id,submission_id,line_id),
    FOREIGN KEY(company_id,claim_id,submission_id,draft_revision) REFERENCES expense_submissions(company_id,claim_id,id,draft_revision),
    FOREIGN KEY(company_id,claim_id,draft_revision,line_id) REFERENCES expense_draft_lines(company_id,claim_id,draft_revision,id),
    FOREIGN KEY(company_id,category_id,category_revision) REFERENCES expense_category_revisions(company_id,category_id,revision),
    CHECK((cost_center_code IS NULL AND cost_center_name IS NULL AND cost_center_version IS NULL) OR
          (cost_center_code IS NOT NULL AND cost_center_name IS NOT NULL AND cost_center_version IS NOT NULL))
);
CREATE TABLE expense_submitted_receipts (
    company_id uuid NOT NULL, submission_id uuid NOT NULL, claim_id uuid NOT NULL, draft_revision integer NOT NULL, line_id uuid NOT NULL,
    ordinal integer NOT NULL CHECK(ordinal BETWEEN 1 AND 3), document_revision_id uuid NOT NULL, sha256 char(64) NOT NULL CHECK(sha256 ~ '^[0-9a-f]{64}$'),
    file_name varchar(180) NOT NULL, media_type varchar(40) NOT NULL, size bigint NOT NULL CHECK(size BETWEEN 1 AND 104857600),
    possible_duplicate boolean NOT NULL,
    PRIMARY KEY(company_id,submission_id,line_id,document_revision_id), UNIQUE(company_id,submission_id,line_id,ordinal),
    FOREIGN KEY(company_id,submission_id,line_id) REFERENCES expense_submitted_lines(company_id,submission_id,line_id),
    FOREIGN KEY(company_id,claim_id,draft_revision,line_id,document_revision_id)
        REFERENCES expense_draft_receipts(company_id,claim_id,draft_revision,line_id,document_revision_id)
);
CREATE INDEX expense_submission_claim ON expense_submissions(company_id,claim_id,number DESC);
CREATE INDEX expense_receipt_digest ON expense_submitted_receipts(company_id,sha256,claim_id,submission_id);
DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['expense_submissions','expense_submitted_lines','expense_submitted_receipts'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
        EXECUTE format('CREATE TRIGGER immutable_expense_submission BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION deny_history_mutation()',t);
    END LOOP;
END $$;
CREATE POLICY expense_submission_actor ON expense_submissions AS RESTRICTIVE FOR INSERT TO PUBLIC WITH CHECK(submitted_by=current_actor_id());
CREATE OR REPLACE FUNCTION protect_expense_claim() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.status<>'DRAFT' OR NEW.version<>0 OR NEW.draft_revision<>0 OR NEW.submission_count<>0 OR NEW.latest_submission_id IS NOT NULL THEN
            RAISE EXCEPTION 'Claims must start as a new draft' USING ERRCODE='23514';END IF;
    ELSE
        IF (NEW.company_id,NEW.id,NEW.employment_id,NEW.created_by,NEW.created_at) IS DISTINCT FROM (OLD.company_id,OLD.id,OLD.employment_id,OLD.created_by,OLD.created_at)
            OR OLD.status='CANCELLED' OR NEW.version<>OLD.version+1 THEN RAISE EXCEPTION 'Claim identity and cancelled claims are immutable' USING ERRCODE='42501';END IF;
        IF OLD.status='DRAFT' AND NEW.status='PENDING' THEN
            IF NEW.draft_revision<>OLD.draft_revision OR NEW.submission_count<>OLD.submission_count+1 OR NEW.latest_submission_id IS NULL OR NEW.latest_submission_id IS NOT DISTINCT FROM OLD.latest_submission_id THEN
                RAISE EXCEPTION 'A submission must advance its immutable reference' USING ERRCODE='23514';END IF;
        ELSE
            IF (NEW.submission_count,NEW.latest_submission_id) IS DISTINCT FROM (OLD.submission_count,OLD.latest_submission_id) THEN
                RAISE EXCEPTION 'Only submission may change its evidence reference' USING ERRCODE='23514';END IF;
            IF OLD.status='DRAFT' AND NEW.status='DRAFT' THEN
                IF NEW.draft_revision<>OLD.draft_revision+1 THEN RAISE EXCEPTION 'Draft revision must advance' USING ERRCODE='23514';END IF;
            ELSIF (OLD.status='DRAFT' AND NEW.status='CANCELLED') OR (OLD.status='PENDING' AND NEW.status='DRAFT') THEN
                IF NEW.draft_revision<>OLD.draft_revision THEN RAISE EXCEPTION 'Action cannot change draft contents' USING ERRCODE='23514';END IF;
            ELSE RAISE EXCEPTION 'Unsupported claim transition' USING ERRCODE='23514';END IF;
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE FUNCTION initialize_expense_submission() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM expense_claims c JOIN approval_requests a ON a.company_id=c.company_id AND a.id=NEW.approval_id
        WHERE c.company_id=NEW.company_id AND c.id=NEW.claim_id AND c.status='PENDING' AND c.latest_submission_id=NEW.id
        AND c.submission_count=NEW.number AND c.draft_revision=NEW.draft_revision AND a.kind='EXPENSE' AND a.resource_id=NEW.id
        AND a.author_id=NEW.submitted_by AND a.requester_id IS NOT DISTINCT FROM NEW.requester_id
        AND a.status IN ('PENDING','BLOCKED') AND NEW.maker_ids @> ARRAY[c.created_by,NEW.submitted_by]) THEN
        RAISE EXCEPTION 'Submission must match its claim and approval' USING ERRCODE='23514';END IF;
    NEW.write_xid:=pg_current_xact_id();RETURN NEW;
END $$;
CREATE TRIGGER initialize_expense_submission BEFORE INSERT ON expense_submissions FOR EACH ROW EXECUTE FUNCTION initialize_expense_submission();
CREATE FUNCTION own_expense_submission_inserts() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM expense_submissions s WHERE s.company_id=NEW.company_id AND s.id=NEW.submission_id AND s.claim_id=NEW.claim_id
        AND s.draft_revision=NEW.draft_revision AND s.write_xid=pg_current_xact_id()) THEN
        RAISE EXCEPTION 'Submitted evidence can only be inserted with its immutable submission' USING ERRCODE='42501';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER own_expense_submitted_line BEFORE INSERT ON expense_submitted_lines FOR EACH ROW EXECUTE FUNCTION own_expense_submission_inserts();
CREATE TRIGGER own_expense_submitted_receipt BEFORE INSERT ON expense_submitted_receipts FOR EACH ROW EXECUTE FUNCTION own_expense_submission_inserts();
CREATE FUNCTION validate_expense_submitted_line() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM expense_draft_lines l JOIN expense_categories c ON c.company_id=l.company_id AND c.id=l.category_id
        LEFT JOIN organization_units u ON u.company_id=l.company_id AND u.id=l.cost_center_id
        WHERE l.company_id=NEW.company_id AND l.claim_id=NEW.claim_id AND l.draft_revision=NEW.draft_revision
        AND l.id=NEW.line_id AND l.category_id=NEW.category_id AND c.version=NEW.category_version
        AND ((l.cost_center_id IS NULL AND NEW.cost_center_code IS NULL) OR
            (u.kind='COST_CENTER' AND u.code=NEW.cost_center_code AND u.name=NEW.cost_center_name AND u.version=NEW.cost_center_version))) THEN
        RAISE EXCEPTION 'Submission policy must describe its draft line' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER validate_expense_submitted_line BEFORE INSERT ON expense_submitted_lines FOR EACH ROW EXECUTE FUNCTION validate_expense_submitted_line();
CREATE FUNCTION validate_expense_submitted_receipt() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM document_revisions r WHERE r.company_id=NEW.company_id AND r.id=NEW.document_revision_id AND r.status='READY'
        AND r.expected_sha256=NEW.sha256 AND r.file_name=NEW.file_name AND r.media_type=NEW.media_type AND r.expected_bytes=NEW.size) THEN
        RAISE EXCEPTION 'Submitted receipt must match ready immutable evidence' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER validate_expense_submitted_receipt BEFORE INSERT ON expense_submitted_receipts FOR EACH ROW EXECUTE FUNCTION validate_expense_submitted_receipt();
CREATE FUNCTION complete_expense_submission() RETURNS trigger LANGUAGE plpgsql AS $$ DECLARE lines integer; receipts integer; makers uuid[]; BEGIN
    SELECT count(*) INTO lines FROM expense_submitted_lines WHERE company_id=NEW.company_id AND submission_id=NEW.id;
    SELECT count(*) INTO receipts FROM expense_submitted_receipts WHERE company_id=NEW.company_id AND submission_id=NEW.id;
    SELECT array_agg(DISTINCT actor_id) INTO makers FROM expense_drafts WHERE company_id=NEW.company_id AND claim_id=NEW.claim_id;
    IF lines=0 OR lines<>(SELECT line_count FROM expense_drafts WHERE company_id=NEW.company_id AND claim_id=NEW.claim_id AND revision=NEW.draft_revision)
        OR receipts<>(SELECT count(*) FROM expense_draft_receipts WHERE company_id=NEW.company_id AND claim_id=NEW.claim_id AND draft_revision=NEW.draft_revision)
        OR NOT NEW.maker_ids @> makers THEN RAISE EXCEPTION 'Submission must freeze all draft evidence and contributors' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER complete_expense_submission AFTER INSERT ON expense_submissions DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION complete_expense_submission();
CREATE OR REPLACE FUNCTION sequence_expense_claim_change() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM expense_claims c WHERE c.company_id=NEW.company_id AND c.id=NEW.claim_id AND c.version=NEW.version
        AND c.draft_revision=NEW.draft_revision AND c.status=NEW.status AND c.latest_submission_id IS NOT DISTINCT FROM NEW.submission_id) THEN
        RAISE EXCEPTION 'Claim history must match its version' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
