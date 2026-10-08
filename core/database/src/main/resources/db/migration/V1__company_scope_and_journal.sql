CREATE FUNCTION current_company_id() RETURNS uuid
LANGUAGE sql STABLE AS $$ SELECT nullif(current_setting('hris.company_id', true), '')::uuid $$;

CREATE FUNCTION current_actor_id() RETURNS uuid
LANGUAGE sql STABLE AS $$ SELECT nullif(current_setting('hris.actor_id', true), '')::uuid $$;

CREATE TABLE companies (
    id uuid PRIMARY KEY,
    code varchar(32) NOT NULL UNIQUE,
    name varchar(200) NOT NULL,
    timezone varchar(80) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE companies ENABLE ROW LEVEL SECURITY;
ALTER TABLE companies FORCE ROW LEVEL SECURITY;
CREATE POLICY company_scope ON companies USING (id = current_company_id()) WITH CHECK (id = current_company_id());

CREATE TABLE audit_entries (
    id uuid PRIMARY KEY,
    company_id uuid REFERENCES companies(id),
    actor_id uuid NOT NULL,
    resource_type varchar(80) NOT NULL,
    resource_id uuid NOT NULL,
    action varchar(100) NOT NULL,
    reason varchar(1000),
    details jsonb NOT NULL DEFAULT '{}',
    correlation_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_company_created ON audit_entries(company_id, created_at DESC, id);
ALTER TABLE audit_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_entries FORCE ROW LEVEL SECURITY;
CREATE POLICY audit_scope ON audit_entries USING (
    company_id = current_company_id() OR (company_id IS NULL AND actor_id = current_actor_id())
) WITH CHECK (
    company_id = current_company_id() OR (company_id IS NULL AND actor_id = current_actor_id())
);

CREATE FUNCTION deny_audit_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Audit entries are immutable' USING ERRCODE = '42501';
END $$;
CREATE TRIGGER immutable_audit BEFORE UPDATE OR DELETE ON audit_entries
FOR EACH ROW EXECUTE FUNCTION deny_audit_mutation();

CREATE TABLE outbox_events (
    id uuid PRIMARY KEY,
    company_id uuid REFERENCES companies(id),
    actor_id uuid NOT NULL,
    event_type varchar(100) NOT NULL,
    resource_id uuid NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    available_at timestamptz NOT NULL DEFAULT now(),
    attempts integer NOT NULL DEFAULT 0,
    lease_until timestamptz,
    lease_owner uuid,
    delivered_at timestamptz,
    failure_code varchar(80)
);
CREATE INDEX outbox_pending ON outbox_events(available_at, id) WHERE delivered_at IS NULL;
ALTER TABLE outbox_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_events FORCE ROW LEVEL SECURITY;
CREATE POLICY outbox_scope ON outbox_events USING (
    company_id = current_company_id() OR (company_id IS NULL AND actor_id = current_actor_id())
) WITH CHECK (
    company_id = current_company_id() OR (company_id IS NULL AND actor_id = current_actor_id())
);

CREATE TABLE operation_receipts (
    company_id uuid NOT NULL REFERENCES companies(id),
    actor_id uuid NOT NULL,
    operation varchar(100) NOT NULL,
    operation_id uuid NOT NULL,
    payload_hash varchar(64) NOT NULL,
    resource_id uuid NOT NULL,
    response jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(company_id, actor_id, operation, operation_id)
);
ALTER TABLE operation_receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE operation_receipts FORCE ROW LEVEL SECURITY;
CREATE POLICY receipt_scope ON operation_receipts USING (
    company_id = current_company_id() AND actor_id = current_actor_id()
) WITH CHECK (
    company_id = current_company_id() AND actor_id = current_actor_id()
);
