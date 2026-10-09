ALTER TABLE payroll_periods DROP CONSTRAINT payroll_periods_status_check;
ALTER TABLE payroll_periods ADD CHECK(status IN('DRAFT','PROCESSING','CALCULATED','CANCELLED'));
ALTER TABLE payroll_periods ADD COLUMN current_run_id uuid;
ALTER TABLE payroll_periods ADD UNIQUE(company_id,id,earnings_month);
ALTER TABLE payroll_period_changes DROP CONSTRAINT payroll_period_changes_status_check;
ALTER TABLE payroll_period_changes ADD CHECK(status IN('DRAFT','PROCESSING','CALCULATED','CANCELLED'));
ALTER TABLE payroll_period_changes ADD COLUMN run_id uuid;

CREATE TABLE payroll_runs (
 company_id uuid NOT NULL,id uuid NOT NULL,period_id uuid NOT NULL,run_number integer NOT NULL CHECK(run_number BETWEEN 1 AND 20),
 earnings_month date NOT NULL,income_due_date date NOT NULL,planned_payment_date date NOT NULL,timezone varchar(80) NOT NULL,
 work_job_id uuid NOT NULL,work_period_version bigint NOT NULL CHECK(work_period_version>=0),policy_revision bigint NOT NULL,
 total_employees integer NOT NULL CHECK(total_employees BETWEEN 1 AND 5000),actor_id uuid NOT NULL REFERENCES accounts(id),
 review_reference varchar(200) NOT NULL CHECK(length(trim(review_reference))>0),reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
 created_at timestamptz NOT NULL,creation_transaction text NOT NULL DEFAULT pg_current_xact_id()::text,
 job_id uuid NOT NULL,status varchar(24) NOT NULL CHECK(status IN('PROCESSING','STOPPED','CALCULATED','ABANDONED')),
 version bigint NOT NULL CHECK(version BETWEEN 0 AND 24),processed integer NOT NULL DEFAULT 0 CHECK(processed BETWEEN 0 AND 5000),
 succeeded integer NOT NULL DEFAULT 0 CHECK(succeeded BETWEEN 0 AND 5000),failed integer NOT NULL DEFAULT 0 CHECK(failed BETWEEN 0 AND 5000),
 last_result integer GENERATED ALWAYS AS(CASE WHEN processed>0 THEN processed END) STORED,
 PRIMARY KEY(company_id,id),UNIQUE(company_id,id,period_id),UNIQUE(company_id,period_id,run_number),UNIQUE(company_id,job_id),
 FOREIGN KEY(company_id,period_id,earnings_month) REFERENCES payroll_periods(company_id,id,earnings_month),
 FOREIGN KEY(company_id,policy_revision) REFERENCES payroll_policy_revisions(company_id,revision),
 FOREIGN KEY(company_id,work_job_id) REFERENCES background_jobs(company_id,id),FOREIGN KEY(company_id,job_id) REFERENCES background_jobs(company_id,id),
 CHECK(income_due_date>=earnings_month AND income_due_date<(earnings_month+interval '1 month')::date),
 CHECK(processed=succeeded+failed AND processed<=total_employees)
);
CREATE UNIQUE INDEX payroll_active_run ON payroll_runs(company_id,period_id) WHERE status<>'ABANDONED';
CREATE INDEX payroll_run_cutoff ON payroll_runs(company_id,earnings_month,id) WHERE status<>'ABANDONED';
ALTER TABLE payroll_periods ADD FOREIGN KEY(company_id,current_run_id,id) REFERENCES payroll_runs(company_id,id,period_id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE payroll_period_changes ADD FOREIGN KEY(company_id,run_id,period_id) REFERENCES payroll_runs(company_id,id,period_id);

CREATE TABLE payroll_run_targets (
 company_id uuid NOT NULL,run_id uuid NOT NULL,period_id uuid NOT NULL,ordinal integer NOT NULL CHECK(ordinal BETWEEN 1 AND 5000),
 employment_id uuid NOT NULL,employee_number varchar(40) NOT NULL,employee_name varchar(200) NOT NULL,employment_version bigint NOT NULL CHECK(employment_version>=0),
 compensation_revision bigint,input_id uuid,input_revision bigint,tax_opening_id uuid,tax_opening_revision bigint,
 PRIMARY KEY(company_id,run_id,ordinal),UNIQUE(company_id,run_id,employment_id),
 FOREIGN KEY(company_id,run_id,period_id) REFERENCES payroll_runs(company_id,id,period_id),
 FOREIGN KEY(company_id,period_id,employment_id) REFERENCES payroll_period_members(company_id,period_id,employment_id),
 FOREIGN KEY(company_id,employment_id,compensation_revision) REFERENCES employee_compensation_revisions(company_id,employment_id,revision),
 FOREIGN KEY(company_id,input_id,input_revision) REFERENCES payroll_input_revisions(company_id,input_id,revision),
 FOREIGN KEY(company_id,tax_opening_id,tax_opening_revision) REFERENCES payroll_tax_opening_revisions(company_id,opening_id,revision),
 CHECK((input_id IS NULL)=(input_revision IS NULL)),CHECK((tax_opening_id IS NULL)=(tax_opening_revision IS NULL))
);
CREATE INDEX payroll_run_target_employee ON payroll_run_targets(company_id,employment_id,run_id);
CREATE TABLE payroll_run_attempts (
 company_id uuid NOT NULL,run_id uuid NOT NULL,job_id uuid NOT NULL,attempt integer NOT NULL CHECK(attempt BETWEEN 1 AND 8),
 base_completed integer NOT NULL CHECK(base_completed BETWEEN 0 AND 5000),started_at timestamptz NOT NULL,
 reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),PRIMARY KEY(company_id,run_id,job_id),UNIQUE(company_id,run_id,attempt),
 FOREIGN KEY(company_id,run_id) REFERENCES payroll_runs(company_id,id),FOREIGN KEY(company_id,job_id) REFERENCES background_jobs(company_id,id)
);
ALTER TABLE payroll_runs ADD FOREIGN KEY(company_id,id,job_id) REFERENCES payroll_run_attempts(company_id,run_id,job_id) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE payroll_run_results (
 company_id uuid NOT NULL,run_id uuid NOT NULL,ordinal integer NOT NULL,job_id uuid NOT NULL,
 status varchar(12) NOT NULL CHECK(status IN('SUCCEEDED','FAILED')),completed_at timestamptz NOT NULL,
 failure_kind varchar(32),failure_code varchar(80),failure_fields jsonb NOT NULL DEFAULT '{}',failure_parameters jsonb NOT NULL DEFAULT '{}',
 facts jsonb,calculation jsonb,taxable_gross numeric(16,2),withheld numeric(16,2),take_home numeric(16,2),
 PRIMARY KEY(company_id,run_id,ordinal),FOREIGN KEY(company_id,run_id,ordinal) REFERENCES payroll_run_targets(company_id,run_id,ordinal),
 FOREIGN KEY(company_id,run_id,job_id) REFERENCES payroll_run_attempts(company_id,run_id,job_id),
 CHECK(jsonb_typeof(failure_fields)='object' AND pg_column_size(failure_fields)<=8192),
 CHECK(jsonb_typeof(failure_parameters)='object' AND pg_column_size(failure_parameters)<=8192),
 CHECK(facts IS NULL OR (jsonb_typeof(facts)='object' AND octet_length(facts::text)<=262144)),
 CHECK(calculation IS NULL OR (jsonb_typeof(calculation)='object' AND octet_length(calculation::text)<=131072)),
 CHECK((status='SUCCEEDED' AND facts IS NOT NULL AND calculation IS NOT NULL AND failure_kind IS NULL AND failure_code IS NULL AND
        failure_fields='{}' AND failure_parameters='{}' AND taxable_gross IS NOT NULL AND withheld IS NOT NULL AND take_home IS NOT NULL AND taxable_gross BETWEEN 0 AND 50000000000 AND
        withheld BETWEEN -600000000000 AND 50000000000 AND take_home BETWEEN 0 AND 650000000000)
    OR (status='FAILED' AND calculation IS NULL AND taxable_gross IS NULL AND withheld IS NULL AND take_home IS NULL AND
        failure_kind IS NOT NULL AND failure_code IS NOT NULL AND failure_kind IN('VALIDATION','NOT_FOUND','CONFLICT','FORBIDDEN') AND failure_code ~ '^[a-z][a-z0-9_]{0,79}$'))
);
CREATE INDEX payroll_result_attempt ON payroll_run_results(company_id,run_id,job_id);
ALTER TABLE payroll_runs ADD FOREIGN KEY(company_id,id,last_result) REFERENCES payroll_run_results(company_id,run_id,ordinal) DEFERRABLE INITIALLY DEFERRED;

