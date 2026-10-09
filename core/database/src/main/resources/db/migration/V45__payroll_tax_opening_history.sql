CREATE TABLE payroll_tax_openings (
    company_id uuid NOT NULL,id uuid NOT NULL,employment_id uuid NOT NULL,
    tax_year integer NOT NULL CHECK(tax_year BETWEEN 2024 AND 2100),version bigint NOT NULL CHECK(version BETWEEN 0 AND 999),
    PRIMARY KEY(company_id,id),UNIQUE(company_id,employment_id,tax_year),
    FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id)
);
CREATE TABLE payroll_tax_opening_revisions (
    company_id uuid NOT NULL,opening_id uuid NOT NULL,revision bigint NOT NULL CHECK(revision BETWEEN 0 AND 999),
    status varchar(20) NOT NULL CHECK(status IN('DRAFT','VERIFIED')),
    terms jsonb NOT NULL CHECK(jsonb_typeof(terms)='object' AND pg_column_size(terms)<=16384),
    prepared_by uuid NOT NULL REFERENCES accounts(id),verified_by uuid REFERENCES accounts(id),
    actor_id uuid NOT NULL REFERENCES accounts(id),reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),recorded_at timestamptz NOT NULL,
    PRIMARY KEY(company_id,opening_id,revision),
    FOREIGN KEY(company_id,opening_id) REFERENCES payroll_tax_openings(company_id,id),
    CHECK((status='DRAFT' AND verified_by IS NULL AND actor_id=prepared_by) OR (status='VERIFIED' AND verified_by IS NOT NULL AND verified_by<>prepared_by AND actor_id=verified_by))
);
ALTER TABLE payroll_tax_openings ADD CONSTRAINT payroll_tax_opening_current_revision FOREIGN KEY(company_id,id,version)
    REFERENCES payroll_tax_opening_revisions(company_id,opening_id,revision) DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX payroll_tax_opening_year ON payroll_tax_openings(company_id,tax_year,employment_id);
CREATE TRIGGER payroll_tax_opening_header BEFORE INSERT OR UPDATE OR DELETE ON payroll_tax_openings FOR EACH ROW EXECUTE FUNCTION protect_payroll_configuration_header();
CREATE FUNCTION validate_payroll_tax_opening_revision() RETURNS trigger LANGUAGE plpgsql AS $$ DECLARE previous payroll_tax_opening_revisions; BEGIN
    IF NOT EXISTS(SELECT 1 FROM payroll_tax_openings WHERE company_id=NEW.company_id AND id=NEW.opening_id AND version=NEW.revision)
        THEN RAISE EXCEPTION 'Tax opening revision must match its current header' USING ERRCODE='23514'; END IF;
    IF NEW.revision=0 THEN
        IF NEW.status<>'DRAFT' THEN RAISE EXCEPTION 'Tax opening must start as a draft' USING ERRCODE='23514'; END IF;
    ELSE
        SELECT * INTO previous FROM payroll_tax_opening_revisions WHERE company_id=NEW.company_id AND opening_id=NEW.opening_id AND revision=NEW.revision-1;
        IF NOT FOUND THEN RAISE EXCEPTION 'Tax opening revisions must be contiguous' USING ERRCODE='23514'; END IF;
        IF NEW.status='VERIFIED' AND (previous.status<>'DRAFT' OR NEW.terms IS DISTINCT FROM previous.terms OR NEW.prepared_by<>previous.prepared_by OR EXISTS(SELECT 1 FROM payroll_tax_opening_revisions WHERE company_id=NEW.company_id AND opening_id=NEW.opening_id AND status='DRAFT' AND prepared_by=NEW.verified_by))
            THEN RAISE EXCEPTION 'Independent verification must preserve draft terms' USING ERRCODE='23514'; END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER payroll_tax_opening_revision BEFORE INSERT ON payroll_tax_opening_revisions FOR EACH ROW EXECUTE FUNCTION validate_payroll_tax_opening_revision();
CREATE TRIGGER payroll_tax_opening_immutable BEFORE UPDATE OR DELETE ON payroll_tax_opening_revisions FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
ALTER TABLE payroll_tax_openings ENABLE ROW LEVEL SECURITY;
ALTER TABLE payroll_tax_openings FORCE ROW LEVEL SECURITY;
CREATE POLICY payroll_tax_opening_scope ON payroll_tax_openings USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
ALTER TABLE payroll_tax_opening_revisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE payroll_tax_opening_revisions FORCE ROW LEVEL SECURITY;
CREATE POLICY payroll_tax_opening_revision_scope ON payroll_tax_opening_revisions USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
