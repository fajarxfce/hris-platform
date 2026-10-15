-- Fixed deadlines have one durable cohort; progress stays in immutable employee outcomes.
CREATE TABLE leave_carry_expiries (
    company_id uuid NOT NULL, id uuid NOT NULL,
    source_kind varchar(12) NOT NULL CHECK(source_kind IN ('BATCH','CLOSING')),
    batch_id uuid, closing_id uuid, employment_id uuid,
    type_id uuid NOT NULL, balance_year integer NOT NULL CHECK(balance_year BETWEEN 1901 AND 2200),
    company_timezone varchar(80) NOT NULL, valid_through date NOT NULL, expires_at timestamptz NOT NULL,
    actor_id uuid NOT NULL REFERENCES accounts(id), created_at timestamptz NOT NULL,
    reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    total_employees integer NOT NULL CHECK(total_employees BETWEEN 1 AND 5000),
    job_id uuid NOT NULL, version bigint NOT NULL DEFAULT 0 CHECK(version BETWEEN 0 AND 7),
    PRIMARY KEY(company_id,id), UNIQUE(company_id,job_id),
    FOREIGN KEY(company_id,batch_id) REFERENCES leave_batches(company_id,id),
    FOREIGN KEY(company_id,closing_id) REFERENCES leave_year_closings(company_id,id),
    FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id),
    FOREIGN KEY(company_id,type_id) REFERENCES leave_types(company_id,id),
    FOREIGN KEY(company_id,job_id) REFERENCES background_jobs(company_id,id),
    CHECK((source_kind='BATCH' AND batch_id IS NOT NULL AND closing_id IS NULL AND employment_id IS NULL)
       OR (source_kind='CLOSING' AND batch_id IS NULL AND closing_id IS NOT NULL AND employment_id IS NOT NULL AND total_employees=1)),
    CHECK(isfinite(expires_at) AND isfinite(created_at) AND expires_at>created_at AND extract(year FROM valid_through)=balance_year)
);
CREATE UNIQUE INDEX leave_carry_expiry_batch ON leave_carry_expiries(company_id,batch_id) WHERE batch_id IS NOT NULL;
CREATE UNIQUE INDEX leave_carry_expiry_closing ON leave_carry_expiries(company_id,closing_id) WHERE closing_id IS NOT NULL;

CREATE TABLE leave_carry_expiry_attempts (
    company_id uuid NOT NULL, expiry_id uuid NOT NULL, job_id uuid NOT NULL,
    attempt integer NOT NULL CHECK(attempt BETWEEN 1 AND 8),
    base_completed integer NOT NULL CHECK(base_completed BETWEEN 0 AND 5000),
    started_at timestamptz NOT NULL, reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    PRIMARY KEY(company_id,expiry_id,job_id), UNIQUE(company_id,expiry_id,attempt), UNIQUE(company_id,job_id),
    FOREIGN KEY(company_id,expiry_id) REFERENCES leave_carry_expiries(company_id,id),
    FOREIGN KEY(company_id,job_id) REFERENCES background_jobs(company_id,id)
);
ALTER TABLE leave_carry_expiries ADD CONSTRAINT leave_carry_expiry_current_attempt
    FOREIGN KEY(company_id,id,job_id) REFERENCES leave_carry_expiry_attempts(company_id,expiry_id,job_id) DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE leave_carry_expiry_outcomes (
    company_id uuid NOT NULL, expiry_id uuid NOT NULL, ordinal integer NOT NULL CHECK(ordinal BETWEEN 1 AND 5000),
    employment_id uuid NOT NULL, job_id uuid NOT NULL,
    status varchar(12) NOT NULL CHECK(status IN ('EXPIRED','UNCHANGED','NO_AWARD')),
    half_days integer NOT NULL CHECK(half_days BETWEEN 0 AND 732), ledger_id uuid, recorded_at timestamptz NOT NULL,
    PRIMARY KEY(company_id,expiry_id,ordinal), UNIQUE(company_id,expiry_id,employment_id), UNIQUE(company_id,ledger_id),
    FOREIGN KEY(company_id,expiry_id,job_id) REFERENCES leave_carry_expiry_attempts(company_id,expiry_id,job_id),
    FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id),
    FOREIGN KEY(company_id,ledger_id) REFERENCES leave_ledger(company_id,id),
    CHECK((status='EXPIRED' AND half_days>0 AND ledger_id IS NOT NULL) OR (status<>'EXPIRED' AND half_days=0 AND ledger_id IS NULL))
);
CREATE INDEX leave_carry_expiry_job_outcomes ON leave_carry_expiry_outcomes(company_id,job_id,ordinal);