CREATE OR REPLACE FUNCTION protect_payroll_period() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='INSERT' THEN
  IF NEW.version<>0 OR NEW.status<>'DRAFT' OR NEW.current_run_id IS NOT NULL OR NEW.creation_transaction<>pg_current_xact_id()::text
   THEN RAISE EXCEPTION 'Payroll period must start as a draft' USING ERRCODE='23514'; END IF;
 ELSIF TG_OP='DELETE' THEN RAISE EXCEPTION 'Payroll period history is retained' USING ERRCODE='23514';
 ELSE
  IF (to_jsonb(NEW)-ARRAY['version','status','current_run_id']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['version','status','current_run_id']) OR NEW.version<>OLD.version+1 OR NOT
   ((OLD.status='DRAFT' AND NEW.status IN('CANCELLED','PROCESSING')) OR (OLD.status='PROCESSING' AND NEW.status IN('CALCULATED','DRAFT')) OR (OLD.status='CALCULATED' AND NEW.status='DRAFT'))
   THEN RAISE EXCEPTION 'Payroll period metadata and state transition are protected' USING ERRCODE='23514'; END IF;
 END IF;
 IF (NEW.status IN('DRAFT','CANCELLED') AND NEW.current_run_id IS NOT NULL) OR (NEW.status IN('PROCESSING','CALCULATED') AND NEW.current_run_id IS NULL)
  OR (TG_OP='UPDATE' AND NEW.status='CALCULATED' AND NEW.current_run_id IS DISTINCT FROM OLD.current_run_id)
  THEN RAISE EXCEPTION 'Payroll period requires its current run' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE OR REPLACE FUNCTION validate_payroll_period_change() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM payroll_periods WHERE company_id=NEW.company_id AND id=NEW.period_id AND version=NEW.revision AND status=NEW.status AND current_run_id IS NOT DISTINCT FROM NEW.run_id)
  THEN RAISE EXCEPTION 'Payroll period change must match its header' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE FUNCTION protect_payroll_run() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p payroll_periods; result payroll_run_results; BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended('payroll:'||NEW.company_id::text,0));
 IF TG_OP='INSERT' THEN
  SELECT * INTO p FROM payroll_periods WHERE company_id=NEW.company_id AND id=NEW.period_id;
  IF p.id IS NULL OR p.status<>'DRAFT' OR p.current_run_id IS NOT NULL OR NEW.version<>0 OR NEW.status<>'PROCESSING' OR NEW.processed<>0 OR
     NEW.creation_transaction<>pg_current_xact_id()::text OR (NEW.total_employees,NEW.planned_payment_date,NEW.timezone) IS DISTINCT FROM (p.participant_count,p.planned_payment_date,p.timezone) OR
     NEW.run_number<>(SELECT count(*)+1 FROM payroll_runs WHERE company_id=NEW.company_id AND period_id=NEW.period_id) OR
     NOT EXISTS(SELECT 1 FROM work_periods WHERE company_id=NEW.company_id AND month=NEW.earnings_month AND job_id=NEW.work_job_id AND version=NEW.work_period_version AND status='CLOSED' AND timezone=NEW.timezone)
   THEN RAISE EXCEPTION 'Payroll run requires its draft and closed workforce evidence' USING ERRCODE='23514'; END IF;
 ELSIF TG_OP='DELETE' THEN RAISE EXCEPTION 'Payroll run history is retained' USING ERRCODE='23514';
 ELSE
  IF (to_jsonb(NEW)-ARRAY['status','job_id','version','processed','succeeded','failed','last_result']) IS DISTINCT FROM
     (to_jsonb(OLD)-ARRAY['status','job_id','version','processed','succeeded','failed','last_result']) OR OLD.status='ABANDONED'
   THEN RAISE EXCEPTION 'Payroll run inputs are immutable' USING ERRCODE='23514'; END IF;
  IF NEW.processed<>OLD.processed THEN
   SELECT * INTO result FROM payroll_run_results WHERE company_id=NEW.company_id AND run_id=NEW.id AND ordinal=NEW.processed;
   IF OLD.status<>'PROCESSING' OR NEW.status<>OLD.status OR NEW.job_id<>OLD.job_id OR NEW.version<>OLD.version OR NEW.processed<>OLD.processed+1 OR result.ordinal IS NULL OR
      NEW.succeeded<>OLD.succeeded+(CASE WHEN result.status='SUCCEEDED' THEN 1 ELSE 0 END) OR NEW.failed<>OLD.failed+(CASE WHEN result.status='FAILED' THEN 1 ELSE 0 END)
    THEN RAISE EXCEPTION 'Payroll progress requires exactly its next retained result' USING ERRCODE='23514'; END IF;
  ELSE
   IF NEW.succeeded<>OLD.succeeded OR NEW.failed<>OLD.failed OR NEW.version<>OLD.version+1 THEN RAISE EXCEPTION 'Payroll lifecycle must preserve progress' USING ERRCODE='23514'; END IF;
   IF NEW.job_id=OLD.job_id THEN
    IF NOT ((OLD.status='PROCESSING' AND NEW.status IN('STOPPED','CALCULATED','ABANDONED')) OR (OLD.status IN('STOPPED','CALCULATED') AND NEW.status='ABANDONED'))
     THEN RAISE EXCEPTION 'Invalid payroll run transition' USING ERRCODE='23514'; END IF;
   ELSIF NEW.status<>'PROCESSING' OR OLD.status NOT IN('PROCESSING','STOPPED') OR NOT EXISTS(SELECT 1 FROM background_jobs WHERE company_id=OLD.company_id AND id=OLD.job_id AND status IN('FAILED','CANCELLED'))
    THEN RAISE EXCEPTION 'Only terminal payroll jobs can resume' USING ERRCODE='23514'; END IF;
  END IF;
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER payroll_run_protected BEFORE INSERT OR UPDATE OR DELETE ON payroll_runs FOR EACH ROW EXECUTE FUNCTION protect_payroll_run();
CREATE FUNCTION validate_payroll_run_target() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r payroll_runs; BEGIN
 SELECT * INTO r FROM payroll_runs WHERE company_id=NEW.company_id AND id=NEW.run_id;
 IF r.id IS NULL OR r.creation_transaction<>pg_current_xact_id()::text OR r.version<>0 OR r.status<>'PROCESSING' OR NEW.ordinal>r.total_employees
  THEN RAISE EXCEPTION 'Payroll target must be inserted with its run' USING ERRCODE='23514'; END IF;
 IF NEW.employment_version IS DISTINCT FROM (SELECT version FROM employments WHERE company_id=NEW.company_id AND id=NEW.employment_id)
  THEN RAISE EXCEPTION 'Payroll target requires its current employment version' USING ERRCODE='23514'; END IF;
 IF NEW.compensation_revision IS DISTINCT FROM (SELECT revision FROM employee_compensation_revisions WHERE company_id=NEW.company_id AND employment_id=NEW.employment_id AND effective_from<=r.earnings_month ORDER BY effective_from DESC,revision DESC LIMIT 1)
  THEN RAISE EXCEPTION 'Payroll target requires its effective compensation revision' USING ERRCODE='23514'; END IF;
 IF NEW.input_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM payroll_inputs WHERE company_id=NEW.company_id AND id=NEW.input_id AND employment_id=NEW.employment_id AND earnings_month=r.earnings_month AND version=NEW.input_revision)
  THEN RAISE EXCEPTION 'Payroll target requires its current monthly input' USING ERRCODE='23514'; END IF;
 IF NEW.tax_opening_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM payroll_tax_openings WHERE company_id=NEW.company_id AND id=NEW.tax_opening_id AND employment_id=NEW.employment_id AND tax_year=extract(year FROM r.earnings_month)::integer AND version=NEW.tax_opening_revision)
  THEN RAISE EXCEPTION 'Payroll target requires its current opening history' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_target_scope BEFORE INSERT ON payroll_run_targets FOR EACH ROW EXECUTE FUNCTION validate_payroll_run_target();
