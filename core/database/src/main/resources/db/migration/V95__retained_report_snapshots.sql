CREATE TABLE report_snapshots (
    company_id uuid NOT NULL REFERENCES companies(id),
    account_id uuid NOT NULL REFERENCES accounts(id),
    id uuid NOT NULL,
    definition jsonb NOT NULL CHECK(jsonb_typeof(definition)='object' AND octet_length(definition::text)<=32768),
    columns jsonb NOT NULL CHECK(jsonb_typeof(columns)='array' AND jsonb_array_length(columns) BETWEEN 1 AND 64
        AND octet_length(columns::text)<=16384),
    evaluated_at timestamptz NOT NULL CHECK(isfinite(evaluated_at)),
    expires_at timestamptz NOT NULL CHECK(isfinite(expires_at) AND expires_at>evaluated_at
        AND expires_at<=evaluated_at+interval '168 hours'),
    row_count integer CHECK(row_count BETWEEN 0 AND 100000),
    stored_bytes bigint CHECK(stored_bytes BETWEEN 0 AND 104857600),
    staged_rows integer NOT NULL DEFAULT 0 CHECK(staged_rows BETWEEN 0 AND 100000),
    staged_bytes bigint NOT NULL DEFAULT 0 CHECK(staged_bytes BETWEEN 0 AND 104857600),
    PRIMARY KEY(company_id,account_id,id),
    UNIQUE(company_id,id),
    CHECK((row_count IS NULL)=(stored_bytes IS NULL))
);
CREATE INDEX report_snapshot_expiry ON report_snapshots(expires_at,company_id,account_id,id);
ALTER TABLE report_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE report_snapshots FORCE ROW LEVEL SECURITY;
CREATE POLICY report_snapshot_owner ON report_snapshots
    USING(company_id=current_company_id() AND account_id=current_actor_id())
    WITH CHECK(company_id=current_company_id() AND account_id=current_actor_id());

CREATE TABLE report_snapshot_rows (
    company_id uuid NOT NULL,
    account_id uuid NOT NULL,
    snapshot_id uuid NOT NULL,
    row_number integer NOT NULL CHECK(row_number BETWEEN 1 AND 100000),
    cells jsonb NOT NULL CHECK(jsonb_typeof(cells)='array' AND jsonb_array_length(cells) BETWEEN 1 AND 64),
    byte_length integer GENERATED ALWAYS AS (octet_length(cells::text)) STORED CHECK(byte_length<=1048576),
    PRIMARY KEY(company_id,account_id,snapshot_id,row_number),
    FOREIGN KEY(company_id,account_id,snapshot_id) REFERENCES report_snapshots(company_id,account_id,id)
);
ALTER TABLE report_snapshot_rows ENABLE ROW LEVEL SECURITY;
ALTER TABLE report_snapshot_rows FORCE ROW LEVEL SECURITY;
CREATE POLICY report_snapshot_row_owner ON report_snapshot_rows
    USING(company_id=current_company_id() AND account_id=current_actor_id())
    WITH CHECK(company_id=current_company_id() AND account_id=current_actor_id());

