ALTER TABLE background_jobs ADD COLUMN progress_mode varchar(16) NOT NULL DEFAULT 'FIXED_TOTAL'
    CHECK(progress_mode IN ('FIXED_TOTAL','UPPER_BOUND'));
ALTER TABLE background_jobs ADD CONSTRAINT completed_job_progress CHECK(status<>'SUCCEEDED' OR
    (NOT cancellation_requested AND (progress_mode='UPPER_BOUND' OR completed_items=total_items)));
CREATE OR REPLACE FUNCTION protect_job_request() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF (OLD.id,OLD.company_id,OLD.actor_id,OLD.kind,OLD.operation_id,OLD.request,OLD.authenticated_at,OLD.credential_version,OLD.correlation_id,OLD.created_at,OLD.total_items,OLD.progress_mode)
        IS DISTINCT FROM (NEW.id,NEW.company_id,NEW.actor_id,NEW.kind,NEW.operation_id,NEW.request,NEW.authenticated_at,NEW.credential_version,NEW.correlation_id,NEW.created_at,NEW.total_items,NEW.progress_mode) THEN
        RAISE EXCEPTION 'Job requests are immutable' USING ERRCODE='42501'; END IF;
    IF OLD.status IN ('SUCCEEDED','FAILED','CANCELLED') AND OLD IS DISTINCT FROM NEW THEN
        RAISE EXCEPTION 'Terminal job results are immutable' USING ERRCODE='42501'; END IF;
    IF NEW.completed_items<OLD.completed_items THEN RAISE EXCEPTION 'Job progress cannot decrease' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TABLE document_inventory_runs (
    company_id uuid NOT NULL REFERENCES companies(id),id uuid NOT NULL,
    job_id uuid NOT NULL UNIQUE REFERENCES background_jobs(id),
    attempts integer NOT NULL CHECK(attempts BETWEEN 1 AND 8),version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
    status varchar(16) NOT NULL DEFAULT 'SCANNING' CHECK(status IN ('SCANNING','COMPLETED','LIMIT_REACHED')),
    started_at timestamptz NOT NULL,cutoff timestamptz NOT NULL CHECK(cutoff=started_at-interval '24 hours'),
    created_by uuid NOT NULL REFERENCES accounts(id),last_key bytea,
    pages integer NOT NULL DEFAULT 0 CHECK(pages BETWEEN 0 AND 10000),
    retained integer NOT NULL DEFAULT 0 CHECK(retained>=0),unknown integer NOT NULL DEFAULT 0 CHECK(unknown>=0),
    anomalous integer NOT NULL DEFAULT 0 CHECK(anomalous>=0),queued integer NOT NULL DEFAULT 0 CHECK(queued>=0),
    recovery_exhausted integer NOT NULL DEFAULT 0 CHECK(recovery_exhausted>=0),scheduled integer NOT NULL DEFAULT 0 CHECK(scheduled>=0),
    finished_at timestamptz,
    PRIMARY KEY(company_id,id),
    CHECK(last_key IS NULL OR (octet_length(last_key)<=1024 AND substring(last_key from 1 for 37)=convert_to(company_id::text||'/','UTF8'))),
    CHECK(retained+unknown+anomalous+queued+recovery_exhausted+scheduled<=pages*100),
    CHECK((status='SCANNING')=(finished_at IS NULL)),
    CHECK(status<>'LIMIT_REACHED' OR pages=10000)
);
CREATE TABLE document_inventory_attempts (
    company_id uuid NOT NULL,run_id uuid NOT NULL,job_id uuid NOT NULL REFERENCES background_jobs(id),
    attempt integer NOT NULL CHECK(attempt BETWEEN 1 AND 8),base_pages integer NOT NULL CHECK(base_pages BETWEEN 0 AND 9999),
    actor_id uuid NOT NULL REFERENCES accounts(id),created_at timestamptz NOT NULL,
    reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    PRIMARY KEY(company_id,run_id,job_id),UNIQUE(company_id,run_id,attempt),
    FOREIGN KEY(company_id,run_id) REFERENCES document_inventory_runs(company_id,id)
);
ALTER TABLE document_inventory_runs ADD FOREIGN KEY(company_id,id,job_id)
    REFERENCES document_inventory_attempts(company_id,run_id,job_id) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE document_inventory_pages (
    company_id uuid NOT NULL,run_id uuid NOT NULL,page_no integer NOT NULL CHECK(page_no BETWEEN 1 AND 10000),
    job_id uuid NOT NULL,created_at timestamptz NOT NULL,actor_id uuid NOT NULL REFERENCES accounts(id),
    has_more boolean NOT NULL,last_key bytea,
    retained integer NOT NULL CHECK(retained>=0),unknown integer NOT NULL CHECK(unknown>=0),
    anomalous integer NOT NULL CHECK(anomalous>=0),queued integer NOT NULL CHECK(queued>=0),
    recovery_exhausted integer NOT NULL CHECK(recovery_exhausted>=0),scheduled integer NOT NULL CHECK(scheduled>=0),
    PRIMARY KEY(company_id,run_id,page_no),
    FOREIGN KEY(company_id,run_id,job_id) REFERENCES document_inventory_attempts(company_id,run_id,job_id),
    CHECK(last_key IS NULL OR (octet_length(last_key)<=1024 AND substring(last_key from 1 for 37)=convert_to(company_id::text||'/','UTF8'))),
    CHECK(retained+unknown+anomalous+queued+recovery_exhausted+scheduled BETWEEN 0 AND 100),
    CHECK(NOT has_more OR retained+unknown+anomalous+queued+recovery_exhausted+scheduled>0)
);
CREATE TABLE document_inventory_recoveries (
    company_id uuid NOT NULL,attempt_id uuid NOT NULL,recovery integer NOT NULL CHECK(recovery BETWEEN 1 AND 3),
    run_id uuid NOT NULL,page_no integer NOT NULL,created_at timestamptz NOT NULL,actor_id uuid NOT NULL REFERENCES accounts(id),
    modified_at timestamptz NOT NULL,observed_etag varchar(200) NOT NULL CHECK(length(trim(observed_etag))>0),
    PRIMARY KEY(company_id,attempt_id,recovery),UNIQUE(company_id,run_id,attempt_id),
    FOREIGN KEY(company_id,attempt_id) REFERENCES document_upload_attempts(company_id,id),
    FOREIGN KEY(company_id,run_id,page_no) REFERENCES document_inventory_pages(company_id,run_id,page_no) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX document_inventory_active ON document_inventory_runs(company_id,id) WHERE status='SCANNING';
DO $$ DECLARE name text; BEGIN
    FOREACH name IN ARRAY ARRAY['document_inventory_runs','document_inventory_attempts','document_inventory_pages','document_inventory_recoveries'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',name);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',name);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',name);
    END LOOP;
    FOREACH name IN ARRAY ARRAY['document_inventory_attempts','document_inventory_pages','document_inventory_recoveries'] LOOP
        EXECUTE format('CREATE POLICY evidence_author ON %I AS RESTRICTIVE FOR INSERT WITH CHECK(actor_id=current_actor_id())',name);
        EXECUTE format('CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION deny_history_mutation()',name);
    END LOOP;
END $$;
CREATE POLICY inventory_creator ON document_inventory_runs AS RESTRICTIVE FOR INSERT WITH CHECK(created_by=current_actor_id());
CREATE TRIGGER preserve_inventory_history BEFORE DELETE ON document_inventory_runs FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE FUNCTION protect_document_inventory() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p document_inventory_pages%ROWTYPE; a document_inventory_attempts%ROWTYPE;
BEGIN
    IF (NEW.company_id,NEW.id,NEW.started_at,NEW.cutoff,NEW.created_by) IS DISTINCT FROM
        (OLD.company_id,OLD.id,OLD.started_at,OLD.cutoff,OLD.created_by) OR OLD.status<>'SCANNING' OR NEW.version<>OLD.version+1 THEN
        RAISE EXCEPTION 'Inventory identity and completed results are immutable' USING ERRCODE='42501'; END IF;
    IF NEW.job_id=OLD.job_id THEN
        SELECT * INTO p FROM document_inventory_pages WHERE company_id=NEW.company_id AND run_id=NEW.id AND page_no=NEW.pages;
        IF p IS NULL OR p.job_id<>NEW.job_id OR NEW.pages<>OLD.pages+1 OR NEW.attempts<>OLD.attempts OR
            (NEW.retained,NEW.unknown,NEW.anomalous,NEW.queued,NEW.recovery_exhausted,NEW.scheduled) IS DISTINCT FROM
            (OLD.retained+p.retained,OLD.unknown+p.unknown,OLD.anomalous+p.anomalous,OLD.queued+p.queued,OLD.recovery_exhausted+p.recovery_exhausted,OLD.scheduled+p.scheduled) OR
            NEW.last_key IS DISTINCT FROM p.last_key OR
            (p.retained+p.unknown+p.anomalous+p.queued+p.recovery_exhausted+p.scheduled>0 AND (NEW.last_key IS NULL OR
                (OLD.last_key IS NOT NULL AND NEW.last_key<=OLD.last_key))) OR
            (p.retained+p.unknown+p.anomalous+p.queued+p.recovery_exhausted+p.scheduled=0 AND NEW.last_key IS DISTINCT FROM OLD.last_key) OR
            NEW.status<>(CASE WHEN NOT p.has_more THEN 'COMPLETED' WHEN NEW.pages=10000 THEN 'LIMIT_REACHED' ELSE 'SCANNING' END) THEN
            RAISE EXCEPTION 'Inventory progress requires matching page evidence' USING ERRCODE='23514'; END IF;
    ELSE
        SELECT * INTO a FROM document_inventory_attempts WHERE company_id=NEW.company_id AND run_id=NEW.id AND job_id=NEW.job_id;
        IF a IS NULL OR NEW.attempts<>OLD.attempts+1 OR a.attempt<>NEW.attempts OR a.base_pages<>OLD.pages OR
            (NEW.pages,NEW.last_key,NEW.retained,NEW.unknown,NEW.anomalous,NEW.queued,NEW.recovery_exhausted,NEW.scheduled,NEW.status) IS DISTINCT FROM
            (OLD.pages,OLD.last_key,OLD.retained,OLD.unknown,OLD.anomalous,OLD.queued,OLD.recovery_exhausted,OLD.scheduled,OLD.status) OR
            NOT EXISTS(SELECT 1 FROM background_jobs WHERE id=OLD.job_id AND status IN ('FAILED','CANCELLED')) THEN
            RAISE EXCEPTION 'Inventory recovery must preserve its checkpoint' USING ERRCODE='23514'; END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER inventory_progress BEFORE UPDATE ON document_inventory_runs FOR EACH ROW EXECUTE FUNCTION protect_document_inventory();
CREATE FUNCTION validate_document_inventory_attempt() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM background_jobs j WHERE j.id=NEW.job_id AND j.company_id=NEW.company_id AND j.actor_id=NEW.actor_id AND
        j.kind='DOCUMENT_INVENTORY' AND j.progress_mode='UPPER_BOUND' AND j.total_items=10000-NEW.base_pages AND j.request->>'runId'=NEW.run_id::text) THEN
        RAISE EXCEPTION 'Inventory job scope does not match' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER inventory_attempt_scope BEFORE INSERT ON document_inventory_attempts FOR EACH ROW EXECUTE FUNCTION validate_document_inventory_attempt();