CREATE FUNCTION validate_payroll_run_attempt() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r payroll_runs; j background_jobs; BEGIN
 SELECT * INTO r FROM payroll_runs WHERE company_id=NEW.company_id AND id=NEW.run_id;
 SELECT * INTO j FROM background_jobs WHERE company_id=NEW.company_id AND id=NEW.job_id;
 IF r.id IS NULL OR j.id IS NULL OR j.actor_id<>r.actor_id OR j.kind<>'PAYROLL_CALCULATE' OR j.request->>'runId' IS DISTINCT FROM r.id::text OR j.progress_mode<>'FIXED_TOTAL' OR
    NEW.base_completed<>r.processed OR j.total_items<>r.total_employees-r.processed+1 OR NEW.attempt<>(SELECT count(*)+1 FROM payroll_run_attempts WHERE company_id=r.company_id AND run_id=r.id)
  THEN RAISE EXCEPTION 'Payroll attempt requires matching job scope and progress' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_attempt_scope BEFORE INSERT ON payroll_run_attempts FOR EACH ROW EXECUTE FUNCTION validate_payroll_run_attempt();
CREATE FUNCTION validate_payroll_run_result() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r payroll_runs; j background_jobs; t payroll_run_targets; i payroll_input_revisions; o payroll_tax_opening_revisions; BEGIN
 SELECT * INTO r FROM payroll_runs WHERE company_id=NEW.company_id AND id=NEW.run_id;
 SELECT * INTO j FROM background_jobs WHERE company_id=NEW.company_id AND id=NEW.job_id;
 IF r.id IS NULL OR r.status<>'PROCESSING' OR r.actor_id IS DISTINCT FROM current_actor_id() OR r.job_id<>NEW.job_id OR NEW.ordinal<>r.processed+1 OR
    j.id IS NULL OR j.status<>'RUNNING' OR j.cancellation_requested OR j.lease_until<=clock_timestamp()
  THEN RAISE EXCEPTION 'Payroll result requires its next active leased step' USING ERRCODE='23514'; END IF;
 IF NEW.status='SUCCEEDED' THEN
  SELECT * INTO t FROM payroll_run_targets WHERE company_id=r.company_id AND run_id=r.id AND ordinal=NEW.ordinal;
  SELECT * INTO i FROM payroll_input_revisions WHERE company_id=t.company_id AND input_id=t.input_id AND revision=t.input_revision;
  SELECT * INTO o FROM payroll_tax_opening_revisions WHERE company_id=t.company_id AND opening_id=t.tax_opening_id AND revision=t.tax_opening_revision;
  IF t.compensation_revision IS NULL OR i.status IS DISTINCT FROM 'VERIFIED' OR o.status IS DISTINCT FROM 'VERIFIED' OR i.employment_version<>t.employment_version OR
    i.work_job_id<>r.work_job_id OR i.work_period_version<>r.work_period_version OR
    (NEW.facts->>'month') IS DISTINCT FROM to_char(r.earnings_month,'YYYY-MM') OR (NEW.facts->>'incomeDueDate') IS DISTINCT FROM r.income_due_date::text OR
    (NEW.calculation->'tax'->>'taxableGross')::numeric IS DISTINCT FROM NEW.taxable_gross OR (NEW.calculation->'tax'->>'withheld')::numeric IS DISTINCT FROM NEW.withheld OR
    (NEW.calculation->'tax'->>'takeHome')::numeric IS DISTINCT FROM NEW.take_home
   THEN RAISE EXCEPTION 'Calculated payroll requires matching verified sources and totals' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_result_scope BEFORE INSERT ON payroll_run_results FOR EACH ROW EXECUTE FUNCTION validate_payroll_run_result();
