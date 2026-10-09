CREATE TABLE leave_batches (
    company_id uuid NOT NULL,id uuid NOT NULL,type_id uuid NOT NULL,
    kind varchar(16) NOT NULL CHECK(kind IN ('ACCRUAL','YEAR_CLOSE')),period date NOT NULL,
    policy_revision bigint NOT NULL,policy_version bigint NOT NULL CHECK(policy_version>=policy_revision),
    policy_snapshot jsonb NOT NULL CHECK(jsonb_typeof(policy_snapshot)='object' AND pg_column_size(policy_snapshot)<=65536),
    timezone varchar(80) NOT NULL,actor_id uuid NOT NULL REFERENCES accounts(id),created_at timestamptz NOT NULL,
    reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),total_employees integer NOT NULL CHECK(total_employees BETWEEN 1 AND 5000),
    job_id uuid NOT NULL,status varchar(16) NOT NULL CHECK(status IN ('RUNNING','STOPPED','COMPLETED')),version bigint NOT NULL CHECK(version BETWEEN 0 AND 24),
    PRIMARY KEY(company_id,id),UNIQUE(company_id,job_id),UNIQUE(company_id,id,kind,type_id),
    FOREIGN KEY(company_id,type_id,policy_revision) REFERENCES leave_type_revisions(company_id,type_id,revision),
    FOREIGN KEY(company_id,job_id) REFERENCES background_jobs(company_id,id),
    CHECK(extract(day FROM period)=1 AND extract(year FROM period) BETWEEN 1900 AND 2199 AND (kind<>'YEAR_CLOSE' OR extract(month FROM period)=12))
);
CREATE INDEX leave_batches_active ON leave_batches(company_id,type_id,kind,period) WHERE status='RUNNING';
CREATE TABLE leave_batch_targets (
    company_id uuid NOT NULL,batch_id uuid NOT NULL,ordinal integer NOT NULL CHECK(ordinal BETWEEN 1 AND 5000),employment_id uuid NOT NULL,
    PRIMARY KEY(company_id,batch_id,ordinal),UNIQUE(company_id,batch_id,employment_id),UNIQUE(company_id,batch_id,ordinal,employment_id),
    FOREIGN KEY(company_id,batch_id) REFERENCES leave_batches(company_id,id),FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id)
);
CREATE TABLE leave_batch_attempts (
    company_id uuid NOT NULL,batch_id uuid NOT NULL,job_id uuid NOT NULL,attempt integer NOT NULL CHECK(attempt BETWEEN 1 AND 8),
    base_completed integer NOT NULL CHECK(base_completed BETWEEN 0 AND 5000),started_at timestamptz NOT NULL,reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    PRIMARY KEY(company_id,batch_id,job_id),UNIQUE(company_id,batch_id,attempt),
    FOREIGN KEY(company_id,batch_id) REFERENCES leave_batches(company_id,id),FOREIGN KEY(company_id,job_id) REFERENCES background_jobs(company_id,id)
);
ALTER TABLE leave_batches ADD FOREIGN KEY(company_id,id,job_id) REFERENCES leave_batch_attempts(company_id,batch_id,job_id) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE leave_batch_results (
    company_id uuid NOT NULL,batch_id uuid NOT NULL,ordinal integer NOT NULL,employment_id uuid NOT NULL,type_id uuid NOT NULL,batch_kind varchar(16) NOT NULL,
    job_id uuid NOT NULL,status varchar(16) NOT NULL CHECK(status IN ('APPLIED','UNCHANGED','SKIPPED','FAILED')),resource_id uuid,
    failure_code varchar(80),parameters jsonb NOT NULL CHECK(jsonb_typeof(parameters)='object' AND pg_column_size(parameters)<=8192),completed_at timestamptz NOT NULL,
    accrual_id uuid GENERATED ALWAYS AS(CASE WHEN batch_kind='ACCRUAL' AND status IN ('APPLIED','UNCHANGED') THEN resource_id END) STORED,
    closing_id uuid GENERATED ALWAYS AS(CASE WHEN batch_kind='YEAR_CLOSE' AND status IN ('APPLIED','UNCHANGED') THEN resource_id END) STORED,
    PRIMARY KEY(company_id,batch_id,ordinal),
    FOREIGN KEY(company_id,batch_id,ordinal,employment_id) REFERENCES leave_batch_targets(company_id,batch_id,ordinal,employment_id),
    FOREIGN KEY(company_id,batch_id,batch_kind,type_id) REFERENCES leave_batches(company_id,id,kind,type_id),
    FOREIGN KEY(company_id,batch_id,job_id) REFERENCES leave_batch_attempts(company_id,batch_id,job_id),
    FOREIGN KEY(company_id,accrual_id,employment_id,type_id) REFERENCES leave_accrual_postings(company_id,id,employment_id,type_id),
    FOREIGN KEY(company_id,closing_id,employment_id,type_id) REFERENCES leave_year_closings(company_id,id,employment_id,type_id),
    CHECK((status IN ('APPLIED','UNCHANGED') AND resource_id IS NOT NULL AND failure_code IS NULL AND parameters='{}'::jsonb)
       OR (status IN ('SKIPPED','FAILED') AND resource_id IS NULL AND failure_code ~ '^[a-z][a-z0-9_]{0,79}$'))
);
CREATE INDEX leave_results_attempt ON leave_batch_results(company_id,batch_id,job_id);

