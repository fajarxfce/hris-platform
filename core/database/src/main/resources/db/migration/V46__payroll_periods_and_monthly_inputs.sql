CREATE TABLE payroll_periods (
    company_id uuid NOT NULL REFERENCES companies(id),id uuid NOT NULL,
    earnings_month date NOT NULL CHECK(extract(day FROM earnings_month)=1 AND extract(year FROM earnings_month) BETWEEN 2024 AND 2100),
    planned_payment_date date NOT NULL CHECK(extract(year FROM planned_payment_date) BETWEEN 2024 AND 2100 AND planned_payment_date>=earnings_month AND planned_payment_date<=((earnings_month+interval '1 month')::date-1+62)),
    timezone varchar(80) NOT NULL,participant_count integer NOT NULL CHECK(participant_count BETWEEN 1 AND 5000),
    author_id uuid NOT NULL REFERENCES accounts(id),created_at timestamptz NOT NULL,creation_transaction text NOT NULL DEFAULT pg_current_xact_id()::text,
    status varchar(24) NOT NULL CHECK(status IN('DRAFT','CANCELLED')),version bigint NOT NULL CHECK(version BETWEEN 0 AND 255),
    PRIMARY KEY(company_id,id)
);
CREATE UNIQUE INDEX payroll_active_earnings_period ON payroll_periods(company_id,earnings_month) WHERE status<>'CANCELLED';
CREATE INDEX payroll_period_listing ON payroll_periods(company_id,earnings_month,id);
CREATE TABLE payroll_period_members (
    company_id uuid NOT NULL,period_id uuid NOT NULL,employment_id uuid NOT NULL,
    PRIMARY KEY(company_id,period_id,employment_id),FOREIGN KEY(company_id,period_id) REFERENCES payroll_periods(company_id,id),
    FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id)
);
CREATE TABLE payroll_period_changes (
    company_id uuid NOT NULL,period_id uuid NOT NULL,revision bigint NOT NULL CHECK(revision BETWEEN 0 AND 255),
    status varchar(24) NOT NULL CHECK(status IN('DRAFT','CANCELLED')),actor_id uuid NOT NULL REFERENCES accounts(id),recorded_at timestamptz NOT NULL,
    reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),PRIMARY KEY(company_id,period_id,revision),
    FOREIGN KEY(company_id,period_id) REFERENCES payroll_periods(company_id,id)
);
ALTER TABLE payroll_periods ADD CONSTRAINT payroll_period_current_change FOREIGN KEY(company_id,id,version)
    REFERENCES payroll_period_changes(company_id,period_id,revision) DEFERRABLE INITIALLY DEFERRED;
CREATE FUNCTION protect_payroll_period() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.version<>0 OR NEW.status<>'DRAFT' OR NEW.creation_transaction<>pg_current_xact_id()::text THEN RAISE EXCEPTION 'Payroll period must start as a draft' USING ERRCODE='23514'; END IF;
    ELSIF TG_OP='DELETE' THEN RAISE EXCEPTION 'Payroll period history is retained' USING ERRCODE='23514';
    ELSE
        IF (to_jsonb(NEW)-ARRAY['version','status']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['version','status']) OR NEW.version<>OLD.version+1 OR OLD.status<>'DRAFT' OR NEW.status<>'CANCELLED'
            THEN RAISE EXCEPTION 'Payroll period metadata and terminal state are immutable' USING ERRCODE='23514'; END IF;
    END IF; RETURN NEW;
END $$;
CREATE TRIGGER payroll_period_state BEFORE INSERT OR UPDATE OR DELETE ON payroll_periods FOR EACH ROW EXECUTE FUNCTION protect_payroll_period();
CREATE FUNCTION validate_payroll_period_change() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM payroll_periods WHERE company_id=NEW.company_id AND id=NEW.period_id AND version=NEW.revision AND status=NEW.status)
        THEN RAISE EXCEPTION 'Payroll period change must match its header' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER payroll_period_change BEFORE INSERT ON payroll_period_changes FOR EACH ROW EXECUTE FUNCTION validate_payroll_period_change();
CREATE TRIGGER payroll_period_changes_immutable BEFORE UPDATE OR DELETE ON payroll_period_changes FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE TRIGGER payroll_period_members_immutable BEFORE UPDATE OR DELETE ON payroll_period_members FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE FUNCTION validate_payroll_period_member() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM payroll_periods WHERE company_id=NEW.company_id AND id=NEW.period_id AND version=0 AND status='DRAFT' AND creation_transaction=pg_current_xact_id()::text)
        THEN RAISE EXCEPTION 'Payroll participants are frozen at creation' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER payroll_period_member BEFORE INSERT ON payroll_period_members FOR EACH ROW EXECUTE FUNCTION validate_payroll_period_member();
CREATE FUNCTION check_payroll_period_members() RETURNS trigger LANGUAGE plpgsql AS $$ DECLARE actual integer; BEGIN
    SELECT count(*) INTO actual FROM payroll_period_members WHERE company_id=NEW.company_id AND period_id=NEW.id;
    IF actual<>NEW.participant_count THEN RAISE EXCEPTION 'Payroll participant snapshot is incomplete' USING ERRCODE='23514'; END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER payroll_period_members_complete AFTER INSERT ON payroll_periods DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_payroll_period_members();

