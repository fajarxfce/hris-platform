ALTER TABLE document_revisions DROP CONSTRAINT document_revisions_status_check;
ALTER TABLE document_revisions ADD CHECK(status IN ('UPLOADING','VALIDATING','VALIDATION_FAILED','READY','REJECTED','CANCELLED','EXPIRED'));
ALTER TABLE document_revisions ADD COLUMN validation_job_id uuid,ADD COLUMN validation_attempts integer NOT NULL DEFAULT 0 CHECK(validation_attempts BETWEEN 0 AND 8),
    ADD COLUMN failure_code varchar(80),ADD COLUMN content_bytes bigint,ADD COLUMN content_sha256 char(64),ADD COLUMN detected_media_type varchar(128),
    ADD COLUMN scan_clean boolean,ADD COLUMN scanner_version varchar(200),ADD COLUMN validated_at timestamptz;
ALTER TABLE document_revisions ADD CHECK(
    (status IN ('READY','REJECTED') AND content_bytes IS NOT NULL AND content_bytes BETWEEN 1 AND 104857600 AND content_sha256 IS NOT NULL AND content_sha256 ~ '^[0-9a-f]{64}$' AND detected_media_type IS NOT NULL AND length(trim(detected_media_type))>0
        AND scan_clean IS NOT NULL AND scanner_version IS NOT NULL AND length(trim(scanner_version))>0 AND validated_at IS NOT NULL)
    OR (status NOT IN ('READY','REJECTED') AND content_bytes IS NULL AND content_sha256 IS NULL AND detected_media_type IS NULL
        AND scan_clean IS NULL AND scanner_version IS NULL AND validated_at IS NULL));
