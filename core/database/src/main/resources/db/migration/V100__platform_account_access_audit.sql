-- Preserve journal writers across backfill and replacement of the capture function.
LOCK TABLE audit_entries IN SHARE ROW EXCLUSIVE MODE;

CREATE FUNCTION platform_audit_permission_values(input jsonb) RETURNS text[]
LANGUAGE plpgsql IMMUTABLE STRICT SECURITY INVOKER AS $$
DECLARE
    encoded text;
    selected text[];
    permission text;
BEGIN
    IF jsonb_typeof(input) <> 'string' THEN RETURN NULL; END IF;
    encoded := input #>> '{}';
    IF octet_length(encoded) > 4096 THEN RETURN NULL; END IF;
    selected := CASE WHEN encoded = '' THEN ARRAY[]::text[] ELSE string_to_array(encoded, ',') END;
    IF cardinality(selected) > 32 THEN RETURN NULL; END IF;
    FOREACH permission IN ARRAY selected LOOP
        IF permission !~ '^[a-z][a-z0-9_.:-]{0,79}$' THEN RETURN NULL; END IF;
    END LOOP;
    IF (SELECT count(DISTINCT p) FROM unnest(selected) AS p) <> cardinality(selected) THEN RETURN NULL; END IF;
    RETURN selected;
END $$;

CREATE TABLE platform_account_access_audit (
    id uuid PRIMARY KEY REFERENCES platform_audit_metadata(id),
    actor_id uuid NOT NULL,
    account_id uuid NOT NULL,
    active boolean NOT NULL,
    permissions text[] NOT NULL CHECK (cardinality(permissions) <= 32),
    reason varchar(1000)
);

INSERT INTO platform_account_access_audit(id, actor_id, account_id, active, permissions, reason)
SELECT id, actor_id, resource_id, details->>'active' = 'true',
    platform_audit_permission_values(details->'permissions'), reason
FROM audit_entries WHERE company_id IS NULL AND resource_type = 'account'
    AND action = 'identity.account_access_saved'
    AND jsonb_typeof(details->'active') = 'string' AND details->>'active' IN ('true','false')
    AND platform_audit_permission_values(details->'permissions') IS NOT NULL;

ALTER TABLE platform_account_access_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform_account_access_audit FORCE ROW LEVEL SECURITY;
CREATE POLICY platform_account_audit_read ON platform_account_access_audit FOR SELECT USING (
    current_company_id() IS NULL AND current_actor_is_platform_auditor()
    AND current_actor_is_identity_administrator()
);
CREATE POLICY platform_account_audit_capture ON platform_account_access_audit FOR INSERT WITH CHECK (
    actor_id = current_actor_id()
);

CREATE FUNCTION validate_platform_account_access_audit() RETURNS trigger
LANGUAGE plpgsql SECURITY INVOKER AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM audit_entries a WHERE a.id = NEW.id AND a.company_id IS NULL
        AND a.resource_type = 'account' AND a.action = 'identity.account_access_saved'
        AND a.actor_id = NEW.actor_id AND a.resource_id = NEW.account_id
        AND jsonb_typeof(a.details->'active') = 'string' AND a.details->>'active' IN ('true','false')
        AND (a.details->>'active' = 'true') = NEW.active
        AND platform_audit_permission_values(a.details->'permissions') = NEW.permissions
        AND a.reason IS NOT DISTINCT FROM NEW.reason
    ) THEN
        RAISE EXCEPTION 'Invalid account audit source' USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER validate_platform_account_audit BEFORE INSERT ON platform_account_access_audit
FOR EACH ROW EXECUTE FUNCTION validate_platform_account_access_audit();
CREATE TRIGGER immutable_platform_account_audit BEFORE UPDATE OR DELETE ON platform_account_access_audit
FOR EACH ROW EXECUTE FUNCTION deny_audit_mutation();

CREATE OR REPLACE FUNCTION capture_platform_audit_metadata() RETURNS trigger
LANGUAGE plpgsql SECURITY INVOKER AS $$
DECLARE
    selected text[];
BEGIN
    INSERT INTO platform_audit_metadata(id, actor_id, resource_type, resource_id, action, correlation_id, created_at)
    VALUES (NEW.id, NEW.actor_id, NEW.resource_type, NEW.resource_id, NEW.action, NEW.correlation_id, NEW.created_at);
    IF NEW.resource_type = 'account' AND NEW.action = 'identity.account_access_saved'
        AND jsonb_typeof(NEW.details->'active') = 'string' AND NEW.details->>'active' IN ('true','false') THEN
        selected := platform_audit_permission_values(NEW.details->'permissions');
        IF selected IS NOT NULL THEN
            INSERT INTO platform_account_access_audit(id, actor_id, account_id, active, permissions, reason)
            VALUES (NEW.id, NEW.actor_id, NEW.resource_id, NEW.details->>'active' = 'true', selected, NEW.reason);
        END IF;
    END IF;
    RETURN NEW;
END $$;
