CREATE TABLE payroll_policies (
    company_id uuid PRIMARY KEY REFERENCES companies(id),
    version bigint NOT NULL CHECK(version BETWEEN 0 AND 999)
);
CREATE TABLE payroll_policy_revisions (
    company_id uuid NOT NULL REFERENCES payroll_policies(company_id),
    revision bigint NOT NULL CHECK(revision BETWEEN 0 AND 999),
    effective_from date NOT NULL CHECK(extract(day FROM effective_from)=1 AND extract(year FROM effective_from) BETWEEN 2024 AND 2100),
    effective_until date NOT NULL CHECK(extract(day FROM effective_until)=1 AND effective_until>=effective_from AND extract(year FROM effective_until)=extract(year FROM effective_from)),
    details jsonb NOT NULL CHECK(jsonb_typeof(details)='object' AND pg_column_size(details)<=65536),
    actor_id uuid NOT NULL REFERENCES accounts(id),reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY(company_id,revision)
);
ALTER TABLE payroll_policies ADD CONSTRAINT payroll_policy_current_revision FOREIGN KEY(company_id,version)
    REFERENCES payroll_policy_revisions(company_id,revision) DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX payroll_policy_effective ON payroll_policy_revisions(company_id,effective_from DESC,revision DESC);
CREATE TABLE employee_compensations (
    company_id uuid NOT NULL,employment_id uuid NOT NULL,version bigint NOT NULL CHECK(version BETWEEN 0 AND 999),
    PRIMARY KEY(company_id,employment_id),FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id)
);
CREATE TABLE employee_compensation_revisions (
    company_id uuid NOT NULL,employment_id uuid NOT NULL,revision bigint NOT NULL CHECK(revision BETWEEN 0 AND 999),
    effective_from date NOT NULL CHECK(extract(day FROM effective_from)=1 AND extract(year FROM effective_from) BETWEEN 2024 AND 2100),
    employee_number varchar(40) NOT NULL,employee_name varchar(200) NOT NULL,
    terms jsonb NOT NULL CHECK(jsonb_typeof(terms)='object' AND pg_column_size(terms)<=65536),
    actor_id uuid NOT NULL REFERENCES accounts(id),reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY(company_id,employment_id,revision),
    FOREIGN KEY(company_id,employment_id) REFERENCES employee_compensations(company_id,employment_id)
);
ALTER TABLE employee_compensations ADD CONSTRAINT compensation_current_revision FOREIGN KEY(company_id,employment_id,version)
    REFERENCES employee_compensation_revisions(company_id,employment_id,revision) DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX compensation_effective ON employee_compensation_revisions(company_id,employment_id,effective_from DESC,revision DESC);

CREATE FUNCTION protect_payroll_configuration_header() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.version<>0 THEN RAISE EXCEPTION 'Initial payroll version must be zero' USING ERRCODE='23514'; END IF;
    ELSIF TG_OP='UPDATE' THEN
        IF NEW.version<>OLD.version+1 OR (to_jsonb(NEW)-'version') IS DISTINCT FROM (to_jsonb(OLD)-'version')
            THEN RAISE EXCEPTION 'Payroll configuration must advance once' USING ERRCODE='23514'; END IF;
    ELSE RAISE EXCEPTION 'Payroll configuration history is retained' USING ERRCODE='23514';
    END IF; RETURN NEW;
END $$;
CREATE TRIGGER payroll_policy_header BEFORE INSERT OR UPDATE OR DELETE ON payroll_policies FOR EACH ROW EXECUTE FUNCTION protect_payroll_configuration_header();
CREATE TRIGGER compensation_header BEFORE INSERT OR UPDATE OR DELETE ON employee_compensations FOR EACH ROW EXECUTE FUNCTION protect_payroll_configuration_header();
CREATE FUNCTION validate_payroll_policy_revision() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM payroll_policies WHERE company_id=NEW.company_id AND version=NEW.revision)
        THEN RAISE EXCEPTION 'Payroll policy revision must match its current header' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE FUNCTION validate_compensation_revision() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM employee_compensations WHERE company_id=NEW.company_id AND employment_id=NEW.employment_id AND version=NEW.revision)
        THEN RAISE EXCEPTION 'Compensation revision must match its current header' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER payroll_policy_revision BEFORE INSERT ON payroll_policy_revisions FOR EACH ROW EXECUTE FUNCTION validate_payroll_policy_revision();
CREATE TRIGGER compensation_revision BEFORE INSERT ON employee_compensation_revisions FOR EACH ROW EXECUTE FUNCTION validate_compensation_revision();
CREATE TRIGGER payroll_policy_immutable BEFORE UPDATE OR DELETE ON payroll_policy_revisions FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE TRIGGER compensation_immutable BEFORE UPDATE OR DELETE ON employee_compensation_revisions FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
ALTER TABLE payroll_policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE payroll_policies FORCE ROW LEVEL SECURITY;
CREATE POLICY payroll_policy_scope ON payroll_policies USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
ALTER TABLE payroll_policy_revisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE payroll_policy_revisions FORCE ROW LEVEL SECURITY;
CREATE POLICY payroll_policy_revision_scope ON payroll_policy_revisions USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
ALTER TABLE employee_compensations ENABLE ROW LEVEL SECURITY;
ALTER TABLE employee_compensations FORCE ROW LEVEL SECURITY;
CREATE POLICY compensation_scope ON employee_compensations USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
ALTER TABLE employee_compensation_revisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE employee_compensation_revisions FORCE ROW LEVEL SECURITY;
CREATE POLICY compensation_revision_scope ON employee_compensation_revisions USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
