-- Retirement keeps metadata and accepted inspection evidence; physical deletion is deferred.
ALTER TABLE document_retention_changes DROP CONSTRAINT document_retention_changes_kind_check;
ALTER TABLE document_retention_changes ADD CONSTRAINT document_retention_kind CHECK(kind IN ('ARCHIVE','RESTORE','PLACE_HOLD','RELEASE_HOLD','RETIRE'));
ALTER TABLE document_retention_changes ADD COLUMN retired_revision_id uuid;
ALTER TABLE document_retention_changes ADD CONSTRAINT document_retention_retired_reference CHECK((kind='RETIRE')=(retired_revision_id IS NOT NULL));
ALTER TABLE document_retention_changes ADD UNIQUE(company_id,document_id,version,retired_revision_id);
CREATE TABLE document_retirements (
    company_id uuid NOT NULL,document_id uuid NOT NULL,revision_id uuid NOT NULL,
    revision_version bigint NOT NULL CHECK(revision_version>0),retention_version bigint NOT NULL CHECK(retention_version BETWEEN 1 AND 9999),
    actor_id uuid NOT NULL REFERENCES accounts(id),retired_at timestamptz NOT NULL,delete_after timestamptz NOT NULL,
    reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    PRIMARY KEY(company_id,revision_id),UNIQUE(company_id,document_id,revision_id,revision_version),
    UNIQUE(company_id,document_id,retention_version,revision_id),
    CHECK(delete_after>=retired_at+interval '5 minutes'),
    FOREIGN KEY(company_id,document_id,revision_id) REFERENCES document_revisions(company_id,document_id,id),
    FOREIGN KEY(company_id,document_id,retention_version,revision_id) REFERENCES document_retention_changes(company_id,document_id,version,retired_revision_id) DEFERRABLE INITIALLY DEFERRED
);
ALTER TABLE document_retirements ENABLE ROW LEVEL SECURITY;ALTER TABLE document_retirements FORCE ROW LEVEL SECURITY;
CREATE POLICY retirement_scope ON document_retirements USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
CREATE POLICY retirement_actor ON document_retirements AS RESTRICTIVE FOR INSERT WITH CHECK(actor_id=current_actor_id());
CREATE TRIGGER preserve_document_retirement BEFORE UPDATE OR DELETE ON document_retirements FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
ALTER TABLE document_retention_changes ADD FOREIGN KEY(company_id,document_id,version,retired_revision_id)
    REFERENCES document_retirements(company_id,document_id,retention_version,revision_id) DEFERRABLE INITIALLY DEFERRED;

-- Replace only checks that own the revision status lifecycle; retain byte, digest and counter bounds.
DO $$ DECLARE item record; status_attribute smallint; BEGIN
    SELECT attnum INTO status_attribute FROM pg_attribute WHERE attrelid='document_revisions'::regclass AND attname='status';
    FOR item IN SELECT conname FROM pg_constraint WHERE conrelid='document_revisions'::regclass AND contype='c' AND status_attribute=ANY(conkey) LOOP
        EXECUTE format('ALTER TABLE document_revisions DROP CONSTRAINT %I',item.conname);
    END LOOP;
END $$;
ALTER TABLE document_revisions ADD CONSTRAINT document_revision_status CHECK(status IN ('UPLOADING','VALIDATING','VALIDATION_FAILED','READY','REJECTED','CANCELLED','EXPIRED','RETIRED'));
ALTER TABLE document_revisions ADD CONSTRAINT document_revision_content_proof CHECK(
    (status IN ('READY','REJECTED','RETIRED') AND content_bytes IS NOT NULL AND content_bytes BETWEEN 1 AND 104857600 AND content_sha256 IS NOT NULL AND content_sha256 ~ '^[0-9a-f]{64}$' AND detected_media_type IS NOT NULL AND length(trim(detected_media_type))>0
        AND scan_clean IS NOT NULL AND scanner_version IS NOT NULL AND length(trim(scanner_version))>0 AND validated_at IS NOT NULL)
    OR (status NOT IN ('READY','REJECTED','RETIRED') AND content_bytes IS NULL AND content_sha256 IS NULL AND detected_media_type IS NULL
        AND scan_clean IS NULL AND scanner_version IS NULL AND validated_at IS NULL));
