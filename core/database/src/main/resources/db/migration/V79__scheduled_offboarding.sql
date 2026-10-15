-- A reviewed offboarding attempt owns its immutable basis. Worker state is independent.
CREATE TABLE offboarding_schedules (
    company_id uuid NOT NULL REFERENCES companies(id), id uuid NOT NULL,
    case_id uuid NOT NULL, employment_id uuid NOT NULL,
    case_version bigint NOT NULL CHECK(case_version >= 0 AND case_version < 9223372036854775807),
    employment_version bigint NOT NULL CHECK(employment_version >= 0 AND employment_version < 9223372036854775807),
    applied_employment_revision bigint NOT NULL CHECK(applied_employment_revision BETWEEN 0 AND employment_version),
    employee_account_id uuid REFERENCES accounts(id), last_working_date date NOT NULL,
    company_timezone varchar(80) NOT NULL, scheduled_for timestamptz NOT NULL,
    valid_until timestamptz NOT NULL, attempt integer NOT NULL CHECK(attempt BETWEEN 1 AND 8),
    job_id uuid NOT NULL UNIQUE REFERENCES background_jobs(id),
    created_by uuid NOT NULL REFERENCES accounts(id), created_at timestamptz NOT NULL,
    reason varchar(1000) NOT NULL CHECK(length(trim(reason)) > 0),
    status varchar(16) NOT NULL CHECK(status IN ('SCHEDULED','COMPLETED','FAILED','CANCELLED')),
    version bigint NOT NULL CHECK(version IN (0,1)), finished_at timestamptz,
    failure_code varchar(80),
    PRIMARY KEY(company_id,id), UNIQUE(company_id,case_id,attempt),
    FOREIGN KEY(company_id,case_id) REFERENCES lifecycle_cases(company_id,id),
    FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id),
    CHECK(isfinite(created_at) AND isfinite(scheduled_for) AND isfinite(valid_until)),
    CHECK(created_at < scheduled_for AND valid_until = scheduled_for + interval '168 hours'),
    CHECK(employee_account_id IS DISTINCT FROM created_by),
    CHECK((status='SCHEDULED' AND version=0 AND finished_at IS NULL AND failure_code IS NULL) OR
          (status='COMPLETED' AND version=1 AND finished_at IS NOT NULL AND failure_code IS NULL) OR
          (status IN ('FAILED','CANCELLED') AND version=1 AND finished_at IS NOT NULL AND
           failure_code IS NOT NULL AND failure_code ~ '^[a-z][a-z0-9_.]{0,79}$')),
    CHECK(finished_at IS NULL OR (isfinite(finished_at) AND finished_at >= created_at))
);
CREATE UNIQUE INDEX offboarding_schedule_pending ON offboarding_schedules(company_id,case_id)
    WHERE status='SCHEDULED';
ALTER TABLE offboarding_schedules ENABLE ROW LEVEL SECURITY;
ALTER TABLE offboarding_schedules FORCE ROW LEVEL SECURITY;
CREATE POLICY company_scope ON offboarding_schedules USING(company_id=current_company_id())
    WITH CHECK(company_id=current_company_id());
CREATE POLICY schedule_creator ON offboarding_schedules AS RESTRICTIVE FOR INSERT
    WITH CHECK(created_by=current_actor_id());