CREATE FUNCTION validate_leave_carry_expiry_origin() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE b leave_batches; c leave_year_closings; months integer; boundary date; BEGIN
    IF TG_OP='UPDATE' THEN
        IF (NEW.company_id,NEW.id,NEW.source_kind,NEW.batch_id,NEW.closing_id,NEW.employment_id,NEW.type_id,NEW.balance_year,
            NEW.company_timezone,NEW.valid_through,NEW.expires_at,NEW.actor_id,NEW.created_at,NEW.reason,NEW.total_employees)
            IS DISTINCT FROM (OLD.company_id,OLD.id,OLD.source_kind,OLD.batch_id,OLD.closing_id,OLD.employment_id,OLD.type_id,OLD.balance_year,
            OLD.company_timezone,OLD.valid_through,OLD.expires_at,OLD.actor_id,OLD.created_at,OLD.reason,OLD.total_employees)
            OR NEW.version<>OLD.version+1 OR NEW.job_id=OLD.job_id
            OR NOT EXISTS(SELECT 1 FROM leave_carry_expiry_attempts WHERE company_id=NEW.company_id AND expiry_id=NEW.id AND job_id=NEW.job_id AND attempt=NEW.version+1)
            THEN RAISE EXCEPTION 'Carry expiry origin is immutable' USING ERRCODE='23514'; END IF;
        RETURN NEW;
    END IF;
    IF NEW.version<>0 THEN RAISE EXCEPTION 'Carry expiry must start at its first attempt' USING ERRCODE='23514'; END IF;
    IF NEW.source_kind='BATCH' THEN
        SELECT * INTO b FROM leave_batches WHERE company_id=NEW.company_id AND id=NEW.batch_id;
        IF b.id IS NULL OR b.kind<>'YEAR_CLOSE' OR b.status<>'RUNNING'
            OR (NEW.type_id,NEW.balance_year,NEW.company_timezone,NEW.actor_id,NEW.created_at,NEW.reason,NEW.total_employees)
            IS DISTINCT FROM (b.type_id,extract(year FROM b.period)::integer+1,b.timezone,b.actor_id,b.created_at,b.reason,b.total_employees)
            THEN RAISE EXCEPTION 'Carry expiry differs from its reviewed batch' USING ERRCODE='23514'; END IF;
        months:=(b.policy_snapshot->'policy'->'accrual'->>'carryValidityMonths')::integer;
    ELSE
        SELECT * INTO c FROM leave_year_closings WHERE company_id=NEW.company_id AND id=NEW.closing_id;
        IF c.id IS NULL OR c.batch_id IS NOT NULL OR NOT c.carry_tracking OR c.carry_half_days<=0
            OR (NEW.type_id,NEW.balance_year,NEW.company_timezone,NEW.actor_id,NEW.created_at,NEW.reason,NEW.employment_id)
            IS DISTINCT FROM (c.type_id,c.balance_year+1,c.company_timezone,c.actor_id,c.recorded_at,c.reason,c.employment_id)
            THEN RAISE EXCEPTION 'Carry expiry differs from its closing award' USING ERRCODE='23514'; END IF;
        months:=(c.policy_snapshot->'policy'->'accrual'->>'carryValidityMonths')::integer;
    END IF;
    boundary:=(make_date(NEW.balance_year,1,1)+make_interval(months=>months))::date;
    IF months IS NULL OR months NOT BETWEEN 1 AND 12 OR NEW.valid_through IS DISTINCT FROM boundary-1
        OR NEW.expires_at IS DISTINCT FROM boundary::timestamp AT TIME ZONE NEW.company_timezone
        THEN RAISE EXCEPTION 'Carry expiry deadline differs from its fixed policy' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER leave_carry_expiry_origin BEFORE INSERT OR UPDATE ON leave_carry_expiries
    FOR EACH ROW EXECUTE FUNCTION validate_leave_carry_expiry_origin();

