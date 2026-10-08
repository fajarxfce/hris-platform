CREATE TABLE documents (
    company_id uuid NOT NULL REFERENCES companies(id),id uuid NOT NULL,
    employment_id uuid NOT NULL,title varchar(160) NOT NULL CHECK(length(trim(title))>0),
    classification varchar(16) NOT NULL CHECK(classification IN ('PERSONAL','HR_ONLY','RECEIPT')),
    revision_count integer NOT NULL DEFAULT 0 CHECK(revision_count BETWEEN 0 AND 100),
    version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
    created_by uuid NOT NULL REFERENCES accounts(id),created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(company_id,id),FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id)
);
CREATE INDEX documents_employee ON documents(company_id,employment_id,id);
CREATE TABLE document_revisions (
    company_id uuid NOT NULL,id uuid NOT NULL,document_id uuid NOT NULL,
    revision_no integer NOT NULL CHECK(revision_no BETWEEN 1 AND 100),
    file_name varchar(180) NOT NULL,media_type varchar(40) NOT NULL CHECK(media_type IN ('application/pdf','image/jpeg','image/png')),
    expected_bytes bigint NOT NULL CHECK(expected_bytes BETWEEN 1 AND 104857600),
    expected_sha256 char(64) NOT NULL CHECK(expected_sha256 ~ '^[0-9a-f]{64}$'),
    status varchar(20) NOT NULL DEFAULT 'UPLOADING' CHECK(status IN ('UPLOADING','CANCELLED','EXPIRED')),
    uploaded_bytes bigint NOT NULL DEFAULT 0 CHECK(uploaded_bytes BETWEEN 0 AND expected_bytes),
    version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
    created_by uuid NOT NULL REFERENCES accounts(id),created_at timestamptz NOT NULL,expires_at timestamptz NOT NULL CHECK(expires_at>created_at),
    reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    PRIMARY KEY(company_id,id),UNIQUE(company_id,document_id,revision_no),UNIQUE(company_id,document_id,id),
    FOREIGN KEY(company_id,document_id) REFERENCES documents(company_id,id)
);
CREATE UNIQUE INDEX one_active_document_revision ON document_revisions(company_id,document_id) WHERE status='UPLOADING';
CREATE INDEX document_revision_expiry ON document_revisions(company_id,expires_at) WHERE status='UPLOADING';
CREATE TABLE document_upload_chunks (
    company_id uuid NOT NULL,revision_id uuid NOT NULL,id uuid NOT NULL,
    ordinal integer NOT NULL CHECK(ordinal BETWEEN 1 AND 100),
    byte_offset bigint NOT NULL CHECK(byte_offset>=0 AND byte_offset=(ordinal-1)::bigint*1048576),
    byte_count integer NOT NULL CHECK(byte_count BETWEEN 1 AND 1048576),
    sha256 char(64) NOT NULL CHECK(sha256 ~ '^[0-9a-f]{64}$'),
    created_by uuid NOT NULL REFERENCES accounts(id),
    status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','COMMITTED')),
    attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 8),
    current_attempt_id uuid,object_key varchar(300),lease_until timestamptz,
    etag varchar(200),committed_version bigint,
    PRIMARY KEY(company_id,revision_id,id),UNIQUE(company_id,revision_id,ordinal),
    FOREIGN KEY(company_id,revision_id) REFERENCES document_revisions(company_id,id),
    CHECK((current_attempt_id IS NULL AND object_key IS NULL) OR (current_attempt_id IS NOT NULL AND object_key IS NOT NULL)),
    CHECK(status<>'COMMITTED' OR (current_attempt_id IS NOT NULL AND etag IS NOT NULL AND committed_version IS NOT NULL AND lease_until IS NULL))
);
CREATE TABLE document_upload_attempts (
    company_id uuid NOT NULL,id uuid NOT NULL,revision_id uuid NOT NULL,chunk_id uuid NOT NULL,
    object_key varchar(300) NOT NULL UNIQUE,byte_count integer NOT NULL CHECK(byte_count BETWEEN 1 AND 1048576),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(company_id,id),UNIQUE(company_id,revision_id,chunk_id,id),
    FOREIGN KEY(company_id,revision_id,chunk_id) REFERENCES document_upload_chunks(company_id,revision_id,id),
    CHECK(object_key=company_id::text || '/' || revision_id::text || '/' || id::text)
);
ALTER TABLE document_upload_chunks ADD FOREIGN KEY(company_id,revision_id,id,current_attempt_id)
    REFERENCES document_upload_attempts(company_id,revision_id,chunk_id,id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE documents ENABLE ROW LEVEL SECURITY;ALTER TABLE documents FORCE ROW LEVEL SECURITY;
ALTER TABLE document_revisions ENABLE ROW LEVEL SECURITY;ALTER TABLE document_revisions FORCE ROW LEVEL SECURITY;
ALTER TABLE document_upload_chunks ENABLE ROW LEVEL SECURITY;ALTER TABLE document_upload_chunks FORCE ROW LEVEL SECURITY;
ALTER TABLE document_upload_attempts ENABLE ROW LEVEL SECURITY;ALTER TABLE document_upload_attempts FORCE ROW LEVEL SECURITY;
CREATE POLICY document_scope ON documents USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
CREATE POLICY document_creator ON documents AS RESTRICTIVE FOR INSERT TO PUBLIC WITH CHECK(created_by=current_actor_id());
CREATE POLICY document_revision_scope ON document_revisions USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
CREATE POLICY document_revision_creator ON document_revisions AS RESTRICTIVE FOR INSERT TO PUBLIC WITH CHECK(created_by=current_actor_id());
CREATE POLICY document_chunk_scope ON document_upload_chunks USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
CREATE POLICY document_chunk_creator ON document_upload_chunks AS RESTRICTIVE FOR INSERT TO PUBLIC WITH CHECK(created_by=current_actor_id());
CREATE POLICY document_attempt_scope ON document_upload_attempts USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
CREATE TRIGGER immutable_document_attempts BEFORE UPDATE OR DELETE ON document_upload_attempts FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE FUNCTION protect_document_header() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF (NEW.company_id,NEW.id,NEW.employment_id,NEW.classification,NEW.title,NEW.created_by,NEW.created_at)
        IS DISTINCT FROM (OLD.company_id,OLD.id,OLD.employment_id,OLD.classification,OLD.title,OLD.created_by,OLD.created_at)
        OR NEW.version<>OLD.version+1 OR NEW.revision_count<>OLD.revision_count+1 THEN
        RAISE EXCEPTION 'Document identity is immutable and revisions must be sequenced' USING ERRCODE='42501';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_document_header BEFORE UPDATE ON documents FOR EACH ROW EXECUTE FUNCTION protect_document_header();
CREATE FUNCTION protect_document_revision() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF (NEW.company_id,NEW.id,NEW.document_id,NEW.revision_no,NEW.file_name,NEW.media_type,NEW.expected_bytes,NEW.expected_sha256,NEW.created_by,NEW.created_at,NEW.expires_at,NEW.reason)
        IS DISTINCT FROM (OLD.company_id,OLD.id,OLD.document_id,OLD.revision_no,OLD.file_name,OLD.media_type,OLD.expected_bytes,OLD.expected_sha256,OLD.created_by,OLD.created_at,OLD.expires_at,OLD.reason)
        OR OLD.status IN ('CANCELLED','EXPIRED') OR NEW.version<>OLD.version+1 OR NEW.uploaded_bytes<OLD.uploaded_bytes THEN
        RAISE EXCEPTION 'Document revision inputs and terminal states are immutable' USING ERRCODE='42501';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_document_revision BEFORE UPDATE ON document_revisions FOR EACH ROW EXECUTE FUNCTION protect_document_revision();
CREATE FUNCTION protect_document_chunk() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF (NEW.company_id,NEW.revision_id,NEW.id,NEW.ordinal,NEW.byte_offset,NEW.byte_count,NEW.sha256,NEW.created_by)
        IS DISTINCT FROM (OLD.company_id,OLD.revision_id,OLD.id,OLD.ordinal,OLD.byte_offset,OLD.byte_count,OLD.sha256,OLD.created_by)
        OR OLD.status='COMMITTED' OR NEW.attempts<OLD.attempts OR NEW.attempts>OLD.attempts+1 THEN
        RAISE EXCEPTION 'Chunk identity and committed results are immutable' USING ERRCODE='42501';END IF;
    IF NEW.current_attempt_id IS DISTINCT FROM OLD.current_attempt_id AND NEW.attempts<>OLD.attempts+1 THEN
        RAISE EXCEPTION 'New upload attempts must increase the counter' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_document_chunk BEFORE UPDATE ON document_upload_chunks FOR EACH ROW EXECUTE FUNCTION protect_document_chunk();
