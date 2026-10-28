-- Owned export requests and immutable private-object reservations/accepted parts.
CREATE TABLE report_exports (
    company_id uuid NOT NULL REFERENCES companies(id), account_id uuid NOT NULL REFERENCES accounts(id), id uuid NOT NULL,
    definition jsonb NOT NULL CHECK(jsonb_typeof(definition)='object' AND octet_length(definition::text)<=32768),
    columns jsonb NOT NULL CHECK(jsonb_typeof(columns)='array' AND jsonb_array_length(columns) BETWEEN 1 AND 64 AND octet_length(columns::text)<=16384),
    sources jsonb NOT NULL CHECK(jsonb_typeof(sources)='object' AND octet_length(sources::text)<=16384),
    client jsonb NOT NULL CHECK(jsonb_typeof(client)='object' AND octet_length(client::text)<=256),
    created_at timestamptz NOT NULL CHECK(isfinite(created_at)),
    expires_at timestamptz NOT NULL CHECK(isfinite(expires_at) AND expires_at>created_at AND expires_at<=created_at+interval '168 hours'),
    job_id uuid NOT NULL REFERENCES background_jobs(id),
    status varchar(16) NOT NULL DEFAULT 'QUEUED' CHECK(status IN ('QUEUED','RENDERING','READY','STOPPED')),
    attempt integer NOT NULL DEFAULT 1 CHECK(attempt BETWEEN 1 AND 8),
    version bigint NOT NULL DEFAULT 0 CHECK(version BETWEEN 0 AND 9999),
    updated_at timestamptz NOT NULL CHECK(isfinite(updated_at) AND updated_at>=created_at),
    snapshot_id uuid,
    completed_rows integer NOT NULL DEFAULT 0 CHECK(completed_rows BETWEEN 0 AND 100000),
    part_count integer NOT NULL DEFAULT 0 CHECK(part_count BETWEEN 0 AND 1024),
    byte_count bigint NOT NULL DEFAULT 0 CHECK(byte_count BETWEEN 0 AND 104857600),
    reservation_count integer NOT NULL DEFAULT 0 CHECK(reservation_count BETWEEN part_count AND 1088),
    reserved_bytes bigint NOT NULL DEFAULT 0 CHECK(reserved_bytes BETWEEN byte_count AND 268435456),
    failure_code varchar(80) CHECK(failure_code ~ '^[a-z][a-z0-9_]{0,79}$'),
    manifest_tag varchar(64) CHECK(manifest_tag ~ '^[0-9a-f]{64}$'),
    finished_at timestamptz CHECK(isfinite(finished_at) AND finished_at BETWEEN created_at AND updated_at),
    retired_at timestamptz CHECK(isfinite(retired_at) AND retired_at>=expires_at AND retired_at<=updated_at),
    PRIMARY KEY(company_id,account_id,id), UNIQUE(company_id,id),
    FOREIGN KEY(company_id,account_id,snapshot_id) REFERENCES report_snapshots(company_id,account_id,id),
    CHECK(snapshot_id IS NULL OR snapshot_id=id),
    CHECK(snapshot_id IS NOT NULL OR (completed_rows=0 AND reservation_count=0)),
    CHECK(status<>'QUEUED' OR snapshot_id IS NULL),
    CHECK(status NOT IN ('RENDERING','READY') OR snapshot_id IS NOT NULL),
    CHECK((part_count=0)=(byte_count=0) AND (reservation_count=0)=(reserved_bytes=0)),
    CHECK(part_count>0 OR completed_rows=0),
    CHECK((status='STOPPED')=(failure_code IS NOT NULL)),
    CHECK((status='READY')=(manifest_tag IS NOT NULL)),
    CHECK((status IN ('READY','STOPPED'))=(finished_at IS NOT NULL)),
    CHECK(status<>'READY' OR part_count>0),
    CHECK(retired_at IS NULL OR finished_at IS NOT NULL)
);
CREATE INDEX report_export_owner_history ON report_exports(company_id,account_id,created_at DESC,id DESC);
CREATE INDEX report_export_retained ON report_exports(company_id,account_id,id) WHERE retired_at IS NULL;
CREATE INDEX report_export_expiry ON report_exports(expires_at,company_id,account_id,id) WHERE retired_at IS NULL;
CREATE INDEX report_export_job_history ON background_jobs(company_id,(request->>'exportId'),created_at,id) WHERE kind='REPORT_EXPORT';
ALTER TABLE report_exports ENABLE ROW LEVEL SECURITY;
ALTER TABLE report_exports FORCE ROW LEVEL SECURITY;
CREATE POLICY report_export_owner ON report_exports
    USING(company_id=current_company_id() AND account_id=current_actor_id())
    WITH CHECK(company_id=current_company_id() AND account_id=current_actor_id());