CREATE FUNCTION check_payroll_run_checkpoint() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r payroll_runs; j background_jobs; a payroll_run_attempts; p payroll_periods; run_key uuid; BEGIN
 IF TG_TABLE_NAME='payroll_runs' THEN run_key:=NEW.id;
 ELSIF TG_TABLE_NAME='background_jobs' THEN run_key:=(NEW.request->>'runId')::uuid;
 ELSIF TG_TABLE_NAME='payroll_periods' THEN
  SELECT * INTO p FROM payroll_periods WHERE company_id=NEW.company_id AND id=NEW.id;
  run_key:=p.current_run_id;
  IF run_key IS NULL THEN
   IF EXISTS(SELECT 1 FROM payroll_runs WHERE company_id=NEW.company_id AND period_id=NEW.id AND status<>'ABANDONED')
    THEN RAISE EXCEPTION 'Active payroll run cannot be detached' USING ERRCODE='23514'; END IF;
   RETURN NULL;
  END IF;
 ELSE run_key:=NEW.run_id; END IF;
 SELECT * INTO r FROM payroll_runs WHERE company_id=NEW.company_id AND id=run_key;
 IF r.id IS NULL THEN RAISE EXCEPTION 'Payroll checkpoint requires its run' USING ERRCODE='23514'; END IF;
 SELECT * INTO p FROM payroll_periods WHERE company_id=r.company_id AND id=r.period_id;
 SELECT * INTO j FROM background_jobs WHERE company_id=r.company_id AND id=r.job_id;
 SELECT * INTO a FROM payroll_run_attempts WHERE company_id=r.company_id AND run_id=r.id AND job_id=r.job_id;
 IF a.job_id IS NULL OR r.processed<>a.base_completed+j.completed_items-(CASE WHEN j.status='SUCCEEDED' THEN 1 ELSE 0 END) OR
  (r.status='CALCULATED' AND (r.processed<>r.total_employees OR j.status<>'SUCCEEDED' OR p.status<>'CALCULATED' OR p.current_run_id IS DISTINCT FROM r.id)) OR
  (r.status IN('PROCESSING','STOPPED') AND (p.status<>'PROCESSING' OR p.current_run_id IS DISTINCT FROM r.id)) OR
  (r.status='STOPPED' AND j.status NOT IN('FAILED','CANCELLED')) OR
  (r.status='ABANDONED' AND (p.current_run_id=r.id OR j.status NOT IN('FAILED','CANCELLED','SUCCEEDED'))) OR
  (j.status='SUCCEEDED' AND r.status NOT IN('CALCULATED','ABANDONED'))
  THEN RAISE EXCEPTION 'Payroll checkpoint, period and retained outcomes must agree' USING ERRCODE='23514'; END IF;
 IF TG_TABLE_NAME='payroll_run_attempts' THEN
  IF NEW.job_id<>r.job_id THEN RAISE EXCEPTION 'Payroll attempt must bind its current run' USING ERRCODE='23514'; END IF;
 ELSIF TG_TABLE_NAME='payroll_run_results' THEN
  IF NEW.ordinal>r.processed THEN RAISE EXCEPTION 'Payroll result must advance progress' USING ERRCODE='23514'; END IF;
 END IF;
 IF TG_TABLE_NAME='payroll_runs' AND TG_OP='INSERT' THEN
  IF r.total_employees<>(SELECT count(*) FROM payroll_run_targets WHERE company_id=r.company_id AND run_id=r.id)
   THEN RAISE EXCEPTION 'Payroll target snapshot must be complete' USING ERRCODE='23514'; END IF;
  IF EXISTS(SELECT 1 FROM leave_requests l JOIN payroll_run_targets t ON t.company_id=l.company_id AND t.employment_id=l.employment_id
    WHERE t.company_id=r.company_id AND t.run_id=r.id AND l.status IN('PENDING','CANCELLATION_PENDING') AND
      EXISTS(SELECT 1 FROM jsonb_array_elements(l.days) d WHERE date_trunc('month',(d->>'workDate')::date)::date=r.earnings_month))
   THEN RAISE EXCEPTION 'Pending leave must be resolved before payroll cutoff' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER payroll_run_checkpoint AFTER INSERT OR UPDATE ON payroll_runs DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_payroll_run_checkpoint();