CREATE FUNCTION protect_report_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE actual_count integer; actual_bytes bigint; first_row integer; last_row integer;
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Report snapshot identity is retained' USING ERRCODE='42501';
    ELSIF TG_OP='INSERT' THEN
        IF NEW.row_count IS NOT NULL OR NEW.staged_rows<>0 OR NEW.staged_bytes<>0 THEN
            RAISE EXCEPTION 'Report snapshot must be captured before sealing' USING ERRCODE='23514';
        END IF;
    ELSE
        IF OLD.row_count IS NOT NULL
            OR (NEW.company_id,NEW.account_id,NEW.id,NEW.definition,NEW.columns,NEW.evaluated_at,NEW.expires_at)
                IS DISTINCT FROM (OLD.company_id,OLD.account_id,OLD.id,OLD.definition,OLD.columns,OLD.evaluated_at,OLD.expires_at) THEN
            RAISE EXCEPTION 'Report snapshot capture is immutable' USING ERRCODE='23514';
        END IF;
        IF NEW.row_count IS NULL THEN
            IF pg_trigger_depth()<>2 OR NEW.staged_rows<=OLD.staged_rows OR NEW.staged_bytes<=OLD.staged_bytes THEN
                RAISE EXCEPTION 'Report snapshot counters belong to capture' USING ERRCODE='23514';
            END IF;
            RETURN NEW;
        END IF;
        SELECT count(*),coalesce(sum(byte_length),0),min(row_number),max(row_number)
            INTO actual_count,actual_bytes,first_row,last_row FROM report_snapshot_rows
            WHERE company_id=NEW.company_id AND account_id=NEW.account_id AND snapshot_id=NEW.id;
        IF NEW.row_count<>actual_count OR NEW.stored_bytes<>actual_bytes
            OR NEW.staged_rows<>OLD.staged_rows OR NEW.staged_bytes<>OLD.staged_bytes
            OR NEW.row_count<>NEW.staged_rows OR NEW.stored_bytes<>NEW.staged_bytes
            OR (actual_count>0 AND (first_row<>1 OR last_row<>actual_count)) THEN
            RAISE EXCEPTION 'Report snapshot rows are incomplete' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_report_snapshot BEFORE INSERT OR UPDATE OR DELETE ON report_snapshots
    FOR EACH ROW EXECUTE FUNCTION protect_report_snapshot();

-- A connection loss, cancellation or a forgotten seal cannot publish a partial capture.
CREATE FUNCTION require_sealed_report_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS(SELECT 1 FROM report_snapshots WHERE company_id=NEW.company_id
        AND account_id=NEW.account_id AND id=NEW.id AND row_count IS NULL) THEN
        RAISE EXCEPTION 'Report snapshot must be sealed before commit' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER sealed_report_snapshot AFTER INSERT ON report_snapshots
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_sealed_report_snapshot();

CREATE FUNCTION protect_report_snapshot_row() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE snapshot report_snapshots%ROWTYPE;
BEGIN
    IF TG_OP='UPDATE' THEN
        RAISE EXCEPTION 'Report snapshot rows are immutable' USING ERRCODE='42501';
    ELSIF TG_OP='DELETE' THEN
        SELECT * INTO snapshot FROM report_snapshots WHERE company_id=OLD.company_id
            AND account_id=OLD.account_id AND id=OLD.snapshot_id FOR SHARE;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Report snapshot scope is unavailable' USING ERRCODE='42501';
        END IF;
        IF snapshot.expires_at>clock_timestamp() THEN
            RAISE EXCEPTION 'Live report snapshot rows are retained' USING ERRCODE='42501';
        END IF;
        RETURN OLD;
    ELSE
        SELECT * INTO snapshot FROM report_snapshots WHERE company_id=NEW.company_id
            AND account_id=NEW.account_id AND id=NEW.snapshot_id FOR UPDATE;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Report snapshot scope is unavailable' USING ERRCODE='42501';
        END IF;
        IF snapshot.row_count IS NOT NULL OR jsonb_array_length(NEW.cells)<>jsonb_array_length(snapshot.columns) THEN
            RAISE EXCEPTION 'Report snapshot rows cannot replace a capture' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
END $$;
CREATE TRIGGER protect_report_snapshot_row BEFORE INSERT OR UPDATE OR DELETE ON report_snapshot_rows
    FOR EACH ROW EXECUTE FUNCTION protect_report_snapshot_row();

-- Account once per insertion statement, rather than rewriting the header for every row.
CREATE FUNCTION account_report_snapshot_rows() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE batch record;
BEGIN
    FOR batch IN SELECT company_id,account_id,snapshot_id,count(*)::integer rows,
        sum(byte_length)::bigint bytes FROM captured_rows
        GROUP BY company_id,account_id,snapshot_id ORDER BY company_id,account_id,snapshot_id LOOP
        UPDATE report_snapshots SET staged_rows=staged_rows+batch.rows,staged_bytes=staged_bytes+batch.bytes
            WHERE company_id=batch.company_id AND account_id=batch.account_id AND id=batch.snapshot_id;
    END LOOP;
    RETURN NULL;
END $$;
CREATE TRIGGER account_report_snapshot_rows AFTER INSERT ON report_snapshot_rows
    REFERENCING NEW TABLE AS captured_rows FOR EACH STATEMENT EXECUTE FUNCTION account_report_snapshot_rows();