CREATE FUNCTION validate_leave_carry_expiry_attempt() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE e leave_carry_expiries; j background_jobs; previous background_jobs; completed integer; BEGIN
    SELECT * INTO e FROM leave_carry_expiries WHERE company_id=NEW.company_id AND id=NEW.expiry_id;
    SELECT * INTO j FROM background_jobs WHERE company_id=NEW.company_id AND id=NEW.job_id;
    SELECT coalesce(max(ordinal),0) INTO completed FROM leave_carry_expiry_outcomes WHERE company_id=NEW.company_id AND expiry_id=NEW.expiry_id;
    IF e.id IS NULL OR j.id IS NULL OR j.status<>'QUEUED' OR j.completed_items<>0 OR j.kind<>'LEAVE_CARRY_EXPIRY'
        OR j.request<>jsonb_build_object('expiryId',e.id::text) OR j.progress_mode<>'FIXED_TOTAL'
        OR j.total_items<>e.total_employees-NEW.base_completed+1 OR NEW.base_completed<>completed
        OR j.created_at<>NEW.started_at OR j.scheduled_for IS NULL OR j.scheduled_for<e.expires_at OR j.scheduled_for<NEW.started_at
        THEN RAISE EXCEPTION 'Carry expiry attempt differs from its job and prefix' USING ERRCODE='23514'; END IF;
    IF NEW.attempt=1 THEN
        IF e.version<>0 OR e.job_id<>NEW.job_id OR NEW.base_completed<>0 OR NEW.started_at<>e.created_at
            OR NEW.reason<>e.reason OR j.actor_id<>e.actor_id OR j.scheduled_for<>e.expires_at
            THEN RAISE EXCEPTION 'Carry expiry initial job differs from its admission' USING ERRCODE='23514'; END IF;
    ELSE
        SELECT * INTO previous FROM background_jobs WHERE company_id=NEW.company_id AND id=e.job_id;
        IF NEW.attempt<>e.version+2 OR previous.id IS NULL OR previous.status NOT IN ('FAILED','CANCELLED') OR NEW.job_id=e.job_id
            THEN RAISE EXCEPTION 'Carry expiry recovery requires a terminal original job' USING ERRCODE='23514'; END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER leave_carry_expiry_attempt_scope BEFORE INSERT ON leave_carry_expiry_attempts
    FOR EACH ROW EXECUTE FUNCTION validate_leave_carry_expiry_attempt();