ALTER TABLE document_revisions ADD CONSTRAINT document_revision_accepted_proof CHECK(status NOT IN ('READY','RETIRED') OR (content_bytes=expected_bytes AND content_sha256=expected_sha256 AND detected_media_type=media_type AND scan_clean=true AND failure_code IS NULL));
ALTER TABLE document_revisions ADD CONSTRAINT document_revision_validation_required CHECK(status NOT IN ('VALIDATING','VALIDATION_FAILED','READY','REJECTED','RETIRED') OR (validation_job_id IS NOT NULL AND validation_attempts>0 AND uploaded_bytes=expected_bytes));
ALTER TABLE document_revisions ADD CONSTRAINT document_revision_failure_required CHECK(status NOT IN ('REJECTED','VALIDATION_FAILED') OR failure_code IS NOT NULL);
ALTER TABLE document_revisions ADD COLUMN retired_revision_id uuid GENERATED ALWAYS AS (CASE WHEN status='RETIRED' THEN id END) STORED;
ALTER TABLE document_revisions ADD FOREIGN KEY(company_id,document_id,retired_revision_id,version)
    REFERENCES document_retirements(company_id,document_id,revision_id,revision_version) DEFERRABLE INITIALLY DEFERRED;

CREATE OR REPLACE VIEW document_retention_state_views WITH(security_invoker=true) AS
    SELECT c.company_id,c.document_id,c.version,c.kind,c.legal_hold,c.archived_at,c.policy_id,c.policy_version,c.retention_days,c.eligible_at,c.actor_id,c.recorded_at,c.reason,
        s.version AS current_version,c.retired_revision_id
    FROM document_retention_states s JOIN document_retention_changes c ON c.company_id=s.company_id AND c.document_id=s.document_id;

CREATE OR REPLACE FUNCTION validate_document_retention_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE prior document_retention_changes%ROWTYPE;
BEGIN
    IF NOT EXISTS(SELECT 1 FROM document_retention_states s WHERE s.company_id=NEW.company_id AND s.document_id=NEW.document_id AND s.version=NEW.version) THEN
        RAISE EXCEPTION 'Retention evidence must match the current state' USING ERRCODE='23514';END IF;
    SELECT * INTO prior FROM document_retention_changes WHERE company_id=NEW.company_id AND document_id=NEW.document_id AND version=NEW.version-1;
    IF NEW.version>0 AND prior IS NULL THEN RAISE EXCEPTION 'Retention history cannot skip revisions' USING ERRCODE='23514';END IF;
    IF NEW.kind='ARCHIVE' THEN
        IF prior.archived_at IS NOT NULL OR NEW.archived_at IS DISTINCT FROM NEW.recorded_at OR NEW.legal_hold<>coalesce(prior.legal_hold,false) THEN
            RAISE EXCEPTION 'Invalid archival transition' USING ERRCODE='23514';END IF;
        IF EXISTS(SELECT 1 FROM document_revisions r WHERE r.company_id=NEW.company_id AND r.document_id=NEW.document_id AND r.status IN ('UPLOADING','VALIDATING','VALIDATION_FAILED')) THEN
            RAISE EXCEPTION 'Cancel unfinished document revisions before archival' USING ERRCODE='23514';END IF;
        IF NEW.policy_id IS NULL AND EXISTS(SELECT 1 FROM document_retention_policies p JOIN documents d ON d.company_id=p.company_id AND d.classification=p.classification WHERE d.company_id=NEW.company_id AND d.id=NEW.document_id) THEN
            RAISE EXCEPTION 'Archival must retain the configured classification policy' USING ERRCODE='23514';END IF;
        IF NEW.policy_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM document_retention_policies p JOIN document_retention_policy_revisions r ON r.company_id=p.company_id AND r.policy_id=p.id AND r.version=p.version
            JOIN documents d ON d.company_id=p.company_id AND d.id=NEW.document_id AND d.classification=p.classification
            WHERE p.company_id=NEW.company_id AND p.id=NEW.policy_id AND p.version=NEW.policy_version AND r.retention_days IS NOT DISTINCT FROM NEW.retention_days) THEN
            RAISE EXCEPTION 'Archival must retain its current classification policy' USING ERRCODE='23514';END IF;
    ELSIF NEW.kind='RESTORE' THEN
        IF prior.archived_at IS NULL OR NEW.archived_at IS NOT NULL OR NEW.legal_hold<>prior.legal_hold THEN
            RAISE EXCEPTION 'Invalid restore transition' USING ERRCODE='23514';END IF;
    ELSIF NEW.kind='RETIRE' THEN
        IF prior.archived_at IS NULL OR prior.eligible_at IS NULL OR prior.eligible_at>NEW.recorded_at OR prior.legal_hold OR NEW.legal_hold OR
            (NEW.archived_at,NEW.policy_id,NEW.policy_version,NEW.retention_days,NEW.eligible_at) IS DISTINCT FROM
            (prior.archived_at,prior.policy_id,prior.policy_version,prior.retention_days,prior.eligible_at) THEN
            RAISE EXCEPTION 'Retirement requires an eligible archive without legal hold' USING ERRCODE='23514';END IF;
    ELSE
        IF (NEW.archived_at,NEW.policy_id,NEW.policy_version,NEW.retention_days,NEW.eligible_at) IS DISTINCT FROM
            (prior.archived_at,prior.policy_id,prior.policy_version,prior.retention_days,prior.eligible_at) OR
            (NEW.kind='PLACE_HOLD' AND (coalesce(prior.legal_hold,false) OR NOT NEW.legal_hold)) OR
            (NEW.kind='RELEASE_HOLD' AND (NOT coalesce(prior.legal_hold,false) OR NEW.legal_hold)) THEN
            RAISE EXCEPTION 'Hold changes must preserve the archival snapshot' USING ERRCODE='23514';END IF;
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION validate_document_retirement() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM document_revisions r WHERE r.company_id=NEW.company_id AND r.document_id=NEW.document_id AND r.id=NEW.revision_id AND r.status='READY' AND NEW.revision_version=r.version+1) OR
        NOT EXISTS(SELECT 1 FROM document_retention_states h JOIN document_retention_changes c ON c.company_id=h.company_id AND c.document_id=h.document_id AND c.version=h.version
            WHERE h.company_id=NEW.company_id AND h.document_id=NEW.document_id AND h.version=NEW.retention_version AND c.kind='RETIRE' AND c.retired_revision_id=NEW.revision_id
            AND c.recorded_at=NEW.retired_at AND c.actor_id=NEW.actor_id AND c.reason=NEW.reason) OR
        EXISTS(SELECT 1 FROM document_evidence_references x WHERE x.company_id=NEW.company_id AND x.document_revision_id=NEW.revision_id) THEN
        RAISE EXCEPTION 'Retirement requires unreferenced accepted content and matching lifecycle evidence' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER document_retirement_scope BEFORE INSERT ON document_retirements FOR EACH ROW EXECUTE FUNCTION validate_document_retirement();