CREATE TABLE payroll_inputs (
    company_id uuid NOT NULL,id uuid NOT NULL,employment_id uuid NOT NULL,
    earnings_month date NOT NULL CHECK(extract(day FROM earnings_month)=1 AND extract(year FROM earnings_month) BETWEEN 2024 AND 2100),version bigint NOT NULL CHECK(version BETWEEN 0 AND 999),
    PRIMARY KEY(company_id,id),UNIQUE(company_id,employment_id,earnings_month),UNIQUE(company_id,id,employment_id,earnings_month),
    FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id)
);
CREATE TABLE payroll_input_revisions (
    company_id uuid NOT NULL,input_id uuid NOT NULL,revision bigint NOT NULL CHECK(revision BETWEEN 0 AND 999),employment_id uuid NOT NULL,earnings_month date NOT NULL,
    work_job_id uuid NOT NULL,work_period_version bigint NOT NULL CHECK(work_period_version>=0),employment_version bigint NOT NULL CHECK(employment_version>=0),
    status varchar(24) NOT NULL CHECK(status IN('DRAFT','VERIFIED')),terms jsonb NOT NULL CHECK(jsonb_typeof(terms)='object' AND pg_column_size(terms)<=65536),
    prepared_by uuid NOT NULL REFERENCES accounts(id),verified_by uuid REFERENCES accounts(id),actor_id uuid NOT NULL REFERENCES accounts(id),
    reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),recorded_at timestamptz NOT NULL,
    PRIMARY KEY(company_id,input_id,revision),FOREIGN KEY(company_id,input_id,employment_id,earnings_month) REFERENCES payroll_inputs(company_id,id,employment_id,earnings_month),
    FOREIGN KEY(company_id,work_job_id,employment_id) REFERENCES work_period_snapshots(company_id,job_id,employment_id),
    CHECK((status='DRAFT' AND verified_by IS NULL AND actor_id=prepared_by) OR (status='VERIFIED' AND verified_by IS NOT NULL AND verified_by<>prepared_by AND actor_id=verified_by))
);
ALTER TABLE payroll_inputs ADD CONSTRAINT payroll_input_current_revision FOREIGN KEY(company_id,id,version)
    REFERENCES payroll_input_revisions(company_id,input_id,revision) DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX payroll_inputs_month ON payroll_inputs(company_id,earnings_month,employment_id);
CREATE TRIGGER payroll_input_header BEFORE INSERT OR UPDATE OR DELETE ON payroll_inputs FOR EACH ROW EXECUTE FUNCTION protect_payroll_configuration_header();
CREATE FUNCTION validate_payroll_input_revision() RETURNS trigger LANGUAGE plpgsql AS $$ DECLARE previous payroll_input_revisions; BEGIN
    IF NOT EXISTS(SELECT 1 FROM payroll_inputs WHERE company_id=NEW.company_id AND id=NEW.input_id AND version=NEW.revision)
        THEN RAISE EXCEPTION 'Payroll input revision must match its header' USING ERRCODE='23514'; END IF;
    IF NOT EXISTS(SELECT 1 FROM work_periods WHERE company_id=NEW.company_id AND job_id=NEW.work_job_id AND month=NEW.earnings_month AND version=NEW.work_period_version AND status='CLOSED')
        THEN RAISE EXCEPTION 'Payroll input requires exact closed workforce evidence' USING ERRCODE='23514'; END IF;
    IF NEW.revision=0 THEN
        IF NEW.status<>'DRAFT' THEN RAISE EXCEPTION 'Payroll input must start as a draft' USING ERRCODE='23514'; END IF;
    ELSE
        SELECT * INTO previous FROM payroll_input_revisions WHERE company_id=NEW.company_id AND input_id=NEW.input_id AND revision=NEW.revision-1;
        IF NOT FOUND THEN RAISE EXCEPTION 'Payroll input revisions must be contiguous' USING ERRCODE='23514'; END IF;
        IF NEW.status='VERIFIED' AND (previous.status<>'DRAFT' OR NEW.terms IS DISTINCT FROM previous.terms OR (NEW.work_job_id,NEW.work_period_version,NEW.employment_version) IS DISTINCT FROM (previous.work_job_id,previous.work_period_version,previous.employment_version) OR NEW.prepared_by<>previous.prepared_by OR EXISTS(SELECT 1 FROM payroll_input_revisions WHERE company_id=NEW.company_id AND input_id=NEW.input_id AND status='DRAFT' AND prepared_by=NEW.verified_by))
            THEN RAISE EXCEPTION 'Independent verification must preserve input terms and evidence' USING ERRCODE='23514'; END IF;
    END IF; RETURN NEW;
END $$;
CREATE TRIGGER payroll_input_revision BEFORE INSERT ON payroll_input_revisions FOR EACH ROW EXECUTE FUNCTION validate_payroll_input_revision();
CREATE TRIGGER payroll_input_history_immutable BEFORE UPDATE OR DELETE ON payroll_input_revisions FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['payroll_periods','payroll_period_members','payroll_period_changes','payroll_inputs','payroll_input_revisions'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
    END LOOP;
END $$;