CREATE FUNCTION protect_leave_batch() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p leave_type_revisions; BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.version<>0 OR NEW.status<>'RUNNING' THEN RAISE EXCEPTION 'Leave batches start running at version zero' USING ERRCODE='23514'; END IF;
        SELECT * INTO p FROM leave_type_revisions WHERE company_id=NEW.company_id AND type_id=NEW.type_id AND revision=NEW.policy_revision;
        IF p.type_id IS NULL OR (NEW.policy_snapshot->>'typeId') IS DISTINCT FROM NEW.type_id::text OR (NEW.policy_snapshot->>'revision') IS DISTINCT FROM NEW.policy_revision::text
            OR (NEW.policy_snapshot->>'code') IS DISTINCT FROM (SELECT code FROM leave_types WHERE company_id=NEW.company_id AND id=NEW.type_id)
            OR (NEW.policy_snapshot->'policy') IS DISTINCT FROM (p.details || jsonb_build_object('attachmentRequired',coalesce(p.details->'attachmentRequired','false'::jsonb),'accrual',p.details->'accrual'))
            THEN RAISE EXCEPTION 'Leave batch policy snapshot is inconsistent' USING ERRCODE='23514'; END IF;
        RETURN NEW;
    END IF;
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Leave batch history is retained' USING ERRCODE='23514'; END IF;
    IF (to_jsonb(NEW)-ARRAY['status','job_id','version']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['status','job_id','version']) OR NEW.version<>OLD.version+1 OR OLD.status='COMPLETED'
        THEN RAISE EXCEPTION 'Leave batch inputs and completed results are immutable' USING ERRCODE='23514'; END IF;
    IF NEW.job_id=OLD.job_id THEN
        IF OLD.status<>'RUNNING' OR NEW.status NOT IN ('STOPPED','COMPLETED') THEN RAISE EXCEPTION 'Invalid leave batch transition' USING ERRCODE='23514'; END IF;
    ELSE
        IF NEW.status<>'RUNNING' OR NOT EXISTS(SELECT 1 FROM background_jobs WHERE company_id=OLD.company_id AND id=OLD.job_id AND status IN ('FAILED','CANCELLED'))
            THEN RAISE EXCEPTION 'Only stopped leave work can resume' USING ERRCODE='23514'; END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER leave_batch_protected BEFORE INSERT OR UPDATE OR DELETE ON leave_batches FOR EACH ROW EXECUTE FUNCTION protect_leave_batch();
