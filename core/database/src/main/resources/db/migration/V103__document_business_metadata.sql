-- Acquire before taking the legacy snapshot; concurrent writers must not escape history.
SET LOCAL lock_timeout='5s';
LOCK TABLE documents IN ACCESS EXCLUSIVE MODE;

ALTER TABLE documents
    ADD COLUMN metadata_version bigint NOT NULL DEFAULT 0 CHECK(metadata_version BETWEEN 0 AND 999),
    ADD COLUMN category varchar(32) NOT NULL DEFAULT 'OTHER',
    ADD COLUMN identity_number varchar(120),
    ADD COLUMN issuing_country varchar(2),
    ADD COLUMN issued_on date,
    ADD COLUMN valid_until date,
    ADD CONSTRAINT document_business_metadata_valid CHECK(category IN ('PASSPORT','NATIONAL_ID','WORK_PERMIT','RESIDENCE_PERMIT','EMPLOYMENT_CONTRACT','CERTIFICATE','RECEIPT','OTHER') AND
        (identity_number IS NULL OR (length(identity_number) BETWEEN 1 AND 120 AND identity_number=btrim(identity_number) AND identity_number !~ '[[:cntrl:]]')) AND
        (issuing_country IS NULL OR issuing_country ~ '^[A-Z]{2}$') AND
        (issued_on IS NULL OR issued_on BETWEEN DATE '1900-01-01' AND DATE '2200-12-31') AND
        (valid_until IS NULL OR valid_until BETWEEN DATE '1900-01-01' AND DATE '2200-12-31') AND
        (issued_on IS NULL OR valid_until IS NULL OR valid_until>=issued_on));

CREATE TABLE document_metadata_revisions (
    company_id uuid NOT NULL, document_id uuid NOT NULL,
    version bigint NOT NULL CHECK(version BETWEEN 0 AND 999),
    document_version bigint NOT NULL CHECK(document_version>=0),
    category varchar(32) NOT NULL, identity_number varchar(120), issuing_country varchar(2),
    issued_on date, valid_until date,
    actor_id uuid REFERENCES accounts(id), recorded_at timestamptz NOT NULL, reason varchar(1000),
    PRIMARY KEY(company_id,document_id,version),
    FOREIGN KEY(company_id,document_id) REFERENCES documents(company_id,id),
    CHECK(category IN ('PASSPORT','NATIONAL_ID','WORK_PERMIT','RESIDENCE_PERMIT','EMPLOYMENT_CONTRACT','CERTIFICATE','RECEIPT','OTHER') AND
        (identity_number IS NULL OR (length(identity_number) BETWEEN 1 AND 120 AND identity_number=btrim(identity_number) AND identity_number !~ '[[:cntrl:]]')) AND
        (issuing_country IS NULL OR issuing_country ~ '^[A-Z]{2}$') AND
        (issued_on IS NULL OR issued_on BETWEEN DATE '1900-01-01' AND DATE '2200-12-31') AND
        (valid_until IS NULL OR valid_until BETWEEN DATE '1900-01-01' AND DATE '2200-12-31') AND
        (issued_on IS NULL OR valid_until IS NULL OR valid_until>=issued_on)),
    CHECK((version=0 AND reason IS NULL) OR (version>0 AND actor_id IS NOT NULL AND
        length(btrim(reason))>0 AND reason !~ '[[:cntrl:]]')),
    CHECK(version=0 OR reason IS NOT NULL)
);

-- A migration is not an employee/administrator verification event.
INSERT INTO document_metadata_revisions(company_id,document_id,version,document_version,
    category,actor_id,recorded_at)
SELECT company_id,id,0,version,'OTHER',NULL,transaction_timestamp() FROM documents;

ALTER TABLE documents ADD CONSTRAINT document_metadata_head
    FOREIGN KEY(company_id,id,metadata_version)
    REFERENCES document_metadata_revisions(company_id,document_id,version)
    DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE document_metadata_revisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_metadata_revisions FORCE ROW LEVEL SECURITY;
