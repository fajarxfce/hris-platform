-- Global audit readers receive metadata, never the private reason/details journal.
LOCK TABLE audit_entries IN SHARE ROW EXCLUSIVE MODE;

CREATE TABLE platform_audit_metadata (
    id uuid PRIMARY KEY REFERENCES audit_entries(id),
    actor_id uuid NOT NULL,
    resource_type varchar(80) NOT NULL,
    resource_id uuid NOT NULL,
    action varchar(100) NOT NULL,
    correlation_id uuid NOT NULL,
    created_at timestamptz NOT NULL
);
CREATE INDEX platform_audit_created ON platform_audit_metadata(created_at DESC, id DESC);

INSERT INTO platform_audit_metadata(id, actor_id, resource_type, resource_id, action, correlation_id, created_at)
SELECT id, actor_id, resource_type, resource_id, action, correlation_id, created_at
FROM audit_entries WHERE company_id IS NULL;

CREATE FUNCTION current_actor_is_platform_auditor() RETURNS boolean
LANGUAGE sql STABLE SECURITY INVOKER AS $$
    SELECT EXISTS (
        SELECT 1 FROM accounts a JOIN platform_permissions p ON p.account_id = a.id
        WHERE a.id = current_actor_id() AND a.active AND p.permission = 'audit.read'
    )
$$;

ALTER TABLE platform_audit_metadata ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform_audit_metadata FORCE ROW LEVEL SECURITY;
CREATE POLICY platform_audit_read ON platform_audit_metadata FOR SELECT USING (
    current_company_id() IS NULL AND current_actor_is_platform_auditor()
);
-- Some existing global journal writes occur while a company is selected. Preserve
-- their admission; this grants no SELECT access to that company transaction.
CREATE POLICY platform_audit_capture ON platform_audit_metadata FOR INSERT WITH CHECK (
    actor_id = current_actor_id()
);

CREATE FUNCTION validate_platform_audit_metadata() RETURNS trigger
LANGUAGE plpgsql SECURITY INVOKER AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM audit_entries a WHERE a.id = NEW.id AND a.company_id IS NULL
        AND a.actor_id = NEW.actor_id AND a.resource_type = NEW.resource_type
        AND a.resource_id = NEW.resource_id AND a.action = NEW.action
        AND a.correlation_id = NEW.correlation_id AND a.created_at = NEW.created_at
    ) THEN
        RAISE EXCEPTION 'Invalid audit metadata source' USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER validate_platform_audit BEFORE INSERT ON platform_audit_metadata
FOR EACH ROW EXECUTE FUNCTION validate_platform_audit_metadata();
CREATE TRIGGER immutable_platform_audit BEFORE UPDATE OR DELETE ON platform_audit_metadata
FOR EACH ROW EXECUTE FUNCTION deny_audit_mutation();

CREATE FUNCTION capture_platform_audit_metadata() RETURNS trigger
LANGUAGE plpgsql SECURITY INVOKER AS $$
BEGIN
    INSERT INTO platform_audit_metadata(id, actor_id, resource_type, resource_id, action, correlation_id, created_at)
    VALUES (NEW.id, NEW.actor_id, NEW.resource_type, NEW.resource_id, NEW.action, NEW.correlation_id, NEW.created_at);
    RETURN NEW;
END $$;
CREATE TRIGGER capture_platform_audit AFTER INSERT ON audit_entries
FOR EACH ROW WHEN (NEW.company_id IS NULL) EXECUTE FUNCTION capture_platform_audit_metadata();
