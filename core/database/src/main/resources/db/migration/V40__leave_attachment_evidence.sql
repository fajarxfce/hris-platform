ALTER TABLE document_evidence_references DROP CONSTRAINT document_evidence_references_source_kind_check;
ALTER TABLE document_evidence_references ADD CONSTRAINT document_evidence_references_source_kind_check
    CHECK(source_kind IN ('EXPENSE_DRAFT','LEAVE_REQUEST'));
ALTER TABLE leave_requests ADD COLUMN attachment_count smallint NOT NULL DEFAULT 0 CHECK(attachment_count BETWEEN 0 AND 3);
ALTER TABLE leave_requests ADD CONSTRAINT leave_attachment_policy CHECK(
    NOT coalesce((type_snapshot#>>'{policy,attachmentRequired}')::boolean,false) OR attachment_count>0
);
CREATE TABLE leave_request_attachments (
    company_id uuid NOT NULL,request_id uuid NOT NULL,ordinal smallint NOT NULL CHECK(ordinal BETWEEN 1 AND 3),
    document_id uuid NOT NULL,document_revision_id uuid NOT NULL,
    file_name varchar(180) NOT NULL,media_type varchar(40) NOT NULL,
    content_bytes bigint NOT NULL CHECK(content_bytes BETWEEN 1 AND 104857600),
    content_sha256 char(64) NOT NULL CHECK(content_sha256~'^[0-9a-f]{64}$'),
    reference_kind varchar(32) GENERATED ALWAYS AS ('LEAVE_REQUEST'::varchar(32)) STORED,
    reference_version integer GENERATED ALWAYS AS (0) STORED,
    PRIMARY KEY(company_id,request_id,document_revision_id),UNIQUE(company_id,request_id,ordinal),
    FOREIGN KEY(company_id,request_id) REFERENCES leave_requests(company_id,id),
    FOREIGN KEY(company_id,document_id,document_revision_id) REFERENCES document_revisions(company_id,document_id,id),
    FOREIGN KEY(company_id,reference_kind,request_id,reference_version,document_revision_id)
        REFERENCES document_evidence_references(company_id,source_kind,source_id,source_version,document_revision_id) DEFERRABLE INITIALLY DEFERRED
);
ALTER TABLE leave_request_attachments ENABLE ROW LEVEL SECURITY;
ALTER TABLE leave_request_attachments FORCE ROW LEVEL SECURITY;
CREATE POLICY leave_attachment_scope ON leave_request_attachments USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
CREATE TRIGGER immutable_leave_attachment BEFORE UPDATE OR DELETE ON leave_request_attachments FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE FUNCTION validate_leave_attachment_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(
        SELECT 1 FROM leave_requests q JOIN documents d ON d.company_id=q.company_id AND d.employment_id=q.employment_id
        JOIN document_revisions r ON r.company_id=d.company_id AND r.document_id=d.id
        WHERE q.company_id=NEW.company_id AND q.id=NEW.request_id AND q.version=0 AND q.status='PENDING'
        AND d.id=NEW.document_id AND d.classification='PERSONAL' AND r.id=NEW.document_revision_id AND r.status='READY'
        AND (r.file_name,r.media_type,r.expected_bytes,r.expected_sha256)=(NEW.file_name,NEW.media_type,NEW.content_bytes,NEW.content_sha256)
    ) THEN RAISE EXCEPTION 'Leave attachments require exact accepted employee document snapshots' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER leave_attachment_snapshot BEFORE INSERT ON leave_request_attachments FOR EACH ROW EXECUTE FUNCTION validate_leave_attachment_snapshot();
CREATE FUNCTION enforce_leave_attachment_completeness() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE company uuid; request uuid; expected integer; actual integer;
BEGIN
    company:=NEW.company_id;
    IF TG_TABLE_NAME='leave_requests' THEN request:=NEW.id; ELSE request:=NEW.request_id; END IF;
    SELECT attachment_count INTO expected FROM leave_requests WHERE company_id=company AND id=request;
    SELECT count(*) INTO actual FROM leave_request_attachments WHERE company_id=company AND request_id=request;
    IF expected IS NULL OR expected<>actual THEN RAISE EXCEPTION 'Leave attachment evidence must commit completely' USING ERRCODE='23514'; END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER leave_attachment_complete AFTER INSERT ON leave_requests DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION enforce_leave_attachment_completeness();
CREATE CONSTRAINT TRIGGER leave_attachment_complete AFTER INSERT ON leave_request_attachments DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION enforce_leave_attachment_completeness();