CREATE POLICY document_metadata_scope ON document_metadata_revisions
    USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
CREATE POLICY document_metadata_actor ON document_metadata_revisions AS RESTRICTIVE FOR INSERT
    WITH CHECK(actor_id=current_actor_id());
CREATE TRIGGER immutable_document_metadata BEFORE UPDATE OR DELETE ON document_metadata_revisions
    FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

CREATE FUNCTION validate_document_metadata_revision() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE h documents%ROWTYPE;
BEGIN
    SELECT * INTO h FROM documents WHERE company_id=NEW.company_id AND id=NEW.document_id;
    IF NOT FOUND OR (h.metadata_version,h.version,h.category,h.identity_number,h.issuing_country,h.issued_on,h.valid_until)
        IS DISTINCT FROM (NEW.version,NEW.document_version,NEW.category,NEW.identity_number,NEW.issuing_country,NEW.issued_on,NEW.valid_until) THEN
        RAISE EXCEPTION 'Document metadata history must match the current head' USING ERRCODE='23514';END IF;
    IF NEW.version=0 THEN
        IF h.version<>0 OR NEW.actor_id IS DISTINCT FROM h.created_by OR NEW.recorded_at<>h.created_at THEN
            RAISE EXCEPTION 'Initial document metadata must identify its creation' USING ERRCODE='23514';END IF;
    ELSIF NOT EXISTS(SELECT 1 FROM document_metadata_revisions r WHERE r.company_id=NEW.company_id
        AND r.document_id=NEW.document_id AND r.version=NEW.version-1 AND r.document_version<NEW.document_version) THEN
        RAISE EXCEPTION 'Document metadata history must be consecutive' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER validate_document_metadata_revision BEFORE INSERT ON document_metadata_revisions
    FOR EACH ROW EXECUTE FUNCTION validate_document_metadata_revision();

CREATE FUNCTION initialize_document_metadata() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    INSERT INTO document_metadata_revisions(company_id,document_id,version,document_version,
        category,identity_number,issuing_country,issued_on,valid_until,actor_id,recorded_at)
    VALUES(NEW.company_id,NEW.id,0,0,NEW.category,NEW.identity_number,NEW.issuing_country,
        NEW.issued_on,NEW.valid_until,NEW.created_by,NEW.created_at);
    RETURN NEW;
END $$;
CREATE TRIGGER initialize_document_metadata AFTER INSERT ON documents
    FOR EACH ROW EXECUTE FUNCTION initialize_document_metadata();