CREATE FUNCTION validate_leave_carry_expiry_outcome() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE e leave_carry_expiries; j background_jobs; b leave_carry_buckets; c leave_year_closings;
    l leave_ledger; m leave_carry_movements; completed integer; employee uuid; BEGIN
    SELECT * INTO e FROM leave_carry_expiries WHERE company_id=NEW.company_id AND id=NEW.expiry_id;
    SELECT * INTO j FROM background_jobs WHERE company_id=NEW.company_id AND id=NEW.job_id;
    SELECT coalesce(max(ordinal),0) INTO completed FROM leave_carry_expiry_outcomes WHERE company_id=NEW.company_id AND expiry_id=NEW.expiry_id;
    IF e.id IS NULL OR j.id IS NULL OR e.job_id<>j.id OR j.kind<>'LEAVE_CARRY_EXPIRY' OR j.status<>'RUNNING'
        OR j.cancellation_requested OR j.lease_until<=clock_timestamp() OR NEW.ordinal<>completed+1 OR NEW.ordinal>e.total_employees
        OR NEW.recorded_at<e.expires_at
        THEN RAISE EXCEPTION 'Carry expiry outcome requires its due active prefix' USING ERRCODE='23514'; END IF;
    IF e.source_kind='BATCH' THEN
        SELECT employment_id INTO employee FROM leave_batch_targets WHERE company_id=NEW.company_id AND batch_id=e.batch_id AND ordinal=NEW.ordinal;
    ELSE employee:=e.employment_id; END IF;
    IF employee IS NULL OR employee<>NEW.employment_id THEN RAISE EXCEPTION 'Carry expiry target differs from its cohort' USING ERRCODE='23514'; END IF;
    SELECT * INTO c FROM leave_year_closings WHERE company_id=NEW.company_id AND employment_id=employee AND type_id=e.type_id AND balance_year=e.balance_year-1
        AND ((e.source_kind='BATCH' AND batch_id=e.batch_id) OR (e.source_kind='CLOSING' AND id=e.closing_id AND batch_id IS NULL));
    SELECT * INTO b FROM leave_carry_buckets WHERE company_id=NEW.company_id AND id=c.id;
    IF NEW.status='NO_AWARD' THEN
        IF b.id IS NOT NULL THEN RAISE EXCEPTION 'Existing carry award cannot be skipped' USING ERRCODE='23514'; END IF;
        RETURN NEW;
    END IF;
    IF b.id IS NULL OR (b.employment_id,b.type_id,b.balance_year,b.company_timezone,b.valid_through,b.expires_at)
        IS DISTINCT FROM (employee,e.type_id,e.balance_year,e.company_timezone,e.valid_through,e.expires_at) OR b.available_half_days<>0
        THEN RAISE EXCEPTION 'Carry expiry outcome differs from its settled award' USING ERRCODE='23514'; END IF;
    IF NEW.status='EXPIRED' THEN
        SELECT * INTO l FROM leave_ledger WHERE company_id=NEW.company_id AND id=NEW.ledger_id;
        SELECT * INTO m FROM leave_carry_movements WHERE company_id=NEW.company_id AND id=l.carry_expiry_id;
        IF l.id IS NULL OR l.kind<>'EXPIRE' OR l.actor_id<>j.actor_id OR l.recorded_at<>NEW.recorded_at
            OR (l.employment_id,l.type_id,l.balance_year) IS DISTINCT FROM (employee,e.type_id,e.balance_year)
            OR l.available_delta<>-NEW.half_days OR l.reserved_delta<>0 OR l.consumed_delta<>0
            OR m.id IS NULL OR m.bucket_id<>b.id OR m.effect<>'EXPIRE' OR m.half_days<>NEW.half_days OR m.ledger_id<>l.id
            THEN RAISE EXCEPTION 'Carry expiry result lacks its exact ledger effect' USING ERRCODE='23514'; END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER leave_carry_expiry_outcome_scope BEFORE INSERT ON leave_carry_expiry_outcomes
    FOR EACH ROW EXECUTE FUNCTION validate_leave_carry_expiry_outcome();

