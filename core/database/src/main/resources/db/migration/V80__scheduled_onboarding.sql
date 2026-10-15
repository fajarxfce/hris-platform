-- Scheduled case initiation references the existing immutable template history.
CREATE TABLE onboarding_schedules (
    company_id uuid NOT NULL REFERENCES companies(id), id uuid NOT NULL,
    case_id uuid NOT NULL, employment_id uuid NOT NULL,
    employment_version bigint NOT NULL CHECK(employment_version >= 0 AND employment_version < 9223372036854775807),
    applied_employment_revision bigint NOT NULL CHECK(applied_employment_revision BETWEEN 0 AND employment_version),
    template_id uuid NOT NULL, template_version bigint NOT NULL CHECK(template_version >= 0 AND template_version < 9223372036854775807),
    target_date date NOT NULL CHECK(target_date BETWEEN DATE '1900-01-01' AND DATE '2200-12-31'),
    starts_on date NOT NULL CHECK(starts_on BETWEEN DATE '1900-01-01' AND DATE '2200-12-31' AND starts_on<=target_date),
    assignees jsonb NOT NULL CHECK(jsonb_typeof(assignees)='object' AND octet_length(assignees::text)<=8192),
    company_timezone varchar(80) NOT NULL, scheduled_for timestamptz NOT NULL,
    valid_until timestamptz NOT NULL, attempt integer NOT NULL CHECK(attempt BETWEEN 1 AND 8),
    job_id uuid NOT NULL UNIQUE REFERENCES background_jobs(id),
    created_by uuid NOT NULL REFERENCES accounts(id), created_at timestamptz NOT NULL,
    reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    status varchar(16) NOT NULL CHECK(status IN ('SCHEDULED','STARTED','FAILED','CANCELLED')),
    version bigint NOT NULL CHECK(version IN (0,1)), finished_at timestamptz,
    failure_code varchar(80),
    PRIMARY KEY(company_id,id), UNIQUE(company_id,case_id), UNIQUE(company_id,employment_id,attempt),
    FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id),
    FOREIGN KEY(company_id,template_id,template_version) REFERENCES lifecycle_template_revisions(company_id,id,revision),
    CHECK(id<>case_id AND isfinite(created_at) AND isfinite(scheduled_for) AND isfinite(valid_until)),
    CHECK(created_at < scheduled_for AND scheduled_for < valid_until AND valid_until <= scheduled_for + interval '168 hours'),
    CHECK((status='SCHEDULED' AND version=0 AND finished_at IS NULL AND failure_code IS NULL) OR
          (status='STARTED' AND version=1 AND finished_at IS NOT NULL AND failure_code IS NULL) OR
          (status IN ('FAILED','CANCELLED') AND version=1 AND finished_at IS NOT NULL AND
           failure_code IS NOT NULL AND failure_code ~ '^[a-z][a-z0-9_.]{0,79}$')),
    CHECK(finished_at IS NULL OR (isfinite(finished_at) AND finished_at >= created_at)),
    CHECK(status<>'STARTED' OR (finished_at>=scheduled_for AND finished_at<valid_until))
);
CREATE UNIQUE INDEX onboarding_schedule_pending ON onboarding_schedules(company_id,employment_id)
    WHERE status='SCHEDULED';
ALTER TABLE onboarding_schedules ENABLE ROW LEVEL SECURITY;
ALTER TABLE onboarding_schedules FORCE ROW LEVEL SECURITY;
CREATE POLICY company_scope ON onboarding_schedules USING(company_id=current_company_id())
    WITH CHECK(company_id=current_company_id());
CREATE POLICY schedule_creator ON onboarding_schedules AS RESTRICTIVE FOR INSERT
    WITH CHECK(created_by=current_actor_id());