CREATE FUNCTION validate_leave_batch_target() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM leave_batches WHERE company_id=NEW.company_id AND id=NEW.batch_id AND version=0 AND status='RUNNING' AND NEW.ordinal<=total_employees)
        THEN RAISE EXCEPTION 'Leave target does not match its initial batch' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER leave_target_scope BEFORE INSERT ON leave_batch_targets FOR EACH ROW EXECUTE FUNCTION validate_leave_batch_target();
CREATE FUNCTION validate_leave_batch_attempt() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE b leave_batches; j background_jobs; BEGIN
    SELECT * INTO b FROM leave_batches WHERE company_id=NEW.company_id AND id=NEW.batch_id;
    SELECT * INTO j FROM background_jobs WHERE company_id=NEW.company_id AND id=NEW.job_id;
    IF b.id IS NULL OR j.id IS NULL OR j.actor_id<>b.actor_id OR j.kind<>(CASE b.kind WHEN 'ACCRUAL' THEN 'LEAVE_ACCRUAL' ELSE 'LEAVE_YEAR_CLOSE' END)
        OR j.request->>'batchId' IS DISTINCT FROM b.id::text OR j.progress_mode<>'FIXED_TOTAL' OR j.total_items<>b.total_employees-NEW.base_completed+1
        OR NEW.base_completed<>(SELECT count(*) FROM leave_batch_results WHERE company_id=b.company_id AND batch_id=b.id)
        OR NEW.attempt<>(SELECT count(*)+1 FROM leave_batch_attempts WHERE company_id=b.company_id AND batch_id=b.id)
        THEN RAISE EXCEPTION 'Leave batch job scope does not match' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER leave_attempt_scope BEFORE INSERT ON leave_batch_attempts FOR EACH ROW EXECUTE FUNCTION validate_leave_batch_attempt();
CREATE FUNCTION validate_leave_batch_result() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE b leave_batches; j background_jobs; p leave_accrual_postings; c leave_year_closings; BEGIN
    SELECT * INTO b FROM leave_batches WHERE company_id=NEW.company_id AND id=NEW.batch_id;
    SELECT * INTO j FROM background_jobs WHERE company_id=NEW.company_id AND id=NEW.job_id;
    IF b.id IS NULL OR b.status<>'RUNNING' OR b.job_id<>NEW.job_id OR j.id IS NULL OR j.status<>'RUNNING' OR j.cancellation_requested OR j.lease_until<=clock_timestamp()
        THEN RAISE EXCEPTION 'Leave result requires its active job' USING ERRCODE='23514'; END IF;
    IF NEW.status IN ('APPLIED','UNCHANGED') AND b.kind='ACCRUAL' THEN
        SELECT * INTO p FROM leave_accrual_postings WHERE company_id=NEW.company_id AND id=NEW.resource_id;
        IF p.id IS NULL OR p.frequency IS DISTINCT FROM (b.policy_snapshot->'policy'->'accrual'->>'frequency')
            OR p.period_key<>(CASE p.frequency WHEN 'ANNUAL' THEN make_date(extract(year FROM b.period)::integer,1,1) ELSE b.period END)
            OR (NEW.status='APPLIED' AND (p.policy_revision<>b.policy_revision OR p.actor_id<>b.actor_id OR p.processed_month<>b.period))
            THEN RAISE EXCEPTION 'Leave result accrual evidence does not match' USING ERRCODE='23514'; END IF;
    ELSIF NEW.status IN ('APPLIED','UNCHANGED') AND b.kind='YEAR_CLOSE' THEN
        SELECT * INTO c FROM leave_year_closings WHERE company_id=NEW.company_id AND id=NEW.resource_id;
        IF c.id IS NULL OR c.balance_year<>extract(year FROM b.period)::integer OR (NEW.status='APPLIED' AND (c.policy_revision<>b.policy_revision OR c.actor_id<>b.actor_id))
            THEN RAISE EXCEPTION 'Leave result closing evidence does not match' USING ERRCODE='23514'; END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER leave_result_scope BEFORE INSERT ON leave_batch_results FOR EACH ROW EXECUTE FUNCTION validate_leave_batch_result();
