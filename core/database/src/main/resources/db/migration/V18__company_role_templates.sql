CREATE TABLE company_role_templates (
    company_id uuid NOT NULL REFERENCES companies(id),
    id uuid NOT NULL,
    code varchar(32) NOT NULL,
    name varchar(200) NOT NULL,
    permissions varchar(100)[] NOT NULL CHECK(cardinality(permissions)<=200),
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
    PRIMARY KEY(company_id,id), UNIQUE(company_id,code)
);
CREATE TABLE role_template_revisions (
    company_id uuid NOT NULL,
    role_id uuid NOT NULL,
    revision bigint NOT NULL,
    code varchar(32) NOT NULL,
    name varchar(200) NOT NULL,
    permissions varchar(100)[] NOT NULL CHECK(cardinality(permissions)<=200),
    active boolean NOT NULL,
    actor_id uuid NOT NULL REFERENCES accounts(id),
    reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    recorded_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(company_id,role_id,revision),
    FOREIGN KEY(company_id,role_id) REFERENCES company_role_templates(company_id,id)
);
CREATE TABLE membership_role_applications (
    company_id uuid NOT NULL,
    account_id uuid NOT NULL,
    membership_version bigint NOT NULL CHECK(membership_version>=0),
    snapshot jsonb NOT NULL CHECK(jsonb_typeof(snapshot)='object' AND octet_length(snapshot::text)<=32768),
    actor_id uuid NOT NULL REFERENCES accounts(id),
    recorded_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(company_id,account_id,membership_version),
    FOREIGN KEY(company_id,account_id) REFERENCES company_memberships(company_id,account_id)
);
ALTER TABLE company_role_templates ENABLE ROW LEVEL SECURITY;
ALTER TABLE company_role_templates FORCE ROW LEVEL SECURITY;
CREATE POLICY role_template_scope ON company_role_templates USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
ALTER TABLE role_template_revisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE role_template_revisions FORCE ROW LEVEL SECURITY;
CREATE POLICY role_revision_scope ON role_template_revisions USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
ALTER TABLE membership_role_applications ENABLE ROW LEVEL SECURITY;
ALTER TABLE membership_role_applications FORCE ROW LEVEL SECURITY;
CREATE POLICY role_application_scope ON membership_role_applications USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
CREATE TRIGGER immutable_role_revision BEFORE UPDATE OR DELETE ON role_template_revisions FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE TRIGGER immutable_role_application BEFORE UPDATE OR DELETE ON membership_role_applications FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE FUNCTION protect_role_template_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.company_id,NEW.id,NEW.code) IS DISTINCT FROM (OLD.company_id,OLD.id,OLD.code) OR NEW.version<>OLD.version+1 THEN
        RAISE EXCEPTION 'Role identity is immutable and changes must be versioned' USING ERRCODE='42501';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_role_template_identity BEFORE UPDATE ON company_role_templates FOR EACH ROW EXECUTE FUNCTION protect_role_template_identity();