CREATE FUNCTION check_leave_carry_expiry_job() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE company_key uuid; job_key uuid; e leave_carry_expiries; j background_jobs; a leave_carry_expiry_attempts; amount integer; BEGIN
    IF TG_TABLE_NAME='background_jobs' THEN
        IF NEW.kind<>'LEAVE_CARRY_EXPIRY' THEN RETURN NULL; END IF;
        -- Global leasing/heartbeat/failure cleanup must not inspect private company tables.
        IF TG_OP='UPDATE' THEN
            IF NEW.completed_items=OLD.completed_items AND NEW.status<>'SUCCEEDED' THEN RETURN NULL; END IF;
        END IF;
        company_key:=NEW.company_id; job_key:=NEW.id;
    ELSE company_key:=NEW.company_id; job_key:=NEW.job_id; END IF;
    SELECT * INTO j FROM background_jobs WHERE company_id=company_key AND id=job_key;
    SELECT * INTO a FROM leave_carry_expiry_attempts WHERE company_id=company_key AND job_id=job_key;
    SELECT * INTO e FROM leave_carry_expiries WHERE company_id=company_key AND id=a.expiry_id;
    SELECT coalesce(max(ordinal),a.base_completed)-a.base_completed INTO amount FROM leave_carry_expiry_outcomes WHERE company_id=company_key AND job_id=job_key;
    IF j.id IS NULL OR a.job_id IS NULL OR e.id IS NULL OR j.kind<>'LEAVE_CARRY_EXPIRY'
        OR j.request<>jsonb_build_object('expiryId',e.id::text) OR j.total_items<>e.total_employees-a.base_completed+1
        OR j.completed_items<>amount+(CASE WHEN j.status='SUCCEEDED' THEN 1 ELSE 0 END)
        OR (j.status='SUCCEEDED' AND a.base_completed+amount<>e.total_employees)
        OR (TG_TABLE_NAME='leave_carry_expiries' AND (e.job_id<>j.id OR e.version+1<>a.attempt))
        THEN RAISE EXCEPTION 'Carry expiry outcome and checkpoint are incomplete' USING ERRCODE='23514'; END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER carry_expiry_job_complete AFTER INSERT OR UPDATE ON background_jobs
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_leave_carry_expiry_job();
CREATE CONSTRAINT TRIGGER carry_expiry_origin_complete AFTER INSERT OR UPDATE ON leave_carry_expiries
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_leave_carry_expiry_job();
CREATE CONSTRAINT TRIGGER carry_expiry_attempt_complete AFTER INSERT ON leave_carry_expiry_attempts
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_leave_carry_expiry_job();
CREATE CONSTRAINT TRIGGER carry_expiry_outcome_complete AFTER INSERT ON leave_carry_expiry_outcomes
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_leave_carry_expiry_job();

CREATE FUNCTION protect_leave_carry_expiry_cancellation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NEW.kind='LEAVE_CARRY_EXPIRY' AND NEW.cancellation_requested AND NOT OLD.cancellation_requested
        THEN RAISE EXCEPTION 'Fixed carry expiry cannot be cancelled' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER carry_expiry_not_cancellable BEFORE UPDATE ON background_jobs
    FOR EACH ROW EXECUTE FUNCTION protect_leave_carry_expiry_cancellation();

DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['leave_carry_expiries','leave_carry_expiry_attempts','leave_carry_expiry_outcomes'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
    END LOOP;
END $$;


-- Historical buckets retain their original metadata. New expiring awards require durable work.
CREATE FUNCTION check_leave_carry_expiry_award() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c leave_year_closings; BEGIN
    IF NEW.validity_months IS NULL THEN RETURN NULL; END IF;
    SELECT * INTO c FROM leave_year_closings WHERE company_id=NEW.company_id AND id=NEW.id;
    IF NOT EXISTS(SELECT 1 FROM leave_carry_expiries e WHERE e.company_id=NEW.company_id AND e.type_id=NEW.type_id
        AND e.balance_year=NEW.balance_year AND e.company_timezone=NEW.company_timezone AND e.valid_through=NEW.valid_through AND e.expires_at=NEW.expires_at
        AND ((c.batch_id IS NOT NULL AND e.source_kind='BATCH' AND e.batch_id=c.batch_id)
          OR (c.batch_id IS NULL AND e.source_kind='CLOSING' AND e.closing_id=c.id AND e.employment_id=NEW.employment_id)))
        THEN RAISE EXCEPTION 'Expiring carry award requires its durable origin' USING ERRCODE='23514'; END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER leave_carry_expiry_award_complete AFTER INSERT ON leave_carry_buckets
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_leave_carry_expiry_award();

CREATE TRIGGER immutable_expiry_origin BEFORE DELETE ON leave_carry_expiries
    FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE TRIGGER immutable_expiry_attempt BEFORE UPDATE OR DELETE ON leave_carry_expiry_attempts
    FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE TRIGGER immutable_expiry_outcome BEFORE UPDATE OR DELETE ON leave_carry_expiry_outcomes
    FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