CREATE FUNCTION finish_document_retirement() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE revision document_revisions%ROWTYPE; count_parts integer; content_size bigint;
BEGIN
    SELECT * INTO revision FROM document_revisions WHERE company_id=NEW.company_id AND id=NEW.revision_id;
    SELECT count(*),coalesce(sum(byte_count),0) INTO count_parts,content_size FROM document_upload_chunks WHERE company_id=NEW.company_id AND revision_id=NEW.revision_id;
    IF revision.status<>'RETIRED' OR revision.version<>NEW.revision_version OR count_parts NOT BETWEEN 1 AND 100 OR content_size<>revision.expected_bytes OR
        EXISTS(SELECT 1 FROM documents d WHERE d.company_id=NEW.company_id AND d.id=NEW.document_id AND d.current_revision_id=NEW.revision_id) OR
        EXISTS(SELECT 1 FROM document_upload_chunks c WHERE c.company_id=NEW.company_id AND c.revision_id=NEW.revision_id AND
            (c.status<>'COMMITTED' OR NOT EXISTS(SELECT 1 FROM object_cleanup_queue q WHERE q.company_id=c.company_id AND q.id=c.current_attempt_id AND q.resource_id=c.revision_id
                AND q.object_key=c.object_key AND q.object_bytes=c.byte_count AND q.status='PENDING' AND q.attempts=0 AND q.created_by=NEW.actor_id AND q.eligible_at>=NEW.delete_after))) THEN
        RAISE EXCEPTION 'Retirement must remove publication and register every accepted object atomically' USING ERRCODE='23514';END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER document_retirement_commit AFTER INSERT ON document_retirements DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION finish_document_retirement();
