CREATE TABLE attendance_day_versions (
    company_id uuid NOT NULL,employment_id uuid NOT NULL,work_date date NOT NULL,version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY(company_id,employment_id,work_date),FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id)
);
CREATE TABLE attendance_corrections (
    company_id uuid NOT NULL,id uuid NOT NULL,employment_id uuid NOT NULL,work_date date NOT NULL,revision bigint NOT NULL,
    clock_in timestamptz,clock_out timestamptz,break_minutes integer NOT NULL,schedule jsonb NOT NULL,
    actor_id uuid NOT NULL REFERENCES accounts(id),recorded_at timestamptz NOT NULL,reason varchar(1000) NOT NULL,
    PRIMARY KEY(company_id,employment_id,work_date,revision),UNIQUE(company_id,id),
    FOREIGN KEY(company_id,employment_id,work_date) REFERENCES attendance_day_versions(company_id,employment_id,work_date),
    CHECK ((clock_in IS NULL AND clock_out IS NULL AND break_minutes=0) OR
        (clock_in IS NOT NULL AND clock_out IS NOT NULL AND clock_out>clock_in AND break_minutes>=0 AND clock_out-clock_in<=interval '24 hours'))
);
CREATE TRIGGER immutable_history BEFORE UPDATE OR DELETE ON attendance_corrections FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['attendance_day_versions','attendance_corrections'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
    END LOOP;
END $$;
