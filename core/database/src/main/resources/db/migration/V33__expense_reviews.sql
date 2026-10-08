ALTER TABLE expense_claims DROP CONSTRAINT expense_claims_status_check;
ALTER TABLE expense_claims ADD CHECK(status IN ('DRAFT','PENDING','RETURNED','APPROVED','REJECTED','CANCELLED'));
ALTER TABLE expense_claim_changes DROP CONSTRAINT expense_claim_changes_status_check;
ALTER TABLE expense_claim_changes ADD CHECK(status IN ('DRAFT','PENDING','RETURNED','APPROVED','REJECTED','CANCELLED'));
ALTER TABLE expense_claim_changes DROP CONSTRAINT expense_claim_changes_kind_check;
ALTER TABLE expense_claim_changes ADD CHECK(kind IN ('DRAFT_SAVED','SUBMITTED','WITHDRAWN','REVIEWED','CANCELLED'));
ALTER TABLE expense_claim_changes DROP CONSTRAINT expense_claim_changes_reason_check;
ALTER TABLE expense_claim_changes ADD CHECK(kind='REVIEWED' OR length(trim(reason))>0);
DROP INDEX expense_claim_open_author;
CREATE INDEX expense_claim_open_author ON expense_claims(company_id,created_by) WHERE status IN ('DRAFT','PENDING','RETURNED');
CREATE TABLE expense_reviews (
    company_id uuid NOT NULL, submission_id uuid NOT NULL, claim_id uuid NOT NULL, claim_version bigint NOT NULL CHECK(claim_version>0),
    approval_id uuid NOT NULL, approval_version bigint NOT NULL CHECK(approval_version>0), step integer NOT NULL CHECK(step BETWEEN 0 AND 7),
    actor_id uuid NOT NULL REFERENCES accounts(id), deciding_for uuid NOT NULL REFERENCES accounts(id),
    decision varchar(12) NOT NULL CHECK(decision IN ('APPROVE','REJECT','RETURN')),
    resulting_status varchar(16) NOT NULL CHECK(resulting_status IN ('PENDING','APPROVED','REJECTED','RETURNED')),
    duplicate_digests varchar(64)[] NOT NULL CHECK(cardinality(duplicate_digests)<=60 AND array_position(duplicate_digests,NULL) IS NULL),
    duplicates_acknowledged boolean NOT NULL, reason varchar(1000) NOT NULL, decided_at timestamptz NOT NULL,
    PRIMARY KEY(company_id,submission_id,step), UNIQUE(company_id,claim_id,claim_version),
    FOREIGN KEY(company_id,claim_id,submission_id) REFERENCES expense_submissions(company_id,claim_id,id),
    FOREIGN KEY(company_id,claim_id,claim_version) REFERENCES expense_claim_changes(company_id,claim_id,version) DEFERRABLE INITIALLY DEFERRED,
    FOREIGN KEY(company_id,approval_id,step) REFERENCES approval_decisions(company_id,request_id,step),
    CHECK((decision='APPROVE' AND cardinality(duplicate_digests)=0) OR length(trim(reason))>0),
    CHECK(decision<>'APPROVE' OR cardinality(duplicate_digests)=0 OR duplicates_acknowledged)
);
ALTER TABLE expense_reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE expense_reviews FORCE ROW LEVEL SECURITY;
CREATE POLICY company_scope ON expense_reviews USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
CREATE POLICY expense_review_actor ON expense_reviews AS RESTRICTIVE FOR INSERT TO PUBLIC WITH CHECK(actor_id=current_actor_id());
CREATE TRIGGER immutable_expense_review BEFORE UPDATE OR DELETE ON expense_reviews FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE OR REPLACE FUNCTION protect_expense_claim() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.status<>'DRAFT' OR NEW.version<>0 OR NEW.draft_revision<>0 OR NEW.submission_count<>0 OR NEW.latest_submission_id IS NOT NULL THEN
            RAISE EXCEPTION 'Claims must start as a new draft' USING ERRCODE='23514';END IF;
    ELSE
        IF (NEW.company_id,NEW.id,NEW.employment_id,NEW.created_by,NEW.created_at) IS DISTINCT FROM (OLD.company_id,OLD.id,OLD.employment_id,OLD.created_by,OLD.created_at)
            OR OLD.status IN ('APPROVED','REJECTED','CANCELLED') OR NEW.version<>OLD.version+1 THEN RAISE EXCEPTION 'Claim identity and terminal claims are immutable' USING ERRCODE='42501';END IF;
        IF OLD.status='DRAFT' AND NEW.status='PENDING' THEN
            IF NEW.draft_revision<>OLD.draft_revision OR NEW.submission_count<>OLD.submission_count+1 OR NEW.latest_submission_id IS NULL OR NEW.latest_submission_id IS NOT DISTINCT FROM OLD.latest_submission_id THEN
                RAISE EXCEPTION 'A submission must advance its immutable reference' USING ERRCODE='23514';END IF;
        ELSE
            IF (NEW.submission_count,NEW.latest_submission_id) IS DISTINCT FROM (OLD.submission_count,OLD.latest_submission_id) THEN
                RAISE EXCEPTION 'Only submission may change its evidence reference' USING ERRCODE='23514';END IF;
            IF OLD.status IN ('DRAFT','RETURNED') AND NEW.status='DRAFT' THEN
                IF NEW.draft_revision<>OLD.draft_revision+1 THEN RAISE EXCEPTION 'Draft revision must advance' USING ERRCODE='23514';END IF;
            ELSIF (OLD.status IN ('DRAFT','RETURNED') AND NEW.status='CANCELLED') OR
                  (OLD.status='PENDING' AND NEW.status IN ('DRAFT','PENDING','RETURNED','APPROVED','REJECTED')) THEN
                IF NEW.draft_revision<>OLD.draft_revision THEN RAISE EXCEPTION 'Action cannot change draft contents' USING ERRCODE='23514';END IF;
                IF OLD.status='PENDING' AND NEW.status='DRAFT' AND NOT EXISTS(
                    SELECT 1 FROM expense_submissions s JOIN approval_requests a ON a.company_id=s.company_id AND a.id=s.approval_id
                    WHERE s.company_id=OLD.company_id AND s.id=OLD.latest_submission_id AND a.status='CANCELLED'
                ) THEN RAISE EXCEPTION 'Withdrawal must cancel its approval' USING ERRCODE='23514';END IF;
            ELSE RAISE EXCEPTION 'Unsupported claim transition' USING ERRCODE='23514';END IF;
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE FUNCTION validate_expense_review() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM expense_claims c JOIN expense_submissions s ON s.company_id=c.company_id AND s.id=c.latest_submission_id
        JOIN approval_requests a ON a.company_id=s.company_id AND a.id=s.approval_id
        JOIN approval_decisions d ON d.company_id=a.company_id AND d.request_id=a.id AND d.step=NEW.step
        WHERE c.company_id=NEW.company_id AND c.id=NEW.claim_id AND c.latest_submission_id=NEW.submission_id AND c.version=NEW.claim_version
        AND c.status=NEW.resulting_status AND a.id=NEW.approval_id AND a.version=NEW.approval_version
        AND d.actor_id=NEW.actor_id AND d.deciding_for=NEW.deciding_for AND d.reason=NEW.reason AND d.decided_at=NEW.decided_at
        AND d.decision=CASE WHEN NEW.decision='APPROVE' THEN 'APPROVE' ELSE 'REJECT' END
        AND NOT NEW.actor_id=ANY(s.maker_ids) AND NOT NEW.deciding_for=ANY(s.maker_ids)
        AND (s.requester_id IS NULL OR s.requester_id NOT IN (NEW.actor_id,NEW.deciding_for))
        AND ((NEW.decision='APPROVE' AND NEW.resulting_status='PENDING' AND a.status IN ('PENDING','BLOCKED') AND a.current_step=NEW.step+1)
          OR (NEW.decision='APPROVE' AND NEW.resulting_status='APPROVED' AND a.status='APPROVED' AND a.current_step=NEW.step)
          OR (NEW.decision='REJECT' AND NEW.resulting_status='REJECTED' AND a.status='REJECTED' AND a.current_step=NEW.step)
          OR (NEW.decision='RETURN' AND NEW.resulting_status='RETURNED' AND a.status='REJECTED' AND a.current_step=NEW.step))) THEN
        RAISE EXCEPTION 'Expense review must match its independent approval decision' USING ERRCODE='23514';END IF;
    IF EXISTS(SELECT 1 FROM unnest(NEW.duplicate_digests) digest WHERE digest !~ '^[0-9a-f]{64}$' OR NOT EXISTS(
        SELECT 1 FROM expense_submitted_receipts r WHERE r.company_id=NEW.company_id AND r.submission_id=NEW.submission_id AND r.sha256=digest)) THEN
        RAISE EXCEPTION 'Review signals must refer to its submitted receipts' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER validate_expense_review BEFORE INSERT ON expense_reviews FOR EACH ROW EXECUTE FUNCTION validate_expense_review();
CREATE FUNCTION complete_expense_claim_review() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM expense_reviews r JOIN expense_claim_changes h ON h.company_id=r.company_id AND h.claim_id=r.claim_id AND h.version=r.claim_version
        WHERE r.company_id=NEW.company_id AND r.claim_id=NEW.id AND r.submission_id=NEW.latest_submission_id AND r.claim_version=NEW.version
        AND r.resulting_status=NEW.status AND h.kind='REVIEWED' AND h.submission_id=r.submission_id AND h.actor_id=r.actor_id AND h.reason=r.reason) THEN
        RAISE EXCEPTION 'Claim review must have immutable decision evidence' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER complete_expense_claim_review AFTER UPDATE ON expense_claims DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
    WHEN(OLD.status='PENDING' AND NEW.status IN ('PENDING','RETURNED','APPROVED','REJECTED')) EXECUTE FUNCTION complete_expense_claim_review();