ALTER TABLE document_revisions ADD CHECK(status<>'READY' OR (content_bytes=expected_bytes AND content_sha256=expected_sha256 AND detected_media_type=media_type AND scan_clean=true AND failure_code IS NULL));
ALTER TABLE document_revisions ADD CHECK(status NOT IN ('VALIDATING','VALIDATION_FAILED','READY','REJECTED') OR (validation_job_id IS NOT NULL AND validation_attempts>0 AND uploaded_bytes=expected_bytes));
ALTER TABLE document_revisions ADD CHECK(status NOT IN ('REJECTED','VALIDATION_FAILED') OR failure_code IS NOT NULL);
DROP INDEX one_active_document_revision;
CREATE UNIQUE INDEX one_active_document_revision ON document_revisions(company_id,document_id) WHERE status IN ('UPLOADING','VALIDATING','VALIDATION_FAILED');
DROP INDEX document_revision_expiry;
CREATE INDEX document_revision_expiry ON document_revisions(company_id,expires_at) WHERE status IN ('UPLOADING','VALIDATING','VALIDATION_FAILED');
CREATE TABLE document_validation_attempts (
    company_id uuid NOT NULL,revision_id uuid NOT NULL,job_id uuid NOT NULL,attempt integer NOT NULL CHECK(attempt BETWEEN 1 AND 8),
    actor_id uuid NOT NULL REFERENCES accounts(id),created_at timestamptz NOT NULL DEFAULT now(),reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    PRIMARY KEY(company_id,revision_id,job_id),UNIQUE(company_id,revision_id,attempt),UNIQUE(company_id,job_id),
    FOREIGN KEY(company_id,revision_id) REFERENCES document_revisions(company_id,id),FOREIGN KEY(company_id,job_id) REFERENCES background_jobs(company_id,id)
);
ALTER TABLE document_validation_attempts ENABLE ROW LEVEL SECURITY;ALTER TABLE document_validation_attempts FORCE ROW LEVEL SECURITY;
CREATE POLICY document_validation_attempt_scope ON document_validation_attempts USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
CREATE POLICY document_validation_attempt_actor ON document_validation_attempts AS RESTRICTIVE FOR INSERT TO PUBLIC WITH CHECK(actor_id=current_actor_id());
CREATE TRIGGER immutable_document_validation_attempt BEFORE UPDATE OR DELETE ON document_validation_attempts FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
ALTER TABLE document_revisions ADD FOREIGN KEY(company_id,id,validation_job_id) REFERENCES document_validation_attempts(company_id,revision_id,job_id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE documents ADD COLUMN current_revision_id uuid;
ALTER TABLE documents ADD FOREIGN KEY(company_id,id,current_revision_id) REFERENCES document_revisions(company_id,document_id,id);
CREATE OR REPLACE FUNCTION protect_document_header() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF (NEW.company_id,NEW.id,NEW.employment_id,NEW.classification,NEW.title,NEW.created_by,NEW.created_at)
        IS DISTINCT FROM (OLD.company_id,OLD.id,OLD.employment_id,OLD.classification,OLD.title,OLD.created_by,OLD.created_at)
        OR NEW.version<>OLD.version+1 THEN RAISE EXCEPTION 'Document identity is immutable and changes must be versioned' USING ERRCODE='42501';END IF;
    IF NEW.revision_count=OLD.revision_count+1 THEN
        IF NEW.current_revision_id IS DISTINCT FROM OLD.current_revision_id THEN RAISE EXCEPTION 'Uploads cannot change the published revision' USING ERRCODE='42501';END IF;
    ELSIF NEW.revision_count=OLD.revision_count AND NEW.current_revision_id IS DISTINCT FROM OLD.current_revision_id THEN
        IF NOT EXISTS(SELECT 1 FROM document_revisions r WHERE r.company_id=NEW.company_id AND r.document_id=NEW.id AND r.id=NEW.current_revision_id AND r.status='READY'
            AND r.revision_no>coalesce((SELECT revision_no FROM document_revisions WHERE company_id=OLD.company_id AND id=OLD.current_revision_id),0)) THEN
            RAISE EXCEPTION 'Only a newer ready revision can be published' USING ERRCODE='23514';END IF;
    ELSE RAISE EXCEPTION 'Document revision sequence is invalid' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE OR REPLACE FUNCTION protect_document_revision() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF (NEW.company_id,NEW.id,NEW.document_id,NEW.revision_no,NEW.file_name,NEW.media_type,NEW.expected_bytes,NEW.expected_sha256,NEW.created_by,NEW.created_at,NEW.expires_at,NEW.reason)
        IS DISTINCT FROM (OLD.company_id,OLD.id,OLD.document_id,OLD.revision_no,OLD.file_name,OLD.media_type,OLD.expected_bytes,OLD.expected_sha256,OLD.created_by,OLD.created_at,OLD.expires_at,OLD.reason)
        OR OLD.status IN ('READY','REJECTED','CANCELLED','EXPIRED') OR NEW.version<>OLD.version+1 OR NEW.uploaded_bytes<OLD.uploaded_bytes THEN
        RAISE EXCEPTION 'Document revision inputs and terminal states are immutable' USING ERRCODE='42501';END IF;
    IF (OLD.status='UPLOADING' AND NEW.status NOT IN ('UPLOADING','VALIDATING','CANCELLED','EXPIRED'))
        OR (OLD.status='VALIDATING' AND NEW.status NOT IN ('VALIDATING','VALIDATION_FAILED','READY','REJECTED','CANCELLED','EXPIRED'))
        OR (OLD.status='VALIDATION_FAILED' AND NEW.status NOT IN ('VALIDATING','CANCELLED','EXPIRED')) THEN
        RAISE EXCEPTION 'Invalid document validation transition' USING ERRCODE='23514';END IF;
    IF NEW.uploaded_bytes<>OLD.uploaded_bytes AND (OLD.status<>'UPLOADING' OR NEW.status<>'UPLOADING') THEN
        RAISE EXCEPTION 'Uploaded content cannot change during validation' USING ERRCODE='42501';END IF;
    IF NEW.validation_job_id IS DISTINCT FROM OLD.validation_job_id THEN
        IF NEW.status<>'VALIDATING' OR NEW.validation_attempts<>OLD.validation_attempts+1 OR NOT EXISTS(SELECT 1 FROM document_validation_attempts a WHERE a.company_id=NEW.company_id AND a.revision_id=NEW.id AND a.job_id=NEW.validation_job_id AND a.attempt=NEW.validation_attempts) THEN
            RAISE EXCEPTION 'New validation jobs require a sequenced attempt' USING ERRCODE='23514';END IF;
    ELSIF NEW.validation_attempts<>OLD.validation_attempts THEN RAISE EXCEPTION 'Validation attempts require a new job' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE FUNCTION initialize_document_header() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NEW.version<>0 OR NEW.revision_count<>0 OR NEW.current_revision_id IS NOT NULL THEN RAISE EXCEPTION 'Documents must start without revisions' USING ERRCODE='23514';END IF;RETURN NEW;
END $$;
CREATE TRIGGER initialize_document_header BEFORE INSERT ON documents FOR EACH ROW EXECUTE FUNCTION initialize_document_header();
CREATE FUNCTION initialize_document_revision() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NEW.status<>'UPLOADING' OR NEW.version<>0 OR NEW.uploaded_bytes<>0 OR NEW.validation_attempts<>0 OR NEW.validation_job_id IS NOT NULL OR NEW.failure_code IS NOT NULL THEN
        RAISE EXCEPTION 'Document revisions must start as empty uploads' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER initialize_document_revision BEFORE INSERT ON document_revisions FOR EACH ROW EXECUTE FUNCTION initialize_document_revision();
CREATE OR REPLACE FUNCTION protect_document_chunk() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF (NEW.company_id,NEW.revision_id,NEW.id,NEW.ordinal,NEW.byte_offset,NEW.byte_count,NEW.sha256,NEW.created_by)
        IS DISTINCT FROM (OLD.company_id,OLD.revision_id,OLD.id,OLD.ordinal,OLD.byte_offset,OLD.byte_count,OLD.sha256,OLD.created_by)
        OR OLD.status='COMMITTED' OR NEW.attempts<OLD.attempts OR NEW.attempts>OLD.attempts+1 THEN
        RAISE EXCEPTION 'Chunk identity and committed results are immutable' USING ERRCODE='42501';END IF;
    IF NEW.current_attempt_id IS DISTINCT FROM OLD.current_attempt_id AND NEW.attempts<>OLD.attempts+1 THEN
        RAISE EXCEPTION 'New upload attempts must increase the counter' USING ERRCODE='23514';END IF;
    IF NEW.current_attempt_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM document_upload_attempts a WHERE a.company_id=NEW.company_id AND a.revision_id=NEW.revision_id
        AND a.chunk_id=NEW.id AND a.id=NEW.current_attempt_id AND a.object_key=NEW.object_key AND a.byte_count=NEW.byte_count) THEN
        RAISE EXCEPTION 'The chunk key must match its immutable write attempt' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER preserve_documents BEFORE DELETE ON documents FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE TRIGGER preserve_document_revisions BEFORE DELETE ON document_revisions FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE TRIGGER preserve_document_chunks BEFORE DELETE ON document_upload_chunks FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

CREATE INDEX document_revision_validation_job ON document_revisions(company_id,validation_job_id) WHERE validation_job_id IS NOT NULL;
CREATE INDEX document_revision_ready_size ON document_revisions(company_id) INCLUDE(expected_bytes) WHERE status='READY';