CREATE FUNCTION validate_document_inventory_recovery() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a document_upload_attempts%ROWTYPE; c document_upload_chunks%ROWTYPE; r document_revisions%ROWTYPE;
BEGIN
    SELECT * INTO a FROM document_upload_attempts WHERE company_id=NEW.company_id AND id=NEW.attempt_id;
    SELECT * INTO c FROM document_upload_chunks WHERE company_id=a.company_id AND revision_id=a.revision_id AND id=a.chunk_id;
    SELECT * INTO r FROM document_revisions WHERE company_id=a.company_id AND id=a.revision_id;
    IF NEW.recovery<>(SELECT count(*)+1 FROM document_inventory_recoveries WHERE company_id=NEW.company_id AND attempt_id=NEW.attempt_id) OR
        (c.current_attempt_id=NEW.attempt_id AND r.status='READY') OR
        NOT (c.current_attempt_id IS DISTINCT FROM NEW.attempt_id OR r.status IN ('CANCELLED','REJECTED','EXPIRED') OR
            (r.status IN ('UPLOADING','VALIDATING','VALIDATION_FAILED') AND r.expires_at<=NEW.created_at)) OR
        NOT EXISTS(SELECT 1 FROM document_inventory_runs h WHERE h.company_id=NEW.company_id AND h.id=NEW.run_id AND NEW.modified_at<=h.cutoff) OR
        NOT EXISTS(SELECT 1 FROM object_cleanup_queue q WHERE q.company_id=NEW.company_id AND q.id=NEW.attempt_id AND q.object_key=a.object_key AND q.resource_id=a.revision_id AND q.object_bytes=a.byte_count) THEN
        RAISE EXCEPTION 'Inventory cleanup requires a disposable registered attempt' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER inventory_recovery_scope BEFORE INSERT ON document_inventory_recoveries FOR EACH ROW EXECUTE FUNCTION validate_document_inventory_recovery();