CREATE TABLE report_export_uploads (
    company_id uuid NOT NULL, account_id uuid NOT NULL, export_id uuid NOT NULL, id uuid NOT NULL,
    part_number integer NOT NULL CHECK(part_number BETWEEN 1 AND 1024),
    after_row integer NOT NULL CHECK(after_row BETWEEN 0 AND 100000),
    row_count integer NOT NULL CHECK(row_count BETWEEN 0 AND 500 AND after_row+row_count<=100000),
    byte_offset bigint NOT NULL CHECK(byte_offset>=0),
    byte_count integer NOT NULL CHECK(byte_count BETWEEN 1 AND 1048576 AND byte_offset+byte_count<=104857600),
    sha256 varchar(64) NOT NULL CHECK(sha256 ~ '^[0-9a-f]{64}$'),
    created_at timestamptz NOT NULL CHECK(isfinite(created_at)),
    PRIMARY KEY(company_id,account_id,export_id,id), UNIQUE(id),
    FOREIGN KEY(company_id,account_id,export_id) REFERENCES report_exports(company_id,account_id,id),
    CHECK((part_number=1)=(after_row=0) AND (part_number=1)=(byte_offset=0)),
    CHECK(row_count>0 OR part_number=1)
);
ALTER TABLE report_export_uploads ENABLE ROW LEVEL SECURITY;
ALTER TABLE report_export_uploads FORCE ROW LEVEL SECURITY;
CREATE POLICY report_export_upload_owner ON report_export_uploads
    USING(company_id=current_company_id() AND account_id=current_actor_id())
    WITH CHECK(company_id=current_company_id() AND account_id=current_actor_id());

CREATE TABLE report_export_parts (
    company_id uuid NOT NULL, account_id uuid NOT NULL, export_id uuid NOT NULL,
    part_number integer NOT NULL CHECK(part_number BETWEEN 1 AND 1024), upload_id uuid NOT NULL,
    etag varchar(512) NOT NULL CHECK(length(etag)>0 AND etag !~ '[[:cntrl:]]'),
    accepted_at timestamptz NOT NULL CHECK(isfinite(accepted_at)),
    PRIMARY KEY(company_id,account_id,export_id,part_number), UNIQUE(upload_id),
    FOREIGN KEY(company_id,account_id,export_id,upload_id) REFERENCES report_export_uploads(company_id,account_id,export_id,id)
);
ALTER TABLE report_export_parts ENABLE ROW LEVEL SECURITY;
ALTER TABLE report_export_parts FORCE ROW LEVEL SECURITY;
CREATE POLICY report_export_part_owner ON report_export_parts
    USING(company_id=current_company_id() AND account_id=current_actor_id())
    WITH CHECK(company_id=current_company_id() AND account_id=current_actor_id());

