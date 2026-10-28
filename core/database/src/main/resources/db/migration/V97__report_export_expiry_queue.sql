-- Global discovery exposes only source identities and deadlines, never report definitions/facts.
CREATE TABLE report_export_expiry_queue (
    company_id uuid NOT NULL, account_id uuid NOT NULL, export_id uuid NOT NULL,
    expires_at timestamptz NOT NULL CHECK(isfinite(expires_at)),
    due_at timestamptz NOT NULL CHECK(isfinite(due_at) AND due_at>=expires_at),
    PRIMARY KEY(company_id,account_id,export_id),
    FOREIGN KEY(company_id,account_id,export_id) REFERENCES report_exports(company_id,account_id,id)
);
CREATE INDEX report_export_cleanup_due ON report_export_expiry_queue(due_at,company_id,account_id,export_id);
ALTER TABLE report_export_expiry_queue ENABLE ROW LEVEL SECURITY;
ALTER TABLE report_export_expiry_queue FORCE ROW LEVEL SECURITY;
CREATE POLICY report_export_expiry_capture ON report_export_expiry_queue FOR INSERT
    WITH CHECK(company_id=current_company_id() AND account_id=current_actor_id());
CREATE POLICY report_export_expiry_discovery ON report_export_expiry_queue FOR SELECT
    TO hris_worker_capability USING(true);
CREATE POLICY report_export_expiry_update ON report_export_expiry_queue FOR UPDATE
    TO hris_worker_capability USING(company_id=current_company_id() AND account_id=current_actor_id())
    WITH CHECK(company_id=current_company_id() AND account_id=current_actor_id());
CREATE POLICY report_export_expiry_delete ON report_export_expiry_queue FOR DELETE
    TO hris_worker_capability USING(company_id=current_company_id() AND account_id=current_actor_id());
GRANT SELECT,UPDATE,DELETE ON report_export_expiry_queue TO hris_worker_capability;

CREATE FUNCTION protect_report_export_expiry() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        IF NOT EXISTS(SELECT 1 FROM report_exports WHERE company_id=OLD.company_id AND account_id=OLD.account_id AND id=OLD.export_id AND retired_at IS NOT NULL) THEN
            RAISE EXCEPTION 'Export cleanup must finish before removing its schedule' USING ERRCODE='23514';
        END IF;
        RETURN OLD;
    END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.due_at<>NEW.expires_at OR NOT EXISTS(SELECT 1 FROM report_exports WHERE company_id=NEW.company_id AND account_id=NEW.account_id AND id=NEW.export_id AND expires_at=NEW.expires_at AND retired_at IS NULL) THEN
            RAISE EXCEPTION 'Export cleanup scope or deadline differs' USING ERRCODE='23514';
        END IF;
    ELSIF (NEW.company_id,NEW.account_id,NEW.export_id,NEW.expires_at) IS DISTINCT FROM (OLD.company_id,OLD.account_id,OLD.export_id,OLD.expires_at)
        OR NEW.due_at>greatest(NEW.expires_at,clock_timestamp()+interval '1 hour') THEN
        RAISE EXCEPTION 'Export cleanup identity and retry bounds are immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_report_export_expiry BEFORE INSERT OR UPDATE OR DELETE ON report_export_expiry_queue FOR EACH ROW EXECUTE FUNCTION protect_report_export_expiry();
CREATE FUNCTION capture_report_export_expiry() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    INSERT INTO report_export_expiry_queue(company_id,account_id,export_id,expires_at,due_at)
    VALUES(NEW.company_id,NEW.account_id,NEW.id,NEW.expires_at,NEW.expires_at);
    RETURN NULL;
END $$;
CREATE TRIGGER capture_report_export_expiry AFTER INSERT ON report_exports FOR EACH ROW EXECUTE FUNCTION capture_report_export_expiry();
INSERT INTO report_export_expiry_queue(company_id,account_id,export_id,expires_at,due_at)
SELECT company_id,account_id,id,expires_at,expires_at FROM report_exports WHERE retired_at IS NULL;
