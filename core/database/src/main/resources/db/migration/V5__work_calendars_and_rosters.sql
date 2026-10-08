CREATE TABLE shift_templates (
    company_id uuid NOT NULL REFERENCES companies(id),id uuid NOT NULL,details jsonb NOT NULL,
    code varchar(32) GENERATED ALWAYS AS (details->>'code') STORED NOT NULL,
    name varchar(200) GENERATED ALWAYS AS (details->>'name') STORED NOT NULL,
    active boolean NOT NULL DEFAULT true,version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY(company_id,id),UNIQUE(company_id,code)
);
CREATE TABLE shift_revisions (
    company_id uuid NOT NULL,shift_id uuid NOT NULL,revision bigint NOT NULL,details jsonb NOT NULL,active boolean NOT NULL,
    actor_id uuid NOT NULL REFERENCES accounts(id),reason varchar(1000) NOT NULL,recorded_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(company_id,shift_id,revision),FOREIGN KEY(company_id,shift_id) REFERENCES shift_templates(company_id,id)
);
CREATE TABLE schedule_versions (
    company_id uuid NOT NULL,employment_id uuid NOT NULL,version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY(company_id,employment_id),FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id)
);
CREATE TABLE schedule_assignments (
    company_id uuid NOT NULL,employment_id uuid NOT NULL,revision bigint NOT NULL,effective_from date NOT NULL,days jsonb NOT NULL,
    actor_id uuid NOT NULL REFERENCES accounts(id),reason varchar(1000) NOT NULL,recorded_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(company_id,employment_id,revision),FOREIGN KEY(company_id,employment_id) REFERENCES schedule_versions(company_id,employment_id)
);
CREATE INDEX schedule_effective ON schedule_assignments(company_id,employment_id,effective_from DESC,revision DESC);
CREATE TABLE roster_days (
    company_id uuid NOT NULL,employment_id uuid NOT NULL,work_date date NOT NULL,shift jsonb,version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY(company_id,employment_id,work_date),FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id)
);
CREATE TABLE roster_revisions (
    company_id uuid NOT NULL,employment_id uuid NOT NULL,work_date date NOT NULL,revision bigint NOT NULL,shift jsonb,
    actor_id uuid NOT NULL REFERENCES accounts(id),reason varchar(1000) NOT NULL,recorded_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(company_id,employment_id,work_date,revision),FOREIGN KEY(company_id,employment_id,work_date) REFERENCES roster_days(company_id,employment_id,work_date)
);
CREATE TABLE work_holidays (
    company_id uuid NOT NULL REFERENCES companies(id),id uuid NOT NULL,work_date date NOT NULL,name varchar(200) NOT NULL,
    active boolean NOT NULL DEFAULT true,version bigint NOT NULL DEFAULT 0,PRIMARY KEY(company_id,id),UNIQUE(company_id,work_date)
);
CREATE TABLE holiday_revisions (
    company_id uuid NOT NULL,holiday_id uuid NOT NULL,revision bigint NOT NULL,work_date date NOT NULL,name varchar(200) NOT NULL,active boolean NOT NULL,
    actor_id uuid NOT NULL REFERENCES accounts(id),reason varchar(1000) NOT NULL,recorded_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(company_id,holiday_id,revision),FOREIGN KEY(company_id,holiday_id) REFERENCES work_holidays(company_id,id)
);
DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['shift_templates','shift_revisions','schedule_versions','schedule_assignments','roster_days','roster_revisions','work_holidays','holiday_revisions'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
    END LOOP;
    FOREACH t IN ARRAY ARRAY['shift_revisions','schedule_assignments','roster_revisions','holiday_revisions'] LOOP
        EXECUTE format('CREATE TRIGGER immutable_history BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION deny_history_mutation()',t);
    END LOOP;
END $$;