CREATE FUNCTION protect_report_export() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE snapshot report_snapshots%ROWTYPE;
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Report export identity is retained' USING ERRCODE='42501';
    END IF;
    IF TG_OP='INSERT' OR NEW.job_id<>OLD.job_id THEN
        IF NOT EXISTS(SELECT 1 FROM background_jobs WHERE id=NEW.job_id AND company_id=NEW.company_id
            AND actor_id=NEW.account_id AND kind='REPORT_EXPORT' AND request->>'exportId'=NEW.id::text AND status='QUEUED') THEN
            RAISE EXCEPTION 'Export job scope is invalid' USING ERRCODE='23514';
        END IF;
    END IF;
    IF TG_OP='INSERT' THEN
        PERFORM pg_advisory_xact_lock(hashtextextended('hris:report-exports:'||NEW.company_id||':'||NEW.account_id,0));
        -- Repeated insertion still reaches its unique conflict without consuming another slot.
        IF EXISTS(SELECT 1 FROM report_exports WHERE company_id=NEW.company_id AND account_id=NEW.account_id AND id=NEW.id) THEN RETURN NEW; END IF;
        IF (SELECT count(*) FROM (SELECT 1 FROM report_exports WHERE company_id=NEW.company_id AND account_id=NEW.account_id
                AND retired_at IS NULL LIMIT 10) retained)>=10
            OR (SELECT count(*) FROM (SELECT 1 FROM report_exports WHERE company_id=NEW.company_id AND account_id=NEW.account_id
                AND status IN ('QUEUED','RENDERING') LIMIT 2) active)>=2 THEN
            RAISE EXCEPTION 'Export owner quota exceeded' USING ERRCODE='23514';
        END IF;
        IF NEW.status<>'QUEUED' OR NEW.attempt<>1 OR NEW.version<>0 OR NEW.updated_at<>NEW.created_at
            OR NEW.snapshot_id IS NOT NULL OR NEW.reservation_count<>0 OR NEW.retired_at IS NOT NULL THEN
            RAISE EXCEPTION 'Initial export state required' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.retired_at IS NOT NULL OR NEW.version<>OLD.version+1 OR NEW.updated_at<OLD.updated_at
        OR (NEW.company_id,NEW.account_id,NEW.id,NEW.definition,NEW.columns,NEW.sources,NEW.client,NEW.created_at,NEW.expires_at)
        IS DISTINCT FROM (OLD.company_id,OLD.account_id,OLD.id,OLD.definition,OLD.columns,OLD.sources,OLD.client,OLD.created_at,OLD.expires_at) THEN
        RAISE EXCEPTION 'Export definition and versions are protected' USING ERRCODE='23514';
    END IF;
    IF (NEW.completed_rows,NEW.part_count,NEW.byte_count,NEW.reservation_count,NEW.reserved_bytes)
        IS DISTINCT FROM (OLD.completed_rows,OLD.part_count,OLD.byte_count,OLD.reservation_count,OLD.reserved_bytes) THEN
        IF pg_trigger_depth()<>2 OR OLD.status<>'RENDERING' OR NEW.status<>OLD.status
            OR (NEW.job_id,NEW.attempt,NEW.snapshot_id,NEW.failure_code,NEW.manifest_tag,NEW.finished_at,NEW.retired_at)
            IS DISTINCT FROM (OLD.job_id,OLD.attempt,OLD.snapshot_id,OLD.failure_code,OLD.manifest_tag,OLD.finished_at,OLD.retired_at)
            OR NEW.completed_rows<OLD.completed_rows OR NEW.part_count<OLD.part_count OR NEW.byte_count<OLD.byte_count
            OR NEW.reservation_count<OLD.reservation_count OR NEW.reserved_bytes<OLD.reserved_bytes THEN
            RAISE EXCEPTION 'Export counters belong to immutable parts' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.retired_at IS NOT NULL THEN
        IF OLD.status NOT IN ('READY','STOPPED') OR NEW.retired_at<NEW.expires_at OR clock_timestamp()<NEW.expires_at
            OR (NEW.reservation_count>0 AND clock_timestamp()<NEW.expires_at+interval '1 hour')
            OR (NEW.status,NEW.job_id,NEW.attempt,NEW.snapshot_id,NEW.failure_code,NEW.manifest_tag,NEW.finished_at)
                IS DISTINCT FROM (OLD.status,OLD.job_id,OLD.attempt,OLD.snapshot_id,OLD.failure_code,OLD.manifest_tag,OLD.finished_at)
            OR EXISTS(SELECT 1 FROM report_snapshot_rows WHERE company_id=NEW.company_id AND account_id=NEW.account_id AND snapshot_id=NEW.id)
            OR EXISTS(SELECT 1 FROM object_cleanup_queue WHERE company_id=NEW.company_id AND resource_id=NEW.id) THEN
            RAISE EXCEPTION 'Export physical retirement is incomplete' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.job_id<>OLD.job_id THEN
        PERFORM pg_advisory_xact_lock(hashtextextended('hris:report-exports:'||NEW.company_id||':'||NEW.account_id,0));
        IF OLD.status='READY' OR NEW.attempt<>OLD.attempt+1 OR NEW.status<>(CASE WHEN OLD.snapshot_id IS NULL THEN 'QUEUED' ELSE 'RENDERING' END)
            OR NEW.snapshot_id IS DISTINCT FROM OLD.snapshot_id OR NEW.finished_at IS NOT NULL OR NEW.failure_code IS NOT NULL
            OR clock_timestamp()>=NEW.expires_at
            OR NOT EXISTS(SELECT 1 FROM background_jobs WHERE id=OLD.job_id AND status IN ('FAILED','CANCELLED'))
            OR (SELECT count(*) FROM (SELECT 1 FROM report_exports WHERE company_id=NEW.company_id AND account_id=NEW.account_id
                AND id<>NEW.id AND status IN ('QUEUED','RENDERING') LIMIT 2) active)>=2 THEN
            RAISE EXCEPTION 'Export recovery is unavailable' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.attempt<>OLD.attempt THEN RAISE EXCEPTION 'Export attempts require a new job' USING ERRCODE='23514'; END IF;
    IF OLD.status='QUEUED' AND NEW.status='RENDERING' THEN
        SELECT * INTO snapshot FROM report_snapshots WHERE company_id=NEW.company_id AND account_id=NEW.account_id AND id=NEW.id;
        IF NEW.snapshot_id IS DISTINCT FROM NEW.id OR NOT FOUND OR snapshot.row_count IS NULL
            OR snapshot.definition<>NEW.definition OR snapshot.columns<>NEW.columns OR snapshot.expires_at<>NEW.expires_at
            OR snapshot.evaluated_at<NEW.created_at OR clock_timestamp()>=NEW.expires_at THEN
            RAISE EXCEPTION 'Export snapshot definition differs' USING ERRCODE='23514';
        END IF;
    ELSIF OLD.status='RENDERING' AND NEW.status='READY' THEN
        IF NEW.finished_at IS DISTINCT FROM NEW.updated_at OR NEW.snapshot_id IS DISTINCT FROM OLD.snapshot_id OR NEW.completed_rows IS DISTINCT FROM
            (SELECT row_count FROM report_snapshots WHERE company_id=NEW.company_id AND account_id=NEW.account_id AND id=NEW.id)
            OR clock_timestamp()>=NEW.expires_at THEN
            RAISE EXCEPTION 'Export manifest is incomplete or expired' USING ERRCODE='23514';
        END IF;
    ELSIF OLD.status IN ('QUEUED','RENDERING') AND NEW.status='STOPPED' THEN
        IF NEW.finished_at IS DISTINCT FROM NEW.updated_at OR NEW.snapshot_id IS DISTINCT FROM OLD.snapshot_id THEN RAISE EXCEPTION 'Stopped export retains its snapshot' USING ERRCODE='23514'; END IF;
    ELSE RAISE EXCEPTION 'Invalid export transition' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_report_export BEFORE INSERT OR UPDATE OR DELETE ON report_exports FOR EACH ROW EXECUTE FUNCTION protect_report_export();