CREATE FUNCTION protect_offboarding_schedule() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE job background_jobs%ROWTYPE; case_row lifecycle_cases%ROWTYPE; employee employments%ROWTYPE;
    account uuid; zone text;
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Offboarding authorizations cannot be deleted' USING ERRCODE='23514';
    END IF;
    IF TG_OP='UPDATE' THEN
        IF OLD.status<>'SCHEDULED' OR NEW.status='SCHEDULED' OR NEW.version<>OLD.version+1 OR
            (to_jsonb(NEW)-ARRAY['status','version','finished_at','failure_code']) IS DISTINCT FROM
            (to_jsonb(OLD)-ARRAY['status','version','finished_at','failure_code']) THEN
            RAISE EXCEPTION 'Offboarding authorization and terminal outcome are immutable' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    SELECT * INTO job FROM background_jobs WHERE id=NEW.job_id;
    SELECT * INTO case_row FROM lifecycle_cases WHERE company_id=NEW.company_id AND id=NEW.case_id FOR SHARE;
    SELECT * INTO employee FROM employments WHERE company_id=NEW.company_id AND id=NEW.employment_id FOR SHARE;
    SELECT account_id INTO account FROM persons WHERE id=employee.person_id FOR SHARE;
    SELECT timezone INTO zone FROM companies WHERE id=NEW.company_id;
    IF NEW.status<>'SCHEDULED' OR NEW.version<>0 OR job.id IS NULL OR
        job.company_id<>NEW.company_id OR job.actor_id<>NEW.created_by OR job.kind<>'PEOPLE_OFFBOARD' OR
        job.status<>'QUEUED' OR job.cancellation_requested OR job.total_items<>1 OR
        job.progress_mode<>'FIXED_TOTAL' OR job.completed_items<>0 OR
        job.request<>jsonb_build_object('scheduleId',NEW.id::text) OR
        job.scheduled_for IS DISTINCT FROM NEW.scheduled_for OR job.created_at<>NEW.created_at OR
        case_row.id IS NULL OR case_row.kind<>'OFFBOARDING' OR case_row.status<>'OPEN' OR
        case_row.version<>NEW.case_version OR case_row.employment_id<>NEW.employment_id OR
        case_row.target_date<>NEW.last_working_date OR employee.version<>NEW.employment_version OR
        account IS DISTINCT FROM NEW.employee_account_id OR zone IS DISTINCT FROM NEW.company_timezone OR
        EXISTS(SELECT 1 FROM lifecycle_tasks WHERE company_id=NEW.company_id AND case_id=NEW.case_id AND
            (status='PENDING' OR (required AND status<>'DONE'))) OR
        NEW.attempt<>(SELECT count(*)+1 FROM offboarding_schedules WHERE company_id=NEW.company_id AND case_id=NEW.case_id) OR
        NOT EXISTS(SELECT 1 FROM employees_at(NEW.company_id,(NEW.created_at AT TIME ZONE NEW.company_timezone)::date)
            WHERE id=NEW.employment_id AND applied_revision=NEW.applied_employment_revision AND
                status<>'ENDED' AND start_date<=NEW.last_working_date AND
                (end_date IS NULL OR end_date=NEW.last_working_date)) OR
        NEW.scheduled_for<>((NEW.last_working_date+1)::timestamp AT TIME ZONE NEW.company_timezone) OR
        NEW.last_working_date NOT BETWEEN (NEW.created_at AT TIME ZONE NEW.company_timezone)::date AND
            (NEW.created_at AT TIME ZONE NEW.company_timezone)::date+365 THEN
        RAISE EXCEPTION 'Offboarding schedule requires its observed case, employment and queued job' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER offboarding_schedule_guard BEFORE INSERT OR UPDATE OR DELETE ON offboarding_schedules
    FOR EACH ROW EXECUTE FUNCTION protect_offboarding_schedule();

-- Queue-wide exhausted-lease recovery does not gain access to People tables. An HR command can
-- explicitly close the retained authorization after observing that terminal job.
CREATE FUNCTION require_offboarding_outcome() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE job background_jobs%ROWTYPE; expected_job_status text;
BEGIN
    SELECT * INTO job FROM background_jobs WHERE id=NEW.job_id AND company_id=NEW.company_id;
    expected_job_status := CASE NEW.status WHEN 'COMPLETED' THEN 'SUCCEEDED' ELSE NEW.status END;
    IF job.id IS NULL OR job.status<>expected_job_status OR
        (NEW.status='COMPLETED' AND job.completed_items<>1) OR
        (NEW.status<>'COMPLETED' AND (job.completed_items<>0 OR job.failure_code IS DISTINCT FROM NEW.failure_code)) THEN
        RAISE EXCEPTION 'Offboarding outcome requires its matching terminal job' USING ERRCODE='23514';
    END IF;
    IF NEW.status='COMPLETED' AND (job.cancellation_requested OR
        NOT EXISTS(SELECT 1 FROM lifecycle_cases c JOIN lifecycle_events e ON e.company_id=c.company_id AND
            e.case_id=c.id AND e.version=c.version WHERE c.company_id=NEW.company_id AND c.id=NEW.case_id AND
            c.version=NEW.case_version+1 AND c.status='COMPLETED' AND e.action='COMPLETED' AND e.actor_id=NEW.created_by) OR
        NOT EXISTS(SELECT 1 FROM employment_revisions WHERE company_id=NEW.company_id AND
            employment_id=NEW.employment_id AND revision=NEW.employment_version+1 AND status='ENDED' AND
            end_date=NEW.last_working_date AND effective_from=(NEW.finished_at AT TIME ZONE NEW.company_timezone)::date AND
            actor_id=NEW.created_by)) THEN
        RAISE EXCEPTION 'Completed offboarding requires its retained case and ended employment' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER offboarding_outcome_complete AFTER UPDATE ON offboarding_schedules
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_offboarding_outcome();

CREATE FUNCTION require_completed_offboarding_job() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM offboarding_schedules WHERE company_id=NEW.company_id AND job_id=NEW.id AND status='COMPLETED') THEN
        RAISE EXCEPTION 'A successful offboarding job requires its completed authorization' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER completed_offboarding_job AFTER UPDATE ON background_jobs
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
    WHEN(NEW.kind='PEOPLE_OFFBOARD' AND NEW.status='SUCCEEDED') EXECUTE FUNCTION require_completed_offboarding_job();
