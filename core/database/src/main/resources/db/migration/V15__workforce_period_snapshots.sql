ALTER TABLE background_jobs ADD CONSTRAINT jobs_company_id_unique UNIQUE(company_id,id);
CREATE INDEX jobs_company_active ON background_jobs(company_id,status,lease_until) WHERE status IN ('QUEUED','RUNNING');
CREATE TABLE work_periods (
    company_id uuid NOT NULL REFERENCES companies(id),id uuid NOT NULL DEFAULT gen_random_uuid(),month date NOT NULL CHECK(extract(day FROM month)=1),
    status varchar(24) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','PROCESSING','REVIEW_REQUIRED','CLOSED')),
    timezone varchar(80),job_id uuid,version bigint NOT NULL DEFAULT 0,started_at timestamptz,closed_at timestamptz,failure_code varchar(80),
    PRIMARY KEY(company_id,id),UNIQUE(company_id,month),UNIQUE(company_id,job_id),
    FOREIGN KEY(company_id,job_id) REFERENCES background_jobs(company_id,id),
    CHECK(status='OPEN' OR (job_id IS NOT NULL AND timezone IS NOT NULL AND started_at IS NOT NULL)),
    CHECK((status='CLOSED')=(closed_at IS NOT NULL))
);
CREATE TABLE work_period_targets (
    company_id uuid NOT NULL,job_id uuid NOT NULL,employment_id uuid NOT NULL,
    PRIMARY KEY(company_id,job_id,employment_id),FOREIGN KEY(company_id,job_id) REFERENCES background_jobs(company_id,id),
    FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id)
);
CREATE TABLE work_period_snapshots (
    company_id uuid NOT NULL,job_id uuid NOT NULL,employment_id uuid NOT NULL,
    payload jsonb NOT NULL CHECK(octet_length(payload::text)<=262144),created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(company_id,job_id,employment_id),
    FOREIGN KEY(company_id,job_id,employment_id) REFERENCES work_period_targets(company_id,job_id,employment_id)
);
CREATE FUNCTION protect_work_period() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF (OLD.company_id,OLD.id,OLD.month) IS DISTINCT FROM (NEW.company_id,NEW.id,NEW.month) OR OLD.status='CLOSED' THEN
        RAISE EXCEPTION 'Closed work periods are immutable' USING ERRCODE='42501';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER immutable_period_identity BEFORE DELETE ON work_periods FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE TRIGGER immutable_closed_period BEFORE UPDATE ON work_periods FOR EACH ROW EXECUTE FUNCTION protect_work_period();
DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['work_periods','work_period_targets','work_period_snapshots'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
    END LOOP;
    FOREACH t IN ARRAY ARRAY['work_period_targets','work_period_snapshots'] LOOP
        EXECUTE format('CREATE TRIGGER immutable_history BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION deny_history_mutation()',t);
    END LOOP;
END $$;
ALTER TABLE attendance_events ADD closing_job_id uuid,
    ADD FOREIGN KEY(company_id,closing_job_id) REFERENCES background_jobs(company_id,id),
    ADD CHECK(closing_job_id IS NULL OR (initial_status='PENDING' AND 'PERIOD_LOCKED'=ANY(issues)));