CREATE CONSTRAINT TRIGGER payroll_period_run_checkpoint AFTER INSERT OR UPDATE ON payroll_periods DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_payroll_run_checkpoint();
CREATE CONSTRAINT TRIGGER payroll_result_checkpoint AFTER INSERT ON payroll_run_results DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_payroll_run_checkpoint();
CREATE CONSTRAINT TRIGGER payroll_attempt_checkpoint AFTER INSERT ON payroll_run_attempts DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_payroll_run_checkpoint();
CREATE CONSTRAINT TRIGGER payroll_job_checkpoint AFTER UPDATE ON background_jobs DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
 WHEN(NEW.kind='PAYROLL_CALCULATE' AND (NEW.completed_items IS DISTINCT FROM OLD.completed_items OR NEW.status='SUCCEEDED')) EXECUTE FUNCTION check_payroll_run_checkpoint();

-- Cutoff is a source-write boundary shared with use cases, not an implicit retry or recalculation.
CREATE FUNCTION guard_payroll_source_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE employee_key uuid; first_month date; last_month date; closed boolean; BEGIN
 PERFORM pg_advisory_xact_lock_shared(hashtextextended('payroll:'||NEW.company_id::text,0));
 IF TG_TABLE_NAME='leave_requests' THEN
  SELECT EXISTS(SELECT 1 FROM payroll_runs r JOIN payroll_run_targets t ON t.company_id=r.company_id AND t.run_id=r.id
   WHERE r.company_id=NEW.company_id AND t.employment_id=NEW.employment_id AND r.status<>'ABANDONED' AND
    EXISTS(SELECT 1 FROM jsonb_array_elements(NEW.days) d WHERE date_trunc('month',(d->>'workDate')::date)::date=r.earnings_month)) INTO closed;
 ELSE
  IF TG_TABLE_NAME='payroll_policy_revisions' THEN employee_key:=NULL;first_month:=NEW.effective_from;last_month:=NULL;
  ELSIF TG_TABLE_NAME='employee_compensation_revisions' THEN employee_key:=NEW.employment_id;first_month:=NEW.effective_from;last_month:=NULL;
  ELSIF TG_TABLE_NAME='payroll_inputs' THEN employee_key:=NEW.employment_id;first_month:=NEW.earnings_month;last_month:=NEW.earnings_month;
  ELSE employee_key:=NEW.employment_id;first_month:=make_date(NEW.tax_year,1,1);last_month:=make_date(NEW.tax_year,12,1); END IF;
  SELECT EXISTS(SELECT 1 FROM payroll_runs r WHERE r.company_id=NEW.company_id AND r.status<>'ABANDONED' AND r.earnings_month>=first_month AND
   (last_month IS NULL OR r.earnings_month<=last_month) AND (employee_key IS NULL OR EXISTS(SELECT 1 FROM payroll_run_targets t WHERE t.company_id=r.company_id AND t.run_id=r.id AND t.employment_id=employee_key))) INTO closed;
 END IF;
 IF closed THEN RAISE EXCEPTION 'Payroll source is frozen by an active run' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payroll_leave_cutoff BEFORE INSERT OR UPDATE ON leave_requests FOR EACH ROW EXECUTE FUNCTION guard_payroll_source_change();
