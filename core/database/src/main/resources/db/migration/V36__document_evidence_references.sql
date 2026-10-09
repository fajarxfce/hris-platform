CREATE TABLE document_evidence_references (
    company_id uuid NOT NULL,source_kind varchar(32) NOT NULL CHECK(source_kind IN ('EXPENSE_DRAFT')),
    source_id uuid NOT NULL,source_version integer NOT NULL CHECK(source_version BETWEEN 0 AND 1000000),
    document_revision_id uuid NOT NULL,created_by uuid NOT NULL REFERENCES accounts(id),created_at timestamptz NOT NULL,
    PRIMARY KEY(company_id,source_kind,source_id,source_version,document_revision_id),
    FOREIGN KEY(company_id,document_revision_id) REFERENCES document_revisions(company_id,id)
);
CREATE INDEX document_evidence_retention ON document_evidence_references(company_id,document_revision_id);
INSERT INTO document_evidence_references(company_id,source_kind,source_id,source_version,document_revision_id,created_by,created_at)
    SELECT DISTINCT r.company_id,'EXPENSE_DRAFT',r.claim_id,r.draft_revision,r.document_revision_id,d.actor_id,d.recorded_at
    FROM expense_draft_receipts r JOIN expense_drafts d ON d.company_id=r.company_id AND d.claim_id=r.claim_id AND d.revision=r.draft_revision;
ALTER TABLE document_evidence_references ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_evidence_references FORCE ROW LEVEL SECURITY;
CREATE POLICY document_evidence_scope ON document_evidence_references USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
CREATE POLICY document_evidence_actor ON document_evidence_references AS RESTRICTIVE FOR INSERT WITH CHECK(created_by=current_actor_id());
CREATE TRIGGER immutable_document_evidence BEFORE UPDATE OR DELETE ON document_evidence_references FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
ALTER TABLE expense_draft_receipts ADD COLUMN reference_kind varchar(32) GENERATED ALWAYS AS ('EXPENSE_DRAFT'::varchar(32)) STORED;
ALTER TABLE expense_draft_receipts ADD FOREIGN KEY(company_id,reference_kind,claim_id,draft_revision,document_revision_id)
    REFERENCES document_evidence_references(company_id,source_kind,source_id,source_version,document_revision_id) DEFERRABLE INITIALLY DEFERRED;