CREATE FUNCTION protect_onboarding_schedule() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE job background_jobs%ROWTYPE; template lifecycle_templates%ROWTYPE;
    employee employments%ROWTYPE; zone text;
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Onboarding authorizations cannot be deleted' USING ERRCODE='23514';
    END IF;
    IF TG_OP='UPDATE' THEN
        IF OLD.status<>'SCHEDULED' OR NEW.status='SCHEDULED' OR NEW.version<>OLD.version+1 OR
            (to_jsonb(NEW)-ARRAY['status','version','finished_at','failure_code']) IS DISTINCT FROM
            (to_jsonb(OLD)-ARRAY['status','version','finished_at','failure_code']) THEN
            RAISE EXCEPTION 'Onboarding authorization and terminal outcome are immutable' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    SELECT * INTO job FROM background_jobs WHERE id=NEW.job_id;
    SELECT * INTO employee FROM employments WHERE company_id=NEW.company_id AND id=NEW.employment_id FOR SHARE;
    SELECT * INTO template FROM lifecycle_templates WHERE company_id=NEW.company_id AND id=NEW.template_id FOR SHARE;
    SELECT timezone INTO zone FROM companies WHERE id=NEW.company_id;
    IF NEW.status<>'SCHEDULED' OR NEW.version<>0 OR job.id IS NULL OR
        job.company_id<>NEW.company_id OR job.actor_id<>NEW.created_by OR job.kind<>'PEOPLE_ONBOARD_START' OR
        job.status<>'QUEUED' OR job.cancellation_requested OR job.total_items<>1 OR
        job.progress_mode<>'FIXED_TOTAL' OR job.completed_items<>0 OR
        job.request<>jsonb_build_object('scheduleId',NEW.id::text) OR
        job.scheduled_for IS DISTINCT FROM NEW.scheduled_for OR job.created_at<>NEW.created_at OR
        employee.id IS NULL OR employee.version<>NEW.employment_version OR
        template.id IS NULL OR NOT template.active OR template.kind<>'ONBOARDING' OR template.version<>NEW.template_version OR
        zone IS DISTINCT FROM NEW.company_timezone OR
        EXISTS(SELECT 1 FROM lifecycle_cases WHERE company_id=NEW.company_id AND
            (id=NEW.case_id OR (employment_id=NEW.employment_id AND status IN ('OPEN','COMPLETED')))) OR
        NEW.attempt<>(SELECT count(*)+1 FROM onboarding_schedules WHERE company_id=NEW.company_id AND employment_id=NEW.employment_id) OR
        NOT EXISTS(SELECT 1 FROM employees_at(NEW.company_id,NEW.target_date)
            WHERE id=NEW.employment_id AND applied_revision=NEW.applied_employment_revision AND status<>'ENDED' AND start_date=NEW.target_date) OR
        NEW.scheduled_for<>(NEW.starts_on::timestamp AT TIME ZONE NEW.company_timezone) OR
        NEW.valid_until<>least(NEW.scheduled_for+interval '168 hours',((NEW.target_date+1)::timestamp AT TIME ZONE NEW.company_timezone)) OR
        NEW.target_date>(NEW.created_at AT TIME ZONE NEW.company_timezone)::date+365 OR
        (SELECT count(*) FROM jsonb_each_text(NEW.assignees))>64 OR
        EXISTS(SELECT 1 FROM jsonb_each_text(NEW.assignees) a WHERE
            a.key !~ '^[a-z][a-z0-9_-]{0,47}$' OR
            NOT EXISTS(SELECT 1 FROM jsonb_array_elements(template.tasks) t WHERE t->>'key'=a.key) OR
            NOT EXISTS(SELECT 1 FROM company_memberships m JOIN accounts u ON u.id=m.account_id
                WHERE m.company_id=NEW.company_id AND m.account_id::text=a.value AND m.active AND u.active AND
                    EXISTS(SELECT 1 FROM membership_permissions p WHERE p.company_id=m.company_id AND p.account_id=m.account_id
                        AND p.permission IN ('people.lifecycle.perform','people.lifecycle.manage')))) THEN
        RAISE EXCEPTION 'Onboarding requires its reviewed employment, template, assignees and queued job' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER onboarding_schedule_guard BEFORE INSERT OR UPDATE OR DELETE ON onboarding_schedules
    FOR EACH ROW EXECUTE FUNCTION protect_onboarding_schedule();