CREATE FUNCTION validate_document_inventory_page() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM document_inventory_runs h JOIN background_jobs j ON j.id=h.job_id WHERE h.company_id=NEW.company_id AND h.id=NEW.run_id AND
        h.job_id=NEW.job_id AND h.pages=NEW.page_no-1 AND h.status='SCANNING' AND j.actor_id=NEW.actor_id AND j.status='RUNNING' AND NOT j.cancellation_requested) OR
        NEW.scheduled<>(SELECT count(*) FROM document_inventory_recoveries WHERE company_id=NEW.company_id AND run_id=NEW.run_id AND page_no=NEW.page_no) THEN
        RAISE EXCEPTION 'Inventory page evidence does not match its active job' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER inventory_page_scope BEFORE INSERT ON document_inventory_pages FOR EACH ROW EXECUTE FUNCTION validate_document_inventory_page();
CREATE VIEW document_inventory_views WITH(security_invoker=true) AS SELECT r.*,j.status AS job_status,j.failure_code,a.base_pages
    FROM document_inventory_runs r JOIN document_inventory_attempts a ON a.company_id=r.company_id AND a.run_id=r.id AND a.job_id=r.job_id
    JOIN background_jobs j ON j.company_id=r.company_id AND j.id=r.job_id;
CREATE VIEW document_inventory_attempt_views WITH(security_invoker=true) AS SELECT a.*,j.status AS job_status,j.failure_code
    FROM document_inventory_attempts a JOIN background_jobs j ON j.company_id=a.company_id AND j.id=a.job_id;