CREATE FUNCTION protect_report_export_upload() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent report_exports%ROWTYPE; total integer;
BEGIN
    IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Export upload evidence is immutable' USING ERRCODE='42501'; END IF;
    SELECT * INTO parent FROM report_exports WHERE company_id=NEW.company_id AND account_id=NEW.account_id AND id=NEW.export_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Export owner is unavailable' USING ERRCODE='42501'; END IF;
    SELECT row_count INTO total FROM report_snapshots WHERE company_id=NEW.company_id AND account_id=NEW.account_id AND id=NEW.export_id;
    IF parent.status<>'RENDERING' OR parent.retired_at IS NOT NULL OR NEW.part_number<>parent.part_count+1 OR NEW.after_row<>parent.completed_rows
        OR NEW.byte_offset<>parent.byte_count OR NEW.created_at<parent.updated_at OR NEW.created_at>=parent.expires_at OR clock_timestamp()>=parent.expires_at
        OR total IS NULL OR NEW.after_row+NEW.row_count>total OR (NEW.row_count=0 AND total<>0)
        OR NOT EXISTS(SELECT 1 FROM object_cleanup_queue WHERE id=NEW.id AND company_id=NEW.company_id AND resource_id=NEW.export_id
            AND created_by=NEW.account_id AND object_key=NEW.company_id::text||'/reports/'||NEW.export_id::text||'/'||NEW.id::text
            AND object_bytes=NEW.byte_count AND status='PENDING' AND eligible_at>=parent.expires_at+interval '1 hour') THEN
        RAISE EXCEPTION 'Export upload reservation is invalid' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_report_export_upload BEFORE INSERT OR UPDATE OR DELETE ON report_export_uploads FOR EACH ROW EXECUTE FUNCTION protect_report_export_upload();