CREATE FUNCTION check_leave_batch_progress() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE b leave_batches; j background_jobs; a leave_batch_attempts; batch_key uuid; company_key uuid; target_count integer; result_count integer; step_count integer; BEGIN
    company_key:=NEW.company_id;
    IF TG_TABLE_NAME='leave_batches' THEN batch_key:=NEW.id;
    ELSIF TG_TABLE_NAME='background_jobs' THEN batch_key:=(NEW.request->>'batchId')::uuid;
    ELSE batch_key:=NEW.batch_id; END IF;
    SELECT * INTO b FROM leave_batches WHERE company_id=company_key AND id=batch_key;
    IF b.id IS NULL THEN RAISE EXCEPTION 'Leave job requires a matching batch' USING ERRCODE='23514'; END IF;
    SELECT count(*) INTO target_count FROM leave_batch_targets WHERE company_id=b.company_id AND batch_id=b.id;
    SELECT count(*) INTO result_count FROM leave_batch_results WHERE company_id=b.company_id AND batch_id=b.id;
    SELECT * INTO j FROM background_jobs WHERE company_id=b.company_id AND id=b.job_id;
    SELECT * INTO a FROM leave_batch_attempts WHERE company_id=b.company_id AND batch_id=b.id AND job_id=b.job_id;
    SELECT count(*) INTO step_count FROM leave_batch_results WHERE company_id=b.company_id AND batch_id=b.id AND job_id=b.job_id;
    IF TG_TABLE_NAME='leave_batch_attempts' THEN
        IF NEW.job_id<>b.job_id THEN RAISE EXCEPTION 'Leave attempt must bind its current batch' USING ERRCODE='23514'; END IF;
    END IF;
    IF target_count<>b.total_employees OR a.job_id IS NULL OR result_count<>a.base_completed+step_count
        OR j.completed_items<>step_count+(CASE WHEN j.status='SUCCEEDED' THEN 1 ELSE 0 END)
        OR (b.status='COMPLETED' AND (result_count<>b.total_employees OR j.status<>'SUCCEEDED'))
        OR (b.status='STOPPED' AND j.status NOT IN ('FAILED','CANCELLED'))
        OR (j.status='SUCCEEDED' AND b.status<>'COMPLETED')
        THEN RAISE EXCEPTION 'Leave job checkpoint does not match its retained results' USING ERRCODE='23514'; END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER leave_batch_complete AFTER INSERT OR UPDATE ON leave_batches DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_leave_batch_progress();
CREATE CONSTRAINT TRIGGER leave_attempt_checkpoint AFTER INSERT ON leave_batch_attempts DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_leave_batch_progress();
CREATE CONSTRAINT TRIGGER leave_result_checkpoint AFTER INSERT ON leave_batch_results DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_leave_batch_progress();
CREATE CONSTRAINT TRIGGER leave_job_complete AFTER UPDATE ON background_jobs DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN (NEW.kind IN ('LEAVE_ACCRUAL','LEAVE_YEAR_CLOSE') AND (NEW.completed_items IS DISTINCT FROM OLD.completed_items OR NEW.status='SUCCEEDED')) EXECUTE FUNCTION check_leave_batch_progress();
DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['leave_batches','leave_batch_targets','leave_batch_attempts','leave_batch_results'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
    END LOOP;
    FOREACH t IN ARRAY ARRAY['leave_batch_targets','leave_batch_attempts','leave_batch_results'] LOOP
        EXECUTE format('CREATE TRIGGER immutable_history BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION deny_history_mutation()',t);
    END LOOP;
END $$;
CREATE POLICY leave_batch_creator ON leave_batches AS RESTRICTIVE FOR INSERT WITH CHECK(actor_id=current_actor_id());