CREATE OR REPLACE FUNCTION initialize_document_header() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NEW.version<>0 OR NEW.metadata_version<>0 OR NEW.revision_count<>0 OR NEW.current_revision_id IS NOT NULL THEN
        RAISE EXCEPTION 'Documents must start without revisions' USING ERRCODE='23514';END IF;RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION protect_document_header() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF (NEW.company_id,NEW.id,NEW.employment_id,NEW.classification,NEW.title,NEW.created_by,NEW.created_at)
        IS DISTINCT FROM (OLD.company_id,OLD.id,OLD.employment_id,OLD.classification,OLD.title,OLD.created_by,OLD.created_at)
        OR NEW.version<>OLD.version+1 THEN RAISE EXCEPTION 'Document identity is immutable and changes must be versioned' USING ERRCODE='42501';END IF;
    IF NEW.metadata_version=OLD.metadata_version+1 THEN
        IF NEW.revision_count<>OLD.revision_count OR NEW.current_revision_id IS DISTINCT FROM OLD.current_revision_id THEN
            RAISE EXCEPTION 'Metadata edits cannot change content revisions' USING ERRCODE='23514';END IF;
        IF (NEW.category,NEW.identity_number,NEW.issuing_country,NEW.issued_on,NEW.valid_until)
            IS NOT DISTINCT FROM (OLD.category,OLD.identity_number,OLD.issuing_country,OLD.issued_on,OLD.valid_until) THEN
            RAISE EXCEPTION 'Metadata edits must change a business fact' USING ERRCODE='23514';END IF;
        IF EXISTS(SELECT 1 FROM document_retention_state_views s WHERE s.company_id=NEW.company_id
            AND s.document_id=NEW.id AND s.version=s.current_version AND s.archived_at IS NOT NULL) THEN
            RAISE EXCEPTION 'Archived document metadata is immutable' USING ERRCODE='23514';END IF;
        RETURN NEW;
    ELSIF NEW.metadata_version<>OLD.metadata_version OR
        (NEW.category,NEW.identity_number,NEW.issuing_country,NEW.issued_on,NEW.valid_until)
        IS DISTINCT FROM (OLD.category,OLD.identity_number,OLD.issuing_country,OLD.issued_on,OLD.valid_until) THEN
        RAISE EXCEPTION 'Metadata changes require the next metadata revision' USING ERRCODE='23514';END IF;
    IF NEW.revision_count=OLD.revision_count+1 THEN
        IF NEW.current_revision_id IS DISTINCT FROM OLD.current_revision_id THEN RAISE EXCEPTION 'Uploads cannot change the published revision' USING ERRCODE='42501';END IF;
    ELSIF NEW.revision_count=OLD.revision_count AND NEW.current_revision_id IS DISTINCT FROM OLD.current_revision_id THEN
        IF NEW.current_revision_id IS NULL THEN
            IF NOT EXISTS(SELECT 1 FROM document_revisions r JOIN document_retention_state_views s ON s.company_id=r.company_id AND s.document_id=r.document_id AND s.version=s.current_version
                WHERE r.company_id=NEW.company_id AND r.id=OLD.current_revision_id AND r.status='RETIRED' AND s.archived_at IS NOT NULL) THEN
                RAISE EXCEPTION 'Publication can only be cleared by archived content retirement' USING ERRCODE='23514';END IF;
        ELSIF NOT EXISTS(SELECT 1 FROM document_revisions r WHERE r.company_id=NEW.company_id AND r.document_id=NEW.id AND r.id=NEW.current_revision_id AND r.status='READY'
            AND r.revision_no>coalesce((SELECT revision_no FROM document_revisions WHERE company_id=OLD.company_id AND id=OLD.current_revision_id),0)) THEN
            RAISE EXCEPTION 'Only a newer ready revision can be published' USING ERRCODE='23514';END IF;
    ELSE RAISE EXCEPTION 'Document revision sequence is invalid' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;


-- Validate each captured head, including multiple content/metadata transitions in one transaction.
CREATE FUNCTION require_document_metadata_history() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r document_metadata_revisions%ROWTYPE;
BEGIN
    SELECT * INTO r FROM document_metadata_revisions WHERE company_id=NEW.company_id
        AND document_id=NEW.id AND version=NEW.metadata_version;
    IF NOT FOUND OR r.document_version>NEW.version OR
        (r.category,r.identity_number,r.issuing_country,r.issued_on,r.valid_until)
        IS DISTINCT FROM (NEW.category,NEW.identity_number,NEW.issuing_country,NEW.issued_on,NEW.valid_until) THEN
        RAISE EXCEPTION 'Document metadata requires matching immutable history' USING ERRCODE='23514';END IF;
    IF TG_OP='UPDATE' AND NEW.metadata_version<>OLD.metadata_version AND r.document_version<>NEW.version THEN
        RAISE EXCEPTION 'Metadata history must identify its document version' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER require_document_metadata_history AFTER INSERT OR UPDATE ON documents
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_document_metadata_history();

REVOKE ALL ON FUNCTION validate_document_metadata_revision() FROM PUBLIC;
REVOKE ALL ON FUNCTION initialize_document_metadata() FROM PUBLIC;
REVOKE ALL ON FUNCTION require_document_metadata_history() FROM PUBLIC;
