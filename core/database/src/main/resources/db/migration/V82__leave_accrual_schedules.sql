CREATE TABLE leave_accrual_schedules (
    company_id uuid NOT NULL, id uuid NOT NULL, type_id uuid NOT NULL,
    type_code varchar(40) NOT NULL, type_name varchar(200) NOT NULL,
    policy_version bigint NOT NULL CHECK (policy_version >= 0),
    frequency varchar(16) NOT NULL CHECK (frequency IN ('MONTHLY', 'ANNUAL')),
    first_period date NOT NULL CHECK (extract(day FROM first_period)=1 AND extract(year FROM first_period) BETWEEN 1900 AND 2199),
    period_count integer NOT NULL CHECK (period_count BETWEEN 1 AND 12),
    total_employees integer NOT NULL CHECK (total_employees BETWEEN 1 AND 5000),
    timezone varchar(80) NOT NULL, actor_id uuid NOT NULL REFERENCES accounts(id),
    created_at timestamptz NOT NULL CHECK (isfinite(created_at)),
    reason varchar(1000) NOT NULL CHECK (length(trim(reason)) > 0),
    PRIMARY KEY (company_id, id),
    FOREIGN KEY (company_id, type_id) REFERENCES leave_types(company_id, id)
);
CREATE TABLE leave_accrual_schedule_entries (
    company_id uuid NOT NULL, schedule_id uuid NOT NULL,
    ordinal integer NOT NULL CHECK (ordinal BETWEEN 1 AND 12),
    batch_id uuid NOT NULL, initial_job_id uuid NOT NULL,
    scheduled_for timestamptz NOT NULL CHECK (isfinite(scheduled_for)),
    PRIMARY KEY (company_id, schedule_id, ordinal),
    UNIQUE (company_id, batch_id),
    FOREIGN KEY (company_id, schedule_id) REFERENCES leave_accrual_schedules(company_id, id),
    FOREIGN KEY (company_id, batch_id) REFERENCES leave_batches(company_id, id),
    FOREIGN KEY (company_id, initial_job_id) REFERENCES background_jobs(company_id, id)
);

-- One complete, immutable plan references existing batch/lease machinery. No new scheduler owns execution.
CREATE FUNCTION check_leave_accrual_schedule() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    s leave_accrual_schedules; e leave_accrual_schedule_entries;
    b leave_batches; j background_jobs; first_batch uuid; expected_month date; due_at timestamptz;
BEGIN
    s := NEW;
    IF (SELECT count(*) FROM leave_accrual_schedule_entries WHERE company_id=s.company_id AND schedule_id=s.id) <> s.period_count THEN
        RAISE EXCEPTION 'A leave accrual schedule requires all reviewed periods' USING ERRCODE='23514';
    END IF;
    SELECT batch_id INTO first_batch FROM leave_accrual_schedule_entries WHERE company_id=s.company_id AND schedule_id=s.id AND ordinal=1;
    FOR e IN SELECT * FROM leave_accrual_schedule_entries WHERE company_id=s.company_id AND schedule_id=s.id ORDER BY ordinal LOOP
        SELECT * INTO b FROM leave_batches WHERE company_id=e.company_id AND id=e.batch_id;
        SELECT * INTO j FROM background_jobs WHERE company_id=e.company_id AND id=e.initial_job_id;
        expected_month := (s.first_period + make_interval(months => (e.ordinal-1) * CASE s.frequency WHEN 'ANNUAL' THEN 12 ELSE 1 END))::date;
        due_at := (CASE s.frequency WHEN 'MONTHLY' THEN expected_month + interval '1 month' ELSE expected_month::timestamp END) AT TIME ZONE s.timezone;
        IF e.ordinal > s.period_count OR b.id IS NULL OR j.id IS NULL OR b.kind<>'ACCRUAL'
            OR extract(year FROM expected_month) NOT BETWEEN 1900 AND 2199
            OR b.period<>expected_month OR b.type_id<>s.type_id OR b.policy_version<>s.policy_version
            OR b.policy_snapshot->'policy'->'accrual'->>'frequency' IS DISTINCT FROM s.frequency
            OR b.policy_snapshot->>'code' IS DISTINCT FROM s.type_code
            OR (e.ordinal=1 AND b.policy_snapshot->'policy'->>'name' IS DISTINCT FROM s.type_name)
            OR b.timezone<>s.timezone OR b.actor_id<>s.actor_id OR b.created_at<>s.created_at OR b.reason<>s.reason
            OR b.total_employees<>s.total_employees OR b.version<>0 OR b.status<>'RUNNING' OR b.job_id<>e.initial_job_id
            OR j.kind<>'LEAVE_ACCRUAL' OR j.actor_id<>s.actor_id OR j.created_at<>s.created_at OR j.status<>'QUEUED'
            OR j.scheduled_for IS DISTINCT FROM e.scheduled_for OR e.scheduled_for<due_at OR e.scheduled_for<s.created_at
        THEN RAISE EXCEPTION 'Scheduled leave work must match its reviewed plan and due date' USING ERRCODE='23514'; END IF;
        IF EXISTS (
            SELECT employment_id FROM leave_batch_targets WHERE company_id=s.company_id AND batch_id=e.batch_id
            EXCEPT SELECT employment_id FROM leave_batch_targets WHERE company_id=s.company_id AND batch_id=first_batch
        ) THEN RAISE EXCEPTION 'Scheduled leave periods require the same reviewed employees' USING ERRCODE='23514'; END IF;
    END LOOP;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER leave_accrual_schedule_complete AFTER INSERT ON leave_accrual_schedules
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_leave_accrual_schedule();
DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['leave_accrual_schedules','leave_accrual_schedule_entries'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
        EXECUTE format('CREATE TRIGGER immutable_history BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION deny_history_mutation()',t);
    END LOOP;
END $$;
CREATE POLICY leave_accrual_schedule_creator ON leave_accrual_schedules AS RESTRICTIVE FOR INSERT
    WITH CHECK (actor_id=current_actor_id());

-- Prevent appending work to an already committed, complete authorization.
CREATE FUNCTION check_leave_accrual_schedule_entry_count() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE expected integer; actual integer;
BEGIN
    SELECT period_count INTO expected FROM leave_accrual_schedules WHERE company_id=NEW.company_id AND id=NEW.schedule_id;
    SELECT count(*) INTO actual FROM leave_accrual_schedule_entries WHERE company_id=NEW.company_id AND schedule_id=NEW.schedule_id;
    IF expected IS NULL OR actual<>expected THEN
        RAISE EXCEPTION 'A leave accrual schedule retains exactly its reviewed periods' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER leave_accrual_schedule_entries_complete AFTER INSERT ON leave_accrual_schedule_entries
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_leave_accrual_schedule_entry_count();