CREATE TRIGGER payroll_input_cutoff BEFORE INSERT OR UPDATE ON payroll_inputs FOR EACH ROW EXECUTE FUNCTION guard_payroll_source_change();
CREATE TRIGGER payroll_opening_cutoff BEFORE INSERT OR UPDATE ON payroll_tax_openings FOR EACH ROW EXECUTE FUNCTION guard_payroll_source_change();
CREATE TRIGGER payroll_compensation_cutoff BEFORE INSERT ON employee_compensation_revisions FOR EACH ROW EXECUTE FUNCTION guard_payroll_source_change();
CREATE TRIGGER payroll_policy_cutoff BEFORE INSERT ON payroll_policy_revisions FOR EACH ROW EXECUTE FUNCTION guard_payroll_source_change();
DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['payroll_runs','payroll_run_targets','payroll_run_attempts','payroll_run_results'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
  EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
 END LOOP;
 FOREACH t IN ARRAY ARRAY['payroll_run_targets','payroll_run_attempts','payroll_run_results'] LOOP
  EXECUTE format('CREATE TRIGGER immutable_history BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION deny_history_mutation()',t);
 END LOOP;
END $$;
CREATE POLICY payroll_run_creator ON payroll_runs AS RESTRICTIVE FOR INSERT WITH CHECK(actor_id=current_actor_id());