CREATE FUNCTION protect_retired_document_reference() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF EXISTS(SELECT 1 FROM document_revisions WHERE company_id=NEW.company_id AND id=NEW.document_revision_id AND status='RETIRED') THEN
        RAISE EXCEPTION 'Retired content cannot become new business evidence' USING ERRCODE='23514';END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER document_reference_available BEFORE INSERT ON document_evidence_references FOR EACH ROW EXECUTE FUNCTION protect_retired_document_reference();

CREATE OR REPLACE FUNCTION protect_document_revision() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF OLD.status='READY' AND NEW.status='RETIRED' THEN
        IF NEW.version<>OLD.version+1 OR
            (to_jsonb(NEW)-'version'-'status'-'retired_revision_id') IS DISTINCT FROM (to_jsonb(OLD)-'version'-'status'-'retired_revision_id') OR
            NOT EXISTS(SELECT 1 FROM document_retirements e WHERE e.company_id=NEW.company_id AND e.document_id=NEW.document_id AND e.revision_id=NEW.id AND e.revision_version=NEW.version) THEN
            RAISE EXCEPTION 'Retirement must preserve accepted content proof and its immutable evidence' USING ERRCODE='23514';END IF;
        RETURN NEW;
    END IF;
    IF (NEW.company_id,NEW.id,NEW.document_id,NEW.revision_no,NEW.file_name,NEW.media_type,NEW.expected_bytes,NEW.expected_sha256,NEW.created_by,NEW.created_at,NEW.expires_at,NEW.reason)
        IS DISTINCT FROM (OLD.company_id,OLD.id,OLD.document_id,OLD.revision_no,OLD.file_name,OLD.media_type,OLD.expected_bytes,OLD.expected_sha256,OLD.created_by,OLD.created_at,OLD.expires_at,OLD.reason)
        OR OLD.status IN ('READY','REJECTED','CANCELLED','EXPIRED','RETIRED') OR NEW.version<>OLD.version+1 OR NEW.uploaded_bytes<OLD.uploaded_bytes THEN
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

CREATE OR REPLACE FUNCTION protect_document_header() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF (NEW.company_id,NEW.id,NEW.employment_id,NEW.classification,NEW.title,NEW.created_by,NEW.created_at)
        IS DISTINCT FROM (OLD.company_id,OLD.id,OLD.employment_id,OLD.classification,OLD.title,OLD.created_by,OLD.created_at)
        OR NEW.version<>OLD.version+1 THEN RAISE EXCEPTION 'Document identity is immutable and changes must be versioned' USING ERRCODE='42501';END IF;
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

CREATE OR REPLACE FUNCTION validate_document_inventory_recovery() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a document_upload_attempts%ROWTYPE; c document_upload_chunks%ROWTYPE; r document_revisions%ROWTYPE;
BEGIN
    SELECT * INTO a FROM document_upload_attempts WHERE company_id=NEW.company_id AND id=NEW.attempt_id;
    SELECT * INTO c FROM document_upload_chunks WHERE company_id=a.company_id AND revision_id=a.revision_id AND id=a.chunk_id;
    SELECT * INTO r FROM document_revisions WHERE company_id=a.company_id AND id=a.revision_id;
    IF NEW.recovery<>(SELECT count(*)+1 FROM document_inventory_recoveries WHERE company_id=NEW.company_id AND attempt_id=NEW.attempt_id) OR
        (c.current_attempt_id=NEW.attempt_id AND r.status='READY') OR
        NOT (c.current_attempt_id IS DISTINCT FROM NEW.attempt_id OR r.status IN ('CANCELLED','REJECTED','EXPIRED','RETIRED') OR
            (r.status IN ('UPLOADING','VALIDATING','VALIDATION_FAILED') AND r.expires_at<=NEW.created_at)) OR
        NOT EXISTS(SELECT 1 FROM document_inventory_runs h WHERE h.company_id=NEW.company_id AND h.id=NEW.run_id AND NEW.modified_at<=h.cutoff) OR
        NOT EXISTS(SELECT 1 FROM object_cleanup_queue q WHERE q.company_id=NEW.company_id AND q.id=NEW.attempt_id AND q.object_key=a.object_key AND q.resource_id=a.revision_id AND q.object_bytes=a.byte_count) THEN
        RAISE EXCEPTION 'Inventory cleanup requires a disposable registered attempt' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