CREATE FUNCTION count_report_export_upload() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    UPDATE report_exports SET reservation_count=reservation_count+1,reserved_bytes=reserved_bytes+NEW.byte_count,version=version+1,updated_at=NEW.created_at
        WHERE company_id=NEW.company_id AND account_id=NEW.account_id AND id=NEW.export_id;
    RETURN NULL;
END $$;
CREATE TRIGGER count_report_export_upload AFTER INSERT ON report_export_uploads FOR EACH ROW EXECUTE FUNCTION count_report_export_upload();

CREATE FUNCTION protect_report_export_part() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent report_exports%ROWTYPE; upload report_export_uploads%ROWTYPE;
BEGIN
    IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Accepted export parts are immutable' USING ERRCODE='42501'; END IF;
    SELECT * INTO parent FROM report_exports WHERE company_id=NEW.company_id AND account_id=NEW.account_id AND id=NEW.export_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Export owner is unavailable' USING ERRCODE='42501'; END IF;
    SELECT * INTO upload FROM report_export_uploads WHERE company_id=NEW.company_id AND account_id=NEW.account_id AND export_id=NEW.export_id AND id=NEW.upload_id;
    IF NOT FOUND OR parent.status<>'RENDERING' OR parent.retired_at IS NOT NULL OR NEW.part_number<>parent.part_count+1
        OR upload.part_number<>NEW.part_number OR upload.after_row<>parent.completed_rows OR upload.byte_offset<>parent.byte_count
        OR NEW.accepted_at<parent.updated_at OR NEW.accepted_at>=parent.expires_at OR clock_timestamp()>=parent.expires_at
        OR NOT EXISTS(SELECT 1 FROM object_cleanup_queue WHERE id=NEW.upload_id AND company_id=NEW.company_id AND resource_id=NEW.export_id
            AND status='PENDING' AND eligible_at>=parent.expires_at+interval '1 hour') THEN
        RAISE EXCEPTION 'Export part is obsolete or expired' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_report_export_part BEFORE INSERT OR UPDATE OR DELETE ON report_export_parts FOR EACH ROW EXECUTE FUNCTION protect_report_export_part();
CREATE FUNCTION count_report_export_part() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE upload report_export_uploads%ROWTYPE;
BEGIN
    SELECT * INTO upload FROM report_export_uploads WHERE company_id=NEW.company_id AND account_id=NEW.account_id AND export_id=NEW.export_id AND id=NEW.upload_id;
    UPDATE report_exports SET completed_rows=completed_rows+upload.row_count,part_count=part_count+1,byte_count=byte_count+upload.byte_count,
        version=version+1,updated_at=NEW.accepted_at WHERE company_id=NEW.company_id AND account_id=NEW.account_id AND id=NEW.export_id;
    RETURN NULL;
END $$;
CREATE TRIGGER count_report_export_part AFTER INSERT ON report_export_parts FOR EACH ROW EXECUTE FUNCTION count_report_export_part();