-- Queue-only exhausted-lease recovery does not acquire People access. Explicit HR recovery closes
-- an authorization whose failed/cancelled job could no longer run its feature cleanup.
CREATE FUNCTION require_onboarding_outcome() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE job background_jobs%ROWTYPE; definitions jsonb; retained_template_name text; expected_job_status text;
BEGIN
    SELECT * INTO job FROM background_jobs WHERE id=NEW.job_id AND company_id=NEW.company_id;
    expected_job_status := CASE NEW.status WHEN 'STARTED' THEN 'SUCCEEDED' ELSE NEW.status END;
    IF job.id IS NULL OR job.status<>expected_job_status OR
        (NEW.status='STARTED' AND job.completed_items<>1) OR
        (NEW.status<>'STARTED' AND (job.completed_items<>0 OR job.failure_code IS DISTINCT FROM NEW.failure_code)) THEN
        RAISE EXCEPTION 'Onboarding outcome requires its matching terminal job' USING ERRCODE='23514';
    END IF;
    IF NEW.status='STARTED' THEN
        SELECT tasks,name INTO definitions,retained_template_name FROM lifecycle_template_revisions
            WHERE company_id=NEW.company_id AND id=NEW.template_id AND revision=NEW.template_version;
        IF job.cancellation_requested OR
            EXISTS(SELECT 1 FROM lifecycle_cases WHERE company_id=NEW.company_id AND
                employment_id=NEW.employment_id AND status='COMPLETED') OR
            NOT EXISTS(SELECT 1 FROM lifecycle_cases c JOIN lifecycle_events e ON e.company_id=c.company_id AND
                e.case_id=c.id AND e.version=0 WHERE c.company_id=NEW.company_id AND c.id=NEW.case_id AND
                c.employment_id=NEW.employment_id AND c.kind='ONBOARDING' AND c.target_date=NEW.target_date AND
                c.template_id=NEW.template_id AND c.template_version=NEW.template_version AND c.template_name=retained_template_name AND
                c.version=0 AND c.status='OPEN' AND c.created_by=NEW.created_by AND c.created_at=NEW.finished_at AND
                e.action='CREATED' AND e.actor_id=NEW.created_by AND e.recorded_at=NEW.finished_at AND
                e.task_key IS NULL AND e.assignee_id IS NULL AND e.reason=NEW.reason) OR
            (SELECT count(*) FROM lifecycle_tasks WHERE company_id=NEW.company_id AND case_id=NEW.case_id)<>jsonb_array_length(definitions) OR
            EXISTS(SELECT 1 FROM jsonb_array_elements(definitions) d LEFT JOIN lifecycle_tasks t ON
                t.company_id=NEW.company_id AND t.case_id=NEW.case_id AND t.key=d->>'key' WHERE
                t.key IS NULL OR t.title IS DISTINCT FROM d->>'title' OR t.required IS DISTINCT FROM (d->>'required')::boolean OR
                t.due_date IS DISTINCT FROM NEW.target_date+(d->>'dueDays')::integer OR
                t.assignee_id::text IS DISTINCT FROM NEW.assignees->>(d->>'key') OR t.status<>'PENDING' OR
                t.completed_by IS NOT NULL OR t.completed_at IS NOT NULL) THEN
            RAISE EXCEPTION 'Started onboarding requires its exact new case, pending checklist and initial history' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER onboarding_outcome_complete AFTER UPDATE ON onboarding_schedules
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_onboarding_outcome();

CREATE FUNCTION require_started_onboarding_job() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM onboarding_schedules WHERE company_id=NEW.company_id AND job_id=NEW.id AND status='STARTED') THEN
        RAISE EXCEPTION 'A successful onboarding job requires its started authorization' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER started_onboarding_job AFTER UPDATE ON background_jobs
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
    WHEN(NEW.kind='PEOPLE_ONBOARD_START' AND NEW.status='SUCCEEDED') EXECUTE FUNCTION require_started_onboarding_job();
